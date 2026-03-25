package com.joinlivora.backend.privateshow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivateSessionRepository extends JpaRepository<PrivateSession, UUID> {
    List<PrivateSession> findAllByStatus(PrivateSessionStatus status);
    List<PrivateSession> findAllByCreator_IdAndStatus(Long creatorId, PrivateSessionStatus status);
    List<PrivateSession> findAllByViewer_IdAndStatus(Long viewerId, PrivateSessionStatus status);

    boolean existsByViewer_IdAndCreator_IdAndStatusIn(Long viewerId, Long creatorId, Collection<PrivateSessionStatus> statuses);
    boolean existsByCreator_IdAndStatus(Long creatorId, PrivateSessionStatus status);
    List<PrivateSession> findAllByStatusAndAcceptedAtBefore(PrivateSessionStatus status, Instant cutoff);

    Optional<PrivateSession> findFirstByCreator_IdAndStatusOrderByStartedAtDesc(Long creatorId, PrivateSessionStatus status);
    List<PrivateSession> findAllByViewer_IdAndCreator_IdAndStatusIn(Long viewerId, Long creatorId, Collection<PrivateSessionStatus> statuses);

    Optional<PrivateSession> findFirstByViewer_IdAndStatusInOrderByRequestedAtDesc(Long viewerId, Collection<PrivateSessionStatus> statuses);
    Optional<PrivateSession> findFirstByCreator_IdAndStatusInOrderByRequestedAtDesc(Long creatorId, Collection<PrivateSessionStatus> statuses);
}
