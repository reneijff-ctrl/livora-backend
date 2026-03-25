package com.joinlivora.backend.chat.analysis;

/**
 * Interface for sentiment analysis of chat messages.
 */
public interface SentimentAnalyzer {
    /**
     * Analyzes the sentiment of a message.
     * 
     * @param message The message to analyze
     * @return The sentiment result
     */
    SentimentResult analyze(String message);
}
