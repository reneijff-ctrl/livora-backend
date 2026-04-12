package com.joinlivora.backend.audit.repository;

import com.joinlivora.backend.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findAllByActorUserIdOrderByCreatedAtDesc(UUID actorUserId);
    List<AuditLog> findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);

    void deleteAllByCreatedAtBefore(Instant threshold);

    List<AuditLog> findByTargetTypeAndActionInOrderByCreatedAtDesc(String targetType, List<String> actions, org.springframework.data.domain.Pageable pageable);
}
