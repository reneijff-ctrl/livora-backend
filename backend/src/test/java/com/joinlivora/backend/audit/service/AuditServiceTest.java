package com.joinlivora.backend.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.audit.model.AuditLog;
import com.joinlivora.backend.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private AuditService auditService;

    @Mock
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, objectMapper);
    }

    @Test
    void logEvent_ShouldSaveNormalizedAuditLog() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("type", "suspicious activity", "score", 85);
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        auditService.logEvent(actorId, "PAYOUT_FREEZE", "USER", targetId, metadata, ipAddress, userAgent);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo("PAYOUT_FREEZE");
        assertThat(saved.getTargetType()).isEqualTo("USER");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getIpAddress()).isEqualTo(ipAddress);
        assertThat(saved.getUserAgent()).isEqualTo(userAgent);
        
        // Metadata should be normalized to JSON
        assertThat(saved.getMetadata()).contains("suspicious activity");
        assertThat(saved.getMetadata()).contains("85");
    }

    @Test
    void logEvent_WithNullMetadata_ShouldSaveSuccessfully() {
        auditService.logEvent(null, "LOGIN", "SYSTEM", null, null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getMetadata()).isNull();
        assertThat(saved.getAction()).isEqualTo("LOGIN");
    }

    @Test
    void logEvent_WithStringMetadata_ShouldSaveAsIs() {
        String metadata = "Simple string metadata";
        auditService.logEvent(null, "TEST", "TEST", null, metadata, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void getActorHistory_ShouldDelegateToRepository() {
        UUID actorId = UUID.randomUUID();
        auditService.getActorHistory(actorId);
        verify(auditLogRepository).findAllByActorUserIdOrderByCreatedAtDesc(actorId);
    }

    @Test
    void getTargetHistory_ShouldDelegateToRepository() {
        UUID targetId = UUID.randomUUID();
        auditService.getTargetHistory("PAYOUT", targetId);
        verify(auditLogRepository).findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc("PAYOUT", targetId);
    }
}








