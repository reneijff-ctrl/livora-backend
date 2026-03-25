package com.joinlivora.backend.streaming;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.creator.model.Creator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Disabled("Legacy streaming architecture")
@ExtendWith(MockitoExtension.class)
class StreamChatBindingTest {

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private LiveStreamService liveStreamServiceMock;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private CreatorGoLiveService creatorGoLiveService;

    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;

    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;

    @Mock
    private LiveViewerCounterService liveViewerCounterService;

    @Mock
    private com.joinlivora.backend.creator.verification.CreatorVerificationRepository creatorVerificationRepository;

    @InjectMocks
    private StreamService streamService;

    private User creator;
    private Stream room;
    private UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setEmail("creatorUserId@test.com");
        creator.setPayoutsEnabled(true);

        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(false)
                .isPaid(false)
                .build();
    }

    @Test
    void startStream_ShouldCreateChatRoomAndliveStream() {
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(room));
        when(streamRepository.save(any(Stream.class))).thenReturn(room);
        Creator creatorEntity = Creator.builder().id(777L).user(creator).build();
        when(creatorRepository.findByUser_Id(creator.getId())).thenReturn(Optional.of(creatorEntity));

        com.joinlivora.backend.creator.model.CreatorVerification verification = new com.joinlivora.backend.creator.model.CreatorVerification();
        verification.setStatus(com.joinlivora.backend.creator.verification.VerificationStatus.APPROVED);
        when(creatorVerificationRepository.findByCreatorId(creator.getId())).thenReturn(Optional.of(verification));

        streamService.startStream(creator, "Title", "Desc", 0L, false, null, null, true);

        verify(creatorGoLiveService).goLive(eq(777L), any());
    }

    @Test
    void startStream_Premium_ShouldCreatePrivateChatRoomAndliveStream() {
        room.setPaid(true);
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(room));
        when(streamRepository.save(any(Stream.class))).thenReturn(room);
        Creator creatorEntity = Creator.builder().id(777L).user(creator).build();
        when(creatorRepository.findByUser_Id(creator.getId())).thenReturn(Optional.of(creatorEntity));

        com.joinlivora.backend.creator.model.CreatorVerification verification = new com.joinlivora.backend.creator.model.CreatorVerification();
        verification.setStatus(com.joinlivora.backend.creator.verification.VerificationStatus.APPROVED);
        when(creatorVerificationRepository.findByCreatorId(creator.getId())).thenReturn(Optional.of(verification));

        streamService.startStream(creator, "Title", "Desc", 0L, false, null, null, false);

        verify(creatorGoLiveService).goLive(eq(777L), any());
    }

    @Test
    void stopStream_ShouldInvokeliveStreamServiceStop() {
        room.setLive(true);
        when(streamRepository.findByCreator(creator)).thenReturn(Optional.of(room));
        when(streamRepository.save(any(Stream.class))).thenReturn(room);

        streamService.stopStream(creator);

        // Verify that StreamService delegates to LiveStreamService
        verify(liveStreamServiceMock).stopStream(eq(creator.getId()));
    }
}









