package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.payout.MonetizationService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PpvService {

    private final PpvContentRepository ppvContentRepository;
    private final PpvPurchaseRepository ppvPurchaseRepository;
    private final UserService userService;
    private final MonetizationService monetizationService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final StripeClient stripeClient;

    public List<PpvContent> getCreatorPpvContent(User creator) {
        return ppvContentRepository.findAllByCreatorAndActiveTrue(creator);
    }

    public PpvContent getPpvContent(UUID id) {
        return ppvContentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("PPV content not found"));
    }

    @Transactional
    public String createPurchaseIntent(User user, UUID ppvId) throws Exception {
        PpvContent content = getPpvContent(ppvId);
        
        if (!content.isActive()) {
            throw new RuntimeException("This content is no longer available");
        }

        // Check if already purchased
        if (hasPurchased(user, content)) {
            throw new RuntimeException("You have already purchased this content");
        }

        // 1. Create Stripe PaymentIntent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(content.getPrice().multiply(new BigDecimal("100")).longValue())
                .setCurrency(content.getCurrency())
                .putMetadata("type", "ppv")
                .putMetadata("user_id", user.getId().toString())
                .putMetadata("ppv_id", content.getId().toString())
                .putMetadata("creator_id", content.getCreator().getId().toString())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent intent = stripeClient.paymentIntents().create(params);

        // 2. Persist Purchase as PENDING
        PpvPurchase purchase = PpvPurchase.builder()
                .ppvContent(content)
                .user(user)
                .amount(content.getPrice())
                .stripePaymentIntentId(intent.getId())
                .status(PpvPurchaseStatus.PENDING)
                .build();
        
        ppvPurchaseRepository.save(purchase);

        log.info("MONETIZATION: Created PPV Purchase PaymentIntent {} for user {} for content {}", 
                intent.getId(), user.getEmail(), content.getId());

        return intent.getClientSecret();
    }

    public boolean hasPurchased(User user, PpvContent content) {
        return ppvPurchaseRepository.findByPpvContentAndUserAndStatus(content, user, PpvPurchaseStatus.PAID).isPresent();
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

            log.info("MONETIZATION: PPV Purchase confirmed and marked as PAID: {}", purchase.getId());
            
            // Notify creator via WebSocket
            messagingTemplate.convertAndSendToUser(
                    purchase.getPpvContent().getCreator().getEmail(),
                    "/queue/notifications",
                    Map.of(
                            "type", "NEW_PPV_SALE",
                            "payload", Map.of(
                                    "title", purchase.getPpvContent().getTitle(),
                                    "amount", purchase.getAmount(),
                                    "fromUser", purchase.getUser().getEmail()
                            )
                    )
            );
        });
    }

    public String getAccessUrl(User user, UUID ppvId) {
        PpvContent content = getPpvContent(ppvId);
        
        if (!hasPurchased(user, content) && !user.getRole().name().equals("ADMIN") && !content.getCreator().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You have not purchased this content");
        }

        // In a real app, generate a signed URL here.
        // For now, return the secured URL (mocking signed URL behavior by adding a token)
        return content.getContentUrl() + "?token=" + UUID.randomUUID().toString() + "&expires=" + (System.currentTimeMillis() + 3600000);
    }

    @Transactional
    public PpvContent createContent(User creator, PpvContent content) {
        content.setCreator(creator);
        return ppvContentRepository.save(content);
    }

    @Transactional
    public PpvContent updateContent(User creator, UUID id, PpvContent updated) {
        PpvContent existing = getPpvContent(id);
        if (!existing.getCreator().getId().equals(creator.getId()) && !creator.getRole().name().equals("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }
        
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setPrice(updated.getPrice());
        existing.setActive(updated.isActive());
        
        return ppvContentRepository.save(existing);
    }

    @Transactional
    public void deleteContent(User creator, UUID id) {
        PpvContent existing = getPpvContent(id);
        if (!existing.getCreator().getId().equals(creator.getId()) && !creator.getRole().name().equals("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }
        ppvContentRepository.delete(existing);
    }
}
