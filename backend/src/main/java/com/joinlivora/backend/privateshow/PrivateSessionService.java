package com.joinlivora.backend.privateshow;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.presence.entity.CreatorPresence;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.exception.CreatorOfflineException;
import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivateSessionService {

    private final PrivateSessionRepository sessionRepository;
    private final PrivateSpySessionRepository spySessionRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final CreatorEarningsService creatorEarningsService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomService chatRoomService;
    private final CreatorPresenceService creatorPresenceService;
    private final CreatorRepository creatorRepository;
    private final CreatorPrivateSettingsService creatorPrivateSettingsService;

    private static final Set<PrivateSessionStatus> ACTIVE_STATUSES = Set.of(
            PrivateSessionStatus.REQUESTED,
            PrivateSessionStatus.ACCEPTED,
            PrivateSessionStatus.ACTIVE
    );

    @Transactional(readOnly = true)
    public PrivateSessionDto getSession(UUID sessionId) {
        PrivateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        return mapToDto(session);
    }

    @Transactional(readOnly = true)
    public PrivateSessionDto getActiveSessionForUser(Long userId) {
        // Check as viewer first, then as creator
        return sessionRepository.findFirstByViewer_IdAndStatusInOrderByRequestedAtDesc(userId, ACTIVE_STATUSES)
                .or(() -> sessionRepository.findFirstByCreator_IdAndStatusInOrderByRequestedAtDesc(userId, ACTIVE_STATUSES))
                .map(this::mapToDto)
                .orElse(null);
    }

    private PrivateSessionDto mapToDto(PrivateSession session) {
        return new PrivateSessionDto(
                session.getId(),
                session.getViewer().getId(),
                session.getViewer().getUsername(),
                session.getCreator().getId(),
                session.getCreator().getUsername(),
                session.getStatus(),
                session.getPricePerMinute(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }

    @Transactional
    public PrivateSessionDto requestPrivateShow(User viewer, Long creatorId, long pricePerMinute) {
        // Validate creator existence via Creator table only (by userId mapping)
        if (!creatorRepository.existsByUser_Id(creatorId)) {
            throw new RuntimeException("Creator not found");
        }

        // Enforce creator private settings
        CreatorPrivateSettings settings = creatorPrivateSettingsService.getOrCreate(creatorId);
        if (!settings.isEnabled()) {
            throw new IllegalStateException("Private sessions are disabled for this creator");
        }

        // Use creator's configured price instead of viewer-supplied value
        long price = settings.getPricePerMinute();

        // TASK 2: Prevent duplicate sessions
        if (sessionRepository.existsByViewer_IdAndCreator_IdAndStatusIn(viewer.getId(), creatorId, ACTIVE_STATUSES)) {
            throw new RuntimeException("Private session already exists");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        // Online status is OPTIONAL; get from presence if available, but do not block
        boolean isOnline = creatorPresenceService.getPresence(creatorId)
                .map(CreatorPresence::isOnline)
                .orElse(false);
        log.debug("Private show request: creator {} online status: {}", creatorId, isOnline);

        if (tokenService.getBalance(viewer).getBalance() < price) {
            throw new RuntimeException("Insufficient tokens for even 1 minute of private show");
        }

        PrivateSession session = PrivateSession.builder()
                .viewer(viewer)
                .creator(creator)
                .pricePerMinute(price)
                .status(PrivateSessionStatus.REQUESTED)
                .build();

        PrivateSession saved = sessionRepository.save(session);

        try {
            messagingTemplate.convertAndSendToUser(creator.getId().toString(), "/queue/private-show-requests", 
                    RealtimeMessage.builder()
                            .type("PRIVATE_SHOW_REQUEST")
                            .timestamp(Instant.now())
                            .payload(Map.of(
                                    "sessionId", saved.getId(),
                                    "viewerEmail", viewer.getEmail(),
                                    "pricePerMinute", price
                            ))
                            .build());
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show request to creator {}: {}", creator.getId(), e.getMessage());
        }

        return mapToDto(saved);
    }

    @Transactional
    public PrivateSessionDto acceptRequest(User creator, UUID sessionId) {
        PrivateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getCreator().getId().equals(creator.getId())) {
            throw new RuntimeException("Only the assigned creator can accept this request");
        }

        if (session.getStatus() != PrivateSessionStatus.REQUESTED) {
            throw new RuntimeException("Request is not in PENDING state");
        }

        session.setStatus(PrivateSessionStatus.ACCEPTED);
        session.setAcceptedAt(Instant.now());
        sessionRepository.save(session);

        Map<String, Object> acceptedPayload = Map.of(
                "sessionId", sessionId,
                "viewerId", session.getViewer().getId(),
                "pricePerMinute", session.getPricePerMinute()
        );

        try {
            messagingTemplate.convertAndSendToUser(session.getViewer().getId().toString(), "/queue/private-show-status",
                    RealtimeMessage.builder()
                            .type("PRIVATE_SHOW_ACCEPTED")
                            .timestamp(Instant.now())
                            .payload(acceptedPayload)
                            .build());
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show accepted to viewer {}: {}", session.getViewer().getId(), e.getMessage());
        }

        try {
            messagingTemplate.convertAndSendToUser(session.getCreator().getId().toString(), "/queue/private-show-status",
                    RealtimeMessage.builder()
                            .type("PRIVATE_SHOW_ACCEPTED")
                            .timestamp(Instant.now())
                            .payload(acceptedPayload)
                            .build());
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show accepted to creator {}: {}", session.getCreator().getId(), e.getMessage());
        }

        return mapToDto(session);
    }

    @Transactional
    public PrivateSessionDto rejectRequest(User creator, UUID sessionId) {
        PrivateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getCreator().getId().equals(creator.getId())) {
            throw new RuntimeException("Only the assigned creator can reject this request");
        }

        session.setStatus(PrivateSessionStatus.REJECTED);
        session.setRejectedAt(Instant.now());
        sessionRepository.save(session);

        try {
            messagingTemplate.convertAndSendToUser(session.getViewer().getId().toString(), "/queue/private-show-status",
                    RealtimeMessage.builder()
                            .type("PRIVATE_SHOW_REJECTED")
                            .timestamp(Instant.now())
                            .payload(Map.of("sessionId", sessionId))
                            .build());
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show rejected to viewer {}: {}", session.getViewer().getId(), e.getMessage());
        }

        return mapToDto(session);
    }

    @Transactional
    public PrivateSessionDto startSession(User creator, UUID sessionId) {
        PrivateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getCreator().getId().equals(creator.getId())) {
            throw new RuntimeException("Only the assigned creator can start this session");
        }

        if (session.getStatus() != PrivateSessionStatus.ACCEPTED) {
            throw new RuntimeException("Session must be ACCEPTED to start");
        }

        return mapToDto(startSessionInternal(session));
    }

    private PrivateSession startSessionInternal(PrivateSession session) {
        // TASK 6: If creator already has active sessions, end them gracefully instead of crashing
        List<PrivateSession> existingActive = sessionRepository.findAllByCreator_IdAndStatus(
                session.getCreator().getId(), PrivateSessionStatus.ACTIVE);
        if (!existingActive.isEmpty()) {
            log.warn("Creator {} has {} existing ACTIVE sessions — ending them before starting new one",
                    session.getCreator().getId(), existingActive.size());
            for (PrivateSession existing : existingActive) {
                try {
                    internalEndSession(existing, "Replaced by new private session");
                } catch (Exception e) {
                    log.error("Failed to end existing active session {}: {}", existing.getId(), e.getMessage());
                }
            }
        }

        session.setStatus(PrivateSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());
        session.setLastBilledAt(Instant.now());

        try {
            billMinute(session);
        } catch (Exception e) {
            log.error("Failed to bill first minute for session {}: {}", session.getId(), e.getMessage());
            session.setStatus(PrivateSessionStatus.ENDED);
            session.setEndedAt(Instant.now());
            session.setEndReason("Billing failed at start");
            sessionRepository.save(session);
            throw new RuntimeException("Cannot start session: billing failed");
        }

        sessionRepository.save(session);

        // Chat room and WebSocket notifications are non-critical; failures must not roll back the session
        UUID chatRoomId = null;
        try {
            com.joinlivora.backend.chat.domain.ChatRoom chatRoom = chatRoomService.createPrivateRoom("private-session-" + session.getId(), session.getCreator().getId());
            chatRoomId = new java.util.UUID(0, chatRoom.getId());
        } catch (Exception e) {
            log.error("Failed to create private chat room for session {}: {}", session.getId(), e.getMessage());
        }

        notifySessionStarted(session, chatRoomId != null ? chatRoomId : session.getId());

        return session;
    }

    private void notifySessionStarted(PrivateSession session, UUID chatRoomId) {
        RealtimeMessage msg = RealtimeMessage.builder()
                .type("PRIVATE_SHOW_STARTED")
                .timestamp(Instant.now())
                .payload(Map.of(
                        "sessionId", session.getId(),
                        "chatRoomId", chatRoomId
                ))
                .build();

        try {
            messagingTemplate.convertAndSendToUser(session.getViewer().getId().toString(), "/queue/private-show-status", msg);
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show started to viewer {}: {}", session.getViewer().getId(), e.getMessage());
        }
        try {
            messagingTemplate.convertAndSendToUser(session.getCreator().getId().toString(), "/queue/private-show-status", msg);
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show started to creator {}: {}", session.getCreator().getId(), e.getMessage());
        }
    }

    @Transactional
    public PrivateSessionDto endSession(User user, UUID sessionId, String reason) {
        PrivateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getCreator().getId().equals(user.getId()) && !session.getViewer().getId().equals(user.getId())) {
            throw new RuntimeException("Only the viewer or creator can end this session");
        }

        internalEndSession(session, reason);
        return mapToDto(session);
    }

    private void internalEndSession(PrivateSession session, String reason) {
        if (session.getStatus() == PrivateSessionStatus.ENDED) {
            return;
        }

        session.setStatus(PrivateSessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        session.setEndReason(reason);
        sessionRepository.save(session);

        endAllSpiesForSession(session.getId());

        notifySessionUpdate(session, "PRIVATE_SHOW_ENDED");
    }

    @Transactional
    public void processBilling() {
        try {
            List<PrivateSession> activeSessions = sessionRepository.findAllByStatus(PrivateSessionStatus.ACTIVE);
            Instant now = Instant.now();

            for (PrivateSession session : activeSessions) {
                try {
                    if (Duration.between(session.getLastBilledAt(), now).toMinutes() >= 1) {
                        try {
                            billMinute(session);
                            session.setLastBilledAt(now);
                            sessionRepository.save(session);
                        } catch (Exception e) {
                            log.warn("Auto-ending private session {} due to billing failure: {}", session.getId(), e.getMessage());
                            internalEndSession(session, "Insufficient tokens");
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error processing billing for session {}: {}", session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Private session billing failed entirely: {}", e.getMessage());
        }
    }

    private void billMinute(PrivateSession session) {
        log.info("Billing private session {}: {} tokens from {} to {}", 
                session.getId(), session.getPricePerMinute(), session.getViewer().getEmail(), session.getCreator().getEmail());
        
        tokenService.deductTokens(session.getViewer(), session.getPricePerMinute(), WalletTransactionType.PRIVATE_SHOW, "Private show: " + session.getId());
        creatorEarningsService.recordPrivateShowEarning(session.getViewer(), session.getCreator(), session.getPricePerMinute(), session.getId());
    }

    private void notifySessionUpdate(PrivateSession session, String type) {
        RealtimeMessage msg = RealtimeMessage.builder()
                .type(type)
                .timestamp(Instant.now())
                .payload(Map.of(
                        "sessionId", session.getId(),
                        "reason", session.getEndReason() != null ? session.getEndReason() : ""
                ))
                .build();

        try {
            messagingTemplate.convertAndSendToUser(session.getViewer().getId().toString(), "/queue/private-show-status", msg);
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show update to viewer {}: {}", session.getViewer().getId(), e.getMessage());
        }
        try {
            messagingTemplate.convertAndSendToUser(session.getCreator().getId().toString(), "/queue/private-show-status", msg);
        } catch (Exception e) {
            log.warn("WebSocket send failed for private show update to creator {}: {}", session.getCreator().getId(), e.getMessage());
        }
    }

    // Expire ACCEPTED sessions that were never started (safety net)
    @Transactional
    public void expireStaleAcceptedSessions() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
            List<PrivateSession> stale = sessionRepository.findAllByStatusAndAcceptedAtBefore(
                    PrivateSessionStatus.ACCEPTED, cutoff);

            for (PrivateSession session : stale) {
                try {
                    log.info("Auto-expiring stale ACCEPTED session {}: accepted at {}", session.getId(), session.getAcceptedAt());
                    session.setStatus(PrivateSessionStatus.ENDED);
                    session.setEndedAt(Instant.now());
                    session.setEndReason("Expired: not started within 5 minutes");
                    sessionRepository.save(session);
                    notifySessionUpdate(session, "PRIVATE_SHOW_ENDED");
                } catch (Exception e) {
                    log.error("Failed to expire stale session {}: {}", session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to expire stale accepted sessions: {}", e.getMessage());
        }
    }

    /**
     * TASK 6: Clean up duplicate ACTIVE sessions.
     * Keeps only the latest per viewer+creator pair, ends all older duplicates.
     */
    @Transactional
    public void cleanupDuplicateActiveSessions() {
        try {
            List<PrivateSession> allActive = sessionRepository.findAllByStatus(PrivateSessionStatus.ACTIVE);
            if (allActive.size() <= 1) {
                return;
            }

            // Group by viewer+creator pair
            java.util.Map<String, List<PrivateSession>> grouped = new java.util.HashMap<>();
            for (PrivateSession s : allActive) {
                String key = s.getViewer().getId() + "-" + s.getCreator().getId();
                grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(s);
            }

            for (java.util.Map.Entry<String, List<PrivateSession>> entry : grouped.entrySet()) {
                List<PrivateSession> sessions = entry.getValue();
                if (sessions.size() <= 1) {
                    continue;
                }

                // Sort by startedAt descending — keep the latest
                sessions.sort((a, b) -> {
                    Instant aTime = a.getStartedAt() != null ? a.getStartedAt() : Instant.MIN;
                    Instant bTime = b.getStartedAt() != null ? b.getStartedAt() : Instant.MIN;
                    return bTime.compareTo(aTime);
                });

                // End all but the latest
                for (int i = 1; i < sessions.size(); i++) {
                    PrivateSession dup = sessions.get(i);
                    log.warn("Ending duplicate ACTIVE session {}: viewer={}, creator={}", dup.getId(), dup.getViewer().getId(), dup.getCreator().getId());
                    try {
                        internalEndSession(dup, "Duplicate session cleanup");
                    } catch (Exception e) {
                        log.error("Failed to end duplicate session {}: {}", dup.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanup duplicate active sessions: {}", e.getMessage());
        }
    }

    // ===================== SPY SESSION METHODS =====================

    private PrivateSpySessionDto mapToSpyDto(PrivateSpySession spy) {
        return new PrivateSpySessionDto(
                spy.getId(),
                spy.getPrivateSession().getId(),
                spy.getSpyViewer().getId(),
                spy.getSpyPricePerMinute(),
                spy.getStatus(),
                spy.getStartedAt(),
                spy.getEndedAt()
        );
    }

    @Transactional
    public PrivateSpySessionDto joinAsSpy(User viewer, UUID privateSessionId) {
        PrivateSession session = sessionRepository.findById(privateSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Private session not found"));

        if (session.getStatus() != PrivateSessionStatus.ACTIVE) {
            throw new IllegalStateException("Private session is not active");
        }

        // Cannot spy on your own session
        if (session.getViewer().getId().equals(viewer.getId())) {
            throw new IllegalStateException("The main private viewer cannot spy on their own session");
        }
        if (session.getCreator().getId().equals(viewer.getId())) {
            throw new IllegalStateException("The creator cannot spy on their own session");
        }

        // Check creator spy settings — always load fresh from DB
        CreatorPrivateSettings settings = creatorPrivateSettingsService.getOrCreate(session.getCreator().getId());
        if (!settings.isEnabled()) {
            throw new AccessDeniedException("Private sessions are disabled for this creator");
        }
        if (!settings.isAllowSpyOnPrivate()) {
            throw new AccessDeniedException("Spy on private is disabled for this creator");
        }

        // Check max spy viewers
        if (settings.getMaxSpyViewers() != null) {
            int currentSpyCount = spySessionRepository.countByPrivateSession_IdAndStatus(privateSessionId, SpySessionStatus.ACTIVE);
            if (currentSpyCount >= settings.getMaxSpyViewers()) {
                throw new IllegalStateException("Maximum spy viewer limit reached");
            }
        }

        // Prevent duplicate spy sessions
        if (spySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(viewer.getId(), privateSessionId, SpySessionStatus.ACTIVE)) {
            throw new IllegalStateException("You are already spying on this session");
        }

        // Lock in the spy price at join time
        long spyPrice = settings.getSpyPricePerMinute();

        // Token balance check
        if (tokenService.getBalance(viewer).getBalance() < spyPrice) {
            throw new InsufficientBalanceException("Insufficient tokens for spy session");
        }

        // Create and persist spy session first so it has an ID for billing reference
        PrivateSpySession spySess = PrivateSpySession.builder()
                .privateSession(session)
                .spyViewer(viewer)
                .spyPricePerMinute(spyPrice)
                .status(SpySessionStatus.ACTIVE)
                .startedAt(Instant.now())
                .lastBilledAt(Instant.now())
                .build();
        spySessionRepository.save(spySess);

        // Bill first minute
        try {
            billSpyMinute(spySess, session);
        } catch (Exception e) {
            log.error("Failed to bill first spy minute for viewer {}: {}", viewer.getId(), e.getMessage());
            // Roll back the spy session
            spySess.setStatus(SpySessionStatus.ENDED);
            spySess.setEndedAt(Instant.now());
            spySess.setEndReason("Billing failed");
            spySessionRepository.save(spySess);
            throw new IllegalStateException("Cannot start spy session: billing failed");
        }

        log.info("Spy session started: spy={} privateSession={} price={}", viewer.getId(), privateSessionId, spyPrice);

        // Notify creator of updated spy count
        notifySpyCountUpdate(session);

        return mapToSpyDto(spySess);
    }

    @Transactional
    public void leaveSpySession(User viewer, UUID spySessionId) {
        PrivateSpySession spy = spySessionRepository.findById(spySessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Spy session not found"));

        if (!spy.getSpyViewer().getId().equals(viewer.getId())) {
            throw new IllegalStateException("Only the spy viewer can leave this session");
        }

        if (spy.getStatus() == SpySessionStatus.ENDED) {
            return;
        }

        internalEndSpySession(spy, "Left by viewer");
    }

    private void internalEndSpySession(PrivateSpySession spy, String reason) {
        spy.setStatus(SpySessionStatus.ENDED);
        spy.setEndedAt(Instant.now());
        spy.setEndReason(reason);
        spySessionRepository.save(spy);

        // Notify the spy viewer
        try {
            RealtimeMessage msg = RealtimeMessage.builder()
                    .type("SPY_SESSION_ENDED")
                    .timestamp(Instant.now())
                    .payload(Map.of(
                            "spySessionId", spy.getId(),
                            "privateSessionId", spy.getPrivateSession().getId(),
                            "reason", reason
                    ))
                    .build();
            messagingTemplate.convertAndSendToUser(spy.getSpyViewer().getId().toString(), "/queue/private-show-status", msg);
        } catch (Exception e) {
            log.warn("WebSocket send failed for spy session end to viewer {}: {}", spy.getSpyViewer().getId(), e.getMessage());
        }

        // Notify creator of updated spy count
        try {
            notifySpyCountUpdate(spy.getPrivateSession());
        } catch (Exception e) {
            log.warn("Failed to notify spy count update: {}", e.getMessage());
        }
    }

    public void endAllSpiesForSession(UUID privateSessionId) {
        List<PrivateSpySession> activeSpies = spySessionRepository.findAllByPrivateSession_IdAndStatus(privateSessionId, SpySessionStatus.ACTIVE);
        for (PrivateSpySession spy : activeSpies) {
            try {
                internalEndSpySession(spy, "Main private session ended");
            } catch (Exception e) {
                log.error("Failed to end spy session {} when main session ended: {}", spy.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void processSpyBilling() {
        try {
            List<PrivateSpySession> activeSpies = spySessionRepository.findAllByStatus(SpySessionStatus.ACTIVE);
            Instant now = Instant.now();

            for (PrivateSpySession spy : activeSpies) {
                try {
                    // If parent session is no longer active, end spy
                    if (spy.getPrivateSession().getStatus() != PrivateSessionStatus.ACTIVE) {
                        internalEndSpySession(spy, "Main private session no longer active");
                        continue;
                    }

                    if (Duration.between(spy.getLastBilledAt(), now).toMinutes() >= 1) {
                        try {
                            billSpyMinute(spy, spy.getPrivateSession());
                            spy.setLastBilledAt(now);
                            spySessionRepository.save(spy);
                        } catch (Exception e) {
                            log.warn("Auto-ending spy session {} due to billing failure: {}", spy.getId(), e.getMessage());
                            internalEndSpySession(spy, "Insufficient tokens");
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error processing spy billing for session {}: {}", spy.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Spy session billing failed entirely: {}", e.getMessage());
        }
    }

    private void billSpyMinute(PrivateSpySession spy, PrivateSession parentSession) {
        log.info("Billing spy session {}: {} tokens from spy {} to creator {}",
                spy.getId(), spy.getSpyPricePerMinute(), spy.getSpyViewer().getId(), parentSession.getCreator().getId());

        tokenService.deductTokens(spy.getSpyViewer(), spy.getSpyPricePerMinute(), WalletTransactionType.PRIVATE_SHOW, "Spy on private: " + spy.getId());
        creatorEarningsService.recordPrivateShowEarning(spy.getSpyViewer(), parentSession.getCreator(), spy.getSpyPricePerMinute(), parentSession.getId());
    }

    public int getActiveSpyCount(UUID privateSessionId) {
        return spySessionRepository.countByPrivateSession_IdAndStatus(privateSessionId, SpySessionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public PrivateSpySessionDto getActiveSpySession(User viewer, UUID privateSessionId) {
        return spySessionRepository.findBySpyViewer_IdAndPrivateSession_IdAndStatus(viewer.getId(), privateSessionId, SpySessionStatus.ACTIVE)
                .map(this::mapToSpyDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PrivateSessionDto getActiveSessionForCreator(Long creatorUserId) {
        return sessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(creatorUserId, PrivateSessionStatus.ACTIVE)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PrivateSessionAvailabilityDto getAvailability(Long creatorUserId, Long currentUserId) {
        var builder = PrivateSessionAvailabilityDto.builder()
                .hasActivePrivate(false)
                .allowSpyOnPrivate(false)
                .canCurrentUserSpy(false)
                .isCurrentUserPrivateViewer(false)
                .isCurrentUserActiveSpy(false);

        var activeOpt = sessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(creatorUserId, PrivateSessionStatus.ACTIVE);
        if (activeOpt.isEmpty()) {
            return builder.build();
        }

        PrivateSession ps = activeOpt.get();
        builder.hasActivePrivate(true);
        builder.activeSessionId(ps.getId().toString());

        if (currentUserId != null && currentUserId.equals(ps.getViewer().getId())) {
            builder.isCurrentUserPrivateViewer(true);
        }

        CreatorPrivateSettings settings = creatorPrivateSettingsService.getOrCreate(creatorUserId);
        if (settings.isAllowSpyOnPrivate()) {
            builder.allowSpyOnPrivate(true);
            builder.spyPricePerMinute(settings.getSpyPricePerMinute());

            if (currentUserId != null) {
                boolean isSpy = spySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(
                        currentUserId, ps.getId(), SpySessionStatus.ACTIVE);
                builder.isCurrentUserActiveSpy(isSpy);

                // Can spy if not the creator, not the main viewer, and not already spying
                boolean canSpy = !currentUserId.equals(creatorUserId)
                        && !currentUserId.equals(ps.getViewer().getId())
                        && !isSpy;
                builder.canCurrentUserSpy(canSpy);
            }
        }

        return builder.build();
    }

    private void notifySpyCountUpdate(PrivateSession session) {
        try {
            int count = spySessionRepository.countByPrivateSession_IdAndStatus(session.getId(), SpySessionStatus.ACTIVE);
            RealtimeMessage msg = RealtimeMessage.builder()
                    .type("SPY_COUNT_UPDATE")
                    .timestamp(Instant.now())
                    .payload(Map.of(
                            "sessionId", session.getId(),
                            "spyCount", count
                    ))
                    .build();
            messagingTemplate.convertAndSendToUser(session.getCreator().getId().toString(), "/queue/private-show-status", msg);
        } catch (Exception e) {
            log.warn("Failed to send spy count update to creator {}: {}", session.getCreator().getId(), e.getMessage());
        }
    }
}
