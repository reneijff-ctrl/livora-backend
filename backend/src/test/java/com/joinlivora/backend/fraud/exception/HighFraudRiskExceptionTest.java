package com.joinlivora.backend.fraud.exception;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HighFraudRiskExceptionTest {

    @Test
    void exception_ShouldHoldScoreAndReasonsAndSafeMessage() {
        int score = 85;
        List<String> reasons = List.of("High velocity", "New account");
        
        HighFraudRiskException ex = new HighFraudRiskException(score, reasons);
        
        assertEquals(score, ex.getScore());
        assertEquals(reasons, ex.getReasons());
        assertEquals("Transaction blocked for security review", ex.getMessage());
    }
}










