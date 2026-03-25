package com.joinlivora.backend.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.Principal;

@Getter
@AllArgsConstructor
public class StompPrincipal implements Principal {
    private final String name;
    private final String userId;

    @Override
    public String getName() {
        return name;
    }
}
