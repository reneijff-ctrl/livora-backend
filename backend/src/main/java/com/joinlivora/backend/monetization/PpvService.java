package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.util.UrlUtils;
import com.stripe.StripeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service("ppvService")
@Slf4j
public class PpvService {

    private final PpvContentRepository ppvContentRepository;
    private final PpvPurchaseRepository ppvPurchaseRepository;
    private final PPVPurchaseValidationService purchaseValidationService;
    private final UserService userService;
    private final CreatorEarningsService creatorEarningsService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final StripeClient stripeClient;

    public PpvService(
            PpvContentRepository ppvContentRepository,
            PpvPurchaseRepository ppvPurchaseRepository,
            PPVPurchaseValidationService purchaseValidationService,
            UserService userService,
            @org.springframework.context.annotation.Lazy CreatorEarningsService monetizationService,
            AnalyticsEventPublisher analyticsEventPublisher,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            StripeClient stripeClient) {
        this.ppvContentRepository = ppvContentRepository;
        this.ppvPurchaseRepository = ppvPurchaseRepository;
        this.purchaseValidationService = purchaseValidationService;
        this.userService = userService;
        this.creatorEarningsService = monetizationService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.stripeClient = stripeClient;
    }

    @Transactional(readOnly = true)
    public List<PpvContent> getCreatorPpvContent(User creator) {
        List<PpvContent> contents = ppvContentRepository.findAllByCreatorAndActiveTrue(creator);
        // Ensure URLs are sanitized for the response
        contents.forEach(c -> c.setContentUrl(UrlUtils.sanitizeUrl(c.getContentUrl())));
        return contents;
    }

    public PpvContent getPpvContent(UUID id) {
        return ppvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PPV content not found"));
    }

    public boolean hasPurchased(User user, PpvContent content) {
        return purchaseValidationService.hasPurchased(user, content);
    }

    public String getAccessUrl(User user, UUID ppvId) {
        PpvContent content = getPpvContent(ppvId);
        
        if (!hasPurchased(user, content) && !user.getRole().name().equals("ADMIN") && !content.getCreator().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You have not purchased this content");
        }

        // In a real app, generate a signed URL here.
        // For now, return the secured URL (mocking signed URL behavior by adding a token)
        String baseUrl = UrlUtils.sanitizeUrl(content.getContentUrl());
        return baseUrl + "?token=" + UUID.randomUUID().toString() + "&expires=" + (System.currentTimeMillis() + 3600000);
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
