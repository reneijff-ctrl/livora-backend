package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@ToString(exclude = "creator")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamRoom {

    @EqualsAndHashCode.Include
    private UUID id;

    private User creator;

    @Builder.Default
    private boolean isLive = false;

    @Builder.Default
    private boolean isPremium = false;

    @Builder.Default
    private boolean slowMode = false;

    @Builder.Default
    private boolean chatEnabled = true;

    private Integer slowModeInterval;

    private Integer maxViewers;

    private String streamCategory;

    @Builder.Default
    private int viewerCount = 0;

    private String streamTitle;

    private String description;

    private String thumbnailUrl;

    private Instant endedAt;

    private Long minChatTokens;

    @Builder.Default
    private boolean isPaid = false;

    private Long pricePerMessage;

    @Builder.Default
    private BigDecimal admissionPrice = BigDecimal.ZERO;

    private Instant createdAt;

    private Instant startedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    public Long getUserId() {
        return creator != null ? creator.getId() : null;
    }

    private Long creatorRecordId;

    @com.fasterxml.jackson.annotation.JsonProperty("creatorRecordId")
    public Long getCreatorRecordId() {
        return creatorRecordId;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("creator")
    public Long getCreatorId() {
        return creator != null ? creator.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("username")
    public String getUsername() {
        return creator != null ? creator.getUsername() : null;
    }
}
