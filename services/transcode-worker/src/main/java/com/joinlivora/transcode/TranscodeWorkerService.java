package com.joinlivora.transcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core transcode worker service.
 *
 * <p><strong>Job lifecycle:</strong>
 * <ol>
 *   <li>A dedicated BLPOP-polling thread blocks on {@code stream:transcode:jobs}.</li>
 *   <li>When a job arrives it is submitted to a bounded {@link ExecutorService}
 *       ({@code transcode.max-concurrent} threads).</li>
 *   <li>Each job thread spawns an FFmpeg child process with NVIDIA h264_nvenc and
 *       writes multi-bitrate HLS to {@code /streams/{streamId}/}.</li>
 *   <li>On FFmpeg exit with a non-zero code the job is retried up to
 *       {@code transcode.max-retries} times with a configurable backoff.</li>
 *   <li>A Redis Pub/Sub subscriber on {@code stream:transcode:stop} calls
 *       {@link Process#destroyForcibly()} on the running FFmpeg process and
 *       schedules HLS cleanup.</li>
 * </ol>
 *
 * <p><strong>Fault tolerance:</strong>
 * <ul>
 *   <li>Redis outage during BLPOP: the polling thread catches the exception,
 *       waits 5 s, and retries — no jobs are lost because the List is durable.</li>
 *   <li>FFmpeg crash: automatic retry up to {@code max-retries}.
 *       After exhausting retries, the stream is logged as DEGRADED (no crash).</li>
 *   <li>Stop signal arrives for a stream with no running process: silently ignored.</li>
 *   <li>Worker shutdown: {@code @PreDestroy} drains the executor and kills all
 *       running FFmpeg processes before the JVM exits.</li>
 * </ul>
 */
@Service
@Slf4j
public class TranscodeWorkerService {

    // ── constants ────────────────────────────────────────────────────────────

    private static final String HLS_MASTER_TEMPLATE = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=3128000,RESOLUTION=1280x720,CODECS="avc1.640028,mp4a.40.2",FRAME-RATE=30.000
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1596000,RESOLUTION=854x480,CODECS="avc1.4d401f,mp4a.40.2",FRAME-RATE=30.000
            480p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=864000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2",FRAME-RATE=30.000
            360p.m3u8
            """;

    /** Redis key prefix for per-stream worker heartbeats. */
    private static final String WORKER_HEARTBEAT_KEY_PREFIX = "stream:worker:";

    // ── collaborators ────────────────────────────────────────────────────────

    private final StringRedisTemplate redisTemplate;
    private final TranscodeProperties props;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;
    private final S3UploadService s3UploadService;

    // ── state ────────────────────────────────────────────────────────────────

    /** streamId → running FFmpeg Process */
    private final ConcurrentHashMap<UUID, Process> runningProcesses = new ConcurrentHashMap<>();

    /** streamId → retry attempt count */
    private final ConcurrentHashMap<UUID, AtomicInteger> retryCounts = new ConcurrentHashMap<>();

    /** Guards the BLPOP polling loop during shutdown. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Bounded thread pool — one thread per concurrent transcode. */
    private ExecutorService transcodeExecutor;

    /** Single-thread that drives the BLPOP polling loop. */
    private Thread pollingThread;

    /** Single-thread scheduled executor for heartbeat + S3 watcher. */
    private ScheduledExecutorService heartbeatExecutor;

    /**
     * Tracks files already uploaded to S3 per stream.
     * Key = streamId, Value = set of filenames already uploaded.
     */
    private final ConcurrentHashMap<UUID, Set<String>> uploadedFiles = new ConcurrentHashMap<>();

    // ── metrics ──────────────────────────────────────────────────────────────

    private Counter jobsStartedCounter;
    private Counter jobsCompletedCounter;
    private Counter jobsFailedCounter;
    private Counter jobsRetriedCounter;
    private Counter stopSignalsCounter;

    // ── constructor ──────────────────────────────────────────────────────────

    /** Watchdog for auto-requeue of orphaned jobs on worker crash. */
    private TranscodeHeartbeatWatchdog heartbeatWatchdog;

    public TranscodeWorkerService(StringRedisTemplate redisTemplate,
                                  TranscodeProperties props,
                                  ObjectMapper objectMapper,
                                  RedisMessageListenerContainer listenerContainer,
                                  S3UploadService s3UploadService,
                                  MeterRegistry meterRegistry,
                                  TranscodeHeartbeatWatchdog heartbeatWatchdog) {
        this.redisTemplate       = redisTemplate;
        this.props               = props;
        this.objectMapper        = objectMapper;
        this.listenerContainer   = listenerContainer;
        this.s3UploadService     = s3UploadService;
        this.heartbeatWatchdog   = heartbeatWatchdog;

        // Micrometer metrics
        this.jobsStartedCounter   = Counter.builder("transcode.jobs.started")
                .description("Total transcode jobs started").register(meterRegistry);
        this.jobsCompletedCounter = Counter.builder("transcode.jobs.completed")
                .description("Total transcode jobs completed successfully").register(meterRegistry);
        this.jobsFailedCounter    = Counter.builder("transcode.jobs.failed")
                .description("Total transcode jobs that exhausted retries").register(meterRegistry);
        this.jobsRetriedCounter   = Counter.builder("transcode.jobs.retried")
                .description("Total FFmpeg restart attempts").register(meterRegistry);
        this.stopSignalsCounter   = Counter.builder("transcode.stop.signals")
                .description("Total stop signals received").register(meterRegistry);

        Gauge.builder("transcode.jobs.active", runningProcesses, ConcurrentHashMap::size)
                .description("Currently running FFmpeg processes")
                .register(meterRegistry);
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void start() {
        transcodeExecutor = new ThreadPoolExecutor(
                props.getMaxConcurrent(),
                props.getMaxConcurrent(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "transcode-worker");
                    t.setDaemon(false);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()   // backpressure: block BLPOP thread if all slots full
        );

        registerStopSignalListener();

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "transcode-heartbeat")
        );
        heartbeatExecutor.scheduleAtFixedRate(
                this::publishHeartbeats,
                props.getHeartbeatIntervalMs(),
                props.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        if (s3UploadService.isEnabled()) {
            heartbeatExecutor.scheduleAtFixedRate(
                    this::watchAndUpload,
                    props.getS3WatchIntervalMs(),
                    props.getS3WatchIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
            log.info("TRANSCODE_S3_WATCHER_STARTED: interval={}ms", props.getS3WatchIntervalMs());
        }

        running.set(true);
        pollingThread = new Thread(this::pollRedisLoop, "transcode-blpop");
        pollingThread.setDaemon(false);
        pollingThread.start();

        log.info("TRANSCODE_WORKER_STARTED: maxConcurrent={} outputDir={} region={} s3={}",
                props.getMaxConcurrent(), props.getOutputDir(),
                props.getRegion(), props.isS3Enabled());
    }

    @PreDestroy
    public void stop() {
        log.info("TRANSCODE_WORKER_SHUTTING_DOWN: terminating {} active processes", runningProcesses.size());
        running.set(false);

        // Kill all running FFmpeg processes
        runningProcesses.forEach((streamId, process) -> {
            if (process.isAlive()) {
                log.info("TRANSCODE_SHUTDOWN_KILL: streamId={}", streamId);
                process.destroyForcibly();
            }
        });
        runningProcesses.clear();

        if (pollingThread != null) {
            pollingThread.interrupt();
        }

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }

        transcodeExecutor.shutdownNow();
        try {
            if (!transcodeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("TRANSCODE_EXECUTOR_DRAIN_TIMEOUT: forcing shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("TRANSCODE_WORKER_STOPPED");
    }

    // ── BLPOP polling loop ───────────────────────────────────────────────────

    /**
     * Blocks on {@code stream:transcode:jobs} via BLPOP.
     * Runs on a dedicated thread; {@code blpopTimeout} seconds ensures the loop
     * checks {@code running} frequently enough for clean shutdown.
     */
    private void pollRedisLoop() {
        log.info("TRANSCODE_BLPOP_LOOP_START: key={}", props.getJobsListKey());
        while (running.get()) {
            try {
                List<String> result = redisTemplate.opsForList()
                        .leftPop(props.getJobsListKey(), Duration.ofSeconds(props.getBlpopTimeout()));

                if (result != null && !result.isEmpty()) {
                    // result is the raw JSON payload
                    String payload = result.get(0);
                    dispatchJob(payload);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    log.error("TRANSCODE_BLPOP_ERROR: {} — retrying in 5s", e.getMessage());
                    sleepUninterruptibly(5_000);
                }
            }
        }
        log.info("TRANSCODE_BLPOP_LOOP_STOPPED");
    }

    // ── job dispatching ──────────────────────────────────────────────────────

    private void dispatchJob(String payload) {
        TranscodeJob job;
        try {
            job = objectMapper.readValue(payload, TranscodeJob.class);
        } catch (Exception e) {
            log.error("TRANSCODE_JOB_PARSE_ERROR: payload='{}' reason={}", payload, e.getMessage());
            return;
        }

        if (job.getStreamId() == null || job.getRtmpUrl() == null) {
            log.error("TRANSCODE_JOB_INVALID: missing streamId or rtmpUrl in payload='{}'", payload);
            return;
        }

        log.info("TRANSCODE_JOB_RECEIVED: streamId={} creatorId={} rtmpUrl={}",
                job.getStreamId(), job.getCreatorId(), job.getRtmpUrl());

        retryCounts.put(job.getStreamId(), new AtomicInteger(0));
        heartbeatWatchdog.registerActiveJob(job.getStreamId(), job);
        transcodeExecutor.submit(() -> runWithRetry(job));
    }

    // ── FFmpeg execution with retry ──────────────────────────────────────────

    /**
     * Runs FFmpeg for the given job, retrying on non-zero exit up to
     * {@code transcode.max-retries} times. After exhausting retries the stream
     * is logged as DEGRADED but the worker continues processing other jobs.
     */
    private void runWithRetry(TranscodeJob job) {
        UUID streamId = job.getStreamId();
        AtomicInteger retries = retryCounts.get(streamId);
        int maxRetries = props.getMaxRetries();

        while (running.get()) {
            int attempt = (retries == null ? 0 : retries.get()) + 1;
            log.info("TRANSCODE_FFMPEG_START: streamId={} attempt={}/{}", streamId, attempt, maxRetries + 1);

            try {
                int exitCode = launchFfmpeg(job);

                if (exitCode == 0) {
                    log.info("TRANSCODE_FFMPEG_DONE: streamId={} exitCode=0", streamId);
                    jobsCompletedCounter.increment();
                    cleanup(streamId);
                    return;
                }

                // Non-zero exit — check if it was a deliberate kill (stop signal)
                if (!running.get() || !runningProcesses.containsKey(streamId)) {
                    log.info("TRANSCODE_FFMPEG_KILLED: streamId={} (stop signal or shutdown)", streamId);
                    cleanup(streamId);
                    return;
                }

                log.warn("TRANSCODE_FFMPEG_FAILED: streamId={} exitCode={} attempt={}/{}",
                        streamId, exitCode, attempt, maxRetries + 1);

                if (retries != null && retries.incrementAndGet() > maxRetries) {
                    log.error("TRANSCODE_STREAM_DEGRADED: streamId={} — exhausted {} retries, " +
                              "stream will be WebRTC-only", streamId, maxRetries);
                    jobsFailedCounter.increment();
                    cleanup(streamId);
                    return;
                }

                jobsRetriedCounter.increment();
                log.info("TRANSCODE_RETRY_WAIT: streamId={} delayMs={}", streamId, props.getRetryDelayMs());
                sleepUninterruptibly(props.getRetryDelayMs());

            } catch (IOException e) {
                log.error("TRANSCODE_FFMPEG_LAUNCH_ERROR: streamId={} reason={}", streamId, e.getMessage());
                if (retries != null && retries.incrementAndGet() > maxRetries) {
                    log.error("TRANSCODE_STREAM_DEGRADED: streamId={} — cannot launch FFmpeg", streamId);
                    jobsFailedCounter.increment();
                    cleanup(streamId);
                    return;
                }
                jobsRetriedCounter.increment();
                sleepUninterruptibly(props.getRetryDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("TRANSCODE_INTERRUPTED: streamId={}", streamId);
                cleanup(streamId);
                return;
            }
        }
    }

    // ── FFmpeg process management ────────────────────────────────────────────

    /**
     * Builds and launches the FFmpeg process, tracking it in {@code runningProcesses}.
     *
     * <p>FFmpeg command structure:
     * <pre>
     * ffmpeg -hwaccel cuda -i {rtmpUrl}
     *   -filter_complex "[0:v]split=3[v1][v2][v3]"
     *   -map "[v1]" -c:v h264_nvenc -b:v 3000k -s 1280x720  -map 0:a -c:a aac -b:a 128k
     *                -f hls -hls_time 4 -hls_playlist_type event
     *                -hls_segment_filename {outputDir}/{streamId}/720p_%03d.ts
     *                {outputDir}/{streamId}/720p.m3u8
     *   -map "[v2]" -c:v h264_nvenc -b:v 1500k -s 854x480   ...  480p.m3u8
     *   -map "[v3]" -c:v h264_nvenc -b:v 800k  -s 640x360   ...  360p.m3u8
     * </pre>
     *
     * @return FFmpeg exit code; 0 = success, non-zero = error
     */
    private int launchFfmpeg(TranscodeJob job) throws IOException, InterruptedException {
        UUID streamId = job.getStreamId();
        String outputBase = props.getOutputDir() + "/" + streamId;

        // Ensure output directory exists
        Files.createDirectories(Paths.get(outputBase));

        // Initialise per-stream upload tracking set
        uploadedFiles.putIfAbsent(streamId, ConcurrentHashMap.newKeySet());

        // Write master playlist (static — variants are fixed)
        writeMasterPlaylist(outputBase, streamId);

        List<String> cmd = buildFfmpegCommand(job.getRtmpUrl(), outputBase);
        log.debug("TRANSCODE_CMD: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("CUDA_VISIBLE_DEVICES", "0");
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(outputBase + "/ffmpeg.log"));

        Process process = pb.start();
        runningProcesses.put(streamId, process);
        jobsStartedCounter.increment();

        log.info("TRANSCODE_PROCESS_STARTED: streamId={} pid={}", streamId,
                process.toHandle().pid());

        int exitCode = process.waitFor();

        // Remove from tracking map (stop signal may have already removed it)
        runningProcesses.remove(streamId, process);
        return exitCode;
    }

    private List<String> buildFfmpegCommand(String rtmpUrl, String outputBase) {
        String timeout  = String.valueOf(props.getRtmpConnectTimeout());
        boolean llMode  = props.isLowLatencyMode();

        // Segment duration: 2s for low-latency, 4s for standard
        String hlsTime  = llMode ? "2" : "4";
        // Playlist window: 4 segments for LL (8s buffer), 6 for standard (24s buffer)
        String listSize = llMode ? "4" : "6";
        // Flags: add split_by_time + program_date_time for LL-HLS player support
        String hlsFlags = llMode
                ? "delete_segments+independent_segments+split_by_time+program_date_time"
                : "delete_segments+independent_segments";

        // Timestamp-based segment filename pattern.
        // Format: {quality}_%Y%m%d_%s_%04d.ts  (e.g. 720p_20250101_1735689600_0001.ts)
        //
        // WHY TIMESTAMPS:
        //   Sequential %04d names reuse filenames across stream restarts, causing CDN
        //   cache poisoning: a cached segment from stream A has the same URL as a new
        //   segment from stream B.  Epoch-second timestamps in the filename ensure each
        //   segment URL is globally unique, making immutable CDN caching safe.
        String seg720 = outputBase + "/720p_%Y%m%d_%s_%04d.ts";
        String seg480 = outputBase + "/480p_%Y%m%d_%s_%04d.ts";
        String seg360 = outputBase + "/360p_%Y%m%d_%s_%04d.ts";

        log.info("TRANSCODE_BUILD_COMMAND: lowLatency={} hlsTime={}s listSize={}", llMode, hlsTime, listSize);

        List<String> cmd = new ArrayList<>();

        cmd.add("ffmpeg");
        // ── Input ──────────────────────────────────────────────────────────
        cmd.add("-hwaccel");        cmd.add("cuda");
        cmd.add("-hwaccel_output_format"); cmd.add("cuda");
        cmd.add("-rtmp_conn_timeout"); cmd.add(timeout);
        cmd.add("-i");              cmd.add(rtmpUrl);
        // ── Split filter ────────────────────────────────────────────────────
        cmd.add("-filter_complex"); cmd.add("[0:v]split=3[v1][v2][v3]");
        // ── 720p output ─────────────────────────────────────────────────────
        cmd.add("-map");  cmd.add("[v1]");
        cmd.add("-c:v");  cmd.add("h264_nvenc");
        cmd.add("-preset"); cmd.add("p4");    // NVENC preset: balanced quality/speed
        cmd.add("-b:v");  cmd.add("3000k");
        cmd.add("-maxrate"); cmd.add("3300k");
        cmd.add("-bufsize"); cmd.add("6000k");
        cmd.add("-s");    cmd.add("1280x720");
        cmd.add("-map");  cmd.add("0:a");
        cmd.add("-c:a");  cmd.add("aac");
        cmd.add("-b:a");  cmd.add("128k");
        cmd.add("-f");    cmd.add("hls");
        cmd.add("-hls_time"); cmd.add(hlsTime);
        cmd.add("-hls_playlist_type"); cmd.add("live");
        cmd.add("-hls_list_size"); cmd.add(listSize);
        cmd.add("-hls_flags"); cmd.add(hlsFlags);
        cmd.add("-hls_segment_filename"); cmd.add(seg720);
        cmd.add(outputBase + "/720p.m3u8");
        // ── 480p output ─────────────────────────────────────────────────────
        cmd.add("-map");  cmd.add("[v2]");
        cmd.add("-c:v");  cmd.add("h264_nvenc");
        cmd.add("-preset"); cmd.add("p4");
        cmd.add("-b:v");  cmd.add("1500k");
        cmd.add("-maxrate"); cmd.add("1650k");
        cmd.add("-bufsize"); cmd.add("3000k");
        cmd.add("-s");    cmd.add("854x480");
        cmd.add("-map");  cmd.add("0:a");
        cmd.add("-c:a");  cmd.add("aac");
        cmd.add("-b:a");  cmd.add("96k");
        cmd.add("-f");    cmd.add("hls");
        cmd.add("-hls_time"); cmd.add(hlsTime);
        cmd.add("-hls_playlist_type"); cmd.add("live");
        cmd.add("-hls_list_size"); cmd.add(listSize);
        cmd.add("-hls_flags"); cmd.add(hlsFlags);
        cmd.add("-hls_segment_filename"); cmd.add(seg480);
        cmd.add(outputBase + "/480p.m3u8");
        // ── 360p output ─────────────────────────────────────────────────────
        cmd.add("-map");  cmd.add("[v3]");
        cmd.add("-c:v");  cmd.add("h264_nvenc");
        cmd.add("-preset"); cmd.add("p4");
        cmd.add("-b:v");  cmd.add("800k");
        cmd.add("-maxrate"); cmd.add("880k");
        cmd.add("-bufsize"); cmd.add("1600k");
        cmd.add("-s");    cmd.add("640x360");
        cmd.add("-map");  cmd.add("0:a");
        cmd.add("-c:a");  cmd.add("aac");
        cmd.add("-b:a");  cmd.add("64k");
        cmd.add("-f");    cmd.add("hls");
        cmd.add("-hls_time"); cmd.add(hlsTime);
        cmd.add("-hls_playlist_type"); cmd.add("live");
        cmd.add("-hls_list_size"); cmd.add(listSize);
        cmd.add("-hls_flags"); cmd.add(hlsFlags);
        cmd.add("-hls_segment_filename"); cmd.add(seg360);
        cmd.add(outputBase + "/360p.m3u8");

        return cmd;
    }

    // ── stop signal handling ─────────────────────────────────────────────────

    /**
     * Registers a Redis Pub/Sub listener on {@code stream:transcode:stop}.
     * The main backend publishes a plain UUID string to this channel when a
     * stream ends. We kill the corresponding FFmpeg process immediately.
     */
    private void registerStopSignalListener() {
        MessageListener stopListener = (Message message, byte[] pattern) -> {
            try {
                String streamIdStr = new String(message.getBody()).trim();
                // Strip surrounding quotes if the value was serialized as a JSON string
                streamIdStr = streamIdStr.replaceAll("^\"|\"$", "");
                UUID streamId = UUID.fromString(streamIdStr);
                handleStopSignal(streamId);
            } catch (Exception e) {
                log.error("TRANSCODE_STOP_SIGNAL_PARSE_ERROR: body='{}' reason={}",
                        new String(message.getBody()), e.getMessage());
            }
        };

        listenerContainer.addMessageListener(
                stopListener,
                new ChannelTopic(props.getStopChannel())
        );
        log.info("TRANSCODE_STOP_LISTENER_REGISTERED: channel={}", props.getStopChannel());
    }

    private void handleStopSignal(UUID streamId) {
        stopSignalsCounter.increment();
        Process process = runningProcesses.remove(streamId);

        if (process == null) {
            log.info("TRANSCODE_STOP_NO_PROCESS: streamId={} (not running on this node)", streamId);
            return;
        }

        if (process.isAlive()) {
            log.info("TRANSCODE_STOP_KILL: streamId={} pid={}", streamId, process.toHandle().pid());
            process.destroyForcibly();
        }

        // Schedule async cleanup to avoid blocking the Pub/Sub thread
        CompletableFuture.runAsync(() -> deleteHlsFiles(streamId), transcodeExecutor)
                .exceptionally(ex -> {
                    log.warn("TRANSCODE_CLEANUP_ERROR: streamId={} reason={}", streamId, ex.getMessage());
                    return null;
                });
    }

    // ── HLS file cleanup ─────────────────────────────────────────────────────

    private void deleteHlsFiles(UUID streamId) {
        Path dir = Paths.get(props.getOutputDir(), streamId.toString());
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
            log.info("TRANSCODE_CLEANUP_DONE: streamId={} dir={}", streamId, dir);
        } catch (IOException e) {
            log.warn("TRANSCODE_CLEANUP_FAILED: streamId={} reason={}", streamId, e.getMessage());
        }
    }

    private void cleanup(UUID streamId) {
        runningProcesses.remove(streamId);
        retryCounts.remove(streamId);
        uploadedFiles.remove(streamId);
        // Delete heartbeat key immediately on clean exit
        deleteHeartbeat(streamId);
        // Deregister from the watchdog so it doesn't try to re-queue a completed job
        heartbeatWatchdog.deregisterActiveJob(streamId);
    }

    // ── Worker heartbeat ─────────────────────────────────────────────────────

    /**
     * Publishes a heartbeat key {@code stream:worker:{streamId}} for every
     * currently-running stream.  TTL = {@link TranscodeProperties#getHeartbeatTtlSeconds()}.
     *
     * <p>If the key expires without renewal (worker crash), {@link TranscodeHeartbeatWatchdog}
     * detects the orphan within ~8 s and re-enqueues the job automatically.
     */
    private void publishHeartbeats() {
        if (!props.isHeartbeatEnabled() || runningProcesses.isEmpty()) return;
        runningProcesses.keySet().forEach(streamId -> {
            try {
                String key   = WORKER_HEARTBEAT_KEY_PREFIX + streamId;
                String value = props.getRegion() + ":" + System.currentTimeMillis();
                redisTemplate.opsForValue().set(key, value,
                        Duration.ofSeconds(props.getHeartbeatTtlSeconds()));
                log.debug("HEARTBEAT_PUBLISHED: streamId={} region={}", streamId, props.getRegion());
            } catch (Exception e) {
                log.warn("HEARTBEAT_PUBLISH_ERROR: streamId={} reason={}", streamId, e.getMessage());
            }
        });
    }

    private void deleteHeartbeat(UUID streamId) {
        try {
            redisTemplate.delete(WORKER_HEARTBEAT_KEY_PREFIX + streamId);
        } catch (Exception e) {
            log.debug("HEARTBEAT_DELETE_ERROR: streamId={} reason={}", streamId, e.getMessage());
        }
    }

    // ── S3 segment watcher ───────────────────────────────────────────────────

    /**
     * Scans the output directory for every active stream and uploads any
     * {@code .ts} or {@code .m3u8} files that have not been uploaded yet.
     *
     * <p>Called every {@link TranscodeProperties#getS3WatchIntervalMs()} ms by the
     * heartbeat executor.  All uploads are fire-and-forget via
     * {@link S3UploadService#upload(Path, String)}.
     */
    private void watchAndUpload() {
        if (runningProcesses.isEmpty()) return;
        runningProcesses.keySet().forEach(streamId -> {
            Path dir = Paths.get(props.getOutputDir(), streamId.toString());
            if (!Files.exists(dir)) return;
            Set<String> uploaded = uploadedFiles.computeIfAbsent(streamId, k -> ConcurrentHashMap.newKeySet());
            try (var walk = Files.list(dir)) {
                walk.filter(p -> {
                    String name = p.getFileName().toString();
                    return (name.endsWith(".ts") || name.endsWith(".m3u8"))
                            && !uploaded.contains(name);
                }).forEach(file -> {
                    String relKey = streamId + "/" + file.getFileName();
                    s3UploadService.upload(file, relKey).thenAccept(ok -> {
                        if (ok) uploaded.add(file.getFileName().toString());
                    });
                });
            } catch (IOException e) {
                log.debug("S3_WATCH_SCAN_ERROR: streamId={} reason={}", streamId, e.getMessage());
            }
        });
    }

    private void writeMasterPlaylist(String outputBase, UUID streamId) {
        Path master = Paths.get(outputBase, "master.m3u8");
        if (Files.exists(master)) return;
        try {
            Files.writeString(master, HLS_MASTER_TEMPLATE);
            // Upload master playlist immediately when S3 is enabled
            s3UploadService.uploadMasterPlaylist(master, streamId.toString());
        } catch (IOException e) {
            log.warn("TRANSCODE_MASTER_PLAYLIST_WRITE_FAILED: dir={} reason={}", outputBase, e.getMessage());
        }
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
