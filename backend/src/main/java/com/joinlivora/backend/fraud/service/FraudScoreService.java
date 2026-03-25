package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.Chargeback;
import com.joinlivora.backend.fraud.ChargebackStatus;
import com.joinlivora.backend.fraud.FraudFlag;
import com.joinlivora.backend.fraud.FraudFlagSource;
import com.joinlivora.backend.fraud.repository.ChargebackRepository;
import com.joinlivora.backend.fraud.repository.FraudFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service("fraudScoreService")
@RequiredArgsConstructor
public class FraudScoreService {

    private final ChargebackRepository chargebackRepository;
    private final FraudFlagRepository fraudFlagRepository;

    public int calculateFraudScore(UUID userId) {
        int score = 0;

        List<Chargeback> chargebacks = chargebackRepository.findByUser_Id(userId.getLeastSignificantBits());
        for (Chargeback cb : chargebacks) {
            if (cb.getStatus() == ChargebackStatus.OPEN) {
                score += 40;
            } else if (cb.getStatus() == ChargebackStatus.LOST) {
                score += 20;
            }
        }

        List<FraudFlag> flags = fraudFlagRepository.findByUserId(userId);
        for (FraudFlag flag : flags) {
            if (flag.getSource() == FraudFlagSource.MANUAL) {
                score += 10;
            }
        }

        return Math.min(score, 100);
    }
}
