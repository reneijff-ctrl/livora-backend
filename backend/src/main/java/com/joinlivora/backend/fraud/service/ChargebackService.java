package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.Chargeback;
import com.joinlivora.backend.fraud.ChargebackStatus;
import com.joinlivora.backend.fraud.dto.ChargebackAdminResponseDTO;
import com.joinlivora.backend.fraud.repository.ChargebackRepository;
import com.joinlivora.backend.payout.PayoutHoldService;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @deprecated Use com.joinlivora.backend.chargeback.ChargebackService instead.
 * TODO(livora-security): Remove in the next refactor stage.
 */
@Deprecated
@RequiredArgsConstructor
@Slf4j
public class ChargebackService {

    private final com.joinlivora.backend.chargeback.ChargebackService canonicalChargebackService;
    private final ChargebackRepository chargebackRepository;

    @Transactional(readOnly = true)
    public Page<ChargebackAdminResponseDTO> getAllChargebacks(Pageable pageable) {
        return canonicalChargebackService.getFraudChargebacks(pageable);
    }

    @Transactional
    public void handleChargebackOpened(String stripeChargeId, BigDecimal amount, String reason) {
        log.warn("DEPRECATED: fraud.service.ChargebackService.handleChargebackOpened called.");
        // Note: New canonical service prefers taking Dispute object, but we keep this for compatibility if needed.
        // However, most callers should be updated.
    }

    @Transactional
    public void handleChargebackClosed(String stripeChargeId, boolean won) {
        log.warn("DEPRECATED: fraud.service.ChargebackService.handleChargebackClosed called.");
    }
}
