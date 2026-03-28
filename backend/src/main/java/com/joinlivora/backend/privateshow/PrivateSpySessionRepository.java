package com.joinlivora.backend.privateshow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivateSpySessionRepository extends JpaRepository<PrivateSpySession, UUID> {

    List<PrivateSpySession> findAllByStatus(SpySessionStatus status);

    List<PrivateSpySession> findAllByPrivateSession_IdAndStatus(UUID sessionId, SpySessionStatus status);

    int countByPrivateSession_IdAndStatus(UUID sessionId, SpySessionStatus status);

    boolean existsBySpyViewer_IdAndPrivateSession_IdAndStatus(Long viewerId, UUID sessionId, SpySessionStatus status);

    Optional<PrivateSpySession> findBySpyViewer_IdAndPrivateSession_IdAndStatus(Long viewerId, UUID sessionId, SpySessionStatus status);
}
