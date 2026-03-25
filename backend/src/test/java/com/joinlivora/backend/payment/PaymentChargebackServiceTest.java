package com.joinlivora.backend.payment;

import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import com.joinlivora.backend.user.User;
import com.stripe.model.Dispute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentChargebackServiceTest {

    @Mock
    private ChargebackRepository chargebackRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private AutoFreezePolicyService autoFreezePolicyService;
    @Mock
    private ChargebackCorrelationService chargebackCorrelationService;
    @Mock
    private ChargebackAlertService chargebackAlertService;
    @Mock
    private ChargebackRiskEscalationService riskEscalationService;
    @Mock
    private ChargebackAuditService chargebackAuditService;
    @Mock
    private com.joinlivora.backend.fraud.service.RiskDecisionAuditService riskDecisionAuditService;
    @Mock
    private com.joinlivora.backend.reputation.service.ReputationEventService reputationEventService;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @InjectMocks
    private PaymentChargebackService chargebackService;

    private User user;
    private Dispute dispute;
    private Payment payment;
    private final String paymentIntentId = "pi_123";

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");

        dispute = mock(Dispute.class);
        lenient().when(dispute.getId()).thenReturn("dp_123");
        lenient().when(dispute.getCharge()).thenReturn("ch_123");
        lenient().when(dispute.getReason()).thenReturn("fraudulent");
        lenient().when(dispute.getAmount()).thenReturn(1000L); // $10.00
        lenient().when(dispute.getCurrency()).thenReturn("usd");

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIpAddress("127.0.0.1");
        payment.setDeviceFingerprint("fp_abc");
        
        User creator = new User();
        creator.setId(2L);
        payment.setCreator(creator);
    }

    @Test
    void processChargeback_ShouldPersistChargebackAndNotifyFraud() {
        when(chargebackRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.empty());
        when(chargebackRepository.findByStripeChargeId("ch_123")).thenReturn(Optional.empty());
        when(paymentRepository.findByStripePaymentIntentId(paymentIntentId)).thenReturn(Optional.of(payment));
        when(chargebackCorrelationService.findCorrelatedChargebacks(any())).thenReturn(java.util.List.of());
        when(dispute.getStatus()).thenReturn("needs_response");
        when(riskEscalationService.evaluateEscalation(2L)).thenReturn(
                RiskEscalationResult.builder()
                        .riskLevel(com.joinlivora.backend.fraud.model.RiskLevel.LOW)
                        .actions(java.util.List.of())
                        .build()
        );

        chargebackService.processChargeback(user, paymentIntentId, dispute);

        ArgumentCaptor<Chargeback> captor = ArgumentCaptor.forClass(Chargeback.class);
        verify(chargebackRepository).save(captor.capture());

        Chargeback saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(new UUID(0L, 1L));
        assertThat(saved.getCreatorId()).isEqualTo(2L);
        assertThat(saved.getTransactionId()).isEqualTo(payment.getId());
        assertThat(saved.getStripeChargeId()).isEqualTo("ch_123");
        assertThat(saved.getStripeDisputeId()).isEqualTo("dp_123");
        assertThat(saved.getReason()).isEqualTo("fraudulent");
        assertThat(saved.getAmount()).isEqualByComparingTo("10.00");
        assertThat(saved.getCurrency()).isEqualTo("usd");
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getDeviceFingerprint()).isEqualTo("fp_abc");
        assertThat(saved.getStatus()).isEqualTo(ChargebackStatus.RECEIVED);

        verify(fraudDetectionService).recordChargeback(user, paymentIntentId);
        verify(autoFreezePolicyService).applyPayerPolicy(user, 1);
        verify(autoFreezePolicyService).applyCreatorEscalation(eq(saved), any());
        verify(chargebackAuditService).audit(eq(saved), eq(1), any());
        verify(riskDecisionAuditService).logDecision(any(), any(), eq("CHARGEBACK_ENFORCEMENT"), any(), any(), any(), any(), any());
        verify(chargebackAlertService).alert(saved, 1);
        verify(reputationEventService).recordEvent(any(), any(), anyInt(), any(), any());
    }

    @Test
    void processChargeback_WhenExisting_ShouldUpdateStatus() {
        Chargeback existing = new Chargeback();
        existing.setId(UUID.randomUUID());
        existing.setStripeDisputeId("dp_123");
        existing.setStatus(ChargebackStatus.RECEIVED);
        
        when(chargebackRepository.findByStripeDisputeId("dp_123")).thenReturn(Optional.of(existing));
        when(paymentRepository.findByStripePaymentIntentId(paymentIntentId)).thenReturn(Optional.of(payment));
        when(dispute.getStatus()).thenReturn("won");

        chargebackService.processChargeback(user, paymentIntentId, dispute);

        assertThat(existing.getStatus()).isEqualTo(ChargebackStatus.WON);
        assertThat(existing.isResolved()).isTrue();
        
        verify(chargebackRepository).save(existing);
        verify(fraudDetectionService, never()).recordChargeback(any(), any());
    }

    @Test
    void processChargeback_WhenPaymentNotFound_ShouldStillPersistChargeback() {
        when(paymentRepository.findByStripePaymentIntentId(paymentIntentId)).thenReturn(Optional.empty());
        when(chargebackCorrelationService.findCorrelatedChargebacks(any())).thenReturn(java.util.List.of());

        chargebackService.processChargeback(user, paymentIntentId, dispute);

        verify(chargebackRepository).save(any(Chargeback.class));
        verify(fraudDetectionService).recordChargeback(user, paymentIntentId);
        verify(autoFreezePolicyService).applyPayerPolicy(user, 1);
        verify(chargebackAuditService).audit(any(), eq(1), any());
        verify(chargebackAlertService).alert(any(), eq(1));
    }
}








