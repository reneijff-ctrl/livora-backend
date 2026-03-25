package com.joinlivora.backend.fraud.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VelocityRulesConfigTest {

    @Autowired
    private VelocityRulesConfig velocityRulesConfig;

    @Test
    void testConfigBinding() {
        assertNotNull(velocityRulesConfig.getLogin());
        assertEquals(5, velocityRulesConfig.getLogin().get("1m").getLimit());
        assertEquals("suspicious", velocityRulesConfig.getLogin().get("1m").getAction());

        assertNotNull(velocityRulesConfig.getTip());
        assertEquals(10, velocityRulesConfig.getTip().get("1m").getLimit());
        assertEquals("suspicious", velocityRulesConfig.getTip().get("1m").getAction());
        assertEquals(50, velocityRulesConfig.getTip().get("10m").getLimit());
        assertEquals("critical", velocityRulesConfig.getTip().get("10m").getAction());

        assertNotNull(velocityRulesConfig.getPayment());
        assertEquals(3, velocityRulesConfig.getPayment().get("5m").getLimit());
        assertEquals("critical", velocityRulesConfig.getPayment().get("5m").getAction());

        assertNotNull(velocityRulesConfig.getMessage());
        assertEquals(30, velocityRulesConfig.getMessage().get("1m").getLimit());
        assertEquals("spam", velocityRulesConfig.getMessage().get("1m").getAction());
    }
}









