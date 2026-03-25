package com.joinlivora.backend.aml;

import com.joinlivora.backend.aml.model.AMLIncident;
import com.joinlivora.backend.aml.repository.AMLIncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AMLIncidentPersistenceTest {

    @Autowired
    private AMLIncidentRepository amlIncidentRepository;

    @Test
    void testSaveAndRetrieveAMLIncident() {
        UUID userId = UUID.randomUUID();
        AMLIncident incident = AMLIncident.builder()
                .userId(userId)
                .riskLevel("CRITICAL")
                .description("High velocity tips detected")
                .createdAt(Instant.now())
                .build();

        AMLIncident saved = amlIncidentRepository.save(incident);
        assertThat(saved.getId()).isNotNull();

        AMLIncident retrieved = amlIncidentRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getUserId()).isEqualTo(userId);
        assertThat(retrieved.getRiskLevel()).isEqualTo("CRITICAL");
        assertThat(retrieved.getDescription()).isEqualTo("High velocity tips detected");
    }

    @Test
    void testFindAllByUserIdOrderByCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        AMLIncident older = AMLIncident.builder()
                .userId(userId)
                .riskLevel("HIGH")
                .description("Older incident")
                .createdAt(now.minusSeconds(3600))
                .build();

        AMLIncident newer = AMLIncident.builder()
                .userId(userId)
                .riskLevel("CRITICAL")
                .description("Newer incident")
                .createdAt(now)
                .build();

        amlIncidentRepository.save(older);
        amlIncidentRepository.save(newer);

        List<AMLIncident> incidents = amlIncidentRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        assertThat(incidents).hasSize(2);
        assertThat(incidents.get(0).getRiskLevel()).isEqualTo("CRITICAL");
        assertThat(incidents.get(1).getRiskLevel()).isEqualTo("HIGH");
    }
}








