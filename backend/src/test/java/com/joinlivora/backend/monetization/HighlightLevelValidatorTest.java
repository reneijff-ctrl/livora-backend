package com.joinlivora.backend.monetization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HighlightLevelValidatorTest {

    private final HighlightLevelValidator validator = new HighlightLevelValidator();

    @Test
    void validate_CurrentConfig_ShouldSucceed() {
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    void validate_WithZeroAmount_ShouldFail() {
        HighlightLevel level = mock(HighlightLevel.class);
        when(level.getMinimumAmount()).thenReturn(BigDecimal.ZERO);
        when(level.name()).thenReturn("MOCK_LEVEL");

        assertThrows(IllegalStateException.class, () -> validator.validate(new HighlightLevel[]{level}));
    }

    @Test
    void validate_WithNegativeDuration_ShouldFail() {
        HighlightLevel level = mock(HighlightLevel.class);
        when(level.getMinimumAmount()).thenReturn(BigDecimal.TEN);
        when(level.getDisplayDurationSeconds()).thenReturn(-1);
        when(level.name()).thenReturn("MOCK_LEVEL");

        assertThrows(IllegalStateException.class, () -> validator.validate(new HighlightLevel[]{level}));
    }

    @Test
    void validate_WithBlankColor_ShouldFail() {
        HighlightLevel level = mock(HighlightLevel.class);
        when(level.getMinimumAmount()).thenReturn(BigDecimal.TEN);
        when(level.getDisplayDurationSeconds()).thenReturn(30);
        when(level.getHighlightColor()).thenReturn("");
        when(level.name()).thenReturn("MOCK_LEVEL");

        assertThrows(IllegalStateException.class, () -> validator.validate(new HighlightLevel[]{level}));
    }

    @Test
    void validate_WithDuplicateAmounts_ShouldFail() {
        HighlightLevel level1 = mock(HighlightLevel.class);
        when(level1.getMinimumAmount()).thenReturn(BigDecimal.TEN);
        when(level1.getDisplayDurationSeconds()).thenReturn(30);
        when(level1.getHighlightColor()).thenReturn("#FFFFFF");
        when(level1.name()).thenReturn("LEVEL1");

        HighlightLevel level2 = mock(HighlightLevel.class);
        when(level2.getMinimumAmount()).thenReturn(BigDecimal.TEN);
        when(level2.getDisplayDurationSeconds()).thenReturn(30);
        when(level2.getHighlightColor()).thenReturn("#000000");
        when(level2.name()).thenReturn("LEVEL2");

        assertThrows(IllegalStateException.class, () -> validator.validate(new HighlightLevel[]{level1, level2}));
    }
}








