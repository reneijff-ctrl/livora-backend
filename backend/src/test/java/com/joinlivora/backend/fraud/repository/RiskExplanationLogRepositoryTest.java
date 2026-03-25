package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskExplanationLog;
import com.joinlivora.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RiskExplanationLogRepositoryTest {

    @Autowired
    private RiskExplanationLogRepository repository;

    @Test
    void testSaveAndFind() {
        UUID requesterId = UUID.randomUUID();
        UUID explanationId = UUID.randomUUID();
        
        RiskExplanationLog log = RiskExplanationLog.builder()
                .requesterId(requesterId)
                .role(Role.ADMIN)
                .explanationId(explanationId)
                .timestamp(Instant.now())
                .build();

        RiskExplanationLog saved = repository.save(log);
        assertThat(saved.getId()).isNotNull();

        RiskExplanationLog found = repository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getRequesterId()).isEqualTo(requesterId);
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.getExplanationId()).isEqualTo(explanationId);
    }
}








