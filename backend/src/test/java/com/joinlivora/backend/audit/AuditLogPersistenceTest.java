package com.joinlivora.backend.audit;

import com.joinlivora.backend.audit.model.AuditLog;
import com.joinlivora.backend.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogPersistenceTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void testSaveAndRetrieveAuditLog() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AuditLog log = AuditLog.builder()
                .actorUserId(actorId)
                .action("UPDATE_USER")
                .targetType("USER")
                .targetId(targetId)
                .metadata("{\"oldRole\": \"USER\", \"newRole\": \"ADMIN\"}")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0")
                .build();

        AuditLog saved = auditLogRepository.save(log);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        AuditLog retrieved = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getActorUserId()).isEqualTo(actorId);
        assertThat(retrieved.getAction()).isEqualTo("UPDATE_USER");
        assertThat(retrieved.getTargetType()).isEqualTo("USER");
        assertThat(retrieved.getTargetId()).isEqualTo(targetId);
        assertThat(retrieved.getMetadata()).contains("newRole");
        assertThat(retrieved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(retrieved.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void testFindAllByActorUserIdOrderByCreatedAtDesc() {
        UUID actorId = UUID.randomUUID();
        AuditLog log1 = AuditLog.builder()
                .actorUserId(actorId)
                .action("ACTION_1")
                .targetType("T")
                .createdAt(Instant.now().minusSeconds(100))
                .build();
        AuditLog log2 = AuditLog.builder()
                .actorUserId(actorId)
                .action("ACTION_2")
                .targetType("T")
                .createdAt(Instant.now())
                .build();

        auditLogRepository.save(log1);
        auditLogRepository.save(log2);

        var logs = auditLogRepository.findAllByActorUserIdOrderByCreatedAtDesc(actorId);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getAction()).isEqualTo("ACTION_2");
        assertThat(logs.get(1).getAction()).isEqualTo("ACTION_1");
    }

    @Test
    void testDeleteAllByCreatedAtBefore() {
        Instant threshold = Instant.now().minusSeconds(1000);
        
        AuditLog oldLog = AuditLog.builder()
                .action("OLD_ACTION")
                .targetType("T")
                .createdAt(threshold.minusSeconds(1))
                .build();
        
        AuditLog newLog = AuditLog.builder()
                .action("NEW_ACTION")
                .targetType("T")
                .createdAt(threshold.plusSeconds(1))
                .build();

        auditLogRepository.save(oldLog);
        auditLogRepository.save(newLog);

        auditLogRepository.deleteAllByCreatedAtBefore(threshold);

        var allLogs = auditLogRepository.findAll();
        assertThat(allLogs).hasSize(1);
        assertThat(allLogs.get(0).getAction()).isEqualTo("NEW_ACTION");
    }
}








