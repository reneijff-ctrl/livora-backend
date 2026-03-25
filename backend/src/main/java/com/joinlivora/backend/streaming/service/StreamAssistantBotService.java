package com.joinlivora.backend.streaming.service;

import java.util.UUID;

/**
 * Service that triggers automated responses for stream events like tips, positive messages, and user joins.
 * Designed to encourage engagement and recognize user contributions.
 */
public interface StreamAssistantBotService {
    
    /**
     * Triggered when a user sends a tip.
     */
    void onTipReceived(Long creatorId, String donorName, double amount, String currency);
    
    /**
     * Triggered when a positive or highlighted message is detected.
     */
    void onPositiveMessage(Long creatorId, String senderName, String message);
    
    /**
     * Triggered when a new user joins the stream.
     */
    void onUserJoined(Long creatorId, String userName);
    /**
     * Triggered when a message is received (for tracking frequency and triggers).
     */
    void onMessageReceived(Long creatorId);

    /**
     * Triggered when a user uses the /tipmenu command.
     */
    void onTipMenuUsed(Long creatorId, String username);

    /**
     * Triggered when a tip goal reaches a milestone percentage (25, 50, 75, 90).
     */
    void onGoalProgress(Long creatorId, String title, long current, long target, int percentage);

    /**
     * Triggered when a tip goal is fully completed.
     */
    void onGoalCompleted(Long creatorId, String title);

    /**
     * Triggered when a tip action is matched and triggered by a viewer's tip.
     */
    void onActionTriggered(Long creatorId, String donorName, long amount, String description);

    /**
     * Triggered when a creator's stream goes live.
     */
    void onStreamStarted(Long creatorId);

    /**
     * Triggered when a milestone within a goal group is reached.
     */
    void onMilestoneReached(Long creatorId, String milestoneTitle);

    /**
     * Triggered when a milestone is almost reached (within 100 tokens).
     */
    void onMilestoneAlmostReached(Long creatorId, String milestoneTitle, long remaining);

    /**
     * Triggered when a creator's stream ends.
     * Implementations must clean up all in-memory state for this creator to prevent memory leaks.
     */
    void onStreamEnded(Long creatorId);

    /**
     * Triggered when a viewer newly follows the creator during a live session.
     */
    void onNewFollow(Long creatorId, String followerUsername);
}
