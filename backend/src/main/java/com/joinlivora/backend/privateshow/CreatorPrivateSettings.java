package com.joinlivora.backend.privateshow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "creator_private_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorPrivateSettings {

    @Id
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "price_per_minute", nullable = false)
    private Long pricePerMinute;

    @Column(name = "allow_spy_on_private", nullable = false)
    private boolean allowSpyOnPrivate;

    @Column(name = "spy_price_per_minute", nullable = false)
    private Long spyPricePerMinute;

    @Column(name = "max_spy_viewers")
    private Integer maxSpyViewers;
}
