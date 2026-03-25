package com.joinlivora.backend.email.event;

import com.joinlivora.backend.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EmailVerificationRequestedEvent extends ApplicationEvent {
    private final User user;

    public EmailVerificationRequestedEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
