package com.joinlivora.backend.payout;

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
class PayoutAuditServiceTest {

    @Mock
    private PayoutAuditLogRepository auditLogRepository;

    @InjectMocks
    private PayoutAuditService payoutAuditService;

    @Test
    void logStatusChange_ShouldSaveLog() {
        UUID payoutId = UUID.randomUUID();
        PayoutStatus oldStatus = PayoutStatus.PENDING;
        PayoutStatus newStatus = PayoutStatus.PROCESSING;
        PayoutActorType actorType = PayoutActorType.SYSTEM;
        String message = "Processing started";

        payoutAuditService.logStatusChange(payoutId, oldStatus, newStatus, actorType, null, message);

        ArgumentCaptor<PayoutAuditLog> captor = ArgumentCaptor.forClass(PayoutAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        PayoutAuditLog savedLog = captor.getValue();
        assertEquals(payoutId, savedLog.getPayoutId());
        assertEquals(oldStatus, savedLog.getPreviousStatus());
        assertEquals(newStatus, savedLog.getNewStatus());
        assertEquals(actorType, savedLog.getActorType());
        assertEquals("STATUS_CHANGE", savedLog.getAction());
        assertEquals(message, savedLog.getMessage());
        assertNotNull(savedLog.getCreatedAt());
    }

    @Test
    void logAction_ShouldSaveLog() {
        UUID payoutId = UUID.randomUUID();
        String action = "RELEASE_HOLD";
        PayoutActorType actorType = PayoutActorType.ADMIN;
        UUID actorId = UUID.randomUUID();
        String message = "Admin released hold";

        payoutAuditService.logAction(payoutId, action, actorType, actorId, message);

        ArgumentCaptor<PayoutAuditLog> captor = ArgumentCaptor.forClass(PayoutAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        PayoutAuditLog savedLog = captor.getValue();
        assertEquals(payoutId, savedLog.getPayoutId());
        assertEquals(actorType, savedLog.getActorType());
        assertEquals(actorId, savedLog.getActorId());
        assertEquals(action, savedLog.getAction());
        assertEquals(message, savedLog.getMessage());
        assertNotNull(savedLog.getCreatedAt());
    }
}










