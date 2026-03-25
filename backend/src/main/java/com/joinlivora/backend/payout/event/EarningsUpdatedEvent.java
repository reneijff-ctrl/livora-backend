package com.joinlivora.backend.payout.event;

import com.joinlivora.backend.payout.EarningSource;
import com.joinlivora.backend.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class EarningsUpdatedEvent extends ApplicationEvent {
    private final User creator;
    private final EarningSource source;
    private final BigDecimal amount;
    private final String currency;

    public EarningsUpdatedEvent(Object source, User creator, EarningSource earningSource, BigDecimal amount, String currency) {
        super(source);
        this.creator = creator;
        this.source = earningSource;
        this.amount = amount;
        this.currency = currency;
    }
}
