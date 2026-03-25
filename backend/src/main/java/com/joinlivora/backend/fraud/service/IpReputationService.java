package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service("ipReputationService")
@Slf4j
@RequiredArgsConstructor
public class IpReputationService {

    private final IpReputationProvider provider;

    @Cacheable(value = "ipReputation", key = "#ip")
    public IpReputation getReputation(String ip) {
        log.info("Looking up IP reputation for: {}", ip);
        return provider.lookup(ip);
    }
}
