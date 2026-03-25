package com.joinlivora.backend.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorAnalyticsRepositoryTest {

    @Autowired
    private CreatorAnalyticsRepository repository;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        CreatorAnalytics analytics = CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(today)
                .totalViews(100)
                .uniqueViewers(50)
                .totalEarnings(new BigDecimal("150.00"))
                .subscriptionEarnings(new BigDecimal("30.00"))
                .ppvEarnings(new BigDecimal("100.00"))
                .tipsEarnings(new BigDecimal("15.00"))
                .liveStreamEarnings(new BigDecimal("5.00"))
                .subscriptionsCount(5)
                .build();

        CreatorAnalytics saved = repository.save(analytics);
        assertNotNull(saved.getId());

        Optional<CreatorAnalytics> found = repository.findByCreatorIdAndDate(creatorId, today);
        assertTrue(found.isPresent());
        assertEquals(100, found.get().getTotalViews());
        assertEquals(0, new BigDecimal("150.00").compareTo(found.get().getTotalEarnings()));
        assertEquals(0, new BigDecimal("30.00").compareTo(found.get().getSubscriptionEarnings()));
        assertEquals(0, new BigDecimal("5.00").compareTo(found.get().getLiveStreamEarnings()));
    }

    @Test
    void testFindRange() {
        UUID creatorId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        repository.save(CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(today.minusDays(2))
                .totalViews(10)
                .build());
        repository.save(CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(today.minusDays(1))
                .totalViews(20)
                .build());
        repository.save(CreatorAnalytics.builder()
                .creatorId(creatorId)
                .date(today)
                .totalViews(30)
                .build());

        List<CreatorAnalytics> result = repository.findAllByCreatorIdAndDateBetweenOrderByDateAsc(
                creatorId, today.minusDays(2), today.minusDays(1));

        assertEquals(2, result.size());
        assertEquals(10, result.get(0).getTotalViews());
        assertEquals(20, result.get(1).getTotalViews());
    }
}








