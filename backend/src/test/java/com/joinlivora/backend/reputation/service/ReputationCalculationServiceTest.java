package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReputationCalculationServiceTest {

    private ReputationCalculationService service;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        service = new ReputationCalculationService();
        creatorId = UUID.randomUUID();
    }

    @ParameterizedTest
    @CsvSource({
            "80, TRUSTED",
            "100, TRUSTED",
            "79, NORMAL",
            "50, NORMAL",
            "49, WATCHED",
            "30, WATCHED",
            "29, RESTRICTED",
            "0, RESTRICTED"
    })
    void determineStatus_ShouldReturnCorrectStatus(int score, ReputationStatus expectedStatus) {
        assertEquals(expectedStatus, service.determineStatus(score));
    }

    @Test
    void applyEvent_ShouldUpdateScoreAndStatus() {
        // Given
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .build();

        ReputationEvent event = ReputationEvent.builder()
                .deltaScore(30)
                .createdAt(Instant.now())
                .build();

        // When
        service.applyEvent(snapshot, event);

        // Then
        assertEquals(80, snapshot.getCurrentScore());
        assertEquals(ReputationStatus.TRUSTED, snapshot.getStatus());
        assertEquals(event.getCreatedAt(), snapshot.getLastPositiveEventAt());
    }

    @Test
    void applyEvent_ShouldClampScoreToMax() {
        // Given
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(90)
                .status(ReputationStatus.TRUSTED)
                .build();

        ReputationEvent event = ReputationEvent.builder()
                .deltaScore(20)
                .build();

        // When
        service.applyEvent(snapshot, event);

        // Then
        assertEquals(100, snapshot.getCurrentScore());
    }

    @Test
    void applyEvent_ShouldClampScoreToMin() {
        // Given
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(10)
                .status(ReputationStatus.RESTRICTED)
                .build();

        ReputationEvent event = ReputationEvent.builder()
                .deltaScore(-20)
                .build();

        // When
        service.applyEvent(snapshot, event);

        // Then
        assertEquals(0, snapshot.getCurrentScore());
    }

    @Test
    void applyEvent_NegativeDelta_ShouldNotUpdateLastPositiveEventAt() {
        // Given
        Instant lastPositive = Instant.now().minusSeconds(3600);
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .lastPositiveEventAt(lastPositive)
                .build();

        ReputationEvent event = ReputationEvent.builder()
                .deltaScore(-10)
                .createdAt(Instant.now())
                .build();

        // When
        service.applyEvent(snapshot, event);

        // Then
        assertEquals(40, snapshot.getCurrentScore());
        assertEquals(ReputationStatus.WATCHED, snapshot.getStatus());
        assertEquals(lastPositive, snapshot.getLastPositiveEventAt());
    }
}








