package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.chat.PPVChatAccessService;
import com.joinlivora.backend.exception.DuplicateRequestException;
import com.joinlivora.backend.exception.PaymentLockedException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service("ppvPurchaseService")
@Slf4j
public class PPVPurchaseService {

    private final PpvContentRepository ppvContentRepository;
    private final PpvPurchaseRepository ppvPurchaseRepository;
    private final PPVPurchaseValidationService purchaseValidationService;
    private final UserService userService;
    private final CreatorEarningsService creatorEarningsService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final StripeClient stripeClient;
    private final PPVChatAccessService ppvChatAccessService;
    private final ChatRoomRepository chatRoomRepository;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final PaymentService paymentService;

    public PPVPurchaseService(
            PpvContentRepository ppvContentRepository,
            PpvPurchaseRepository ppvPurchaseRepository,
            PPVPurchaseValidationService purchaseValidationService,
            UserService userService,
            CreatorEarningsService creatorEarningsService,
            AnalyticsEventPublisher analyticsEventPublisher,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            StripeClient stripeClient,
            PPVChatAccessService ppvChatAccessService,
            ChatRoomRepository chatRoomRepository,
            com.joinlivora.backend.streaming.StreamRepository streamRepository,
            PaymentService paymentService) {
        this.ppvContentRepository = ppvContentRepository;
        this.ppvPurchaseRepository = ppvPurchaseRepository;
        this.purchaseValidationService = purchaseValidationService;
        this.userService = userService;
        this.creatorEarningsService = creatorEarningsService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.stripeClient = stripeClient;
        this.ppvChatAccessService = ppvChatAccessService;
        this.chatRoomRepository = chatRoomRepository;
        this.streamRepository = streamRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public String createPurchaseIntent(User user, UUID ppvId, String ipAddress, String country, String userAgent, String clientRequestId) throws Exception {
        PpvContent content = ppvContentRepository.findById(ppvId)
                .orElseThrow(() -> new ResourceNotFoundException("PPV content not found"));
        
        // Idempotency check
        if (clientRequestId != null) {
            java.util.Optional<PpvPurchase> existing = ppvPurchaseRepository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("MONETIZATION: Duplicate PPV purchase request {} for creator {}. Returning existing status.", clientRequestId, user.getEmail());
                PpvPurchase p = existing.get();
                if (p.getStatus() == PpvPurchaseStatus.PAID) {
                    throw new DuplicateRequestException("You have already purchased this content.");
                }
                if (p.getStripePaymentIntentId() != null) {
                    try {
                        PaymentIntent pi = stripeClient.paymentIntents().retrieve(p.getStripePaymentIntentId());
                        return pi.getClientSecret();
                    } catch (Exception e) {
                        log.error("Failed to retrieve existing PaymentIntent", e);
                    }
                }
            }
        }

        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = paymentService.checkPaymentLock(user, content.getPrice(), ipAddress, country, userAgent, null);
        
        if (!content.isActive()) {
            throw new IllegalStateException("This content is no longer available");
        }

        // Check if already purchased (COMPLETED)
        if (purchaseValidationService.hasPurchased(user, content)) {
            throw new IllegalStateException("You have already purchased this content");
        }

        // Check for existing PENDING purchase for this content (alternative idempotency)
        ppvPurchaseRepository.findByPpvContentAndUserAndStatus(content, user, PpvPurchaseStatus.PENDING).ifPresent(p -> {
            log.info("MONETIZATION: Found existing PENDING purchase for creator {} and content {}. Reusing it.", user.getEmail(), content.getId());
            // We could return this, but Stripe secrets might be old. 
            // Better to keep it simple for now or refresh if needed.
            // For now, let's just use the clientRequestId check above as primary.
        });

        // 1. Create Stripe PaymentIntent
        User creator = content.getCreator();
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(content.getPrice().multiply(new BigDecimal("100")).longValue())
                .setCurrency(content.getCurrency())
                .putMetadata("type", "ppv")
                .putMetadata("user_id", user.getId().toString())
                .putMetadata("ppv_id", content.getId().toString())
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("client_request_id", clientRequestId != null ? clientRequestId : "")
                .putMetadata("fraud_risk_level", riskLevel != null ? riskLevel.name() : "LOW")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);

