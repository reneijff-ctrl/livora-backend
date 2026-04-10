package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.wallet.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LivestreamAccessControllerTest {

    private LivestreamAccessService accessService;
    private LiveViewerCounterService liveViewerCounterService;
    private TokenService tokenService;
    private StreamRepository streamRepository;
    private com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    private LivestreamAccessController controller;

    @BeforeEach
    void setUp() {
        accessService = mock(LivestreamAccessService.class);
        liveViewerCounterService = mock(LiveViewerCounterService.class);
        tokenService = mock(TokenService.class);
        streamRepository = mock(StreamRepository.class);
        creatorPresenceService = mock(com.joinlivora.backend.presence.service.CreatorPresenceService.class);
        controller = new LivestreamAccessController(accessService, liveViewerCounterService, tokenService, streamRepository, creatorPresenceService);
    }

    private Stream buildLiveStream(UUID id, Long creatorUserId, boolean isPaid, BigDecimal admissionPrice) {
        User creator = new User();
        creator.setId(creatorUserId);
        Stream stream = new Stream();
        stream.setId(id);
        stream.setCreator(creator);
        stream.setLive(true);
        stream.setPaid(isPaid);
        stream.setAdmissionPrice(admissionPrice);
        return stream;
    }

    @Test
    void getAccess_returnsAdmissionPrice_whenStreamLive() {
        Long creatorId = 15L;
        Long viewerId = 25L;
        UUID streamId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(viewerId, "viewer@test.com", "pwd", Collections.emptyList());

        Stream stream = buildLiveStream(streamId, creatorId, true, new BigDecimal("30"));
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId)).thenReturn(List.of(stream));
        when(accessService.hasAccess(eq(streamId), eq(viewerId))).thenReturn(false);
        when(liveViewerCounterService.getViewerCount(creatorId)).thenReturn(100L);

        var response = controller.getAccess(principal, creatorId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(30, response.getBody().getAdmissionPrice().intValue());
        assertEquals(100L, response.getBody().getViewerCount());
        assertFalse(response.getBody().isHasAccess());
        assertTrue(response.getBody().isLive());
        assertEquals(streamId, response.getBody().getStreamId());
    }

    @Test
    void purchaseAccess_success_whenLiveAndPaid() {
        Long creatorId = 10L;
        Long viewerId = 20L;
        UUID streamId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(viewerId, "viewer@test.com", "pwd", Collections.emptyList());

        Stream stream = buildLiveStream(streamId, creatorId, true, new BigDecimal("25"));
        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId)).thenReturn(List.of(stream));
        when(accessService.hasAccess(eq(streamId), eq(viewerId))).thenReturn(false);

        var response = controller.purchaseAccess(principal, creatorId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("success", true), response.getBody());

        verify(tokenService).spendTokens(viewerId, 25L, WalletTransactionType.LIVESTREAM_ADMISSION, creatorId.toString());
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(accessService).grantAccess(eq(streamId), eq(viewerId), durationCaptor.capture());
        assertEquals(Duration.ofSeconds(7200), durationCaptor.getValue());
    }

    @Test
    void purchaseAccess_returnsNotFound_whenNoLiveStream() {
        Long creatorId = 11L;
        Long viewerId = 21L;
        UserPrincipal principal = new UserPrincipal(viewerId, "viewer@test.com", "pwd", Collections.emptyList());

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId)).thenReturn(Collections.emptyList());

        var response = controller.purchaseAccess(principal, creatorId);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(Map.of("success", false), response.getBody());
        verify(tokenService, never()).spendTokens(anyLong(), anyLong(), any(), anyString());
        verify(accessService, never()).grantAccess(any(UUID.class), anyLong(), any());
    }

    @Test
    void purchaseAccess_returnsUnauthorized_whenNoPrincipal() {
        var response = controller.purchaseAccess(null, 1L);
        assertEquals(401, response.getStatusCode().value());
    }
}
