package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StubIpReputationProviderTest {

    private final StubIpReputationProvider provider = new StubIpReputationProvider();

    @Test
    void lookup_ShouldReturnSafeReputation() {
        String ip = "8.8.8.8";
        IpReputation result = provider.lookup(ip);

        assertNotNull(result);
        assertEquals(ip, result.getIp());
        assertEquals(0, result.getRiskScore());
        assertFalse(result.isProxy());
        assertFalse(result.isVpn());
        assertFalse(result.isTor());
        assertEquals("US", result.getCountryCode());
    }
}








