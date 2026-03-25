package com.joinlivora.backend.reputation.repository;

import com.joinlivora.backend.reputation.model.ReputationEvent;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ReputationEventRepositoryTest {

    @Autowired
    private ReputationEventRepository repository;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        ReputationEvent event = ReputationEvent.builder()
                .creatorId(creatorId)
                .type(ReputationEventType.TIP)
                .deltaScore(10)
                .source(ReputationEventSource.SYSTEM)
                .metadata(Map.of("amount", 100, "currency", "EUR"))
                .build();

        ReputationEvent saved = repository.save(event);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        List<ReputationEvent> events = repository.findAllByCreatorIdOrderByCreatedAtDesc(creatorId);
        assertFalse(events.isEmpty());
        assertEquals(1, events.size());
        
        ReputationEvent found = events.get(0);
        assertEquals(creatorId, found.getCreatorId());
        assertEquals(ReputationEventType.TIP, found.getType());
        assertEquals(10, found.getDeltaScore());
        assertEquals(ReputationEventSource.SYSTEM, found.getSource());
        assertEquals(100, found.getMetadata().get("amount"));
    }
}








