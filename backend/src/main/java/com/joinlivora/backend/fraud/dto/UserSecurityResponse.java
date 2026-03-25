package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityResponse {
    private List<DeviceFingerprint> fingerprints;
    private List<RuleFraudSignal> signals;
}
