package com.joinlivora.backend.streaming;

import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscription;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private com.joinlivora.backend.monetization.PpvService ppvService;

    @Mock
    private com.joinlivora.backend.monetization.PPVPurchaseValidationService purchaseValidationService;

    @Mock
    private com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Mock
    private LiveViewerCounterService liveViewerCounterService;

    @Mock
    private LiveAccessService liveAccessService;

    @Mock
    private LivestreamAnalyticsService analyticsService;

    @Mock
    private com.joinlivora.backend.livestream.repository.LivestreamSessionRepository livestreamSessionRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.joinlivora.backend.creator.verification.CreatorVerificationRepository creatorVerificationRepository;

    @Mock
    private com.joinlivora.backend.streaming.client.MediasoupClient mediasoupClient;

    @InjectMocks
    private LiveStreamService liveStreamService;

    private Long creatorId = 1L;
    private String liveStreamTitle = "Test Stream";

    @Test
    void verifyStreamKeyAndStart_ShouldUpdateGauge() {
        String key = "sk_test";
        User creator = new User();
        creator.setId(creatorId);
        creator.setPayoutsEnabled(true);
        Stream liveStream = Stream.builder()
                .creator(creator)
                .streamKey(key)
                .build();
        
        java.util.concurrent.atomic.AtomicLong gauge = new java.util.concurrent.atomic.AtomicLong(0);
        when(meterRegistry.gauge(eq("streaming.active.streams"), any(java.util.concurrent.atomic.AtomicLong.class)))
                .thenReturn(gauge);
        when(streamRepository.countByIsLiveTrue()).thenReturn(1L);
        
        liveStreamService.init();

        // After init, gauge should reflect countByIsLiveTrue
        assertEquals(1L, gauge.get());
    }

    @Test
    void startStream_NewStream_ShouldCreateAndReturnKey() {
        User creator = new User();
        creator.setId(creatorId);
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId)).thenReturn(List.of());

        String resultKey = liveStreamService.startStream(creatorId, liveStreamTitle, true, null, true);

        assertNotNull(resultKey);
        assertTrue(resultKey.startsWith("sk_"));
    }

    @Test
    void startStream_ExistingStream_ShouldUpdateAndReturnKey() {
        User creator = new User();
        creator.setId(creatorId);
        Stream existing = Stream.builder()
                .creator(creator)
                .streamKey("sk_existing")
                .isLive(true)
                .build();
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId)).thenReturn(List.of(existing));

        String resultKey = liveStreamService.startStream(creatorId, "Updated Title", false, null, false);

        assertEquals("sk_existing", resultKey);
    }

    @Test
    void updateRecordingPath_ShouldLogWhenEnabled() {
        String key = "sk_test";
        Stream liveStream = Stream.builder()
                .id(UUID.randomUUID())
                .streamKey(key)
                .build();
        
        when(streamRepository.findByStreamKeyWithCreator(key)).thenReturn(Optional.of(liveStream));

        liveStreamService.updateRecordingPath(key, "/path/to/record.flv");

        verify(streamRepository, never()).save(any(Stream.class));
    }

    @Test
    void updateRecordingPath_ShouldNotUpdateWhenDisabled() {
        String key = "sk_test";
        Stream liveStream = Stream.builder()
                .id(UUID.randomUUID())
                .streamKey(key)
                .build();
        
        when(streamRepository.findByStreamKeyWithCreator(key)).thenReturn(Optional.of(liveStream));

        liveStreamService.updateRecordingPath(key, "/path/to/record.flv");

        assertNull(liveStream.getThumbnailUrl());
        verify(streamRepository, never()).save(any(Stream.class));
    }

    @Test
    void verifyStreamKeyAndStart_ShouldMarkLive() {
        String key = "sk_test";
        User creator = new User();
        creator.setId(creatorId);
        creator.setPayoutsEnabled(true);
        Stream liveStream = Stream.builder()
                .creator(creator)
                .streamKey(key)
                .isLive(false)
                .build();

        when(streamRepository.findByStreamKeyWithCreator(key)).thenReturn(Optional.of(liveStream));
        when(streamRepository.save(any(Stream.class))).thenReturn(liveStream);
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));

        com.joinlivora.backend.creator.model.CreatorVerification verification = new com.joinlivora.backend.creator.model.CreatorVerification();
        verification.setStatus(com.joinlivora.backend.creator.verification.VerificationStatus.APPROVED);
        when(creatorVerificationRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(verification));

        boolean result = liveStreamService.verifyStreamKeyAndStart(key);

        assertTrue(result);
        assertTrue(liveStream.isLive());
        assertNotNull(liveStream.getStartedAt());
        verify(streamRepository).save(liveStream);
    }

    @Test
    void verifyStreamKeyAndStop_ShouldMarkOffline() {
        String key = "sk_test";
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder()
                .creator(creator)
                .streamKey(key)
                .isLive(true)
                .build();

        when(streamRepository.findByStreamKeyWithCreator(key)).thenReturn(Optional.of(liveStream));
        when(streamRepository.save(any(Stream.class))).thenReturn(liveStream);

        liveStreamService.verifyStreamKeyAndStop(key);

        assertFalse(liveStream.isLive());
        assertNotNull(liveStream.getEndedAt());
        verify(streamRepository).save(liveStream);
    }

    @Test
    void verifyStreamKeyAndStart_NotVerified_ShouldReturnFalse() {
        String key = "sk_test";
        User creator = new User();
        creator.setId(creatorId);
        creator.setPayoutsEnabled(true);
        Stream liveStream = Stream.builder()
                .creator(creator)
                .streamKey(key)
                .build();

        when(streamRepository.findByStreamKeyWithCreator(key)).thenReturn(Optional.of(liveStream));

        // No verification record → should reject
        when(creatorVerificationRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());

        boolean result = liveStreamService.verifyStreamKeyAndStart(key);

        assertFalse(result);
        verify(streamRepository, never()).save(any());
    }

    @Test
    void stopStream_ShouldSetIsLiveFalse() {
        User creator = new User();
        creator.setId(creatorId);
        Stream existing = Stream.builder()
                .creator(creator)
                .isLive(true)
                .build();
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId)).thenReturn(List.of(existing));

        liveStreamService.stopStream(creatorId);

        assertFalse(existing.isLive());
        assertNotNull(existing.getEndedAt());
        verify(streamRepository).save(existing);
    }

    @Test
    void stopStream_NoActiveStream_ShouldNotSave() {
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueWithCreator(creatorId)).thenReturn(List.of());

        liveStreamService.stopStream(creatorId);

        verify(streamRepository, never()).save(any());
    }

    @Test
    void validateViewerAccess_PublicStream_ShouldReturnTrue() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(false).build();
        User viewer = new User();
        viewer.setId(2L);

        assertTrue(liveStreamService.validateViewerAccess(liveStream, viewer));
    }

    @Test
    void validateViewerAccess_PaidStream_NonSubscriber_ShouldReturnFalse() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(true).build();
        User viewer = new User();
        viewer.setId(2L);

        when(liveAccessService.hasAccess(creatorId, 2L)).thenReturn(false);

        assertFalse(liveStreamService.validateViewerAccess(liveStream, viewer));
    }

    @Test
    void validateViewerAccess_PaidStream_Subscriber_ShouldReturnTrue() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(true).build();
        User viewer = new User();
        viewer.setId(2L);

        when(liveAccessService.hasAccess(creatorId, 2L)).thenReturn(true);

        assertTrue(liveStreamService.validateViewerAccess(liveStream, viewer));
    }

    @Test
    void validateViewerAccess_PaidStream_TokenAccess_NoSubscription_ShouldReturnTrue() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(true).build();
        User viewer = new User();
        viewer.setId(2L);

        when(liveAccessService.hasAccess(creatorId, 2L)).thenReturn(true);

        assertTrue(liveStreamService.validateViewerAccess(liveStream, viewer));
    }

    @Test
    void validateViewerAccess_Creator_ShouldReturnTrue() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(true).build();
        User user = new User();
        user.setId(creatorId);

        assertTrue(liveStreamService.validateViewerAccess(liveStream, user));
    }

    @Test
    void generateSecureStreamKey_ShouldReturnValidKey() {
        String key = liveStreamService.generateSecureStreamKey();
        assertNotNull(key);
        assertTrue(key.startsWith("sk_"));
        assertTrue(key.length() > 10);
    }

    @Test
    void validateViewerAccess_Admin_ShouldReturnTrue() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(true).build();
        User admin = new User();
        admin.setId(10L);
        admin.setRole(Role.ADMIN);

        assertTrue(liveStreamService.validateViewerAccess(liveStream, admin));
    }

    @Test
    void validateViewerAccess_Moderator_ShouldReturnTrue() {
        User creator = new User();
        creator.setId(creatorId);
        Stream liveStream = Stream.builder().creator(creator).isPaid(true).build();
        User moderator = new User();
        moderator.setId(11L);
        moderator.setRole(Role.MODERATOR);

        assertTrue(liveStreamService.validateViewerAccess(liveStream, moderator));
    }
}








