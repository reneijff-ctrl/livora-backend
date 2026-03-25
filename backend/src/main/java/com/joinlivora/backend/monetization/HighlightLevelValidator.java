package com.joinlivora.backend.monetization;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Component("highlightLevelValidator")
@Slf4j
public class HighlightLevelValidator {

    @PostConstruct
    public void validateOnStartup() {
        validate(HighlightLevel.values());
    }

    public void validate(HighlightLevel[] levels) {
        log.info("Validating HighlightLevel configuration...");
        
        if (levels == null || levels.length == 0) {
            throw new IllegalStateException("No HighlightLevels defined!");
        }

        Set<BigDecimal> amounts = new HashSet<>();
        
        for (HighlightLevel level : levels) {
            // 1. Minimum amount must be positive
            if (level.getMinimumAmount() == null || level.getMinimumAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("HighlightLevel " + level.name() + " has invalid minimum amount: " + level.getMinimumAmount());
            }

            // 2. Duration must be positive
            if (level.getDisplayDurationSeconds() <= 0) {
                throw new IllegalStateException("HighlightLevel " + level.name() + " has invalid duration: " + level.getDisplayDurationSeconds());
            }

            // 3. Color must not be blank
            if (level.getHighlightColor() == null || level.getHighlightColor().isBlank()) {
                throw new IllegalStateException("HighlightLevel " + level.name() + " has blank highlight color");
            }

            // 4. Ensure unique minimum amounts
            if (!amounts.add(level.getMinimumAmount())) {
                throw new IllegalStateException("Duplicate minimum amount found for HighlightLevel: " + level.getMinimumAmount());
            }
        }
        
        log.info("HighlightLevel configuration is valid.");
    }
}
