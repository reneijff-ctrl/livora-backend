package com.joinlivora.backend.email.event;

import com.joinlivora.backend.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PasswordResetRequestedEvent extends ApplicationEvent {
    private final User user;
    private final String token;

    public PasswordResetRequestedEvent(Object source, User user, String token) {
        super(source);
        this.user = user;
        this.token = token;
    }
}
