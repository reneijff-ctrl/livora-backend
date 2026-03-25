package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Deprecated(since = "2026-02-22", forRemoval = false)
/**
 * @deprecated Use {@link HomepageCreatorDto} for homepage creators to get full stream metadata.
 */
public class OnlineCreatorDto {
    private Long creatorId;
    private String username;
    private String displayName;
    private boolean online;
    private java.time.Instant lastSeen;
}
