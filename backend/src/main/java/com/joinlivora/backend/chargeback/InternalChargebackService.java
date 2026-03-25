package com.joinlivora.backend.chargeback;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import com.joinlivora.backend.fraud.model.FraudDecision;
import com.joinlivora.backend.fraud.repository.FraudDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing chargeback cases.
 */
/**
 * @deprecated Use com.joinlivora.backend.chargeback.ChargebackService instead.
 * TODO(livora-security): Remove in the next refactor stage.
 */
@Deprecated
@Service("internalChargebackService")
@RequiredArgsConstructor
@Slf4j
public class InternalChargebackService {

    private final ChargebackService canonicalChargebackService;

    @Transactional
    public ChargebackCase registerChargeback(UUID userId, String paymentIntentId, BigDecimal amount, String currency, String reason) {
        log.warn("DEPRECATED: InternalChargebackService.registerChargeback called.");
        // In a real scenario, we might want to map this to the new service
        // but for now we just log it as deprecated.
        return null;
    }

    @Transactional
    public ChargebackCase updateStatus(UUID chargebackId, ChargebackStatus newStatus) {
        log.warn("DEPRECATED: InternalChargebackService.updateStatus called.");
        return canonicalChargebackService.updateStatus(chargebackId, newStatus);
    }

    /**
     * Gets the count of chargebacks for a creator.
     *
     * @param userId The ID of the creator
     * @return The number of chargeback cases
     */
    @Transactional(readOnly = true)
    public long getChargebackCount(UUID userId) {
        return canonicalChargebackService.getChargebackCount(userId);
    }

    @Transactional(readOnly = true)
    public List<ChargebackCase> getAllChargebacks() {
        return canonicalChargebackService.getAllChargebackCases();
    }

    @Transactional(readOnly = true)
    public Optional<ChargebackCase> getChargebackById(UUID id) {
        log.warn("DEPRECATED: getChargebackById called.");
        return canonicalChargebackService.getChargebackById(id);
    }
}
