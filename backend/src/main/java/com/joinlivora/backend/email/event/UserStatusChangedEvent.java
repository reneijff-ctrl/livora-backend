package com.joinlivora.backend.email.event;

import com.joinlivora.backend.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserStatusChangedEvent extends ApplicationEvent {
    private final User user;
    private final String reason;

    public UserStatusChangedEvent(Object source, User user, String reason) {
        super(source);
        this.user = user;
        this.reason = reason;
    }
}
