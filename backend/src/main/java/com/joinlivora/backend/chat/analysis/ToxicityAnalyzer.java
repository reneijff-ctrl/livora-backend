package com.joinlivora.backend.chat.analysis;

/**
 * Interface for toxicity analysis of chat messages.
 */
public interface ToxicityAnalyzer {
    /**
     * Checks if a message is toxic.
     * 
     * @param message The message to analyze
     * @return true if the message is toxic, false otherwise
     */
    boolean isToxic(String message);
}
