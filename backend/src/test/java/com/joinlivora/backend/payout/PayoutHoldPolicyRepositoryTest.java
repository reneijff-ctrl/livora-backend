package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
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
class PayoutHoldPolicyRepositoryTest {

    @Autowired
    private PayoutHoldPolicyRepository repository;

    @Test
    void testSaveAndFind() {
        UUID subjectId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        
        PayoutHoldPolicy policy = PayoutHoldPolicy.builder()
                .subjectType(RiskSubjectType.CREATOR)
                .subjectId(subjectId)
                .holdLevel(HoldLevel.MEDIUM)
                .holdDays(7)
                .reason("suspicious activity")
                .expiresAt(expiresAt)
                .build();

        PayoutHoldPolicy saved = repository.save(policy);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        List<PayoutHoldPolicy> found = repository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(subjectId, RiskSubjectType.CREATOR);
        assertEquals(1, found.size());
        assertEquals(HoldLevel.MEDIUM, found.get(0).getHoldLevel());
        assertEquals(7, found.get(0).getHoldDays());
        assertEquals("suspicious activity", found.get(0).getReason());
        assertEquals(expiresAt.truncatedTo(ChronoUnit.MILLIS), found.get(0).getExpiresAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void testFindMultipleOrderedByCreatedAt() throws InterruptedException {
        UUID subjectId = UUID.randomUUID();
        
        repository.save(PayoutHoldPolicy.builder()
                .subjectType(RiskSubjectType.USER)
                .subjectId(subjectId)
                .holdLevel(HoldLevel.SHORT)
                .holdDays(3)
                .build());
        
        Thread.sleep(10); // Ensure different createdAt
        
        repository.save(PayoutHoldPolicy.builder()
                .subjectType(RiskSubjectType.USER)
                .subjectId(subjectId)
                .holdLevel(HoldLevel.LONG)
                .holdDays(30)
                .build());

        List<PayoutHoldPolicy> found = repository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(subjectId, RiskSubjectType.USER);
        assertEquals(2, found.size());
        assertEquals(HoldLevel.LONG, found.get(0).getHoldLevel());
        assertEquals(HoldLevel.SHORT, found.get(1).getHoldLevel());
    }
}








