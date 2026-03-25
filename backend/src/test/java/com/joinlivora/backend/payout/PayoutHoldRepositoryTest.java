package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PayoutHoldRepositoryTest {

    @Autowired
    private PayoutHoldRepository repository;

    @Test
    void testSaveAndFind() {
        UUID userId = UUID.randomUUID();
        Instant holdUntil = Instant.now().plus(14, ChronoUnit.DAYS);
        
        PayoutHold hold = PayoutHold.builder()
                .userId(userId)
                .riskLevel(RiskLevel.HIGH)
                .holdUntil(holdUntil)
                .status(PayoutHoldStatus.ACTIVE)
                .reason("High fraud risk")
                .build();

        PayoutHold saved = repository.save(hold);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        List<PayoutHold> found = repository.findAllByUserIdOrderByCreatedAtDesc(userId);
        assertEquals(1, found.size());
        assertEquals(RiskLevel.HIGH, found.get(0).getRiskLevel());
        assertEquals(PayoutHoldStatus.ACTIVE, found.get(0).getStatus());
        assertEquals("High fraud risk", found.get(0).getReason());
        assertEquals(holdUntil.truncatedTo(ChronoUnit.MILLIS), found.get(0).getHoldUntil().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void testFindMultipleOrderedByCreatedAt() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        
        repository.save(PayoutHold.builder()
                .userId(userId)
                .riskLevel(RiskLevel.LOW)
                .holdUntil(Instant.now().plus(3, ChronoUnit.DAYS))
                .status(PayoutHoldStatus.RELEASED)
                .build());
        
        Thread.sleep(10); // Ensure different createdAt
        
        repository.save(PayoutHold.builder()
                .userId(userId)
                .riskLevel(RiskLevel.MEDIUM)
                .holdUntil(Instant.now().plus(7, ChronoUnit.DAYS))
                .status(PayoutHoldStatus.ACTIVE)
                .build());

        List<PayoutHold> found = repository.findAllByUserIdOrderByCreatedAtDesc(userId);
        assertEquals(2, found.size());
        assertEquals(RiskLevel.MEDIUM, found.get(0).getRiskLevel());
        assertEquals(RiskLevel.LOW, found.get(1).getRiskLevel());
    }
}








