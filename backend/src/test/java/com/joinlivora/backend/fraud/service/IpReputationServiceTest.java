package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpReputationServiceTest {

    @Mock
    private IpReputationProvider provider;

    @InjectMocks
    private IpReputationService service;

    @Test
    void getReputation_ShouldCallProvider() {
        String ip = "1.2.3.4";
        IpReputation mockReputation = IpReputation.builder()
                .ip(ip)
                .riskScore(10)
                .build();

        when(provider.lookup(ip)).thenReturn(mockReputation);

        IpReputation result = service.getReputation(ip);

        assertNotNull(result);
        assertEquals(ip, result.getIp());
        assertEquals(10, result.getRiskScore());
        verify(provider, times(1)).lookup(ip);
    }
}








