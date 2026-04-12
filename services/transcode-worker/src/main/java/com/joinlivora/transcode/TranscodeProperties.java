package com.joinlivora.transcode;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed binding for the {@code transcode.*} configuration namespace.
 * All values can be overridden via environment variables or
 * {@code application.yml} — see {@code services/transcode-worker/src/main/resources/application.yml}.
 */
@Component
@ConfigurationProperties(prefix = "transcode")
@Data
public class TranscodeProperties {

    /** HLS output root directory (must match the Docker volume mount). */
    private String outputDir = "/streams";

    /** Maximum number of concurrent FFmpeg processes on this worker instance. */
    private int maxConcurrent = 4;

    /** Seconds to wait for an RTMP stream to appear before the first retry. */
    private int rtmpConnectTimeout = 30;

    /** Maximum FFmpeg restart attempts before a stream is marked degraded. */
    private int maxRetries = 3;

    /** Delay in milliseconds between retry attempts. */
    private long retryDelayMs = 5000;

    /** Redis List key that BLPOP polls for incoming jobs. */
    private String jobsListKey = "stream:transcode:jobs";

    /** Redis Pub/Sub channel for stop signals from the main backend. */
    private String stopChannel = "stream:transcode:stop";

    /** BLPOP blocking timeout in seconds. Use 5 to allow graceful shutdown checks. */
    private int blpopTimeout = 5;

    /**
     * When {@code true}, uses 2-second HLS segments (4-segment list) and enables
     * {@code split_by_time + program_date_time} flags for Low-Latency HLS (LL-HLS).
     * Target end-to-end latency: 3–5 seconds.
     * When {@code false} (default), uses 4-second segments (6-segment list).
     * Standard HLS latency: 20–30 seconds.
     */
    private boolean lowLatencyMode = false;

    // ── S3 / R2 object storage ─────────────────────────────────────────────────

    /**
     * When {@code true}, completed HLS segments and playlists are uploaded to S3/R2
     * after each FFmpeg segment flush.  Local files are retained as a local cache
     * and deleted on stream end.  When {@code false} (default), output stays on the
     * local {@link #outputDir} volume only (single-region mode).
     */
    private boolean s3Enabled = false;

    /** S3 or R2 bucket name (e.g. {@code livora-hls}). */
    private String s3Bucket = "livora-hls";

    /**
     * S3-compatible endpoint URL.
     * For AWS S3 leave empty (SDK resolves from region).
     * For Cloudflare R2 set to {@code https://<account-id>.r2.cloudflarestorage.com}.
     */
    private String s3Endpoint = "";

    /** AWS/R2 region (e.g. {@code eu-central-1} or {@code auto} for R2). */
    private String s3Region = "auto";

    /** S3/R2 access key ID. */
    private String s3AccessKeyId = "";

    /** S3/R2 secret access key. */
    private String s3SecretAccessKey = "";

    /**
     * Key prefix inside the bucket (no leading slash).
     * Segments are uploaded to {@code <s3KeyPrefix>/<streamId>/<filename>}.
     */
    private String s3KeyPrefix = "streams";

    /**
     * Polling interval in milliseconds for the S3 upload watcher.
     * The watcher scans the output directory for new {@code .ts} and {@code .m3u8}
     * files and uploads any that have not been uploaded yet.
     */
    private long s3WatchIntervalMs = 1000;

    // ── Worker heartbeat (failover) ────────────────────────────────────────────

    /**
     * When {@code true}, the worker publishes a heartbeat key
     * {@code stream:worker:<streamId>} to Redis every {@link #heartbeatIntervalMs}
     * milliseconds while FFmpeg is running.  If another worker detects that the key
     * has expired (TTL = {@link #heartbeatTtlSeconds}), it can re-enqueue the job.
     */
    private boolean heartbeatEnabled = true;

    /** Milliseconds between heartbeat Redis writes per active stream. */
    private long heartbeatIntervalMs = 5000;

    /**
     * TTL in seconds for the {@code stream:worker:<streamId>} key.
     * Should be at least 2× {@link #heartbeatIntervalMs / 1000}.
     */
    private int heartbeatTtlSeconds = 15;

    /** Region identifier embedded in the heartbeat value (e.g. {@code eu}, {@code us}). */
    private String region = "eu";
}
