package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.RiskExplanationLog;
import com.joinlivora.backend.fraud.repository.RiskExplanationLogRepository;
import com.joinlivora.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RiskExplanationAuditServiceTest {

    @Mock
    private RiskExplanationLogRepository repository;

    @InjectMocks
    private RiskExplanationAuditService auditService;

    @Test
    void logRequest_ShouldSaveLogEntry() {
        UUID requesterId = UUID.randomUUID();
        UUID explanationId = UUID.randomUUID();
        Role role = Role.ADMIN;

        auditService.logRequest(requesterId, role, explanationId);

        ArgumentCaptor<RiskExplanationLog> captor = ArgumentCaptor.forClass(RiskExplanationLog.class);
        verify(repository).save(captor.capture());

        RiskExplanationLog log = captor.getValue();
        assertEquals(requesterId, log.getRequesterId());
        assertEquals(role, log.getRole());
        assertEquals(explanationId, log.getExplanationId());
        assertNotNull(log.getTimestamp());
    }
}








