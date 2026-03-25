package com.joinlivora.backend.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.chat.dto.ChatErrorCode;
import com.joinlivora.backend.chat.dto.ChatModeRequest;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscription;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.testutil.TestUserFactory;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatModeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    private User creator;
    private User regularUser;
    private User subscriber;
    private User moderator;
    private User admin;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        creator = userRepository.save(TestUserFactory.createCreator("creator-" + suffix + "@test.com"));
        regularUser = userRepository.save(TestUserFactory.createViewer("user-" + suffix + "@test.com"));
        subscriber = userRepository.save(TestUserFactory.createViewer("sub-" + suffix + "@test.com"));
        moderator = userRepository.save(TestUserFactory.createUser("mod-" + suffix + "@test.com", Role.MODERATOR));
        admin = userRepository.save(TestUserFactory.createUser("admin-" + suffix + "@test.com", Role.ADMIN));

        room = ChatRoom.builder()
                .name("test-room-" + suffix)
                .createdBy(creator)
                .chatMode(ChatMode.PUBLIC)
                .isPrivate(false)
                .build();
        room = chatRoomRepository.save(room);

        // Setup subscription for subscriber
        UserSubscription sub = new UserSubscription();
        sub.setUser(subscriber);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);
    }

    @Test
    void testPublicChatAccess() {
        // Mode is PUBLIC by default
        assertTrue(chatRoomService.validateAccess(room.getName(), regularUser.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), subscriber.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), moderator.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), admin.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), creator.getId()));
    }

    @Test
    void testSubscriberOnlyEnforcement() {
        // Update mode via service
        chatRoomService.updateChatMode(room.getId(), ChatMode.SUBSCRIBERS_ONLY, creator.getId());

        // Regular creator should be rejected
        ChatAccessException ex = assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess(room.getName(), regularUser.getId()));
        assertEquals(ChatErrorCode.SUBSCRIBERS_ONLY, ex.getErrorCode());

        // Subscriber should be allowed
        assertTrue(chatRoomService.validateAccess(room.getName(), subscriber.getId()));
        
        // Staff/Creator bypass
        assertTrue(chatRoomService.validateAccess(room.getName(), creator.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), moderator.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), admin.getId()));
    }

    @Test
    void testCreatorOnlyEnforcement() {
        // Update mode
        chatRoomService.updateChatMode(room.getId(), ChatMode.CREATORS_ONLY, creator.getId());

        // Regular creator and subscriber should be rejected
        assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess(room.getName(), regularUser.getId()));
        assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess(room.getName(), subscriber.getId()));

        // Room creator allowed
        assertTrue(chatRoomService.validateAccess(room.getName(), creator.getId()));
        
        // Other creators allowed
        User otherCreator = userRepository.save(TestUserFactory.createCreator("other-creator@test.com"));
        assertTrue(chatRoomService.validateAccess(room.getName(), otherCreator.getId()));

        // Staff override
        assertTrue(chatRoomService.validateAccess(room.getName(), moderator.getId()));
        assertTrue(chatRoomService.validateAccess(room.getName(), admin.getId()));
    }

    @Test
    void testModeratorsOnlyEnforcementAndOverride() {
        // Update mode
        chatRoomService.updateChatMode(room.getId(), ChatMode.MODERATORS_ONLY, creator.getId());

        // Regular creator, subscriber, and other creators should be rejected
        assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess(room.getName(), regularUser.getId()));
        
        User otherCreator = userRepository.save(TestUserFactory.createCreator("other-creator2@test.com"));
        assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess(room.getName(), otherCreator.getId()));

        // Moderator allowed
        assertTrue(chatRoomService.validateAccess(room.getName(), moderator.getId()));
        
        // Admin allowed
        assertTrue(chatRoomService.validateAccess(room.getName(), admin.getId()));

        // Room creator allowed (bypass)
        assertTrue(chatRoomService.validateAccess(room.getName(), creator.getId()));
    }

    @Test
    void testUpdateChatModeViaApi() throws Exception {
        ChatModeRequest request = new ChatModeRequest(ChatMode.MODERATORS_ONLY);

        mockMvc.perform(put("/api/rooms/" + room.getId() + "/chat-mode")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(creator.getEmail()).roles("CREATOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify in DB
        ChatRoom updatedRoom = chatRoomRepository.findById(room.getId()).orElseThrow();
        assertEquals(ChatMode.MODERATORS_ONLY, updatedRoom.getChatMode());
    }

    @Test
    void testUpdateChatModeViaApi_ForbiddenForRegularUser() throws Exception {
        ChatModeRequest request = new ChatModeRequest(ChatMode.MODERATORS_ONLY);

        mockMvc.perform(put("/api/rooms/" + room.getId() + "/chat-mode")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(regularUser.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}








