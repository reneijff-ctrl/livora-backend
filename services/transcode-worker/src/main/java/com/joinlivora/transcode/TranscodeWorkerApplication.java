package com.joinlivora.transcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the standalone Transcode Worker service.
 *
 * <p>This service:
 * <ul>
 *   <li>Polls {@code stream:transcode:jobs} (Redis List via BLPOP) for new stream jobs.</li>
 *   <li>Spawns one GPU-accelerated FFmpeg process per job (NVIDIA h264_nvenc).</li>
 *   <li>Listens on the {@code stream:transcode:stop} Pub/Sub channel to kill processes.</li>
 *   <li>Writes multi-bitrate HLS output to {@code /streams/{streamId}/}.</li>
 *   <li>Retries failed FFmpeg processes up to {@code transcode.max-retries} times.</li>
 * </ul>
 *
 * <p>This is intentionally a separate process from the main livora-backend so that
 * FFmpeg CPU/GPU usage cannot starve the Spring Boot application thread pool.
 */
@SpringBootApplication
@EnableAsync
public class TranscodeWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranscodeWorkerApplication.class, args);
    }
}
