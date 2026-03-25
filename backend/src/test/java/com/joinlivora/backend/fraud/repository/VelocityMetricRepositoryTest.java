package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.model.VelocityMetric;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class VelocityMetricRepositoryTest {

    @Autowired
    private VelocityMetricRepository repository;

    @Test
    void testSaveAndFind() {
        Long userId = 1L;
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        VelocityMetric metric = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.LOGIN)
                .count(5)
                .windowStart(start)
                .windowEnd(end)
                .build();

        VelocityMetric saved = repository.save(metric);
        assertNotNull(saved.getId());

        Optional<VelocityMetric> found = repository.findByUserIdAndActionTypeAndWindowStartAndWindowEnd(
                userId, VelocityActionType.LOGIN, start, end);

        assertTrue(found.isPresent());
        assertEquals(5, found.get().getCount());
    }

    @Test
    void testUniqueConstraint() {
        Long userId = 1L;
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        VelocityMetric metric1 = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.PAYMENT)
                .count(1)
                .windowStart(start)
                .windowEnd(end)
                .build();
        repository.saveAndFlush(metric1);

        VelocityMetric metric2 = VelocityMetric.builder()
                .userId(userId)
                .actionType(VelocityActionType.PAYMENT)
                .count(2)
                .windowStart(start)
                .windowEnd(end)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(metric2);
        });
    }
}








