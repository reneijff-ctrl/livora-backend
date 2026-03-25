package com.joinlivora.backend.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpReputation implements Serializable {
    private String ip;
    private int riskScore; // 0-100
    private boolean proxy;
    private boolean vpn;
    private boolean tor;
    private String countryCode;
    private String isp;
}
