package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.model.FraudSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class FraudSignalRepositoryTest {

    @Autowired
    private RuleFraudSignalRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void testCountByUserIdAndCreatedAtAfter() {
        Long userId = 1L;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.MEDIUM)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.AUTOMATIC_ESCALATION)
                .reason("Test 1")
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.HIGH)
                .source(FraudSource.PAYMENT)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.PAYMENT_FAILURE)
                .reason("Test 2")
                .createdAt(now.minus(30, ChronoUnit.MINUTES))
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.HIGH)
                .source(FraudSource.LOGIN)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.IP_MISMATCH)
                .reason("Test 3")
                .createdAt(now)
                .build());

        long count = repository.countByUserIdAndCreatedAtAfter(userId, hourAgo);
        assertEquals(2, count);
    }

    @Test
    void testFindTop10ByUserIdOrderByCreatedAtDesc() {
        Long userId = 2L;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        for (int i = 0; i < 15; i++) {
            repository.save(RuleFraudSignal.builder()
                    .userId(userId)
                    .riskLevel(FraudDecisionLevel.LOW)
                    .source(FraudSource.SYSTEM)
                    .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                    .reason("Reason " + i)
                    .createdAt(now.minus(i, ChronoUnit.MINUTES))
                    .build());
        }

        List<RuleFraudSignal> top10 = repository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
        assertEquals(10, top10.size());
        assertEquals("Reason 0", top10.get(0).getReason()); // Most recent (i=0)
        assertEquals("Reason 9", top10.get(9).getReason()); // 10th most recent (i=9)
    }
    @Test
    void testCountByUserIdAndRiskLevelAndCreatedAtAfter() {
        Long userId = 3L;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant dayAgo = now.minus(24, ChronoUnit.HOURS);

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.MEDIUM)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("M1")
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.MEDIUM)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("M2")
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.HIGH)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("H1")
                .createdAt(now.minus(3, ChronoUnit.HOURS))
                .build());

        long count = repository.countByUserIdAndRiskLevelAndCreatedAtAfter(userId, FraudDecisionLevel.MEDIUM, dayAgo);
        assertEquals(2, count);
    }

    @Test
    void testFindAllByUserId() {
        Long userId = 4L;
        Long otherUserId = 5L;

        repository.save(RuleFraudSignal.builder()
                .userId(userId)
                .riskLevel(FraudDecisionLevel.LOW)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("U1")
                .createdAt(Instant.now())
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(otherUserId)
                .riskLevel(FraudDecisionLevel.LOW)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("Other")
                .createdAt(Instant.now())
                .build());

        Page<RuleFraudSignal> page = repository.findAllByUserId(userId, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("U1", page.getContent().get(0).getReason());
    }

    @Test
    void testFindAllByReasonContaining() {
        String ip = "1.2.3.4";
        repository.save(RuleFraudSignal.builder()
                .userId(10L)
                .riskLevel(FraudDecisionLevel.MEDIUM)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_IP)
                .reason("New IP detected: " + ip)
                .createdAt(Instant.now())
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(11L)
                .riskLevel(FraudDecisionLevel.LOW)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_IP)
                .reason("New IP detected: 5.6.7.8")
                .createdAt(Instant.now())
                .build());

        List<RuleFraudSignal> signals = repository.findAllByReasonContaining(ip);
        assertEquals(1, signals.size());
        assertEquals("New IP detected: 1.2.3.4", signals.get(0).getReason());
    }

    @Test
    void testCountUnresolvedByRiskLevel() {
        repository.save(RuleFraudSignal.builder()
                .userId(1L)
                .riskLevel(FraudDecisionLevel.HIGH)
                .resolved(false)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("H1")
                .createdAt(Instant.now())
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(2L)
                .riskLevel(FraudDecisionLevel.HIGH)
                .resolved(false)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("H2")
                .createdAt(Instant.now())
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(3L)
                .riskLevel(FraudDecisionLevel.MEDIUM)
                .resolved(false)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("M1")
                .createdAt(Instant.now())
                .build());

        repository.save(RuleFraudSignal.builder()
                .userId(4L)
                .riskLevel(FraudDecisionLevel.LOW)
                .resolved(true)
                .source(FraudSource.SYSTEM)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .reason("L1_RESOLVED")
                .createdAt(Instant.now())
                .build());

        Map<RiskLevel, Long> counts = repository.countUnresolvedByRiskLevel();
        
        assertEquals(2L, counts.get(RiskLevel.HIGH));
        assertEquals(1L, counts.get(RiskLevel.MEDIUM));
        assertEquals(null, counts.get(RiskLevel.LOW));
    }
}









