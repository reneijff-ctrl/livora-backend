package com.joinlivora.backend.chat;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.chat.dto.ChatErrorCode;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatModeAuthorizationTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSubscriptionRepository subscriptionRepository;
    @Mock
    private PpvService ppvService;
    @Mock
    private PPVPurchaseValidationService purchaseValidationService;
    @Mock
    private PPVChatAccessService ppvChatAccessService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private User creator;
    private User normalUser;
    private User subscriber;
    private User admin;
    private User moderator;
    private User otherCreator;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setRole(Role.CREATOR);

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setEmail("creator@test.com");
        normalUser.setRole(Role.USER);

        subscriber = new User();
        subscriber.setId(3L);
        subscriber.setEmail("sub@test.com");
        subscriber.setRole(Role.USER);

        admin = new User();
        admin.setId(4L);
        admin.setEmail("admin@test.com");
        admin.setRole(Role.ADMIN);

        moderator = new User();
        moderator.setId(5L);
        moderator.setEmail("mod@test.com");
        moderator.setRole(Role.MODERATOR);

        otherCreator = new User();
        otherCreator.setId(6L);
        otherCreator.setEmail("other@test.com");
        otherCreator.setRole(Role.CREATOR);

        room = ChatRoom.builder()
                .id(java.util.UUID.randomUUID())
                .name("liveStream-123")
                .createdBy(creator)
                .chatMode(ChatMode.PUBLIC)
                .isPrivate(false)
                .build();
    }

    @Test
    void validateAccess_PublicMode_AnyoneCanChat() {
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 2L));
    }

    @Test
    void validateAccess_SubscribersOnly_NormalUserRejected() {
        room.setChatMode(ChatMode.SUBSCRIBERS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
        when(subscriptionRepository.findByUserAndStatus(normalUser, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());

        ChatAccessException ex = assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess("liveStream-123", 2L));
        assertEquals(ChatErrorCode.SUBSCRIBERS_ONLY, ex.getErrorCode());
    }

    @Test
    void validateAccess_SubscribersOnly_SubscriberAllowed() {
        room.setChatMode(ChatMode.SUBSCRIBERS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(3L)).thenReturn(Optional.of(subscriber));
        when(subscriptionRepository.findByUserAndStatus(subscriber, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(new com.joinlivora.backend.payment.UserSubscription()));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 3L));
    }

    @Test
    void validateAccess_CreatorsOnly_NormalUserRejected() {
        room.setChatMode(ChatMode.CREATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));

        ChatAccessException ex = assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess("liveStream-123", 2L));
        assertEquals(ChatErrorCode.CREATORS_ONLY, ex.getErrorCode());
    }

    @Test
    void validateAccess_CreatorsOnly_OtherCreatorAllowed() {
        room.setChatMode(ChatMode.CREATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(6L)).thenReturn(Optional.of(otherCreator));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 6L));
    }

    @Test
    void validateAccess_ModeratorsOnly_NormalUserRejected() {
        room.setChatMode(ChatMode.MODERATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));

        ChatAccessException ex = assertThrows(ChatAccessException.class, () ->
                chatRoomService.validateAccess("liveStream-123", 2L));
        assertEquals(ChatErrorCode.MODERATORS_ONLY, ex.getErrorCode());
    }

    @Test
    void validateAccess_ModeratorsOnly_ModeratorAllowed() {
        room.setChatMode(ChatMode.MODERATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(5L)).thenReturn(Optional.of(moderator));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 5L));
    }

    @Test
    void validateAccess_ModeratorsOnly_AdminAllowed() {
        room.setChatMode(ChatMode.MODERATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(4L)).thenReturn(Optional.of(admin));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 4L));
    }

    @Test
    void validateAccess_Bypass_CreatorAlwaysAllowed() {
        room.setChatMode(ChatMode.MODERATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 1L));
    }

    @Test
    void validateAccess_Bypass_AdminAlwaysAllowedEvenInCreatorsOnly() {
        room.setChatMode(ChatMode.CREATORS_ONLY);
        when(chatRoomRepository.findByName("liveStream-123")).thenReturn(Optional.of(room));
        when(userRepository.findById(4L)).thenReturn(Optional.of(admin));

        assertTrue(chatRoomService.validateAccess("liveStream-123", 4L));
    }
}








