package com.joinlivora.backend.payment;

import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.tip.DirectTip;
import com.joinlivora.backend.tip.TipService;
import com.joinlivora.backend.tip.TipStatus;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StripeTipWebhookTest {

    @Mock
    private UserService userService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TipService directTipService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private CreatorEarningsService creatorEarningsService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ObjectProvider<StripeWebhookService> selfProvider;

    @InjectMocks
    private StripeWebhookService stripeWebhookService;

    private User user;
    private User creator;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@example.com");
    }

    @Test
    void handleTipCheckoutSession_ShouldCreatePaymentAndCompleteTip() {
        // Given
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_123");
        when(session.getPaymentIntent()).thenReturn("pi_test_123");
        when(session.getAmountTotal()).thenReturn(2000L); // 20.00 EUR
        when(session.getCurrency()).thenReturn("eur");
        when(session.getMetadata()).thenReturn(Map.of(
                "type", "TIP",
                "creator_id", "2",
                "user_id", "1"
        ));

        when(userService.getById(1L)).thenReturn(user);
        when(userService.getById(2L)).thenReturn(creator);

        DirectTip pendingTip = new DirectTip();
        pendingTip.setId(UUID.randomUUID());
        when(directTipService.saveTip(any(DirectTip.class))).thenReturn(pendingTip);

        // When
        stripeWebhookService.handleTipCheckoutSession(session);

        // Then
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertEquals(new BigDecimal("20"), savedPayment.getAmount());
        assertEquals("eur", savedPayment.getCurrency());
        assertEquals(user, savedPayment.getUser());
        assertEquals(creator, savedPayment.getCreator());
        assertEquals("pi_test_123", savedPayment.getStripePaymentIntentId());
        assertEquals("cs_test_123", savedPayment.getStripeSessionId());
        assertTrue(savedPayment.isSuccess());

        ArgumentCaptor<DirectTip> tipCaptor = ArgumentCaptor.forClass(DirectTip.class);
        verify(directTipService).saveTip(tipCaptor.capture());
        DirectTip createdTip = tipCaptor.getValue();
        assertEquals(user, createdTip.getUser());
        assertEquals(creator, createdTip.getCreator());
        assertEquals(new BigDecimal("20"), createdTip.getAmount());
        assertEquals(TipStatus.PENDING, createdTip.getStatus());

        verify(directTipService).completeTip(pendingTip.getId());
        verify(invoiceService).createInvoice(eq(user), eq(new BigDecimal("20")), eq("eur"), any(), eq(InvoiceType.TIPS), any(), any(), any());
    }

    @Test
    void processEventAsync_ShouldRouteTipCheckoutSession() {
        // Given
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        
        Session session = mock(Session.class);
        when(session.getMetadata()).thenReturn(Map.of("type", "TIP"));
        when(session.getCurrency()).thenReturn("eur");
        
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(session));
        
        StripeWebhookService self = mock(StripeWebhookService.class);
        when(selfProvider.getIfAvailable()).thenReturn(self);
        
        WebhookEvent webhookEvent = new WebhookEvent();
        when(webhookEventRepository.findById(any())).thenReturn(Optional.of(webhookEvent));
        
        Counter counter = mock(Counter.class);
        when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);
        when(meterRegistry.counter(anyString())).thenReturn(counter);

        // When
        stripeWebhookService.processEventAsync(event, UUID.randomUUID());

        // Then
        verify(self).handleTipCheckoutSession(session);
    }
}








