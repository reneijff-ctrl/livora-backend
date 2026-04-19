package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.user.User;
import com.stripe.model.Dispute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentChargebackServiceTest {

    @Mock
    private ChargebackService canonicalChargebackService;

    @InjectMocks
    private PaymentChargebackService chargebackService;

    private User user;
    private Dispute dispute;
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
        lenient().when(dispute.getAmount()).thenReturn(1000L);
        lenient().when(dispute.getCurrency()).thenReturn("usd");
    }

    @Test
    void processChargeback_ShouldDelegateToCanonicalService() {
        chargebackService.processChargeback(user, paymentIntentId, dispute);
        verify(canonicalChargebackService).handleDisputeCreated(user, paymentIntentId, dispute);
    }

    @Test
    void findCorrelatedChargebacksForUser_ShouldDelegateToCanonicalService() {
        Long userId = 1L;
        ChargebackCase cb = ChargebackCase.builder()
                .id(UUID.randomUUID())
                .userId(new UUID(0L, userId))
                .amount(new BigDecimal("10.00"))
                .currency("usd")
                .status(ChargebackStatus.OPEN)
                .fraudScoreAtTime(0)
                .build();
        when(canonicalChargebackService.findCorrelatedCasesByUserId(userId)).thenReturn(List.of(cb));

        List<ChargebackCase> result = chargebackService.findCorrelatedChargebacksForUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(new UUID(0L, userId));
        verify(canonicalChargebackService).findCorrelatedCasesByUserId(userId);
    }
}
