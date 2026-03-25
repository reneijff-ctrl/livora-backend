package com.joinlivora.backend.payout.event;

import com.joinlivora.backend.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class EarningsUnlockedEvent extends ApplicationEvent {
    private final User creator;
    private final long unlockedTokens;
    private final BigDecimal unlockedRevenue;

    public EarningsUnlockedEvent(Object source, User creator, long unlockedTokens, BigDecimal unlockedRevenue) {
        super(source);
        this.creator = creator;
        this.unlockedTokens = unlockedTokens;
        this.unlockedRevenue = unlockedRevenue;
    }
}
