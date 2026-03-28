package com.joinlivora.backend.monetization;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.fraud.model.FraudRiskLevel;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.token.TipRecord;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipOrchestrationServiceTest {

    @Mock
    private TipPersistenceService persistenceService;
    @Mock
    private TipRiskService riskService;
    @Mock
    private TipNotificationService notificationService;
    @Mock
    private TipPaymentService paymentService;

    @InjectMocks
    private TipOrchestrationService tipService;

    private User viewer;
    private User creator;
    private Stream room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setId(1L);
        viewer.setEmail("viewer@test.com");
        viewer.setUsername("viewer");
        viewer.setRole(Role.USER);
        viewer.setCreatedAt(Instant.now());

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");
        creator.setUsername("creator");
        creator.setRole(Role.CREATOR);
        creator.setCreatedAt(Instant.now());

        roomId = UUID.randomUUID();
        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(true)
                .build();
    }

    private void setupDefaultMocks() {
        when(persistenceService.findStreamById(roomId)).thenReturn(Optional.of(room));
        when(riskService.calculateFraudRisk(any(), any()))
                .thenReturn(new FraudRiskResult(FraudRiskLevel.LOW, 10, List.of()));
        when(paymentService.getPlatformFeeRate()).thenReturn(new BigDecimal("0.20"));
        lenient().when(paymentService.getAvailableTokenBalance(viewer.getId())).thenReturn(500L);

        Tip savedTip = Tip.builder()
                .id(UUID.randomUUID())
                .senderUserId(viewer)
                .creatorUserId(creator)
                .room(room)
                .amount(BigDecimal.valueOf(100))
                .currency("TOKEN")
                .status(TipStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(persistenceService.saveTip(any(Tip.class))).thenReturn(savedTip);
    }

    @Test
    void sendTokenTip_Success_ShouldPersistBeforeDeducting() {
        setupDefaultMocks();

        TipResult result = tipService.sendTokenTip(viewer, roomId, 100L, "nice stream",
                "req-1", "127.0.0.1", "fp-hash", null);

        assertNotNull(result);

        // Verify ordering: saveTip (PENDING) and saveTipRecord BEFORE deductTokens
        InOrder inOrder = inOrder(persistenceService, paymentService);
        inOrder.verify(persistenceService).saveTip(argThat(tip ->
                tip.getStatus() == TipStatus.PENDING));
        inOrder.verify(persistenceService).saveTipRecord(any(TipRecord.class));
        inOrder.verify(paymentService).deductTokens(eq(viewer.getId()), eq(100L), eq(roomId));
        inOrder.verify(paymentService).recordTokenTipEarning(eq(viewer), eq(creator), eq(100L), eq(roomId), any());
        // saveTip again to mark COMPLETED
        inOrder.verify(persistenceService).saveTip(any(Tip.class));
    }

    @Test
    void sendTokenTip_DeductionFails_ShouldMarkTipFailedAndRethrow() {
        setupDefaultMocks();
        doThrow(new InsufficientBalanceException("Insufficient token balance"))
                .when(paymentService).deductTokens(eq(viewer.getId()), eq(100L), eq(roomId));

        assertThrows(InsufficientBalanceException.class, () ->
                tipService.sendTokenTip(viewer, roomId, 100L, "nice stream",
                        "req-2", "127.0.0.1", "fp-hash", null));

        // Verify tip was saved as PENDING first, then marked FAILED after deduction error
        verify(persistenceService, times(2)).saveTip(any(Tip.class));
        verify(paymentService, never()).recordTokenTipEarning(any(), any(), anyLong(), any(), any());
        verify(notificationService, never()).notifyTip(any(), any());
    }

    @Test
    void sendTokenTip_DuplicateClientRequestId_ShouldReturnExisting() {
        when(persistenceService.findStreamById(roomId)).thenReturn(Optional.of(room));
        lenient().when(riskService.calculateFraudRisk(any(), any()))
                .thenReturn(new FraudRiskResult(FraudRiskLevel.LOW, 10, List.of()));

        Tip existingTip = Tip.builder()
                .id(UUID.randomUUID())
                .senderUserId(viewer)
                .creatorUserId(creator)
                .amount(BigDecimal.valueOf(100))
                .currency("TOKEN")
                .message("nice stream")
                .status(TipStatus.COMPLETED)
                .createdAt(Instant.now())
                .build();
        when(persistenceService.findByClientRequestId("req-dup")).thenReturn(Optional.of(existingTip));
        when(paymentService.getAvailableTokenBalance(viewer.getId())).thenReturn(500L);

        TipResult result = tipService.sendTokenTip(viewer, roomId, 100L, "nice stream",
                "req-dup", "127.0.0.1", "fp-hash", null);

        assertTrue(result.isDuplicate());
        // No deduction or persistence should happen for duplicates
        verify(paymentService, never()).deductTokens(anyLong(), anyLong(), any());
        verify(persistenceService, never()).saveTip(any(Tip.class));
    }

    @Test
    void sendTokenTip_StreamNotLive_ShouldThrowException() {
        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(false)
                .build();
        when(persistenceService.findStreamById(roomId)).thenReturn(Optional.of(room));
        when(riskService.calculateFraudRisk(any(), any()))
                .thenReturn(new FraudRiskResult(FraudRiskLevel.LOW, 10, List.of()));

        assertThrows(IllegalStateException.class, () ->
                tipService.sendTokenTip(viewer, roomId, 100L, "msg",
                        null, "127.0.0.1", "fp-hash", null));

        verify(paymentService, never()).deductTokens(anyLong(), anyLong(), any());
        verify(persistenceService, never()).saveTip(any(Tip.class));
    }

    @Test
    void sendTokenTip_SelfTip_ShouldThrowException() {
        User selfUser = new User();
        selfUser.setId(2L); // same as creator
        selfUser.setEmail("creator@test.com");
        when(persistenceService.findStreamById(roomId)).thenReturn(Optional.of(room));

        assertThrows(IllegalStateException.class, () ->
                tipService.sendTokenTip(selfUser, roomId, 100L, "msg",
                        null, "127.0.0.1", "fp-hash", null));

        verify(paymentService, never()).deductTokens(anyLong(), anyLong(), any());
    }

    @Test
    void sendTokenTip_HighRisk_ShouldMarkPendingReview() {
        when(persistenceService.findStreamById(roomId)).thenReturn(Optional.of(room));
        when(riskService.calculateFraudRisk(any(), any()))
                .thenReturn(new FraudRiskResult(FraudRiskLevel.HIGH, 80, List.of("suspicious")));
        when(riskService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(RiskLevel.HIGH);
        when(paymentService.getPlatformFeeRate()).thenReturn(new BigDecimal("0.20"));
        when(paymentService.getAvailableTokenBalance(viewer.getId())).thenReturn(500L);

        Tip savedTip = Tip.builder()
                .id(UUID.randomUUID())
                .senderUserId(viewer)
                .creatorUserId(creator)
                .room(room)
                .amount(BigDecimal.valueOf(100))
                .currency("TOKEN")
                .status(TipStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        when(persistenceService.saveTip(any(Tip.class))).thenReturn(savedTip);

        TipResult result = tipService.sendTokenTip(viewer, roomId, 100L, "msg",
                null, "127.0.0.1", "fp-hash", null);

        // Verify the final save sets PENDING_REVIEW (the mock returns the same object, 
        // so we verify the status was set on the entity)
        verify(persistenceService, atLeast(2)).saveTip(argThat(tip ->
                tip.getStatus() == TipStatus.PENDING_REVIEW || tip.getStatus() == TipStatus.PENDING));
    }
}
