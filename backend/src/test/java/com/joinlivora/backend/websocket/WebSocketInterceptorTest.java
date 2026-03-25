package com.joinlivora.backend.websocket;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WebSocketInterceptorTest {

    @Mock
    private MessageChannel channel;

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private LiveAccessService liveAccessService;

    @InjectMocks
    private WebSocketInterceptor interceptor;

    private StompHeaderAccessor accessor;

    @BeforeEach
    void setUp() {
        accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    }

    @Test
    void preSend_Subscribe_StreamVideo_Authenticated_ShouldSucceed() {
        java.util.UUID liveStreamId = java.util.UUID.randomUUID();
        accessor.setDestination("/exchange/amq.topic/stream." + liveStreamId + ".video");
        setAuthenticatedUser("123");

        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_StreamVideo_Anonymous_ShouldThrowException() {
        java.util.UUID liveStreamId = java.util.UUID.randomUUID();
        accessor.setDestination("/exchange/amq.topic/stream." + liveStreamId + ".video");
        // No user set

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    @Test
    void preSend_Subscribe_CreatorEarnings_Own_ShouldSucceed() {
        Long creatorId = 1L;
        accessor.setDestination("/exchange/amq.topic/creator." + creatorId + ".earnings");
        setAuthenticatedUser(creatorId.toString());
        
        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_CreatorEarnings_Other_ShouldThrowException() {
        Long creatorId = 1L;
        Long otherUserId = 2L;
        accessor.setDestination("/exchange/amq.topic/creator." + creatorId + ".earnings");
        setAuthenticatedUser(otherUserId.toString());
        
        assertThrows(AccessDeniedException.class, () -> 
            interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    @Test
    void preSend_Subscribe_CreatorEarnings_Admin_ShouldSucceed() {
        Long creatorId = 1L;
        Long adminId = 99L;
        accessor.setDestination("/exchange/amq.topic/creator." + creatorId + ".earnings");
        
        User user = new User();
        user.setId(adminId);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("ws_user", user);
        accessor.setSessionAttributes(sessionAttributes);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername(adminId.toString())
                .password("pass")
                .roles("ADMIN")
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);
        
        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_ViewerCount_AnyUser_ShouldSucceed() {
        Long creatorId = 1L;
        accessor.setDestination("/exchange/amq.topic/viewers." + creatorId);
        setAuthenticatedUser("123");
        
        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    private void setAuthenticatedUser(String id) {
        User user = new User();
        user.setId(Long.parseLong(id));
        
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("ws_user", user);
        accessor.setSessionAttributes(sessionAttributes);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                id, "", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);
    }

    // --- WebRTC Room Access Tests ---

    @Test
    void preSend_Subscribe_WebRtcRoom_Creator_ShouldSucceed() {
        UUID roomId = UUID.randomUUID();
        Long creatorId = 10L;
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        setAuthenticatedUser(creatorId.toString());

        User creator = new User();
        creator.setId(creatorId);
        Stream stream = Stream.builder().creator(creator).isLive(true).isPaid(false).build();
        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.of(stream));

        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_PublicStream_ShouldSucceed() {
        UUID roomId = UUID.randomUUID();
        Long creatorId = 10L;
        Long viewerId = 20L;
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        setAuthenticatedUser(viewerId.toString());

        User creator = new User();
        creator.setId(creatorId);
        Stream stream = Stream.builder().creator(creator).isLive(true).isPaid(false).build();
        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.of(stream));

        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_PaidStream_WithAccess_ShouldSucceed() {
        UUID roomId = UUID.randomUUID();
        Long creatorId = 10L;
        Long viewerId = 20L;
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        setAuthenticatedUser(viewerId.toString());

        User creator = new User();
        creator.setId(creatorId);
        Stream stream = Stream.builder().creator(creator).isLive(true).isPaid(true).build();
        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.of(stream));
        when(liveAccessService.hasAccess(creatorId, viewerId)).thenReturn(true);

        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_PaidStream_WithoutAccess_ShouldThrow() {
        UUID roomId = UUID.randomUUID();
        Long creatorId = 10L;
        Long viewerId = 20L;
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        setAuthenticatedUser(viewerId.toString());

        User creator = new User();
        creator.setId(creatorId);
        Stream stream = Stream.builder().creator(creator).isLive(true).isPaid(true).build();
        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.of(stream));
        when(liveAccessService.hasAccess(creatorId, viewerId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_NoStream_ShouldThrow() {
        UUID roomId = UUID.randomUUID();
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        setAuthenticatedUser("20");

        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_NotLive_ShouldThrow() {
        UUID roomId = UUID.randomUUID();
        Long creatorId = 10L;
        Long viewerId = 20L;
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        setAuthenticatedUser(viewerId.toString());

        User creator = new User();
        creator.setId(creatorId);
        Stream stream = Stream.builder().creator(creator).isLive(false).isPaid(false).build();
        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.of(stream));

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_Anonymous_ShouldThrow() {
        UUID roomId = UUID.randomUUID();
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);
        // No user set

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_Admin_ShouldSucceed() {
        UUID roomId = UUID.randomUUID();
        Long creatorId = 10L;
        Long adminId = 99L;
        accessor.setDestination("/exchange/amq.topic/webrtc.room." + roomId);

        User adminUser = new User();
        adminUser.setId(adminId);
        adminUser.setRole(Role.ADMIN);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("ws_user", adminUser);
        accessor.setSessionAttributes(sessionAttributes);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername(adminId.toString())
                .password("pass")
                .roles("ADMIN")
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);

        User creator = new User();
        creator.setId(creatorId);
        Stream stream = Stream.builder().creator(creator).isLive(true).isPaid(true).build();
        when(streamRepository.findByMediasoupRoomIdWithCreator(roomId)).thenReturn(Optional.of(stream));

        Message<?> result = interceptor.preSend(MessageBuilder.build(accessor), channel);
        assertNotNull(result);
    }

    @Test
    void preSend_Subscribe_WebRtcRoom_InvalidRoomId_ShouldThrow() {
        accessor.setDestination("/exchange/amq.topic/webrtc.room.not-a-uuid");
        setAuthenticatedUser("20");

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(MessageBuilder.build(accessor), channel)
        );
    }

    private static class MessageBuilder {
        static Message<?> build(StompHeaderAccessor accessor) {
            return org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }
    }
}
