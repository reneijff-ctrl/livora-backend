package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;
import org.springframework.stereotype.Component;

@Component("stubIpReputationProvider")
public class StubIpReputationProvider implements IpReputationProvider {
    @Override
    public IpReputation lookup(String ip) {
        // Return a safe reputation by default for the stub
        return IpReputation.builder()
                .ip(ip)
                .riskScore(0)
                .proxy(false)
                .vpn(false)
                .tor(false)
                .countryCode("US")
                .isp("Stub ISP")
                .build();
    }
}
