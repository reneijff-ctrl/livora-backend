package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.analysis.SentimentAnalyzer;
import com.joinlivora.backend.chat.analysis.SentimentResult;
import com.joinlivora.backend.chat.analysis.ToxicityAnalyzer;
import com.joinlivora.backend.chat.dto.ModerateResult;
import com.joinlivora.backend.chat.dto.ModerationSeverity;
import com.joinlivora.backend.streaming.ModerationSettings;
import com.joinlivora.backend.streaming.ModerationSettingsRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.ChatMessage;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatModerationService {

    private final ModerationRepository moderationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ToxicityAnalyzer toxicityAnalyzer;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final ModerationSettingsRepository settingsRepository;
    private final com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepository;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;
    private final com.joinlivora.backend.moderation.service.AIModerationEngineService aiModerationEngineService;

    @Value("${livora.chat.moderation.banned-words:}")
    private List<String> bannedWords;
    @Value("${livora.chat.moderation.caps-threshold:0.7}")
    private double capsThreshold;
    @Value("${livora.chat.moderation.repeated-chars-threshold:6}")
    private int repeatedCharsThreshold;
    @Value("${livora.chat.moderation.duplicate-window-seconds:30}")
    private int duplicateWindowSeconds;
    @Value("${livora.chat.moderation.duplicate-threshold:3}")
    private int duplicateThreshold;

    public ChatModerationService(
            ModerationRepository moderationRepository,
            UserRepository userRepository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            StringRedisTemplate redisTemplate,
            ToxicityAnalyzer toxicityAnalyzer,
            SentimentAnalyzer sentimentAnalyzer,
            ModerationSettingsRepository settingsRepository,
            @org.springframework.beans.factory.annotation.Qualifier("chatRoomRepositoryV2") com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepository,
            com.joinlivora.backend.streaming.StreamRepository streamRepository,
            com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService,
            com.joinlivora.backend.moderation.service.AIModerationEngineService aiModerationEngineService) {
        this.moderationRepository = moderationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.toxicityAnalyzer = toxicityAnalyzer;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.settingsRepository = settingsRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.streamRepository = streamRepository;
        this.adminRealtimeEventService = adminRealtimeEventService;
        this.aiModerationEngineService = aiModerationEngineService;
    }

    /**
     * Moderates a chat message according to automatic rules.
     * 
     * @param message The message content to moderate
     * @param userId The ID of the user sending the message (null for system messages)
     * @return A ModerateResult indicating if the message is allowed and why
     */
    public ModerateResult moderate(String message, Long userId, Long creatorId) {
        // Requirements: Do NOT block system messages
        if (userId == null) {
            return ModerateResult.allowed();
        }

        if (message == null || message.trim().isEmpty()) {
            return ModerateResult.allowed();
        }

        // Requirements: Do NOT block tip messages (caller responsibility or detection)
        // Note: For now we assume the caller handles this or we check if userId is valid.
        // If we want to detect tips here, we'd need more context.

        String content = message.trim();

        // Load creator settings if applicable
        ModerationSettings creatorSettings = null;
        if (creatorId != null) {
            creatorSettings = settingsRepository.findByCreatorUserId(creatorId).orElse(null);
        }

        // 1. Excessive caps
        double capsLimit = (creatorSettings != null && creatorSettings.isStrictMode()) ? 0.4 : capsThreshold;
        if (checkExcessiveCaps(content, capsLimit)) {
            reportSpam(userId, creatorId);
            return ModerateResult.blocked("Excessive caps usage is not allowed", ModerationSeverity.LOW);
        }

        // 2. Repeated characters
        int repeatLimit = (creatorSettings != null && creatorSettings.isStrictMode()) ? 4 : repeatedCharsThreshold;
        if (checkRepeatedChars(content, repeatLimit)) {
            reportSpam(userId, creatorId);
            return ModerateResult.blocked("Too many repeated characters", ModerationSeverity.LOW);
        }

        // 3. Links (http, www)
        if (checkLinks(content)) {
            return ModerateResult.blocked("Sharing links is not permitted in chat", ModerationSeverity.MEDIUM);
        }

        // 4. Banned words (Global)
        if (checkBannedWords(content)) {
            return ModerateResult.blocked("Message contains prohibited content", ModerationSeverity.HIGH);
        }

        // 4b. Banned words (Creator specific) — load via Redis cache moderation:{creatorId}
        if (creatorId != null) {
            List<String> creatorBanned = getCreatorBannedWordsCached(creatorId, creatorSettings);
            if (checkCreatorBannedWords(content, creatorBanned)) {
                return ModerateResult.blocked("Message contains content prohibited by the creator", ModerationSeverity.HIGH);
            }
        }

        // 5. Duplicate message spam (same message 3x in 30 sec)
        if (checkDuplicateSpam(userId, content)) {
            reportSpam(userId, creatorId);
            return ModerateResult.blocked("Please avoid sending the same message repeatedly", ModerationSeverity.MEDIUM);
        }

        // 6. Sentiment & Toxicity analysis
        SentimentResult sentiment = sentimentAnalyzer.analyze(content);
        if (sentiment.isToxic()) {
            return ModerateResult.blocked("Message contains toxic content", ModerationSeverity.HIGH);
        }

        // Optional: Block extremely negative sentiment (threshold -0.8)
        if (sentiment.getScore() <= -0.9) {
            return ModerateResult.blocked("Message is too negative or hostile", ModerationSeverity.MEDIUM);
        }

        return ModerateResult.allowed(sentiment.isPositive());
    }

    private boolean checkExcessiveCaps(String message, double threshold) {
        if (message.length() < 5) return false;
        long upperCount = message.chars().filter(Character::isUpperCase).count();
        long letterCount = message.chars().filter(Character::isLetter).count();
        if (letterCount < 3) return false;
        return (double) upperCount / letterCount > threshold;
    }

    private boolean checkRepeatedChars(String message, int threshold) {
        String regex = "(.)\\1{" + (threshold - 1) + ",}";
        return Pattern.compile(regex).matcher(message).find();
    }

    private boolean checkLinks(String message) {
        String lower = message.toLowerCase();
        return lower.contains("http://") || lower.contains("https://") || lower.contains("www.");
    }

    private boolean checkBannedWords(String message) {
        if (bannedWords == null || bannedWords.isEmpty()) return false;
        String lower = message.toLowerCase();
        for (String word : bannedWords) {
            if (lower.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkCreatorBannedWords(String message, List<String> customBannedWords) {
        if (customBannedWords == null || customBannedWords.isEmpty()) return false;
        String lower = message.toLowerCase();
        for (String word : customBannedWords) {
            if (word == null || word.isBlank()) continue;
            if (lower.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // Cache creator banned words in Redis under key moderation:{creatorId}
    private List<String> getCreatorBannedWordsCached(Long creatorId, ModerationSettings preloadedSettings) {
        try {
            String key = "moderation:" + creatorId;
            var ops = redisTemplate.opsForValue();
            String cached = ops.get(key);
            if (cached != null) {
                return Arrays.stream(cached.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList());
            }
            // Cache miss: load from preloaded settings or repository
            String wordsStr;
            if (preloadedSettings != null) {
                wordsStr = preloadedSettings.getBannedWords();
            } else {
                wordsStr = settingsRepository.findByCreatorUserId(creatorId)
                        .map(ModerationSettings::getBannedWords)
                        .orElse("");
            }

            List<String> words = wordsStr == null ? List.of() : Arrays.stream(wordsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());

            // Store in Redis for faster future access (1 hour TTL)
            String serialized = String.join("\n", words);
            try {
                ops.set(key, serialized, 1, TimeUnit.HOURS);
            } catch (Exception ignore) {
                // fail open on cache set errors
            }
            return words;
        } catch (Exception e) {
            // Fail open on Redis errors, fall back to DB
            try {
                String wordsStr = (preloadedSettings != null)
                        ? preloadedSettings.getBannedWords()
                        : settingsRepository.findByCreatorUserId(creatorId)
                            .map(ModerationSettings::getBannedWords)
                            .orElse("");
                
                return wordsStr == null ? List.of() : Arrays.stream(wordsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList());
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    private void reportSpam(Long userId, Long creatorId) {
        if (userId == null || creatorId == null) return;
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                // Try to find active streamId for this creator
                java.util.List<com.joinlivora.backend.streaming.Stream> liveStreams = streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorId);
                UUID streamId = liveStreams.isEmpty() ? null : liveStreams.get(0).getId();
                if (streamId != null) {
                    adminRealtimeEventService.broadcastChatSpamDetected(streamId, user.getUsername());
                    aiModerationEngineService.evaluateStreamRisk(
                        streamId,
                        0,
                        0,
                        25,
                        0
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to report chat spam: {}", e.getMessage());
        }
    }

    private boolean checkDuplicateSpam(Long userId, String message) {
        try {
            String hash = hashMessage(message);
            String key = "chat:spam:" + userId + ":" + hash;
            
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, duplicateWindowSeconds, TimeUnit.SECONDS);
            }
            
            return count != null && count >= duplicateThreshold;
        } catch (Exception e) {
            log.error("Error checking duplicate spam", e);
            return false; // Fail open for moderation to avoid blocking everything on Redis failure
        }
    }

    private String hashMessage(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(message.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(message.toLowerCase().trim().hashCode());
        }
    }

    @Transactional
    public void deleteMessage(String roomId, String messageId, Long moderatorId) {
        log.info("Moderation: Deleting message {} in room {} by moderator {}", messageId, roomId, moderatorId);
        
        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new RuntimeException("Moderator not found"));

        Moderation moderation = Moderation.builder()
                .action(ModerationAction.DELETE_MESSAGE)
                .moderator(moderator)
                .roomId(roomId)
                .messageId(messageId)
                .build();
        
        moderationRepository.save(moderation);

        RealtimeMessage deleteEvent = RealtimeMessage.builder()
                .type("MESSAGE_DELETED")
                .timestamp(Instant.now())
                .payload(Map.of("messageId", messageId))
                .build();
        
        Long creatorId = resolveCreatorId(roomId);
        if (creatorId != null) {
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, deleteEvent);
        } else {
            log.warn("Moderation: Could not resolve creatorId for delete event in room {}", roomId);
        }
    }

    @Transactional
    public void muteUser(Long targetUserId, Long moderatorId, Duration duration, String roomId) {
        log.info("Moderation: Muting creator {} for {} in room {} by moderator {}", targetUserId, duration, roomId, moderatorId);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target creator not found"));
        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new RuntimeException("Moderator not found"));
        
        Moderation moderation = Moderation.builder()
                .action(ModerationAction.MUTE)
                .targetUser(targetUser)
                .moderator(moderator)
                .roomId(roomId)
                .expiresAt(Instant.now().plus(duration))
                .build();
        
        moderationRepository.save(moderation);

        broadcastSystemMessageToRoom(roomId, "User muted: " + targetUser.getUsername(), targetUser);
    }

    @Transactional
    public void shadowMuteUser(Long targetUserId, Long moderatorId, Duration duration, String roomId) {
        log.info("Moderation: Shadow-muting creator {} for {} in room {} by moderator {}", targetUserId, duration, roomId, moderatorId);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target creator not found"));
        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new RuntimeException("Moderator not found"));

        Moderation moderation = Moderation.builder()
                .action(ModerationAction.SHADOW_MUTE)
                .targetUser(targetUser)
                .moderator(moderator)
                .roomId(roomId)
                .expiresAt(Instant.now().plus(duration))
                .build();

        moderationRepository.save(moderation);
        // NO system message broadcast for shadow-mute to keep it stealthy
    }

    @Transactional
    public void banUser(Long targetUserId, Long moderatorId, String roomId) {
        log.info("Moderation: Banning creator {} in room {} by moderator {}", targetUserId, roomId, moderatorId);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target creator not found"));
        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new RuntimeException("Moderator not found"));
        
        if (isBanned(targetUserId, roomId)) {
            return;
        }

        Moderation moderation = Moderation.builder()
                .action(ModerationAction.BAN)
                .targetUser(targetUser)
                .moderator(moderator)
                .roomId(roomId)
                .build();
        
        moderationRepository.save(moderation);

        broadcastSystemMessageToRoom(roomId, "User banned: " + targetUser.getUsername(), targetUser);
    }

    private void broadcastSystemMessageToRoom(String roomId, String content, User targetUser) {
        Long creatorId = resolveCreatorId(roomId);
        if (creatorId == null) {
            log.warn("Moderation: Could not resolve creatorId for room {} - skipping broadcast", roomId);
            return;
        }

        ChatMessage systemMessage = ChatMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .content(content)
                .system(true)
                .timestamp(Instant.now())
                .build();
        
        RealtimeMessage realtimeMessage = RealtimeMessage.ofChat(systemMessage);
        
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, realtimeMessage);

        // Disconnect banned user if message is about a ban
        if (targetUser != null && content.startsWith("User banned:")) {
            RealtimeMessage disconnectMessage = RealtimeMessage.builder()
                    .type("DISCONNECT")
                    .payload(Map.of("type", "You have been banned from this room: " + roomId))
                    .timestamp(Instant.now())
                    .build();
            messagingTemplate.convertAndSendToUser(targetUser.getId().toString(), "/queue/notifications", disconnectMessage);
        }
    }

    private Long resolveCreatorId(String roomId) {
        if (roomId == null) return null;
        try {
            if (roomId.startsWith("stream-")) {
                UUID streamId = UUID.fromString(roomId.substring(7));
                // Resolve from unified Stream identity
                return streamRepository.findById(streamId)
                        .map(stream -> stream.getCreator().getId())
                        .orElseGet(() -> streamRepository.findByMediasoupRoomId(streamId)
                                .map(stream -> stream.getCreator().getId())
                                .orElse(null));
            } else {
                // Try as ChatRoom name (V2)
                return chatRoomRepository.findByName(roomId)
                        .map(com.joinlivora.backend.chat.domain.ChatRoom::getCreatorId)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.error("Failed to resolve creatorId from roomId: {}", roomId);
            return null;
        }
    }

    public boolean isMuted(Long userId, String roomId) {
        if (userId == null) return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        
        // Bypass for Admin
        if (user.getRole() == Role.ADMIN) {
            return false;
        }
        
        // Check for active mute
        return moderationRepository.findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(
                user, ModerationAction.MUTE, Instant.now()).isPresent();
    }

    public boolean isShadowMuted(Long userId, String roomId) {
        if (userId == null) return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;

        // Bypass for Admin
        if (user.getRole() == Role.ADMIN) {
            return false;
        }

        // Global shadowban
        if (user.isShadowbanned()) {
            return true;
        }

        // Room-specific or active shadow mute
        return moderationRepository.findTopByTargetUserAndActionAndExpiresAtAfterOrderByCreatedAtDesc(
                user, ModerationAction.SHADOW_MUTE, Instant.now()).isPresent();
    }

    public boolean isBanned(Long userId, String roomId) {
        if (userId == null || roomId == null) return false;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;

        // Bypass for Admin
        if (user.getRole() == Role.ADMIN) {
            return false;
        }

        return moderationRepository.existsByTargetUserAndActionAndRoomId(user, ModerationAction.BAN, roomId);
    }

    public void invalidateCreatorCache(Long creatorId) {
        try {
            redisTemplate.delete("moderation:" + creatorId);
        } catch (Exception e) {
            log.error("Failed to invalidate creator cache for {}", creatorId, e);
        }
    }
}
