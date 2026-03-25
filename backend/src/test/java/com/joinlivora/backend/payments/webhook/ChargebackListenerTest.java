package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.payment.PaymentChargebackService;
import com.joinlivora.backend.user.User;
import com.stripe.model.Dispute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChargebackListenerTest {

    @Mock
    private PaymentChargebackService chargebackService;

    @InjectMocks
    private ChargebackListener chargebackListener;

    @Test
    void handleChargeback_ShouldCallChargebackService() {
        // Given
        User user = new User();
        user.setEmail("test@example.com");
        user.setId(1L);
        Dispute dispute = mock(Dispute.class);
        ChargebackEvent event = new ChargebackEvent(this, user, "pi_123", dispute);

        // When
        chargebackListener.handleChargeback(event);

        // Then
        verify(chargebackService).processChargeback(user, "pi_123", dispute);
    }
}








