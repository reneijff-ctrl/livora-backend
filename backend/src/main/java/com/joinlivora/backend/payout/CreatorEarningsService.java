package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.analytics.CreatorStats;
import com.joinlivora.backend.analytics.CreatorStatsRepository;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payout.dto.*;
import com.joinlivora.backend.payout.event.EarningsUpdatedEvent;
import com.joinlivora.backend.token.CreatorEarnings;
import com.joinlivora.backend.token.CreatorEarningsRepository;
import com.joinlivora.backend.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service("creatorEarningsService")
@Slf4j
public class CreatorEarningsService {

    private final CreatorEarningRepository creatorEarningRepository;
    private final PaymentRepository paymentRepository;
    private final CreatorEarningsRepository creatorEarningsRepository;
    private final PayoutRepository payoutRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final CreatorStatsRepository creatorStatsRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final PayoutHoldService payoutHoldService;
    private final StripePayoutAdapter stripePayoutAdapter;
    private final StripeAccountRepository stripeAccountRepository;
    private final PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;
    private final AuditService auditService;
    private final PlatformBalanceRepository platformBalanceRepository;

    public CreatorEarningsService(
            CreatorEarningRepository creatorEarningRepository,
            PaymentRepository paymentRepository,
            CreatorEarningsRepository creatorEarningsRepository,
            PayoutRepository payoutRepository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher eventPublisher,
            CreatorStatsRepository creatorStatsRepository,
            LegacyCreatorProfileRepository creatorProfileRepository,
            PayoutHoldService payoutHoldService,
            StripePayoutAdapter stripePayoutAdapter,
            StripeAccountRepository stripeAccountRepository,
            PayoutCreatorEarningsRepository payoutCreatorEarningsRepository,
            AuditService auditService,
            PlatformBalanceRepository platformBalanceRepository) {
        this.creatorEarningRepository = creatorEarningRepository;
        this.paymentRepository = paymentRepository;
        this.creatorEarningsRepository = creatorEarningsRepository;
        this.payoutRepository = payoutRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.creatorStatsRepository = creatorStatsRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.payoutHoldService = payoutHoldService;
        this.stripePayoutAdapter = stripePayoutAdapter;
        this.stripeAccountRepository = stripeAccountRepository;
        this.payoutCreatorEarningsRepository = payoutCreatorEarningsRepository;
        this.auditService = auditService;
        this.platformBalanceRepository = platformBalanceRepository;
    }

    @org.springframework.beans.factory.annotation.Value("${livora.monetization.platform-fee-percentage:30}")
    private int platformFeePercentage;

    @org.springframework.beans.factory.annotation.Value("${livora.monetization.earnings-dry-run:false}")
    private boolean earningsDryRun;
    
    public static final BigDecimal TOKEN_TO_EUR_RATE = new BigDecimal("0.01");         // 1 Token = 0.01 EUR

    public BigDecimal getPlatformFeeRate() {
        return BigDecimal.valueOf(platformFeePercentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void recordSubscriptionEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.SUBSCRIPTION);
    }

