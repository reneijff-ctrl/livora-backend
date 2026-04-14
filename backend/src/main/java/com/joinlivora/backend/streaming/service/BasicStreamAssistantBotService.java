package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.monetization.TipGoal;
import com.joinlivora.backend.monetization.TipGoalService;
import com.joinlivora.backend.monetization.TipActionService;
import com.joinlivora.backend.monetization.TipAction;
import com.joinlivora.backend.monetization.TipMenuCategoryService;
import com.joinlivora.backend.privateshow.CreatorPrivateSettings;
import com.joinlivora.backend.privateshow.CreatorPrivateSettingsService;
import com.joinlivora.backend.privateshow.PrivateSessionRepository;
import com.joinlivora.backend.privateshow.PrivateSessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Basic implementation of StreamAssistantBotService.
 * Provides generic, encouraging responses for stream events.
 */
@Service
@Slf4j
public class BasicStreamAssistantBotService implements StreamAssistantBotService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CreatorRepository creatorRepository;
    private final TipGoalService tipGoalService;
    private final TipActionService tipActionService;
    private final TipMenuCategoryService tipMenuCategoryService;
    private final CreatorPrivateSettingsService creatorPrivateSettingsService;
    private final PrivateSessionRepository privateSessionRepository;

    public BasicStreamAssistantBotService(
            SimpMessagingTemplate messagingTemplate,
            CreatorRepository creatorRepository,
            @Lazy TipGoalService tipGoalService,
            @Lazy TipActionService tipActionService,
            TipMenuCategoryService tipMenuCategoryService,
            CreatorPrivateSettingsService creatorPrivateSettingsService,
            PrivateSessionRepository privateSessionRepository) {
        this.messagingTemplate = messagingTemplate;
        this.creatorRepository = creatorRepository;
        this.tipGoalService = tipGoalService;
        this.tipActionService = tipActionService;
        this.tipMenuCategoryService = tipMenuCategoryService;
        this.creatorPrivateSettingsService = creatorPrivateSettingsService;
        this.privateSessionRepository = privateSessionRepository;
    }

    private final Map<Long, Integer> messageCounts = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastReminderTimes = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastThankYouTimes = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, UserState>> perCreatorUserStates = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> announcedMilestones = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastActionTriggeredTimes = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastPrivateShowPromoTimes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final long THANK_YOU_COOLDOWN_SECONDS = 30;
    private static final long ACTION_TRIGGERED_COOLDOWN_SECONDS = 10;
    private static final long PRIVATE_SHOW_PROMO_COOLDOWN_SECONDS = 600; // 10 minutes
    private static final Set<Integer> GOAL_MILESTONES = Set.of(25, 50, 75, 90);

    private static final List<String> PRIVATE_SHOW_PROMO_TEMPLATES = List.of(
            "Private shows are open — %d tokens/min \uD83D\uDC9C",
            "Want me all to yourself? Private shows are available now — %d tokens/min ✨",
            "You can request a private session anytime — %d tokens/min \uD83D\uDD12",
            "Feeling bold? Ask for a private — %d tokens/min \uD83D\uDE18",
            "Privates are active and available — %d tokens/min \uD83D\uDC8B"
    );

    private static class UserState {
        final AtomicInteger tipCount = new AtomicInteger(0);
        final AtomicBoolean usedTipMenu = new AtomicBoolean(false);
        final AtomicReference<Instant> lastReminderTimestamp = new AtomicReference<>(Instant.MIN);
    }

    @Override
    public void onMessageReceived(Long creatorId) {
        // 1. Increment message count for this creator
        int count = messageCounts.merge(creatorId, 1, Integer::sum);

        // 2. Check for 60-second cooldown
        Instant lastReminder = lastReminderTimes.get(creatorId);
        if (lastReminder != null && Duration.between(lastReminder, Instant.now()).getSeconds() < 60) {
            return;
        }

        // 3. Trigger condition: Every 5 messages OR 10% random chance
        boolean trigger = (count >= 5) || (random.nextDouble() < 0.10);

        if (trigger) {
            // Reset count
            messageCounts.put(creatorId, 0);
            // Update last reminder time
            lastReminderTimes.put(creatorId, Instant.now());

            String creatorUsername = creatorRepository.findUsernameByUserId(creatorId)
                    .orElse("Creator");

            if (!tipMenuCategoryService.getEnabledCategories(creatorId).isEmpty()) {
                String reminderMessage = "💎 Tip Menu Available! Type /tipmenu to see all actions.";
                broadcastBotMessage(creatorId, creatorUsername, reminderMessage);
            }
        }

        // 4. Private show promo (independent cooldown)
        maybePromotePrivateShow(creatorId);
    }

    @Override
    public void onTipMenuUsed(Long creatorId, String username) {
        if (username == null) return;
        perCreatorUserStates.computeIfAbsent(creatorId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(username, k -> new UserState())
                .usedTipMenu.set(true);
    }

    @Override
    public void onTipReceived(Long creatorId, String donorName, double amount, String currency) {

        String creatorUsername = creatorRepository.findUsernameByUserId(creatorId)
                .orElse("Creator");

        // 1. Logic for contextual tipmenu reminder
        if (donorName != null && !"anonymous".equalsIgnoreCase(donorName)) {
            UserState userState = perCreatorUserStates.computeIfAbsent(creatorId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(donorName, k -> new UserState());

            int count = userState.tipCount.incrementAndGet();
            Instant now = Instant.now();

            if (count >= 2 && !userState.usedTipMenu.get()) {
                Instant last = userState.lastReminderTimestamp.get();
                if (Duration.between(last, now).getSeconds() >= 60) {
                    if (userState.lastReminderTimestamp.compareAndSet(last, now)) {
                        if (!tipMenuCategoryService.getEnabledCategories(creatorId).isEmpty()) {
                            String reminderMessage = "💎 Tip Menu Available! Type /tipmenu to see all actions.";
                            broadcastBotMessage(creatorId, creatorUsername, reminderMessage);
                        }
                    }
                }
            }
        }

        // 2. Format and broadcast tip message (with 30s cooldown)
        Instant lastThankYou = lastThankYouTimes.get(creatorId);
        Instant now = Instant.now();
        if (lastThankYou == null || Duration.between(lastThankYou, now).getSeconds() >= THANK_YOU_COOLDOWN_SECONDS) {
            String botMessage;

            if ("TOKEN".equalsIgnoreCase(currency) || "TOKENS".equalsIgnoreCase(currency)) {
                long tokenAmount = (long) amount;
                botMessage = String.format(
                        "🔥 Thank you %s for tipping %d tokens!",
                        donorName,
                        tokenAmount
                );
            } else {
                String currencySymbol = getCurrencySymbol(currency);
                botMessage = String.format(
                        java.util.Locale.US,
                        "🔥 Thank you %s for the %s%.2f tip!",
                        donorName,
                        currencySymbol,
                        amount
                );
            }

            broadcastBotMessage(creatorId, creatorUsername, botMessage);
            lastThankYouTimes.put(creatorId, now);
        }
    }

    @Override
    public void onPositiveMessage(Long creatorId, String senderName, String message) {
        String creatorUsername = creatorRepository.findUsernameByUserId(creatorId)
                .orElse("Creator");
        String botMessage = String.format("Love the energy, %s! Thanks for the positive vibes! ✨ Keep it up, everyone!", senderName);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    @Override
    public void onUserJoined(Long creatorId, String userName) {
        if (userName == null || "anonymous".equalsIgnoreCase(userName)) {
            return;
        }
        String creatorUsername = creatorRepository.findUsernameByUserId(creatorId)
                .orElse("Creator");
        String botMessage = String.format("Welcome to the stream, %s! Happy to have you here! 👋 Don't forget to follow and engage!", userName);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    private void broadcastBotMessage(Long creatorId, String senderUsername, String message) {
        ChatMessageDto botDto = ChatMessageDto.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("BOT")
                .senderUsername(senderUsername)
                .sender(senderUsername) // Backward compatibility
                .content(message)
                .message(message) // Backward compatibility
                .timestamp(Instant.now())
                .systemMessage(false)
                .creatorUserId(creatorId)
                .build();

        log.info("Assistant Bot: Sending message to creator {}: {}", creatorId, message);
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, botDto);
    }

    @Override
    public void onGoalProgress(Long creatorId, String title, long current, long target, int percentage) {
        // Only announce at milestone percentages
        if (!GOAL_MILESTONES.contains(percentage)) {
            return;
        }

        Set<Integer> announced = announcedMilestones.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet());
        if (!announced.add(percentage)) {
            return; // Already announced this milestone for the current goal
        }

        String creatorUsername = resolveCreatorUsername(creatorId);
        long remaining = target - current;
        String botMessage = String.format("🎯 Goal '%s': %d/%d tokens — only %d left!", title, current, target, remaining);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    @Override
    public void onGoalCompleted(Long creatorId, String title) {
        // Reset milestones for the next goal
        announcedMilestones.remove(creatorId);

        String creatorUsername = resolveCreatorUsername(creatorId);
        String botMessage = String.format("🎉 GOAL REACHED! %s unlocked!", title);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    @Override
    public void onActionTriggered(Long creatorId, String donorName, long amount, String description) {
        Instant lastTriggered = lastActionTriggeredTimes.get(creatorId);
        Instant now = Instant.now();
        if (lastTriggered != null && Duration.between(lastTriggered, now).getSeconds() < ACTION_TRIGGERED_COOLDOWN_SECONDS) {
            return;
        }
        lastActionTriggeredTimes.put(creatorId, now);

        String creatorUsername = resolveCreatorUsername(creatorId);
        String botMessage = String.format("⚡ %s tipped %d tokens for: %s!", donorName, amount, description);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    @Override
    public void onStreamStarted(Long creatorId) {
        // Bot message on stream start is disabled — creator should not receive self-referential messages.
        // Only reset milestone tracking for the new stream session.
        announcedMilestones.remove(creatorId);
    }

    private String resolveCreatorUsername(Long creatorId) {
        return creatorRepository.findUsernameByUserId(creatorId)
                .orElse("Creator");
    }

    private String getCurrencySymbol(String currency) {
        if ("USD".equalsIgnoreCase(currency)) return "$";
        if ("EUR".equalsIgnoreCase(currency)) return "€";
        if ("GBP".equalsIgnoreCase(currency)) return "£";
        return currency; // do NOT default to $
    }

    private final Map<Long, Instant> lastMilestoneAlmostTimes = new ConcurrentHashMap<>();
    private static final long MILESTONE_ALMOST_COOLDOWN_SECONDS = 30;

    @Override
    public void onMilestoneReached(Long creatorId, String milestoneTitle) {
        String creatorUsername = resolveCreatorUsername(creatorId);
        String botMessage = String.format("🔥 %s unlocked!", milestoneTitle);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    @Override
    public void onMilestoneAlmostReached(Long creatorId, String milestoneTitle, long remaining) {
        Instant lastAlmost = lastMilestoneAlmostTimes.get(creatorId);
        Instant now = Instant.now();
        if (lastAlmost != null && Duration.between(lastAlmost, now).getSeconds() < MILESTONE_ALMOST_COOLDOWN_SECONDS) {
            return;
        }
        lastMilestoneAlmostTimes.put(creatorId, now);

        String creatorUsername = resolveCreatorUsername(creatorId);
        String botMessage = String.format("💎 Only %d tokens until %s!", remaining, milestoneTitle);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }

    @Override
    public void onStreamEnded(Long creatorId) {
        log.info("Assistant Bot: Cleaning up state for creator {}", creatorId);
        messageCounts.remove(creatorId);
        lastReminderTimes.remove(creatorId);
        lastThankYouTimes.remove(creatorId);
        perCreatorUserStates.remove(creatorId);
        announcedMilestones.remove(creatorId);
        lastActionTriggeredTimes.remove(creatorId);
        lastMilestoneAlmostTimes.remove(creatorId);
        lastPrivateShowPromoTimes.remove(creatorId);
    }

    private void maybePromotePrivateShow(Long creatorId) {
        try {
            // Check 10-minute cooldown
            Instant lastPromo = lastPrivateShowPromoTimes.get(creatorId);
            Instant now = Instant.now();
            if (lastPromo != null && Duration.between(lastPromo, now).getSeconds() < PRIVATE_SHOW_PROMO_COOLDOWN_SECONDS) {
                return;
            }

            // Check creator has private shows enabled with valid price
            CreatorPrivateSettings settings = creatorPrivateSettingsService.getOrCreate(creatorId);
            if (!settings.isEnabled() || settings.getPricePerMinute() <= 0) {
                return;
            }

            // Check no active private session running
            if (privateSessionRepository.existsByCreator_IdAndStatus(creatorId, PrivateSessionStatus.ACTIVE)) {
                return;
            }

            // Update cooldown and send promo
            lastPrivateShowPromoTimes.put(creatorId, now);

            String creatorUsername = resolveCreatorUsername(creatorId);
            String template = PRIVATE_SHOW_PROMO_TEMPLATES.get(random.nextInt(PRIVATE_SHOW_PROMO_TEMPLATES.size()));
            String promoMessage = String.format(template, settings.getPricePerMinute());

            // Append spy availability info if enabled
            if (settings.isAllowSpyOnPrivate() && settings.getSpyPricePerMinute() != null && settings.getSpyPricePerMinute() > 0) {
                promoMessage += String.format(" | Spy available — %d tokens/min 👁", settings.getSpyPricePerMinute());
            }

            broadcastBotMessage(creatorId, creatorUsername, promoMessage);
        } catch (Exception e) {
            log.error("Error sending private show promo for creator {}: {}", creatorId, e.getMessage());
        }
    }

    @Override
    public void onNewFollow(Long creatorId, String followerUsername) {
        if (followerUsername == null || followerUsername.isBlank()) {
            return;
        }
        String creatorUsername = resolveCreatorUsername(creatorId);
        String botMessage = String.format("Thank you for following, %s 💜", followerUsername);
        broadcastBotMessage(creatorId, creatorUsername, botMessage);
    }
}
