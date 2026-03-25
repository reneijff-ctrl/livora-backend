package com.joinlivora.backend.aml;

import com.joinlivora.backend.aml.model.AMLRule;
import com.joinlivora.backend.aml.repository.AMLRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AMLRulePersistenceTest {

    @Autowired
    private AMLRuleRepository amlRuleRepository;

    @Test
    void testSaveAndRetrieveAMLRule() {
        AMLRule rule = AMLRule.builder()
                .code("HIGH_TIP_VELOCITY")
                .description("Triggered when a creator sends tips too quickly")
                .threshold(100)
                .enabled(true)
                .build();

        AMLRule saved = amlRuleRepository.save(rule);
        assertThat(saved.getId()).isNotNull();

        AMLRule retrieved = amlRuleRepository.findByCode("HIGH_TIP_VELOCITY").orElseThrow();
        assertThat(retrieved.getDescription()).isEqualTo("Triggered when a creator sends tips too quickly");
        assertThat(retrieved.getThreshold()).isEqualTo(100);
        assertThat(retrieved.isEnabled()).isTrue();
    }
}








