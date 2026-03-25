package com.joinlivora.backend.streaming;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Disabled("Legacy streaming architecture")
@ExtendWith(MockitoExtension.class)
class CreatorStreamVerificationTest {

    @Mock
    private StreamRepository streamRepository;
    @Mock
    private LiveStreamService liveStreamServiceMock;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private UserService userService;
    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private CreatorGoLiveService creatorGoLiveService;
    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;
    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;
    @Mock
    private LiveViewerCounterService liveViewerCounterService;
    @Mock
    private CreatorVerificationRepository creatorVerificationRepository;

    @InjectMocks
    private StreamService streamService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creator@test.com");
        creator.setStatus(UserStatus.ACTIVE);
        creator.setPayoutsEnabled(true);
    }

    @Test
    void startStream_WhenPayoutsDisabled_ShouldThrowException() {
        creator.setPayoutsEnabled(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            streamService.startStream(creator, "Title", "Desc", 0L, false, null, BigDecimal.ZERO, false);
        });
    }

    @Test
    void startStream_WhenNotVerified_ShouldThrowException() {
        // Mock behavior: no verification record found
        when(creatorVerificationRepository.findByCreatorId(creator.getId())).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            streamService.startStream(creator, "Title", "Desc", 0L, false, null, BigDecimal.ZERO, false);
        });
    }

    @Test
    void startStream_WhenPending_ShouldThrowException() {
        CreatorVerification verification = CreatorVerification.builder()
                .status(VerificationStatus.PENDING)
                .build();
        when(creatorVerificationRepository.findByCreatorId(creator.getId())).thenReturn(Optional.of(verification));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            streamService.startStream(creator, "Title", "Desc", 0L, false, null, BigDecimal.ZERO, false);
        });
    }

    @Test
    void startStream_WhenRejected_ShouldThrowException() {
        CreatorVerification verification = CreatorVerification.builder()
                .status(VerificationStatus.REJECTED)
                .build();
        when(creatorVerificationRepository.findByCreatorId(creator.getId())).thenReturn(Optional.of(verification));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            streamService.startStream(creator, "Title", "Desc", 0L, false, null, BigDecimal.ZERO, false);
        });
    }

    @Test
    void startStream_WhenApproved_ShouldSucceed() {
        CreatorVerification verification = CreatorVerification.builder()
                .status(VerificationStatus.APPROVED)
                .build();
        when(creatorVerificationRepository.findByCreatorId(creator.getId())).thenReturn(Optional.of(verification));
        
        Stream savedRoom = new Stream();
        savedRoom.setId(UUID.randomUUID());

        // Needed for getCreatorRoom
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(savedRoom));
        // Needed for save
        when(streamRepository.save(any(Stream.class))).thenReturn(savedRoom);

        streamService.startStream(creator, "Title", "Desc", 0L, false, null, BigDecimal.ZERO, false);
    }
}








