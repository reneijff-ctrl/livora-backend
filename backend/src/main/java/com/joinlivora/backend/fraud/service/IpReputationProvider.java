package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.IpReputation;

public interface IpReputationProvider {
    IpReputation lookup(String ip);
}
