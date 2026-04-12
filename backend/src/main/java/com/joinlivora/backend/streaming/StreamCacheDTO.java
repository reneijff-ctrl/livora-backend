package com.joinlivora.backend.streaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for caching Stream metadata in Redis without triggering LazyInitializationException.
 * ONLY contains primitives, IDs, and simple values.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamCacheDTO implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private UUID id;
    private Long creatorUserId;
    private String title;
    private boolean isLive;
    private Instant startedAt;
    private Instant endedAt;
    private String streamKey;
    private String thumbnailUrl;
    private UUID mediasoupRoomId;
    private java.math.BigDecimal admissionPrice;
}
