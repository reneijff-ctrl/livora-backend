package com.joinlivora.backend.email.event;

import com.joinlivora.backend.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PayoutStatusChangedEvent extends ApplicationEvent {
    private final User user;
    private final boolean enabled;
    private final String reason;

    public PayoutStatusChangedEvent(Object source, User user, boolean enabled, String reason) {
        super(source);
        this.user = user;
        this.enabled = enabled;
        this.reason = reason;
    }
}
