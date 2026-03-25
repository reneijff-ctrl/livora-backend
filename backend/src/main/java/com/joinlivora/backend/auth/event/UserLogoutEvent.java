package com.joinlivora.backend.auth.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when a user logs out of the application.
 */
public class UserLogoutEvent extends ApplicationEvent {
    private final String email;

    public UserLogoutEvent(Object source, String email) {
        super(source);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
