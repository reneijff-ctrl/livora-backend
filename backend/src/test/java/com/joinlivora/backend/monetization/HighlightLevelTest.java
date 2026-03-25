package com.joinlivora.backend.monetization;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class HighlightLevelTest {

    @Test
    void fromAmount_ShouldReturnCorrectLevel() {
        assertNull(HighlightLevel.fromAmount(new BigDecimal("5.00")));
        assertEquals(HighlightLevel.BASIC, HighlightLevel.fromAmount(new BigDecimal("10.00")));
        assertEquals(HighlightLevel.BASIC, HighlightLevel.fromAmount(new BigDecimal("25.00")));
        assertEquals(HighlightLevel.PREMIUM, HighlightLevel.fromAmount(new BigDecimal("50.00")));
        assertEquals(HighlightLevel.PREMIUM, HighlightLevel.fromAmount(new BigDecimal("75.00")));
        assertEquals(HighlightLevel.ULTRA, HighlightLevel.fromAmount(new BigDecimal("100.00")));
        assertEquals(HighlightLevel.ULTRA, HighlightLevel.fromAmount(new BigDecimal("500.00")));
    }

    @Test
    void enumProperties_ShouldBeCorrect() {
        assertEquals("#FFFFFF", HighlightLevel.BASIC.getHighlightColor());
        assertEquals(30, HighlightLevel.BASIC.getDisplayDurationSeconds());
        
        assertEquals("#FFD700", HighlightLevel.PREMIUM.getHighlightColor());
        assertEquals(60, HighlightLevel.PREMIUM.getDisplayDurationSeconds());
        
        assertEquals("#FF4500", HighlightLevel.ULTRA.getHighlightColor());
        assertEquals(120, HighlightLevel.ULTRA.getDisplayDurationSeconds());
    }

    @Test
    void fromAmount_WithNull_ShouldReturnNull() {
        assertNull(HighlightLevel.fromAmount(null));
    }

    @Test
    void qualifies_ShouldReturnCorrectResult() {
        assertTrue(HighlightLevel.BASIC.qualifies(new BigDecimal("10.00")));
        assertTrue(HighlightLevel.BASIC.qualifies(new BigDecimal("100.00")));
        assertFalse(HighlightLevel.BASIC.qualifies(new BigDecimal("5.00")));
        assertFalse(HighlightLevel.BASIC.qualifies(null));

        assertTrue(HighlightLevel.ULTRA.qualifies(new BigDecimal("100.00")));
        assertFalse(HighlightLevel.ULTRA.qualifies(new BigDecimal("99.99")));
    }
}








