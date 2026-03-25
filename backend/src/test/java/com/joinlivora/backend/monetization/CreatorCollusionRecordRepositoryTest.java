package com.joinlivora.backend.monetization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CreatorCollusionRecordRepositoryTest {

    @Autowired
    private CreatorCollusionRecordRepository repository;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        CreatorCollusionRecord record = CreatorCollusionRecord.builder()
                .creatorId(creatorId)
                .score(85)
                .detectedPattern("REPEATED_TIPPING, CIRCULAR_TIPPING")
                .build();

        CreatorCollusionRecord saved = repository.save(record);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEvaluatedAt()).isNotNull();

        org.springframework.data.domain.Page<CreatorCollusionRecord> page = repository.findAllByCreatorIdOrderByEvaluatedAtDesc(creatorId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getScore()).isEqualTo(85);
        assertThat(page.getContent().get(0).getDetectedPattern()).isEqualTo("REPEATED_TIPPING, CIRCULAR_TIPPING");
    }
}









