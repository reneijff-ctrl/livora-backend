package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.PPVChatAccessService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Disabled("Legacy streaming architecture")
@ExtendWith(MockitoExtension.class)
class PPVPurchaseServiceTest {

    @Mock
    private PpvContentRepository ppvContentRepository;
    @Mock
    private PpvPurchaseRepository ppvPurchaseRepository;
    @Mock
    private PPVPurchaseValidationService purchaseValidationService;
    @Mock
    private UserService userService;
    @Mock
    private CreatorEarningsService monetizationService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private PPVChatAccessService ppvChatAccessService;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private StreamRepository StreamRepository;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PPVPurchaseService ppvPurchaseService;

    private User user;
    private PpvContent content;
    private UUID ppvId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        ppvId = UUID.randomUUID();
        content = PpvContent.builder()
                .id(ppvId)
                .title("Test PPV")
                .price(BigDecimal.TEN)
                .currency("eur")
                .creator(new User("creator@test.com", "pass", com.joinlivora.backend.user.Role.CREATOR))
                .active(true)
                .build();
        content.getCreator().setId(2L);
    }

    @Test
    void confirmPurchase_ShouldGrantChatAccess() throws Exception {
        String paymentIntentId = "pi_123";
        PpvPurchase purchase = PpvPurchase.builder()
                .id(UUID.randomUUID())
                .ppvContent(content)
                .user(user)
                .amount(BigDecimal.TEN)
                .status(PpvPurchaseStatus.PENDING)
                .build();

        when(ppvPurchaseRepository.findByStripePaymentIntentId(paymentIntentId)).thenReturn(Optional.of(purchase));
        
        UUID liveStreamRoomId = UUID.randomUUID();
        ChatRoom chatRoom = ChatRoom.builder()
                .name("liveStream-" + liveStreamRoomId)
                .ppvContent(content)
                .build();
        when(chatRoomRepository.findByPpvContentId(ppvId)).thenReturn(Optional.of(chatRoom));
        
        Stream liveStreamRoom = new Stream();
        liveStreamRoom.setId(liveStreamRoomId);
        when(StreamRepository.findById(liveStreamRoomId)).thenReturn(Optional.of(liveStreamRoom));

        ppvPurchaseService.confirmPurchase(paymentIntentId);

        assertEquals(PpvPurchaseStatus.PAID, purchase.getStatus());
        verify(ppvPurchaseRepository).save(purchase);
        verify(ppvChatAccessService).grantAccess(user, liveStreamRoom, content, null);
        verify(analyticsEventPublisher).publishEvent(eq(AnalyticsEventType.PAYMENT_SUCCEEDED), eq(user), anyMap());
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/notifications"), anyMap());
    }

    @Test
    void createPurchaseIntent_ShouldSucceed() throws Exception {
        when(ppvContentRepository.findById(ppvId)).thenReturn(Optional.of(content));
        when(purchaseValidationService.hasPurchased(user, content)).thenReturn(false);

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_new");
        when(intent.getClientSecret()).thenReturn("secret_123");
        
        com.stripe.service.PaymentIntentService piService = mock(com.stripe.service.PaymentIntentService.class);
        when(stripeClient.paymentIntents()).thenReturn(piService);
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);

        String result = ppvPurchaseService.createPurchaseIntent(user, ppvId, "127.0.0.1", "US", "Test UA", "req_123");

        assertEquals("secret_123", result);
        verify(ppvPurchaseRepository).save(argThat(p -> "req_123".equals(p.getClientRequestId())));
    }
}








