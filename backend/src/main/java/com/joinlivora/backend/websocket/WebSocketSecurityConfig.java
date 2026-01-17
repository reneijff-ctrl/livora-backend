package com.joinlivora.backend.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                .simpDestMatchers("/app/**").authenticated()
                .simpDestMatchers("/app/admin/**").hasRole("ADMIN")
                .simpDestMatchers("/app/admin/stream/stop").hasRole("ADMIN")
                .simpSubscribeDestMatchers("/user/**").authenticated()
                .simpSubscribeDestMatchers("/topic/presence").authenticated()
                .simpSubscribeDestMatchers("/topic/public").authenticated()
                .simpSubscribeDestMatchers("/topic/premium").authenticated()
                .simpSubscribeDestMatchers("/topic/streams").authenticated()
                .simpSubscribeDestMatchers("/topic/rooms/*/tips").permitAll()
                .simpSubscribeDestMatchers("/topic/admin/**").hasRole("ADMIN")
                .simpSubscribeDestMatchers("/queue/webrtc").authenticated()
                .anyMessage().denyAll();
        
        return messages.build();
    }
}
