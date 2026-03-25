package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscription;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatIntegrationTest {

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
    private ModerationRepository moderationRepository;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.joinlivora.backend.chat.analysis.ToxicityAnalyzer toxicityAnalyzer;

    @InjectMocks
    private ChatRoomService chatRoomService;

    @InjectMocks
    private ChatModerationService moderationService;

    private User creator;
    private User viewer;
    private String roomKey = "private-test-room";

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creatorUserId@test.com");
        creator.setRole(Role.CREATOR);

        viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");
        viewer.setRole(Role.USER);
    }

    @Test
    void testScenario_CreatorMutesViewerInPrivateRoom() {
        // 1. Creator creates a private room
        String roomName = "private-test-room";
        when(chatRoomRepository.findByName(roomName)).thenReturn(java.util.Optional.empty());
        when(userRepository.findById(creator.getId())).thenReturn(java.util.Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatRoom room = chatRoomService.createPrivateRoom(roomName, creator.getId());
        assertNotNull(room);
        assertTrue(room.isPrivate());

        // 2. Viewer without payment tries to join -> denied
        when(chatRoomRepository.findByName(roomName)).thenReturn(java.util.Optional.of(room));
        when(userRepository.findById(viewer.getId())).thenReturn(java.util.Optional.of(viewer));
        when(subscriptionRepository.findByUserAndStatus(viewer, SubscriptionStatus.ACTIVE)).thenReturn(java.util.Optional.empty());

        assertThrows(AccessDeniedException.class, () -> chatRoomService.validateAccess(roomName, viewer.getId()),
                "Viewer without subscription should be denied access to private room");

        // 3. Viewer subscribes
        // (In this mock test, we just change the mock behavior)

        // 4. Viewer joins private chat
        when(subscriptionRepository.findByUserAndStatus(viewer, SubscriptionStatus.ACTIVE)).thenReturn(java.util.Optional.of(new UserSubscription()));
        
        boolean accessGranted = chatRoomService.validateAccess(roomName, viewer.getId());
        assertTrue(accessGranted, "Subscribed viewer should be granted access to private room");

        // 5. Creator mutes viewer
        when(userRepository.findById(viewer.getId())).thenReturn(java.util.Optional.of(viewer));
        when(userRepository.findById(creator.getId())).thenReturn(java.util.Optional.of(creator));
        
        moderationService.muteUser(viewer.getId(), creator.getId(), java.time.Duration.ofMinutes(10), roomName);
        
        verify(moderationRepository).save(any(Moderation.class));

        // 6. Viewer cannot send messages
        when(moderationRepository.findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(eq(viewer), eq(ModerationAction.MUTE), any(java.time.Instant.class)))
                .thenReturn(java.util.Optional.of(new Moderation()));

        assertTrue(moderationService.isMuted(viewer.getId(), roomName), "Viewer should be muted");
    }
}