        // Stripe Connect (destination charges)
        // All creator earnings are recorded in our internal platform balance (ledger) via CreatorEarningsService.
        // Funds flow via Stripe are configured as destination charges so the application fee is collected by the platform.
        if (creator.getStripeAccountId() != null && !creator.getStripeAccountId().isBlank()) {
            java.math.BigDecimal feeRate = creatorEarningsService.getPlatformFeeRate();
            long applicationFeeAmount = content.getPrice()
                    .multiply(feeRate)
                    .multiply(new java.math.BigDecimal("100"))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValue();

            builder
                .setApplicationFeeAmount(applicationFeeAmount)
                .setTransferData(
                    PaymentIntentCreateParams.TransferData.builder()
                        .setDestination(creator.getStripeAccountId())
                        .build()
                );
            // TODO: In the future, we may want to hold funds on the platform balance 
            // and use manual transfers for scheduled payouts instead of destination charges.
        }

        if (ipAddress != null) builder.putMetadata("ip_address", ipAddress);
        if (country != null) builder.putMetadata("country", country);
        if (userAgent != null) builder.putMetadata("user_agent", userAgent);

        PaymentIntent intent = stripeClient.paymentIntents().create(builder.build());

        // 2. Persist Purchase as PENDING
        PpvPurchase purchase = PpvPurchase.builder()
                .ppvContent(content)
                .user(user)
                .amount(content.getPrice())
                .stripePaymentIntentId(intent.getId())
                .clientRequestId(clientRequestId)
                .status(PpvPurchaseStatus.PENDING)
                .build();
        
        ppvPurchaseRepository.save(purchase);

        log.info("MONETIZATION: Created PPV Purchase PaymentIntent {} for creator {} for content {}",
                intent.getId(), user.getEmail(), content.getId());

        return intent.getClientSecret();
    }

    @Transactional
    public void confirmPurchase(String paymentIntentId) {
        ppvPurchaseRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(purchase -> {
            if (purchase.getStatus() == PpvPurchaseStatus.PAID) return;

            purchase.setStatus(PpvPurchaseStatus.PAID);
            ppvPurchaseRepository.save(purchase);

            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.PAYMENT_SUCCEEDED,
                    purchase.getUser(),
                    Map.of(
                            "type", "ppv",
                            "amount", purchase.getAmount(),
                            "contentId", purchase.getPpvContent().getId()
                    )
            );

            log.info("MONETIZATION: PPV Purchase confirmed and marked as COMPLETED: {}", purchase.getId());
            
            // Grant chat access if associated with a stream room
            chatRoomRepository.findByPpvContentId(purchase.getPpvContent().getId()).ifPresent(chatRoom -> {
                if (chatRoom.getName() != null && chatRoom.getName().startsWith("stream-")) {
                    try {
                        UUID streamRoomId = UUID.fromString(chatRoom.getName().substring("stream-".length()));
                        // Resolve unified Stream identity
                        streamRepository.findById(streamRoomId)
                                .or(() -> streamRepository.findByMediasoupRoomId(streamRoomId))
                                .ifPresent(stream -> {
                                    ppvChatAccessService.grantAccess(purchase.getUser(), stream, purchase.getPpvContent(), null);
                                });
                    } catch (Exception e) {
                        log.error("Failed to parse stream room ID from chat room name: {}", chatRoom.getName(), e);
                    }
                }
            });

            // Notify creator via WebSocket
            messagingTemplate.convertAndSendToUser(
                    purchase.getPpvContent().getCreator().getId().toString(),
                    "/queue/notifications",
                    Map.of(
                            "type", "NEW_PPV_SALE",
                            "payload", Map.of(
                                    "title", purchase.getPpvContent().getTitle(),
                                    "amount", purchase.getAmount(),
                                    "user", purchase.getUser().getEmail()
                            )
                    )
            );
        });
    }

}
