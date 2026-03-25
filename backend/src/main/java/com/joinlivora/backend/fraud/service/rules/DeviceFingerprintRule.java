package com.joinlivora.backend.fraud.service.rules;

import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import com.joinlivora.backend.fraud.repository.DeviceFingerprintRepository;
import com.joinlivora.backend.fraud.service.FraudRiskRule;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("deviceFingerprintRule")
@RequiredArgsConstructor
public class DeviceFingerprintRule implements FraudRiskRule {

    private final DeviceFingerprintRepository deviceFingerprintRepository;

    @Override
    public int evaluate(User user, Map<String, Object> context) {
        String currentFingerprint = (String) context.get("deviceFingerprint");
        if (currentFingerprint == null) return 0;

        List<DeviceFingerprint> sharingDevices = deviceFingerprintRepository.findAllByFingerprintHash(currentFingerprint);
        
        long distinctUsers = sharingDevices.stream()
                .map(DeviceFingerprint::getUserId)
                .distinct()
                .count();

        if (distinctUsers > 5) return 90;
        if (distinctUsers > 3) return 50;
        if (distinctUsers > 1) return 20;
        
        return 0;
    }

    @Override
    public String getName() {
        return "DEVICE_FINGERPRINT_REUSE";
    }
}
