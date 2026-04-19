package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service("chargebackHistoryService")
@RequiredArgsConstructor
@Slf4j
public class ChargebackHistoryService {

    private final ChargebackCaseRepository chargebackCaseRepository;

    @Transactional(readOnly = true)
    public List<ChargebackCase> getHistory(Long userId) {
        log.info("Fetching chargeback history for creator: {}", userId);
        return chargebackCaseRepository.findAllByUserId(new UUID(0L, userId));
    }

    @Transactional(readOnly = true)
    public int getChargebackRiskScore(Long userId) {
        List<ChargebackCase> history = getHistory(userId);
        if (history.isEmpty()) {
            return 0;
        }

        // Scoring logic: 20 points per chargeback, capped at 100.
        int score = history.size() * 20;
        return Math.min(score, 100);
    }
}