    @Transactional
    public void recordTipEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.TIP);
    }

    @Transactional
    public void recordDirectTipEarning(com.joinlivora.backend.tip.DirectTip tip) {
        User creator = tip.getCreator();
        BigDecimal gross = tip.getAmount();
        BigDecimal fee = gross.multiply(getPlatformFeeRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fee);
        BigDecimal netEur = convertToEur(net, tip.getCurrency());

        // 1. Credit creator balance (Tokens)
        // We convert currency to tokens for the unified balance
        long tokensToCredit = netEur.divide(TOKEN_TO_EUR_RATE, 0, RoundingMode.HALF_UP).longValue();
        
        if (earningsDryRun) {
            log.info("MONETIZATION: DRY-RUN MODE ACTIVE. Crediting locked balance instead of active balance for creator {}", creator.getEmail());
            creditLockedBalance(creator, tokensToCredit);
        } else {
            creditCreatorBalance(creator, tokensToCredit);
        }

        // 1.1 Credit real-currency payout balance
        creditPayoutEarnings(creator, netEur, earningsDryRun);

        // 1.2 Update platform balances (ledger)
        updatePlatformBalances(fee, net, tip.getCurrency());

        // Update live stats
        updateLiveStats(creator, netEur, 0, EarningSource.TIP);

        // 2. Persist history record
        CreatorEarning earning = CreatorEarning.builder()
                .creator(creator)
                .user(tip.getUser())
                .grossAmount(gross)
                .platformFee(fee)
                .netAmount(net)
                .currency(tip.getCurrency())
                .sourceType(EarningSource.TIP)
                .stripeSessionId(tip.getStripeSessionId())
                .locked(earningsDryRun)
                .dryRun(earningsDryRun)
                .build();

        creatorEarningRepository.save(earning);

        if (earningsDryRun) {
            log.info("MONETIZATION: [DRY-RUN] Recorded direct TIP earning for creator {}: Gross={} {}, Fee={}, Net={}, Credited {} LOCKED tokens",
                    creator.getEmail(), gross, tip.getCurrency(), fee, net, tokensToCredit);
            auditService.logEvent(null, AuditService.DRY_RUN_EARNING_RECORDED, "USER", new UUID(0L, creator.getId()), 
                    Map.of("source", "DIRECT_TIP", "gross", gross, "net", net, "currency", tip.getCurrency()), null, null);
        } else {
            log.info("MONETIZATION: Recorded direct TIP earning for creator {}: Gross={} {}, Fee={}, Net={}, Credited {} tokens",
                    creator.getEmail(), gross, tip.getCurrency(), fee, net, tokensToCredit);
        }

        broadcastUpdate(creator, EarningSource.TIP, gross, tip.getCurrency());
    }

    @Transactional
    public void recordPPVEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.PPV);
    }

    @Transactional
    public void recordHighlightedChatEarning(Payment payment, User creator) {
        processEarning(payment, creator, EarningSource.HIGHLIGHTED_CHAT);
    }

    @Transactional
    @CacheEvict(value = "creatorEarnings", key = "#creator.id")
    public void recordTokenTipEarning(User viewer, User creator, long amount, java.util.UUID roomId, com.joinlivora.backend.fraud.model.RiskLevel riskLevel) {
        recordTokenEarning(viewer, creator, amount, roomId, riskLevel, EarningSource.TIP);
    }

    @Transactional
    @CacheEvict(value = "creatorEarnings", key = "#creator.id")
    public void recordChatEarning(User viewer, User creator, long amount, java.util.UUID roomId) {
        // For CHAT, we assume LOW risk by default as it's typically small per-message payments
        recordTokenEarning(viewer, creator, amount, roomId, com.joinlivora.backend.fraud.model.RiskLevel.LOW, EarningSource.CHAT);
    }

    @Transactional
    @CacheEvict(value = "creatorEarnings", key = "#creator.id")
    public void recordPrivateShowEarning(User viewer, User creator, long amount, java.util.UUID sessionId) {
        recordTokenEarning(viewer, creator, amount, sessionId, com.joinlivora.backend.fraud.model.RiskLevel.LOW, EarningSource.PRIVATE_SHOW);
    }

    @Transactional
    @CacheEvict(value = "creatorEarnings", key = "#creator.id")
    public void recordPPVTokenEarning(User viewer, User creator, long amount, java.util.UUID contentId) {
        // For PPV, we assume LOW risk by default as it's a direct user action
        recordTokenEarning(viewer, creator, amount, contentId, com.joinlivora.backend.fraud.model.RiskLevel.LOW, EarningSource.PPV);
    }

    private void recordTokenEarning(User viewer, User creator, long amount, java.util.UUID roomId, com.joinlivora.backend.fraud.model.RiskLevel riskLevel, EarningSource source) {
        BigDecimal gross = BigDecimal.valueOf(amount);
        BigDecimal fee = gross.multiply(getPlatformFeeRate()).setScale(0, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fee);

        long netTokens = net.longValue();

        PayoutHold appliedHold = null;
        if (riskLevel != null && riskLevel != com.joinlivora.backend.fraud.model.RiskLevel.LOW) {
            appliedHold = payoutHoldService.createHold(creator, riskLevel, null, "Fraud risk detected in " + source + ": " + riskLevel);
        }

        // 1. Credit creator balance (Tokens)
        boolean isLocked = appliedHold != null || payoutHoldService.hasActiveHold(creator) || earningsDryRun;

        if (isLocked) {
            creditLockedBalance(creator, netTokens);
        } else {
            creditCreatorBalance(creator, netTokens);
        }

        // 1.1 Credit real-currency payout balance
        BigDecimal netEur = net.multiply(TOKEN_TO_EUR_RATE);
        creditPayoutEarnings(creator, netEur, isLocked);

        // 1.2 Update platform balances (ledger)
        updatePlatformBalances(fee, net, "TOKEN");

        // Update live stats
        updateLiveStats(creator, BigDecimal.ZERO, netTokens, source);

        // 2. Persist history record
        CreatorEarning earning = CreatorEarning.builder()
                .creator(creator)
                .user(viewer)
                .grossAmount(gross)
                .platformFee(fee)
                .netAmount(net)
                .currency("TOKEN")
                .sourceType(source)
                .locked(isLocked)
                .dryRun(earningsDryRun)
                .payoutHold(appliedHold)
                .build();

        creatorEarningRepository.save(earning);

        if (earningsDryRun) {
            log.info("MONETIZATION: [DRY-RUN] Recorded token {} earning for creator {}: Gross={}, Fee={}, Net={}, Credited {} LOCKED tokens",
                    source, creator.getEmail(), gross, fee, net, netTokens);
            auditService.logEvent(null, AuditService.DRY_RUN_EARNING_RECORDED, "USER", new UUID(0L, creator.getId()), 
                    Map.of("source", "TOKEN_" + source, "gross", gross, "net", net, "currency", "TOKEN"), null, null);
        } else {
            log.info("MONETIZATION: Recorded token {} earning for creator {}: Gross={}, Fee={}, Net={}, Credited {} tokens",
                    source, creator.getEmail(), gross, fee, net, netTokens);
        }

        if (isLocked && appliedHold == null) {
            payoutHoldService.linkToActiveHolds(earning);
        }

        if (riskLevel == com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM) {
            applyMediumRiskHold(creator);
        }

        log.info("MONETIZATION: Recorded TOKEN {} earning for creator {}: Gross={}, Fee={}, Net={}, Risk={}",
                source, creator.getEmail(), gross, fee, net, riskLevel);

        broadcastUpdate(creator, source, gross, "TOKEN");
    }

    @Transactional
    public void reverseEarningByStripeId(String stripeId) {
        creatorEarningRepository.findByStripeChargeId(stripeId).ifPresent(earning -> {
            log.info("MONETIZATION: Reversing earning {} for creator {} due to refund", earning.getId(), earning.getCreator().getEmail());

            // 1. Deduct from creator balance
            long tokensToDeduct;
            if ("TOKEN".equals(earning.getCurrency())) {
                tokensToDeduct = earning.getNetAmount().longValue();
            } else {
                tokensToDeduct = earning.getNetAmount().divide(TOKEN_TO_EUR_RATE, 0, RoundingMode.HALF_UP).longValue();
            }

            creditCreatorBalance(earning.getCreator(), -tokensToDeduct);

            // 1.1 Deduct from real-currency payout balance
            BigDecimal netEurToDeduct = convertToEur(earning.getNetAmount().negate(), earning.getCurrency());
            creditPayoutEarnings(earning.getCreator(), netEurToDeduct, earning.isLocked());

            // 1.2 Update platform balances (ledger)
            updatePlatformBalances(earning.getPlatformFee().negate(), earning.getNetAmount().negate(), earning.getCurrency());

            // Update live stats
            BigDecimal netToDeduct = earning.getNetAmount().negate();
            long tokensToDeductStats = tokensToDeduct;
            updateLiveStats(earning.getCreator(), 
                    "TOKEN".equals(earning.getCurrency()) ? BigDecimal.ZERO : netToDeduct,
                    "TOKEN".equals(earning.getCurrency()) ? -tokensToDeductStats : 0, 
                    earning.getSourceType());

            // 2. Add reversal record
            CreatorEarning reversal = CreatorEarning.builder()
                    .creator(earning.getCreator())
                    .grossAmount(earning.getGrossAmount().negate())
                    .platformFee(earning.getPlatformFee().negate())
                    .netAmount(earning.getNetAmount().negate())
                    .currency(earning.getCurrency())
                    .sourceType(EarningSource.CHARGEBACK)
                    .stripeChargeId(earning.getStripeChargeId() + "_reversal")
                    .build();
            creatorEarningRepository.save(reversal);
        });
    }

    private void processEarning(Payment payment, User creator, EarningSource source) {
        if (creator == null) {
            log.warn("No creatorUserId associated with payment {}, skipping creatorUserId earning record", payment.getId());
            return;
        }

        BigDecimal gross = payment.getAmount();
        BigDecimal fee = gross.multiply(getPlatformFeeRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fee);

        PayoutHold appliedHold = null;
        if (payment.getRiskLevel() != null && payment.getRiskLevel() != com.joinlivora.backend.fraud.model.RiskLevel.LOW) {
            appliedHold = payoutHoldService.createHold(creator, payment.getRiskLevel(), payment.getId(), 
                    "Fraud risk detected in transaction: " + payment.getRiskLevel());
        }

        // 1. Credit creator balance
        // We convert currency to tokens for the unified balance
        long tokensToCredit = net.divide(TOKEN_TO_EUR_RATE, 0, RoundingMode.HALF_UP).longValue();
        
        boolean isLocked = appliedHold != null || payoutHoldService.hasActiveHold(creator) || earningsDryRun;

        if (isLocked) {
            creditLockedBalance(creator, tokensToCredit);
        } else {
            creditCreatorBalance(creator, tokensToCredit);
        }

        // 1.1 Credit real-currency payout balance
        creditPayoutEarnings(creator, net, isLocked);

        // 1.2 Update platform balances (ledger)
        updatePlatformBalances(fee, net, payment.getCurrency());

        // Update live stats
        updateLiveStats(creator, net, 0, source);

        // 2. Persist history record
        CreatorEarning earning = CreatorEarning.builder()
                .creator(creator)
                .user(payment.getUser())
                .grossAmount(gross)
                .platformFee(fee)
                .netAmount(net)
                .currency(payment.getCurrency())
                .sourceType(source)
                .stripeChargeId(payment.getStripePaymentIntentId())
                .stripeSessionId(payment.getStripeSessionId())
                .locked(isLocked || earningsDryRun)
                .dryRun(earningsDryRun)
                .payoutHold(appliedHold)
                .build();

        creatorEarningRepository.save(earning);

        if (earningsDryRun) {
            log.info("MONETIZATION: [DRY-RUN] Recorded {} earning for creator {}: Gross={} {}, Fee={}, Net={}, Credited {} LOCKED tokens",
                    source, creator.getEmail(), gross, payment.getCurrency(), fee, net, tokensToCredit);
            auditService.logEvent(null, AuditService.DRY_RUN_EARNING_RECORDED, "USER", new UUID(0L, creator.getId()), 
                    Map.of("source", source, "gross", gross, "net", net, "currency", payment.getCurrency()), null, null);
        } else {
            log.info("MONETIZATION: Recorded {} earning for creator {}: Gross={} {}, Fee={}, Net={}, Credited {} tokens, Risk={}",
                    source, creator.getEmail(), gross, payment.getCurrency(), fee, net, tokensToCredit, payment.getRiskLevel());
        }

        broadcastUpdate(creator, source, gross, payment.getCurrency());

        payment.setCreator(creator);
        paymentRepository.save(payment);
    }

    private void applyMediumRiskHold(User creator) {
        stripeAccountRepository.findByUser(creator).ifPresent(sa -> {
            if (sa.getStripeAccountId() != null) {
                try {
                    // Shorten payout delay means increasing the delay days to a safer value like 7 days
                    stripePayoutAdapter.enforceHold(sa.getStripeAccountId(), 7);
                    log.info("MONETIZATION: Enforced 7-day payout delay for creator {} due to MEDIUM risk payment", creator.getEmail());
                } catch (Exception e) {
                    log.error("MONETIZATION: Failed to enforce payout delay for creator {}", creator.getId(), e);
                }
            }
        });
    }

    private void broadcastUpdate(User creator, EarningSource source, BigDecimal amount, String currency) {
        try {
            // 1. Publish Spring Event for internal listeners
            eventPublisher.publishEvent(new EarningsUpdatedEvent(this, creator, source, amount, currency));

            // 2. Broadcast to WebSocket for frontend
            CreatorEarningsDTO currentAggregatedEarnings = getAggregatedEarnings(creator);
            CreatorEarningsUpdateDTO update = CreatorEarningsUpdateDTO.builder()
                    .type(source.name())
                    .amount(amount)
                    .currency(currency)
                    .currentAggregatedEarnings(currentAggregatedEarnings)
                    .build();

            String topic = "/exchange/amq.topic/creator." + creator.getId() + ".earnings";
            messagingTemplate.convertAndSend(topic, update);
            log.debug("MONETIZATION: Broadcasted earnings update to {}", topic);
        } catch (Exception e) {
            log.error("MONETIZATION: Failed to broadcast earnings update for creator {}", creator.getId(), e);
        }
    }

    public void creditCreatorBalance(User creator, long tokens) {
        CreatorEarnings earnings = getOrCreateCreatorEarnings(creator);
        
        earnings.setTotalEarnedTokens(earnings.getTotalEarnedTokens() + tokens);
        earnings.setAvailableTokens(earnings.getAvailableTokens() + tokens);
        creatorEarningsRepository.save(earnings);
    }

    public void creditLockedBalance(User creator, long tokens) {
        CreatorEarnings earnings = getOrCreateCreatorEarnings(creator);

        earnings.setTotalEarnedTokens(earnings.getTotalEarnedTokens() + tokens);
        earnings.setLockedTokens(earnings.getLockedTokens() + tokens);
        creatorEarningsRepository.save(earnings);
    }

    /**
     * Atomically gets or creates a CreatorEarnings row for the given creator.
     * Uses try-insert-catch-duplicate pattern to prevent race conditions
     * where two concurrent first-tips both see empty and both create new rows.
     * The UNIQUE(user_id) constraint on creator_earnings guarantees exactly one row per creator.
     */
    private CreatorEarnings getOrCreateCreatorEarnings(User creator) {
        Optional<CreatorEarnings> existing = creatorEarningsRepository.findByUserWithLock(creator);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            CreatorEarnings newEarnings = CreatorEarnings.builder()
                    .user(creator)
                    .totalEarnedTokens(0)
                    .availableTokens(0)
                    .lockedTokens(0)
                    .build();
            creatorEarningsRepository.saveAndFlush(newEarnings);
            return newEarnings;
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent CreatorEarnings creation for creator {}, fetching existing row", creator.getId());
            return creatorEarningsRepository.findByUserWithLock(creator)
                    .orElseThrow(() -> new IllegalStateException(
                            "CreatorEarnings row not found after duplicate key conflict for creator " + creator.getId()));
        }
    }

    public void updateLiveStats(User creator, BigDecimal netAmount, long netTokens, EarningSource source) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator).orElse(null);
        if (profile == null) return;

        UUID creatorId = profile.getId();
        Instant now = Instant.now();

        // Ensure stats record exists before atomic increment
        if (!creatorStatsRepository.existsById(creatorId)) {
            try {
                creatorStatsRepository.save(CreatorStats.builder()
                        .creatorId(creatorId)
                        .updatedAt(now)
                        .build());
                creatorStatsRepository.flush();
            } catch (Exception e) {
                // Ignore if created concurrently
            }
        }

        // Perform atomic increments
        if (netAmount.compareTo(BigDecimal.ZERO) != 0) {
            creatorStatsRepository.incrementTotalNetEarnings(creatorId, netAmount, now);
        }
        
        if (netTokens != 0) {
            creatorStatsRepository.incrementTotalNetTokens(creatorId, netTokens, now);
        }

        long delta = netAmount.compareTo(BigDecimal.ZERO) >= 0 ? 1 : -1;
        if (source == EarningSource.SUBSCRIPTION) {
            creatorStatsRepository.incrementSubscriptionCount(creatorId, delta, now);
        } else if (source == EarningSource.TIP) {
            creatorStatsRepository.incrementTipsCount(creatorId, delta, now);
        } else if (source == EarningSource.HIGHLIGHTED_CHAT) {
            creatorStatsRepository.incrementHighlightsCount(creatorId, delta, now);
        }
    }

    @Transactional
    public int unlockExpiredEarnings() {
        List<CreatorEarning> expired = creatorEarningRepository.findExpiredLockedEarnings(Instant.now());
        if (expired.isEmpty()) return 0;

        java.util.Map<User, List<CreatorEarning>> byCreator = expired.stream()
                .collect(java.util.stream.Collectors.groupingBy(CreatorEarning::getCreator));

        byCreator.forEach((creator, earnings) -> {
            long totalTokens = 0;
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalEurForNewBalance = BigDecimal.ZERO;

            for (CreatorEarning e : earnings) {
                e.setLocked(false);
                BigDecimal eurValue = convertToEur(e.getNetAmount(), e.getCurrency());
                totalEurForNewBalance = totalEurForNewBalance.add(eurValue);

                long tokensForThisEarning;
                if ("TOKEN".equals(e.getCurrency())) {
                    tokensForThisEarning = e.getNetAmount().longValue();
                    totalTokens += tokensForThisEarning;
                } else {
                    totalRevenue = totalRevenue.add(e.getNetAmount());
                    tokensForThisEarning = e.getNetAmount().divide(TOKEN_TO_EUR_RATE, 0, RoundingMode.HALF_UP).longValue();
                    totalTokens += tokensForThisEarning;
                }
            }

            creatorEarningRepository.saveAll(earnings);
            
            // Update summary table
            final long tokensToUnlock = totalTokens;
            final BigDecimal revenueToUnlock = totalRevenue;
            final BigDecimal eurToUnlockForNewBalance = totalEurForNewBalance;
            
            creatorEarningsRepository.findByUserWithLock(creator).ifPresent(ce -> {
                ce.setAvailableTokens(ce.getAvailableTokens() + tokensToUnlock);
                ce.setLockedTokens(Math.max(0, ce.getLockedTokens() - tokensToUnlock));
                creatorEarningsRepository.save(ce);
            });

            payoutCreatorEarningsRepository.findByUserWithLock(creator).ifPresent(ce -> {
                ce.setAvailableBalance(ce.getAvailableBalance().add(eurToUnlockForNewBalance));
                ce.setPendingBalance(ce.getPendingBalance().subtract(eurToUnlockForNewBalance).max(BigDecimal.ZERO));
                payoutCreatorEarningsRepository.save(ce);
            });

            log.info("MONETIZATION: Unlocked {} earnings for creator {}: {} tokens, {} revenue",
                    earnings.size(), creator.getEmail(), totalTokens, totalRevenue);

            eventPublisher.publishEvent(new com.joinlivora.backend.payout.event.EarningsUnlockedEvent(this, creator, totalTokens, totalRevenue));
        });

        return expired.size();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCreatorStats(User creator) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator).orElse(null);
        
        if (profile != null) {
            Optional<CreatorStats> statsOpt = creatorStatsRepository.findById(profile.getId());
            if (statsOpt.isPresent()) {
                CreatorStats stats = statsOpt.get();
                return Map.of(
                    "totalNetEarnings", stats.getTotalNetEarnings(),
                    "subscriptionCount", stats.getSubscriptionCount(),
                    "tipsCount", stats.getTipsCount(),
                    "highlightsCount", stats.getHighlightsCount()
                );
            }
        }

        // Fallback to repository for new creators or missing stats
        BigDecimal totalNetEarnings = creatorEarningRepository.sumTotalNetEarningsByCreator(creator);
        if (totalNetEarnings == null) totalNetEarnings = BigDecimal.ZERO;
        
        long subscriptionCount = creatorEarningRepository.countByCreatorAndSource(creator, EarningSource.SUBSCRIPTION);
        long tipsCount = creatorEarningRepository.countByCreatorAndSource(creator, EarningSource.TIP);
        long highlightsCount = creatorEarningRepository.countByCreatorAndSource(creator, EarningSource.HIGHLIGHTED_CHAT);

        return Map.of(
            "totalNetEarnings", totalNetEarnings,
            "subscriptionCount", subscriptionCount,
            "tipsCount", tipsCount,
            "highlightsCount", highlightsCount
        );
    }

    @Cacheable(value = "creatorEarnings", key = "#creator.id", unless = "#result == null")
    @Transactional(readOnly = true)
    public CreatorEarningsDTO getAggregatedEarnings(User creator) {
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(creator).orElse(null);
        CreatorStats stats = null;
        
        if (profile != null) {
            stats = creatorStatsRepository.findById(profile.getId()).orElse(null);
        }

        BigDecimal pendingPayout = payoutRepository.sumEurAmountByUserAndStatus(creator, PayoutStatus.PENDING);
        if (pendingPayout == null) pendingPayout = BigDecimal.ZERO;

        if (stats != null) {
            return CreatorEarningsDTO.builder()
                    .totalTokens(stats.getTotalNetTokens())
                    .totalRevenue(stats.getTotalNetEarnings())
                    .todayTokens(stats.getTodayNetTokens())
                    .todayRevenue(stats.getTodayNetEarnings())
                    .pendingPayout(pendingPayout)
                    .lastUpdated(stats.getUpdatedAt() != null ? stats.getUpdatedAt() : Instant.now())
                    .build();
        }

        // Fallback for new creators or missing stats
        BigDecimal totalRevenue = creatorEarningRepository.sumTotalNetRevenueByCreator(creator);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        
        BigDecimal totalTokens = creatorEarningRepository.sumTotalNetTokensByCreator(creator);
        long totalTokensLong = totalTokens != null ? totalTokens.longValue() : 0L;

        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal todayRevenue = creatorEarningRepository.sumNetRevenueByCreatorAndSince(creator, today);
        if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;
        
        BigDecimal todayTokens = creatorEarningRepository.sumNetTokensByCreatorAndSince(creator, today);
        long todayTokensLong = todayTokens != null ? todayTokens.longValue() : 0L;

        return CreatorEarningsDTO.builder()
                .totalTokens(totalTokensLong)
                .totalRevenue(totalRevenue)
                .todayTokens(todayTokensLong)
                .todayRevenue(todayRevenue)
                .pendingPayout(pendingPayout)
                .lastUpdated(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public CreatorEarningsReportDTO getEarningsReport(User creator) {
        Instant now = Instant.now();
        Instant dailyStart = now.truncatedTo(ChronoUnit.DAYS);
        Instant weeklyStart = now.truncatedTo(ChronoUnit.DAYS).minus(7, ChronoUnit.DAYS);
        Instant monthlyStart = now.truncatedTo(ChronoUnit.DAYS).minus(30, ChronoUnit.DAYS);

        return CreatorEarningsReportDTO.builder()
                .daily(getPeriodStats(creator, dailyStart, now))
                .weekly(getPeriodStats(creator, weeklyStart, now))
                .monthly(getPeriodStats(creator, monthlyStart, now))
                .build();
    }

    @Transactional(readOnly = true)
    public List<CreatorEarningDto> getRecentTransactions(User creator, int limit) {
        return creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator)
                .stream()
                .limit(limit)
                .map(this::mapToDto)
                .toList();
    }

    private CreatorEarningsReportDTO.PeriodStats getPeriodStats(User creator, Instant start, Instant end) {
        BigDecimal totalRevenue = creatorEarningRepository.sumNetRevenueByCreatorAndPeriod(creator, start, end);
        BigDecimal totalTokens = creatorEarningRepository.sumNetTokensByCreatorAndPeriod(creator, start, end);
        List<Object[]> grouped = creatorEarningRepository.sumNetEarningsByCreatorAndPeriodGroupedBySourceAndCurrency(creator, start, end);

        Map<EarningSource, BigDecimal> revenueBySource = new EnumMap<>(EarningSource.class);
        Map<EarningSource, Long> tokensBySource = new EnumMap<>(EarningSource.class);

        for (Object[] row : grouped) {
            EarningSource source = (EarningSource) row[0];
            String currency = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];

            if ("TOKEN".equals(currency)) {
                tokensBySource.put(source, amount.longValue());
            } else {
                revenueBySource.put(source, amount);
            }
        }

        return CreatorEarningsReportDTO.PeriodStats.builder()
                .totalEarnings(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalTokens(totalTokens != null ? totalTokens.longValue() : 0L)
                .revenueBySource(revenueBySource)
                .tokensBySource(tokensBySource)
                .build();
    }

    @Transactional(readOnly = true)
    public CreatorEarningsSummary getEarningsSummary(User creator) {
        com.joinlivora.backend.payout.CreatorEarnings earnings = payoutCreatorEarningsRepository.findByCreator(creator)
                .orElse(com.joinlivora.backend.payout.CreatorEarnings.builder()
                        .creator(creator)
                        .availableBalance(BigDecimal.ZERO)
                        .pendingBalance(BigDecimal.ZERO)
                        .totalEarned(BigDecimal.ZERO)
                        .build());

        Instant lastPayoutDate = payoutRepository.findAllByUserOrderByCreatedAtDesc(creator).stream()
                .filter(p -> p.getStatus() == PayoutStatus.COMPLETED)
                .map(Payout::getCreatedAt)
                .findFirst()
                .orElse(null);

        // Calculate monthly earnings
        Instant since = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal monthEarnings = creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator).stream()
                .filter(e -> !e.getCreatedAt().isBefore(since))
                .map(e -> convertToEur(e.getNetAmount(), e.getCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Stripe balance mocked if needed
        // In a real scenario, this would fetch from Stripe API
        BigDecimal mockedStripeBalance = BigDecimal.ZERO; 
        BigDecimal totalAvailable = earnings.getAvailableBalance().add(mockedStripeBalance);

        return CreatorEarningsSummary.builder()
                .totalEarned(earnings.getTotalEarned())
                .availableBalance(totalAvailable)
                .pendingBalance(earnings.getPendingBalance())
                .monthEarnings(monthEarnings)
                .lastPayoutDate(lastPayoutDate)
                .build();
    }

    @Transactional(readOnly = true)
    public CreatorEarningsOverviewDTO getEarningsOverview(User creator) {
        BigDecimal totalEarnings = creatorEarningRepository.sumTotalNetEarningsByCreator(creator);
        if (totalEarnings == null) totalEarnings = BigDecimal.ZERO;

        List<CreatorEarning> allEarnings = creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal pendingBalance = BigDecimal.ZERO;
        
        for (CreatorEarning e : allEarnings) {
            BigDecimal eurValue = convertToEur(e.getNetAmount(), e.getCurrency());
            if (!e.isLocked() && isHoldReleasedOrNone(e)) {
                availableBalance = availableBalance.add(eurValue);
            } else {
                pendingBalance = pendingBalance.add(eurValue);
            }
        }

        List<CreatorEarningDto> lastEarnings = allEarnings.stream()
                .limit(10)
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return CreatorEarningsOverviewDTO.builder()
                .totalEarnings(totalEarnings)
                .availableBalance(availableBalance)
                .pendingBalance(pendingBalance)
                .lastEarnings(lastEarnings)
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<CreatorEarningsResponseDTO> getBalance(User user) {
        return payoutCreatorEarningsRepository.findByCreator(user)
                .map(this::mapToBalanceDto);
    }

    private CreatorEarningsResponseDTO mapToBalanceDto(com.joinlivora.backend.payout.CreatorEarnings entity) {
        return CreatorEarningsResponseDTO.builder()
                .id(entity.getId())
                .availableBalance(entity.getAvailableBalance())
                .pendingBalance(entity.getPendingBalance())
                .totalEarned(entity.getTotalEarned())
                .payoutsDisabled(entity.isPayoutsDisabled())
                .build();
    }

    public CreatorEarningDto mapToDto(CreatorEarning entity) {
        return CreatorEarningDto.builder()
                .id(entity.getId())
                .grossAmount(entity.getGrossAmount())
                .platformFee(entity.getPlatformFee())
                .netAmount(entity.getNetAmount())
                .currency(entity.getCurrency())
                .sourceType(entity.getSourceType())
                .stripeChargeId(entity.getStripeChargeId())
                .locked(entity.isLocked())
                .createdAt(entity.getCreatedAt())
                .status(entity.isLocked() ? "LOCKED" : "AVAILABLE")
                .supporterName(entity.getUser() != null ? 
                        (entity.getUser().getUsername() != null ? entity.getUser().getUsername() : entity.getUser().getEmail().split("@")[0]) : 
                        "Anonymous")
                .build();
    }

    public BigDecimal convertToEur(BigDecimal amount, String currency) {
        if ("EUR".equalsIgnoreCase(currency)) {
            return amount;
        }
        if ("TOKEN".equalsIgnoreCase(currency)) {
            return amount.multiply(TOKEN_TO_EUR_RATE);
        }
        return amount;
    }

    private boolean isHoldReleasedOrNone(CreatorEarning earning) {
        if (earning.getPayoutHold() == null) {
            return true;
        }
        return earning.getPayoutHold().getStatus() == PayoutHoldStatus.RELEASED;
    }

    @Transactional
    public void creditPayoutEarnings(User creator, BigDecimal netAmount, boolean isLocked) {
        com.joinlivora.backend.payout.CreatorEarnings earnings = payoutCreatorEarningsRepository.findByUserWithLock(creator)
                .orElse(com.joinlivora.backend.payout.CreatorEarnings.builder()
                        .creator(creator)
                        .availableBalance(BigDecimal.ZERO)
                        .pendingBalance(BigDecimal.ZERO)
                        .totalEarned(BigDecimal.ZERO)
                        .build());

        earnings.setTotalEarned(earnings.getTotalEarned().add(netAmount));
        if (isLocked) {
            earnings.setPendingBalance(earnings.getPendingBalance().add(netAmount));
        } else {
            earnings.setAvailableBalance(earnings.getAvailableBalance().add(netAmount));
        }
        payoutCreatorEarningsRepository.save(earnings);
    }

    public void updatePlatformBalances(BigDecimal fee, BigDecimal creatorEarning, String currency) {
        BigDecimal feeInEur = convertToEur(fee, currency);
        BigDecimal creatorEarningInEur = convertToEur(creatorEarning, currency);
        
        PlatformBalance balance = platformBalanceRepository.findSingleWithLock()
                .orElse(PlatformBalance.builder()
                        .totalFeesCollected(BigDecimal.ZERO)
                        .totalCreatorEarnings(BigDecimal.ZERO)
                        .availableBalance(BigDecimal.ZERO)
                        .updatedAt(Instant.now())
                        .build());

        balance.setTotalFeesCollected(balance.getTotalFeesCollected().add(feeInEur));
        balance.setTotalCreatorEarnings(balance.getTotalCreatorEarnings().add(creatorEarningInEur));
        balance.setAvailableBalance(balance.getAvailableBalance().add(feeInEur));
        balance.setUpdatedAt(Instant.now());
        
        platformBalanceRepository.save(balance);
        log.info("MONETIZATION: Updated platform balances. Fee: {} EUR, Creator Earning: {} EUR. Total fees: {}, Total creator earnings: {}", 
                feeInEur, creatorEarningInEur, balance.getTotalFeesCollected(), balance.getTotalCreatorEarnings());
    }
}
