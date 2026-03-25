package com.joinlivora.backend.payment;

import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.payout.*;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTransferTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private CreatorPayoutRepository creatorPayoutRepository;
    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;
    @Mock
    private UserService userService;
    @Mock
    private UserSubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock
    private com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private com.joinlivora.backend.token.TokenPackageRepository tokenPackageRepository;
    @Mock
    private com.joinlivora.backend.payout.CreatorConnectService creatorConnectService;
    @Mock
    private com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;
    @Mock
    private TipOrchestrationService tipService;
    @Mock
    private com.joinlivora.backend.monetization.PPVPurchaseService ppvPurchaseService;
    @Mock
    private com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService;
    @Mock
    private com.joinlivora.backend.streaming.StreamService LiveStreamService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private com.stripe.StripeClient stripeClient;
    @Mock
    private com.joinlivora.backend.fraud.service.FraudDetectionService fraudDetectionService;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<StripeWebhookService> selfProvider;

    @InjectMocks
    private StripeWebhookService service;

    private UUID payoutId;
    private Transfer mockTransfer;

    @BeforeEach
    void setUp() {
        payoutId = UUID.randomUUID();
        mockTransfer = mock(Transfer.class);
        when(mockTransfer.getId()).thenReturn("tr_123");
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("payoutId", payoutId.toString());
        when(mockTransfer.getMetadata()).thenReturn(metadata);
    }

    @Test
    void handleTransferPaid_ShouldUpdateCreatorPayout() {
        CreatorPayout payout = CreatorPayout.builder()
                .id(payoutId)
                .status(PayoutStatus.PENDING)
                .build();

        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        service.handleTransferPaid(mockTransfer);

        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("tr_123", payout.getStripeTransferId());
        verify(creatorPayoutRepository).save(payout);
    }

    @Test
    void handleTransferPaid_ShouldUpdateLegacyPayout() {
        Payout payout = Payout.builder()
                .id(payoutId)
                .status(PayoutStatus.PENDING)
                .build();

        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.empty());
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        service.handleTransferPaid(mockTransfer);

        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("tr_123", payout.getStripeTransferId());
        verify(payoutRepository).save(payout);
    }

    @Test
    void handleTransferFailed_ShouldUpdateCreatorPayout() {
        CreatorPayout payout = CreatorPayout.builder()
                .id(payoutId)
                .status(PayoutStatus.PENDING)
                .build();

        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        service.handleTransferFailed(mockTransfer);

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        verify(creatorPayoutRepository).save(payout);
    }

    @Test
    void handleTransferFailed_ShouldUpdateLegacyPayoutAndRestoreEarnings() {
        User user = new User();
        user.setEmail("test@test.com");
        Payout payout = Payout.builder()
                .id(payoutId)
                .user(user)
                .tokenAmount(1000)
                .status(PayoutStatus.PENDING)
                .build();

        com.joinlivora.backend.token.CreatorEarnings earnings = new com.joinlivora.backend.token.CreatorEarnings();
        earnings.setAvailableTokens(5000);

        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.empty());
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(tokenService.getCreatorEarnings(user)).thenReturn(earnings);

        service.handleTransferFailed(mockTransfer);

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertEquals(6000, earnings.getAvailableTokens());
        verify(payoutRepository).save(payout);
        verify(tokenService).updateCreatorEarnings(earnings);
    }
}








