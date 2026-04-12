package com.joinlivora.transcode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Deserialized representation of a transcode job payload pushed by the main backend.
 *
 * <p>JSON format:
 * <pre>{@code
 * {
 *   "streamId":  "550e8400-e29b-41d4-a716-446655440000",
 *   "creatorId": 42,
 *   "rtmpUrl":   "rtmp://nginx:1935/live/my-stream-key"
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscodeJob {

    /** UUID of the {@code Stream} entity in the main database. */
    private UUID streamId;

    /** Creator's user ID — used for correlation logging. */
    private Long creatorId;

    /** Full RTMP ingest URL that FFmpeg will read from. */
    private String rtmpUrl;
    /**
     * Epoch milliseconds when this job was first enqueued (set by the backend publisher).
     * Used by {@link TranscodeHeartbeatWatchdog} to skip re-queue of stale jobs
     * older than 6 hours.
     */
    private Long queuedAt;
}
