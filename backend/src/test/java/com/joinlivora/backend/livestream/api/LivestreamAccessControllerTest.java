package com.joinlivora.backend.livestream.api;

import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LivestreamAccessControllerTest {

    private LivestreamAccessService accessService;
    private LiveViewerCounterService liveViewerCounterService;
    private TokenService tokenService;
    private StreamRepository StreamRepository;
    private com.joinlivora.backend.livestream.repository.LivestreamSessionRepository sessionRepository;
    private com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    private LivestreamAccessController controller;

    @BeforeEach
    void setUp() {
        accessService = mock(LivestreamAccessService.class);
        liveViewerCounterService = mock(LiveViewerCounterService.class);
        tokenService = mock(TokenService.class);
        StreamRepository = mock(StreamRepository.class);
        sessionRepository = mock(com.joinlivora.backend.livestream.repository.LivestreamSessionRepository.class);
        creatorPresenceService = mock(com.joinlivora.backend.presence.service.CreatorPresenceService.class);
        controller = new LivestreamAccessController(accessService, liveViewerCounterService, tokenService, StreamRepository, sessionRepository, creatorPresenceService);
    }

    @Test
    void getAccess_returnsAdmissionPrice_whenSessionExists() {
        Long creatorId = 15L;
        Long viewerId = 25L;
        UserPrincipal principal = new UserPrincipal(viewerId, "viewer@test.com", "pwd", Collections.emptyList());

        com.joinlivora.backend.livestream.domain.LivestreamSession session = com.joinlivora.backend.livestream.domain.LivestreamSession.builder()
                .id(100L)
                .status(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)
                .isPaid(true)
                .admissionPrice(new BigDecimal("30"))
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(accessService.hasAccess(eq(100L), eq(viewerId))).thenReturn(false);
        when(liveViewerCounterService.getViewerCount(creatorId)).thenReturn(100L);

        var response = controller.getAccess(principal, creatorId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(30, response.getBody().getAdmissionPrice().intValue());
        assertEquals(100L, response.getBody().getViewerCount());
        assertFalse(response.getBody().isHasAccess());
        assertTrue(response.getBody().isLive());
        assertEquals(100L, response.getBody().getSessionId());
    }

    @Test
    void purchaseAccess_success_whenLiveAndPaid() {
        Long creatorId = 10L;
        Long viewerId = 20L;
        UserPrincipal principal = new UserPrincipal(viewerId, "viewer@test.com", "pwd", Collections.emptyList());

        com.joinlivora.backend.livestream.domain.LivestreamSession session = com.joinlivora.backend.livestream.domain.LivestreamSession.builder()
                .id(200L)
                .status(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)
                .isPaid(true)
                .admissionPrice(new BigDecimal("25"))
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(accessService.hasAccess(eq(200L), eq(viewerId))).thenReturn(false);

        var response = controller.purchaseAccess(principal, creatorId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("success", true), response.getBody());

        verify(tokenService).spendTokens(viewerId, 25L, WalletTransactionType.LIVESTREAM_ADMISSION, creatorId.toString());
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(accessService).grantAccess(eq(200L), eq(viewerId), durationCaptor.capture());
        assertEquals(Duration.ofSeconds(7200), durationCaptor.getValue());
    }

    @Test
    void purchaseAccess_returnsNotFound_whenNoLiveSession() {
        Long creatorId = 11L;
        Long viewerId = 21L;
        UserPrincipal principal = new UserPrincipal(viewerId, "viewer@test.com", "pwd", Collections.emptyList());

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.empty());

        var response = controller.purchaseAccess(principal, creatorId);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(Map.of("success", false), response.getBody());
        verify(tokenService, never()).spendTokens(anyLong(), anyLong(), any(), anyString());
        verify(accessService, never()).grantAccess(anyLong(), anyLong(), any());
    }

    @Test
    void purchaseAccess_returnsUnauthorized_whenNoPrincipal() {
        var response = controller.purchaseAccess(null, 1L);
        assertEquals(401, response.getStatusCode().value());
    }
}








