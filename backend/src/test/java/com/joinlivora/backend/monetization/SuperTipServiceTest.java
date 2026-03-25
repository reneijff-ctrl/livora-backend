package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.exception.SuperTipException;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.monetization.dto.SuperTipErrorCode;
import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.chargeback.InternalChargebackService;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.chat.SlowModeBypassService;
import com.joinlivora.backend.chat.SlowModeBypassSource;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperTipServiceTest {

    @Mock
    private SuperTipRepository superTipRepository;
    @Mock
    private TokenWalletService tokenWalletService;
    @Mock
    private CreatorEarningsService monetizationService;
    @Mock
    private StreamRepository streamRepository;
    @Mock
    private TipValidationService tipValidationService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private SlowModeBypassService slowModeBypassService;
    @Mock
    private com.joinlivora.backend.payment.PaymentService paymentService;
    @Mock
    private AMLRulesEngine amlRulesEngine;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;
    @Mock
    private TipRepository tipRepository;
    @Mock
    private FraudScoringService fraudRiskService;
    @Mock
    private InternalChargebackService chargebackService;
    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;
    @Mock
    private com.joinlivora.backend.fraud.service.EnforcementService enforcementService;

    @InjectMocks
    private SuperTipService superTipService;

    private User viewer;
    private User creator;
    private Stream room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setId(1L);
        viewer.setEmail("viewer@test.com");
        viewer.setCreatedAt(java.time.Instant.now());

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");
        creator.setCreatedAt(java.time.Instant.now());

        roomId = UUID.randomUUID();
        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(true)
                .build();

        ReflectionTestUtils.setField(superTipService, "bypassDurationSeconds", 300);

        lenient().when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(com.joinlivora.backend.fraud.model.FraudRiskLevel.LOW, 10, java.util.Collections.emptyList()));
        lenient().when(chargebackService.getChargebackCount(any())).thenReturn(0L);
    }

    @Test
    void sendSuperTip_Basic_Success() {
        long amountTokens = 1000; // 10.00 EUR -> BASIC
        String message = "Basic!";
        
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(viewer.getId())).thenReturn(2000L);
        when(superTipRepository.save(any(SuperTip.class))).thenAnswer(invocation -> {
            SuperTip st = invocation.getArgument(0);
            st.setId(UUID.randomUUID());
            return st;
        });

        SuperTipResponse result = superTipService.sendSuperTip(viewer, roomId, amountTokens, message, "req-123", null, null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(HighlightLevel.BASIC, result.getHighlightLevel());
        assertEquals(BigDecimal.valueOf(amountTokens), result.getAmount());
        assertEquals(message, result.getMessage());
        
        verify(tipValidationService).validateSuperTip(viewer, roomId);
        verify(tokenWalletService).deductTokens(eq(viewer.getId()), eq(amountTokens), eq(WalletTransactionType.TIP), anyString());
        verify(monetizationService).recordTokenTipEarning(eq(viewer), eq(creator), eq(amountTokens), eq(roomId), eq(com.joinlivora.backend.fraud.model.RiskLevel.LOW));
        verify(superTipRepository).save(any(SuperTip.class));
        verify(slowModeBypassService, never()).grantBypass(any(), any(), anyInt(), any());
        
        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.SUPERTIP_SENT),
                eq(viewer),
                argThat(map -> 
                    map.get("highlightLevel").equals(HighlightLevel.BASIC.name()) &&
                    map.get("amount").equals(amountTokens) &&
                    map.get("roomId").equals(roomId) &&
                    map.get("creatorUserId").equals(creator.getId()) &&
                    map.get("senderUserId").equals(viewer.getId())
                )
        );
        verify(amlRulesEngine).evaluateRules(creator, BigDecimal.ZERO);
    }

    @Test
    void sendSuperTip_Premium_Success() {
        long amountTokens = 5000; // 50.00 EUR -> PREMIUM
        
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(viewer.getId())).thenReturn(6000L);
        when(superTipRepository.save(any(SuperTip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperTipResponse result = superTipService.sendSuperTip(viewer, roomId, amountTokens, "Premium!", "req-2", null, null);

        assertTrue(result.isSuccess());
        assertEquals(HighlightLevel.PREMIUM, result.getHighlightLevel());
        assertEquals(60, result.getDurationSeconds());

        verify(slowModeBypassService).grantBypass(viewer, room, 300, SlowModeBypassSource.SUPERTIP);
    }

    @Test
    void sendSuperTip_Ultra_Success() {
        long amountTokens = 10000; // 100.00 EUR -> ULTRA
        
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(viewer.getId())).thenReturn(15000L);
        when(superTipRepository.save(any(SuperTip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperTipResponse result = superTipService.sendSuperTip(viewer, roomId, amountTokens, "Ultra!", "req-3", null, null);

        assertTrue(result.isSuccess());
        assertEquals(HighlightLevel.ULTRA, result.getHighlightLevel());
        assertEquals(120, result.getDurationSeconds());

        verify(slowModeBypassService).grantBypass(viewer, room, 300, SlowModeBypassSource.SUPERTIP);
    }

    @Test
    void sendSuperTip_AmountTooLow_ThrowsException() {
        long amountTokens = 500; // 5.00 EUR -> Below BASIC (10.00)
        
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));

        SuperTipException ex = assertThrows(SuperTipException.class, () -> 
            superTipService.sendSuperTip(viewer, roomId, amountTokens, "Too low", "req-4", null, null));
        
        assertEquals(SuperTipErrorCode.INVALID_HIGHLIGHT_LEVEL, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Minimum SuperTip amount is 1000 tokens"));
        verifyNoInteractions(tokenWalletService, monetizationService);
    }

    @Test
    void sendSuperTip_HighRisk_ShouldMarkPendingReview() {
        long amountTokens = 1000;
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(viewer.getId())).thenReturn(2000L);
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.HIGH,
                        80, 
                        java.util.List.of("High risk reasons")));

        when(superTipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SuperTipResponse result = superTipService.sendSuperTip(viewer, roomId, amountTokens, "High risk", "req-high-st", "127.0.0.1", "fp");

        assertTrue(result.isSuccess());
        verify(monetizationService).recordTokenTipEarning(eq(viewer), eq(creator), eq(amountTokens), eq(roomId), eq(com.joinlivora.backend.fraud.model.RiskLevel.HIGH));
        
        ArgumentCaptor<SuperTip> stCaptor = ArgumentCaptor.forClass(SuperTip.class);
        verify(superTipRepository).save(stCaptor.capture());
        assertEquals(TipStatus.PENDING_REVIEW, stCaptor.getValue().getStatus());
    }

    @Test
    void sendSuperTip_CriticalRisk_ShouldBlockAndRecordIncident() {
        long amountTokens = 1000;
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.CRITICAL,
                        95, 
                        java.util.List.of("Critical risk reasons")));

        assertThrows(HighFraudRiskException.class, () ->
                superTipService.sendSuperTip(viewer, roomId, amountTokens, "Critical risk", "req-crit-st", "127.0.0.1", "fp"));

        verify(enforcementService).recordFraudIncident(any(), contains("CRITICAL_FRAUD_RISK"), anyMap());
        verify(superTipRepository, never()).save(any());
    }

    @Test
    void sendSuperTip_RoomNotFound_ThrowsException() {
        when(streamRepository.findById(roomId)).thenReturn(Optional.empty());
        when(streamRepository.findByMediasoupRoomId(roomId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            superTipService.sendSuperTip(viewer, roomId, 1000, "Oops", "req-5", null, null));
    }

    @Test
    void sendSuperTip_StreamNotLive_ThrowsException() {
        room.setLive(false);
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThrows(IllegalStateException.class, () -> 
            superTipService.sendSuperTip(viewer, roomId, 1000, "Oops", "req-6", null, null));
    }

    @Test
    void sendSuperTip_InsufficientBalance_ThrowsException() {
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(viewer.getId())).thenReturn(500L);

        SuperTipException ex = assertThrows(SuperTipException.class, () -> 
            superTipService.sendSuperTip(viewer, roomId, 1000, "Not enough coins", "req-7", null, null));
        
        assertEquals(SuperTipErrorCode.INSUFFICIENT_BALANCE, ex.getErrorCode());
        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
    }
}











