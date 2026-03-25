package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.dto.ChatPpvAccessResponse;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.dto.ChatErrorCode;
import com.joinlivora.backend.presence.model.CreatorAvailabilityStatus;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.exception.chat.ChatRoomAlreadyExistsException;
import com.joinlivora.backend.exception.chat.PpvRoomAlreadyExistsException;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscription;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepositoryV2;

    @Mock
    private CreatorProfileService creatorProfileService;

    @Mock
    private ChatRoomRepository chatRoomRepository; // V1 (legacy)

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

    @Mock
    private com.joinlivora.backend.privateshow.PrivateSessionRepository privateSessionRepository;

    @Mock
    private CreatorPresenceService creatorPresenceService;

    @Mock
    private CreatorRepository creatorRepository;

    @Mock
    private com.joinlivora.backend.streaming.LiveStreamService liveStreamService;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private Long creatorId;
    private String name;
    private User creator;

    @BeforeEach
    void setUp() {
        creatorId = 1L;
        name = "test-room";
        creator = new User();
        creator.setId(creatorId);
        
        // Default lenient mock for live stream status
        lenient().when(liveStreamService.isStreamActive(anyLong())).thenReturn(false);
    }

    @Test
    void getOrCreateRoom_CreatorInactiveButOnline_ShouldSucceed() {
        Long creatorUserId = 100L;
        Long internalCreatorId = 200L;
        com.joinlivora.backend.creator.model.Creator creatorEntity = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(new com.joinlivora.backend.user.User())
                .active(false) // Inactive but...
                .build();
        creatorEntity.getUser().setId(creatorUserId);
        creatorEntity.getUser().setStatus(UserStatus.ACTIVE);

        when(creatorRepository.findByUser_Id(creatorUserId)).thenReturn(Optional.of(creatorEntity));
        // Online presence
        when(creatorPresenceService.getAvailability(creatorUserId)).thenReturn(CreatorAvailabilityStatus.ONLINE);
        
        when(chatRoomRepositoryV2.findMainRoom(internalCreatorId, "private-session-")).thenReturn(Optional.of(new com.joinlivora.backend.chat.domain.ChatRoom()));

        assertDoesNotThrow(() -> chatRoomService.getOrCreateRoom(creatorUserId));
    }

    @Test
    void getOrCreateRoom_SyncStatusWithPresence() {
        Long creatorUserId = 100L;
        Long internalCreatorId = 200L;
        com.joinlivora.backend.creator.model.Creator creatorEntity = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(new com.joinlivora.backend.user.User())
                .active(true)
                .build();
        creatorEntity.getUser().setId(creatorUserId);
        creatorEntity.getUser().setStatus(UserStatus.ACTIVE);

        when(creatorRepository.findByUser_Id(creatorUserId)).thenReturn(Optional.of(creatorEntity));
        
        // 1. Existing ACTIVE room, creator goes OFFLINE
        com.joinlivora.backend.chat.domain.ChatRoom activeRoom = new com.joinlivora.backend.chat.domain.ChatRoom();
        activeRoom.setStatus(com.joinlivora.backend.chat.domain.ChatRoomStatus.ACTIVE);
        when(chatRoomRepositoryV2.findMainRoom(internalCreatorId, "private-session-")).thenReturn(Optional.of(activeRoom));
        when(creatorPresenceService.getAvailability(creatorUserId)).thenReturn(CreatorAvailabilityStatus.OFFLINE);
        when(chatRoomRepositoryV2.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.joinlivora.backend.chat.domain.ChatRoom result1 = chatRoomService.getOrCreateRoom(creatorUserId);
        assertEquals(com.joinlivora.backend.chat.domain.ChatRoomStatus.PAUSED, result1.getStatus());

        // 2. Existing WAITING room, creator goes ONLINE
        com.joinlivora.backend.chat.domain.ChatRoom waitingRoom = new com.joinlivora.backend.chat.domain.ChatRoom();
        waitingRoom.setStatus(com.joinlivora.backend.chat.domain.ChatRoomStatus.WAITING_FOR_CREATOR);
        when(chatRoomRepositoryV2.findMainRoom(internalCreatorId, "private-session-")).thenReturn(Optional.of(waitingRoom));
        when(creatorPresenceService.getAvailability(creatorUserId)).thenReturn(CreatorAvailabilityStatus.ONLINE);

        com.joinlivora.backend.chat.domain.ChatRoom result2 = chatRoomService.getOrCreateRoom(creatorUserId);
        assertEquals(com.joinlivora.backend.chat.domain.ChatRoomStatus.ACTIVE, result2.getStatus());
        assertNotNull(result2.getActivatedAt());
    }

    @Test
    void getOrCreateRoom_CreatorBanned_ShouldThrowError() {
        Long creatorUserId = 100L;
        com.joinlivora.backend.user.User user = new com.joinlivora.backend.user.User();
        user.setId(creatorUserId);
        user.setStatus(UserStatus.SUSPENDED);

        com.joinlivora.backend.creator.model.Creator creatorEntity = com.joinlivora.backend.creator.model.Creator.builder()
                .id(200L)
                .user(user)
                .active(true)
                .build();

        when(creatorRepository.findByUser_Id(creatorUserId)).thenReturn(Optional.of(creatorEntity));

        com.joinlivora.backend.exception.BusinessException exception = assertThrows(
                com.joinlivora.backend.exception.BusinessException.class,
                () -> chatRoomService.getOrCreateRoom(creatorUserId)
        );
        assertEquals("Creator account is banned", exception.getMessage());
    }

    @Test
    void getOrCreateRoom_NoPresenceRecord_ShouldStillCreateOrReturnRoom() {
        Long creatorUserId = 100L;
        Long internalCreatorId = 200L;
        // Build Creator
        com.joinlivora.backend.creator.model.Creator creatorEntity = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(new com.joinlivora.backend.user.User())
                .active(true)
                .build();
        creatorEntity.getUser().setId(creatorUserId);
        creatorEntity.getUser().setStatus(UserStatus.ACTIVE);

        when(creatorRepository.findByUser_Id(creatorUserId)).thenReturn(Optional.of(creatorEntity));
        // No presence record for internalCreatorId -> OFFLINE
        when(creatorPresenceService.getAvailability(creatorUserId)).thenReturn(CreatorAvailabilityStatus.OFFLINE);

        // Simulate not existing then creating
        when(chatRoomRepositoryV2.findMainRoom(internalCreatorId, "private-session-")).thenReturn(Optional.empty());
        when(chatRoomRepositoryV2.save(any(com.joinlivora.backend.chat.domain.ChatRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.joinlivora.backend.chat.domain.ChatRoom result = chatRoomService.getOrCreateRoom(creatorUserId);

        assertNotNull(result);
    }

    @Test
    void getOrCreateRoom_CreatorNotFound_ShouldThrow404() {
        Long creatorUserId = 1L;
        when(creatorRepository.findByUser_Id(creatorUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> chatRoomService.getOrCreateRoom(creatorUserId));
    }

    @Test
    void getOrCreateRoom_CreatorOffline_ShouldSetStatusWaitingForCreator() {
        Long internalCreatorId = 100L;
        Long creatorUserId = 42L;

        com.joinlivora.backend.creator.model.Creator creatorEntity = com.joinlivora.backend.creator.model.Creator.builder()
                .id(internalCreatorId)
                .user(new com.joinlivora.backend.user.User())
                .active(true)
                .build();
        creatorEntity.getUser().setId(creatorUserId);
        creatorEntity.getUser().setStatus(UserStatus.ACTIVE);

        when(creatorRepository.findByUser_Id(creatorUserId)).thenReturn(Optional.of(creatorEntity));

        // Offline presence
        when(creatorPresenceService.getAvailability(creatorUserId)).thenReturn(CreatorAvailabilityStatus.OFFLINE);

        // No existing room by creator, will create
        when(chatRoomRepositoryV2.findMainRoom(internalCreatorId, "private-session-")).thenReturn(Optional.empty());
        when(chatRoomRepositoryV2.save(any(com.joinlivora.backend.chat.domain.ChatRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.joinlivora.backend.chat.domain.ChatRoom created = chatRoomService.getOrCreateRoom(creatorUserId);

        assertNotNull(created);
        assertEquals(internalCreatorId, created.getCreatorId());
        assertEquals(ChatRoomStatus.WAITING_FOR_CREATOR, created.getStatus());
    }

    @Test
    void createPublicRoom_ShouldCreateSuccessfully() {
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createPublicRoom(name, creatorId);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(creatorId, result.getCreatorId());
        assertFalse(result.isPrivate());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void createPrivateRoom_ShouldCreateSuccessfully() {
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createPrivateRoom(name, creatorId);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(creatorId, result.getCreatorId());
        assertTrue(result.isPrivate());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void createPublicRoom_WhenNameExists_ShouldThrowException() {
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(new ChatRoom()));

        assertThrows(ChatRoomAlreadyExistsException.class, () -> chatRoomService.createPublicRoom(name, creatorId));
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void createPrivateRoom_WithExistingPpvRoom_ShouldThrowException() {
        UUID ppvId = UUID.randomUUID();
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.findByPpvContentId(ppvId)).thenReturn(Optional.of(new ChatRoom()));

        assertThrows(PpvRoomAlreadyExistsException.class, () -> chatRoomService.createPrivateRoom(name, creatorId, ppvId));
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void createLiveStreamRoom_ShouldCreateSuccessfully() {
        UUID liveStreamRoomId = UUID.randomUUID();
        String expectedName = "stream-" + liveStreamRoomId;
        when(chatRoomRepository.findByName(expectedName)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createLiveStreamRoom(liveStreamRoomId, creatorId, true, null, true, 10L);

        assertNotNull(result);
        assertEquals(expectedName, result.getName());
        assertTrue(result.isPrivate());
        assertTrue(result.isPaid());
        assertEquals(10L, result.getPricePerMessage());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void createLiveStreamRoom_WithPpv_ShouldCreateSuccessfully() {
        UUID liveStreamRoomId = UUID.randomUUID();
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);
        String expectedName = "stream-" + liveStreamRoomId;

        when(chatRoomRepository.findByName(expectedName)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(ppvService.getPpvContent(ppvId)).thenReturn(content);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createLiveStreamRoom(liveStreamRoomId, creatorId, false, ppvId, false, null);

        assertNotNull(result);
        assertEquals(expectedName, result.getName());
        assertTrue(result.isPrivate()); // Private because of PPV
        assertEquals(content, result.getPpvContent());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void createLiveStreamRoom_Existing_ShouldUpdate() {
        UUID liveStreamRoomId = UUID.randomUUID();
        String expectedName = "stream-" + liveStreamRoomId;
        ChatRoom existing = ChatRoom.builder().name(expectedName).isPrivate(false).build();

        when(chatRoomRepository.findByName(expectedName)).thenReturn(Optional.of(existing));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createLiveStreamRoom(liveStreamRoomId, creatorId, true, null, true, 20L);

        assertNotNull(result);
        assertTrue(result.isPrivate());
        assertTrue(result.isPaid());
        assertEquals(20L, result.getPricePerMessage());
        verify(chatRoomRepository).save(existing);
    }

    @Test
    void validateAccess_PublicRoom_ShouldReturnTrue() {
        ChatRoom publicRoom = ChatRoom.builder()
                .name(name)
                .isPrivate(false)
                .build();
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(publicRoom));

        assertTrue(chatRoomService.validateAccess(name, 2L));
    }

    @Test
    void validateAccess_PrivateRoom_Creator_ShouldReturnTrue() {
        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .build();
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));

        assertTrue(chatRoomService.validateAccess(name, creatorId));
    }

    @Test
    void validateAccess_PrivateRoom_Subscriber_ShouldReturnTrue() {
        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .build();
        Long subscriberId = 2L;
        User subscriber = new User();
        subscriber.setId(subscriberId);

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));
        when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
        when(subscriptionRepository.findByUserAndStatus(subscriber, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(new UserSubscription()));

        assertTrue(chatRoomService.validateAccess(name, subscriberId));
    }

    @Test
    void validateAccess_PrivateRoom_NonSubscriber_ShouldReturnTrue() {
        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .build();
        Long userId = 2L;

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));

        assertTrue(chatRoomService.validateAccess(name, userId));
    }

    @Test
    void validateAccess_NonExistentRoom_ShouldReturnTrue() {
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.empty());

        assertTrue(chatRoomService.validateAccess(name, creatorId));
    }

    @Test
    void createPrivateRoom_WithPpv_ShouldCreateSuccessfully() {
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.empty());
        when(chatRoomRepository.findByPpvContentId(ppvId)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(ppvService.getPpvContent(ppvId)).thenReturn(content);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createPrivateRoom(name, creatorId, ppvId);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(content, result.getPpvContent());
        assertEquals(ppvId, result.getPpvContent() != null ? result.getPpvContent().getId() : null);
        assertTrue(result.isPrivate());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    void validateAccess_PrivateRoom_PpvPurchaser_ShouldReturnTrue() {
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);

        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .ppvContent(content)
                .build();
        Long userId = 2L;
        User user = new User();
        user.setId(userId);

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(purchaseValidationService.hasPurchased(userId, ppvId)).thenReturn(true);

        assertTrue(chatRoomService.validateAccess(name, userId));
    }

    @Test
    void validateAccess_PrivateRoom_PpvNonPurchaser_ShouldReturnTrue() {
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);

        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .ppvContent(content)
                .build();
        Long userId = 2L;

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));

        assertTrue(chatRoomService.validateAccess(name, userId));
    }

    @Test
    void validateAccess_PrivateRoom_Anonymous_ShouldReturnTrue() {
        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .build();
        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));

        assertTrue(chatRoomService.validateAccess(name, null));
    }

    @Test
    void createPpvChatRoom_New_ShouldCreateSuccessfully() {
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);
        content.setCreator(creator);

        when(ppvService.getPpvContent(ppvId)).thenReturn(content);
        when(chatRoomRepository.findByPpvContentId(ppvId)).thenReturn(Optional.empty());
        when(chatRoomRepository.findByName("ppv-" + ppvId)).thenReturn(Optional.empty());
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createPpvChatRoom(ppvId, creatorId);

        assertNotNull(result);
        assertEquals("ppv-" + ppvId, result.getName());
        assertEquals(ppvId, result.getPpvContent() != null ? result.getPpvContent().getId() : null);
    }

    @Test
    void createPpvChatRoom_Existing_ShouldReturnExisting() {
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);
        content.setCreator(creator);
        ChatRoom existing = ChatRoom.builder().name("ppv-" + ppvId).creatorId(creatorId).build();

        when(ppvService.getPpvContent(ppvId)).thenReturn(content);
        when(chatRoomRepository.findByPpvContentId(ppvId)).thenReturn(Optional.of(existing));

        ChatRoom result = chatRoomService.createPpvChatRoom(ppvId, creatorId);

        assertEquals(existing, result);
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void createPpvChatRoom_NonCreator_ShouldThrowException() {
        UUID ppvId = UUID.randomUUID();
        PpvContent content = new PpvContent();
        content.setId(ppvId);
        content.setCreator(creator);

        when(ppvService.getPpvContent(ppvId)).thenReturn(content);

        assertThrows(AccessDeniedException.class, () -> chatRoomService.createPpvChatRoom(ppvId, 2L));
    }

    @Test
    void validateAccess_PrivateRoom_Admin_ShouldReturnTrue() {
        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .build();
        User admin = new User();
        admin.setId(10L);
        admin.setRole(Role.ADMIN);

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));

        assertTrue(chatRoomService.validateAccess(name, 10L));
    }

    @Test
    void validateAccess_PrivateRoom_Moderator_ShouldReturnTrue() {
        ChatRoom privateRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId)
                .isPrivate(true)
                .build();
        User moderator = new User();
        moderator.setId(11L);
        moderator.setRole(Role.MODERATOR);

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(privateRoom));
        when(userRepository.findById(11L)).thenReturn(Optional.of(moderator));

        assertTrue(chatRoomService.validateAccess(name, 11L));
    }

    @Test
    void validateAccess_PpvRoom_PpvCreator_ShouldReturnTrue() {
        User ppvCreator = new User();
        ppvCreator.setId(20L);
        
        PpvContent ppv = new PpvContent();
        ppv.setCreator(ppvCreator);
        ppv.setId(UUID.randomUUID());

        ChatRoom ppvRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creatorId) // Room creatorUserId is someone else
                .isPrivate(true)
                .ppvContent(ppv)
                .build();

        User user = new User();
        user.setId(20L);
        user.setRole(Role.CREATOR);

        when(chatRoomRepository.findByName(name)).thenReturn(Optional.of(ppvRoom));

        assertTrue(chatRoomService.validateAccess(name, 20L));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PPV_CHAT_ACCESS_GRANTED),
                any(),
                argThat(metadata ->
                        "PPV_CREATOR_BYPASS".equals(metadata.get("reason")) &&
                        Long.valueOf(20L).equals(metadata.get("userId"))
                )
        );
    }

    @Test
    void validateAccess_PpvRoom_Purchaser_ShouldLogEvent() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom ppvRoom = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .isPrivate(true)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");

        when(chatRoomRepository.findByName("ppv-room")).thenReturn(Optional.of(ppvRoom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(purchaseValidationService.hasPurchased(2L, ppvId)).thenReturn(true);

        assertTrue(chatRoomService.validateAccess("ppv-room", 2L));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PPV_CHAT_ACCESS_GRANTED),
                eq(viewer),
                argThat(metadata ->
                        "PURCHASE_VERIFIED".equals(metadata.get("reason")) &&
                        ppvId.equals(metadata.get("ppvContentId")) &&
                        viewer.getId().equals(metadata.get("userId"))
                )
        );
    }

    @Test
    void validateAccess_PpvRoom_NonPurchaser_ShouldLogDenial() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom ppvRoom = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .isPrivate(true)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");

        when(chatRoomRepository.findByName("ppv-room")).thenReturn(Optional.of(ppvRoom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(purchaseValidationService.hasPurchased(2L, ppvId)).thenReturn(false);

        ChatAccessException exception = assertThrows(ChatAccessException.class, () -> chatRoomService.validateAccess("ppv-room", 2L));
        assertEquals(ChatErrorCode.CHAT_ACCESS_REQUIRED, exception.getErrorCode());

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PPV_CHAT_MESSAGE_BLOCKED),
                eq(viewer),
                argThat(metadata -> 
                        "PURCHASE_REQUIRED".equals(metadata.get("reason")) &&
                        ppvId.equals(metadata.get("ppvContentId")) &&
                        viewer.getId().equals(metadata.get("userId"))
                )
        );
    }

    @Test
    void validateAccess_PpvRoom_CreatorBypass_ShouldLogEvent() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom ppvRoom = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .isPrivate(true)
                .creatorId(creatorId)
                .build();

        when(chatRoomRepository.findByName("ppv-room")).thenReturn(Optional.of(ppvRoom));

        assertTrue(chatRoomService.validateAccess("ppv-room", creatorId));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PPV_CHAT_ACCESS_GRANTED),
                eq(creator),
                argThat(metadata -> 
                        "CREATOR_BYPASS".equals(metadata.get("reason")) &&
                        creator.getId().equals(metadata.get("userId"))
                )
        );
    }

    @Test
    void validateAccess_PpvRoom_Anonymous_ShouldLogDenial() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom ppvRoom = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .isPrivate(true)
                .creatorId(creatorId)
                .build();

        when(chatRoomRepository.findByName("ppv-room")).thenReturn(Optional.of(ppvRoom));

        assertThrows(AccessDeniedException.class, () -> chatRoomService.validateAccess("ppv-room", null));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PPV_CHAT_MESSAGE_BLOCKED),
                isNull(),
                argThat(metadata -> 
                        "UNAUTHENTICATED".equals(metadata.get("reason")) &&
                        !metadata.containsKey("userId")
                )
        );
    }

    @Test
    void validateAccess_PpvRoom_InactiveContent_ShouldThrowException() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder()
                .id(ppvId)
                .active(false)
                .build();
        ChatRoom ppvRoom = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .isPrivate(true)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");

        when(chatRoomRepository.findByName("ppv-room")).thenReturn(Optional.of(ppvRoom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, 
                () -> chatRoomService.validateAccess("ppv-room", 2L));
        assertEquals("This content is no longer available", exception.getMessage());

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PPV_CHAT_MESSAGE_BLOCKED),
                eq(viewer),
                argThat(metadata -> 
                        "INACTIVE_CONTENT".equals(metadata.get("reason")) &&
                        viewer.getId().equals(metadata.get("userId"))
                )
        );
    }

    @Test
    void validateAccess_PpvRoom_DeletedContent_ShouldThrowException() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        // ppvContent is null, simulating deleted content where FK was set to null
        ChatRoom ppvRoom = ChatRoom.builder()
                .id(roomId)
                .name("ppv-" + ppvId)
                .ppvContent(null)
                .isPrivate(true)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);
        viewer.setEmail("viewer@test.com");

        when(chatRoomRepository.findByName("ppv-" + ppvId)).thenReturn(Optional.of(ppvRoom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, 
                () -> chatRoomService.validateAccess("ppv-" + ppvId, 2L));
        assertEquals("The associated content has been deleted", exception.getMessage());
    }

    @Test
    void checkPpvAccess_Purchased_ShouldReturnHasAccessTrue() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);
        
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(purchaseValidationService.hasPurchased(2L, ppvId)).thenReturn(true);

        ChatPpvAccessResponse result = chatRoomService.checkPpvAccess(new UUID(0, roomId), 2L);

        assertTrue(result.isHasAccess());
        assertEquals(ppvId, result.getPpvContentId());
        assertNull(result.getExpiresAt());
    }

    @Test
    void checkPpvAccess_WithExpiration_ShouldReturnExpiresAt() {
        UUID ppvId = UUID.randomUUID();
        UUID liveStreamRoomId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("stream-" + liveStreamRoomId)
                .ppvContent(content)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        PPVChatAccess access = PPVChatAccess.builder().expiresAt(expiresAt).build();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(ppvChatAccessService.getActiveAccess(2L, liveStreamRoomId)).thenReturn(Optional.of(access));

        ChatPpvAccessResponse result = chatRoomService.checkPpvAccess(new UUID(0, roomId), 2L);

        assertTrue(result.isHasAccess());
        assertEquals(ppvId, result.getPpvContentId());
        assertEquals(expiresAt, result.getExpiresAt());
    }

    @Test
    void checkPpvAccess_NoAccess_ShouldReturnHasAccessFalse() {
        UUID ppvId = UUID.randomUUID();
        Long roomId = 1001L;
        PpvContent content = PpvContent.builder().id(ppvId).build();
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .name("ppv-room")
                .ppvContent(content)
                .creatorId(creatorId)
                .build();
        User viewer = new User();
        viewer.setId(2L);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(purchaseValidationService.hasPurchased(2L, ppvId)).thenReturn(false);

        ChatPpvAccessResponse result = chatRoomService.checkPpvAccess(new UUID(0, roomId), 2L);

        assertFalse(result.isHasAccess());
        assertEquals(ppvId, result.getPpvContentId());
    }

    @Test
    void updateChatMode_AsCreator_ShouldSucceed() {
        Long roomId = 1001L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(creatorId)
                .chatMode(ChatMode.PUBLIC)
                .build();

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatRoom result = chatRoomService.updateChatMode(new UUID(0, roomId), ChatMode.SUBSCRIBERS_ONLY, creatorId);

        assertEquals(ChatMode.SUBSCRIBERS_ONLY, result.getChatMode());
        verify(chatRoomRepository).save(room);
        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.CHAT_MODE_CHANGED),
                eq(creator),
                argThat(metadata ->
                        metadata.get("roomId").equals(roomId) &&
                        metadata.get("oldMode").equals("PUBLIC") &&
                        metadata.get("newMode").equals("SUBSCRIBERS_ONLY")
                )
        );
    }

    @Test
    void updateChatMode_AsModerator_ShouldSucceed() {
        Long roomId = 1001L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(creatorId)
                .chatMode(ChatMode.PUBLIC)
                .build();
        User moderator = new User();
        moderator.setId(5L);
        moderator.setRole(Role.MODERATOR);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(5L)).thenReturn(Optional.of(moderator));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatRoom result = chatRoomService.updateChatMode(new UUID(0, roomId), ChatMode.CREATORS_ONLY, 5L);

        assertEquals(ChatMode.CREATORS_ONLY, result.getChatMode());
        verify(chatRoomRepository).save(room);
        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.CHAT_MODE_CHANGED),
                eq(moderator),
                argThat(metadata ->
                        metadata.get("roomId").equals(roomId) &&
                        metadata.get("oldMode").equals("PUBLIC") &&
                        metadata.get("newMode").equals("CREATORS_ONLY")
                )
        );
    }

    @Test
    void updateChatMode_AsNormalUser_ShouldThrowException() {
        Long roomId = 1001L;
        ChatRoom room = ChatRoom.builder()
                .id(roomId)
                .creatorId(creatorId)
                .chatMode(ChatMode.PUBLIC)
                .build();
        User normalUser = new User();
        normalUser.setId(2L);
        normalUser.setRole(Role.USER);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));

        assertThrows(AccessDeniedException.class, () -> 
                chatRoomService.updateChatMode(new UUID(0, roomId), ChatMode.SUBSCRIBERS_ONLY, 2L));
        verify(chatRoomRepository, never()).save(any());
    }
}









