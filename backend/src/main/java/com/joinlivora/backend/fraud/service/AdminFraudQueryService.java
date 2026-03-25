package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.fraud.dto.FailedLoginDTO;
import com.joinlivora.backend.fraud.dto.FraudDashboardMetricsDTO;
import com.joinlivora.backend.fraud.dto.PaymentAnomalyDTO;
import com.joinlivora.backend.fraud.model.FraudEvent;
import com.joinlivora.backend.fraud.model.FraudScore;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.model.RiskScore;
import com.joinlivora.backend.fraud.repository.FraudEventRepository;
import com.joinlivora.backend.fraud.repository.FraudScoreRepository;
import com.joinlivora.backend.fraud.repository.RiskScoreRepository;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.admin.dto.UserAdminResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service("adminFraudQueryService")
@RequiredArgsConstructor
@Slf4j
public class AdminFraudQueryService {

    private final FraudEventRepository fraudEventRepository;
    private final FraudScoreRepository fraudScoreRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final UserRepository userRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final PaymentRepository paymentRepository;
    private final RuleFraudSignalRepository ruleFraudSignalRepository;

    @Transactional(readOnly = true)
    public List<FraudEvent> getFraudHistory(UUID userId) {
        log.info("Fetching fraud history for creator: {}", userId);
        return fraudEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<FraudScore> getUsersWithEnforcement() {
        log.info("Fetching all users with active enforcement actions");
        return fraudScoreRepository.findAllByRiskLevelNot("LOW");
    }

    @Transactional(readOnly = true)
    public Optional<RiskScore> getRiskScore(UUID userId) {
        log.info("Fetching risk score for creator: {}", userId);
        return riskScoreRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponseDTO> getUsersByFraudRiskLevel(FraudRiskLevel riskLevel, Pageable pageable) {
        log.info("Fetching users with fraud risk level: {}", riskLevel);
        return userRepository.findAllByFraudRiskLevel(riskLevel, pageable)
                .map(user -> UserAdminResponseDTO.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .status(user.getStatus())
                        .fraudRiskLevel(user.getFraudRiskLevel())
                        .payoutsEnabled(user.isPayoutsEnabled())
                        .shadowbanned(user.isShadowbanned())
                        .createdAt(user.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<FailedLoginDTO> getFailedLogins(Pageable pageable) {
        log.info("Fetching failed login attempts");
        return analyticsEventRepository.findAllByEventType(AnalyticsEventType.USER_LOGIN_FAILED, pageable)
                .map(event -> FailedLoginDTO.builder()
                        .email(event.getUser() != null ? event.getUser().getEmail() : (event.getMetadata() != null ? (String) event.getMetadata().get("email") : null))
                        .timestamp(event.getCreatedAt())
                        .ipAddress(event.getMetadata() != null ? (String) event.getMetadata().get("ip") : null)
                        .userAgent(event.getMetadata() != null ? (String) event.getMetadata().get("userAgent") : null)
                        .build());
    }

    @Transactional(readOnly = true)
    public Page<PaymentAnomalyDTO> getPaymentAnomalies(Pageable pageable) {
        log.info("Fetching payment anomalies");
        return paymentRepository.findAllByRiskLevelIn(List.of(RiskLevel.HIGH, RiskLevel.CRITICAL), pageable)
                .map(payment -> PaymentAnomalyDTO.builder()
                        .paymentId(payment.getId())
                        .userEmail(payment.getUser().getEmail())
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .riskLevel(payment.getRiskLevel())
                        .createdAt(payment.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public FraudDashboardMetricsDTO getFraudDashboardMetrics() {
        log.info("Fetching fraud dashboard metrics");
        
        long unresolvedCount = ruleFraudSignalRepository.countByResolvedFalse();
        Map<RiskLevel, Long> unresolvedByRisk = ruleFraudSignalRepository.countUnresolvedByRiskLevel();
        
        long criticalCount = unresolvedByRisk.getOrDefault(RiskLevel.CRITICAL, 0L);
        long highCount = unresolvedByRisk.getOrDefault(RiskLevel.HIGH, 0L);
        
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant since24h = now.minus(24, java.time.temporal.ChronoUnit.HOURS);
        long enforcementCount = fraudEventRepository.countByCreatedAtAfter(since24h);
        
        java.time.Instant since1h = now.minus(1, java.time.temporal.ChronoUnit.HOURS);
        long newAccountHigh = ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH, since1h);
        long newAccountMedium = ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(FraudSignalType.NEW_ACCOUNT_TIPPING_MEDIUM, since1h);
        long cluster = ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER, since1h);
        long rapidRepeats = ruleFraudSignalRepository.countByTypeAndCreatedAtAfter(FraudSignalType.RAPID_TIP_REPEATS, since1h);
        
        return FraudDashboardMetricsDTO.builder()
                .unresolvedSignals(unresolvedCount)
                .criticalSignals(criticalCount)
                .highSignals(highCount)
                .enforcementLast24h(enforcementCount)
                .newAccountTippingHigh(newAccountHigh)
                .newAccountTippingMedium(newAccountMedium)
                .newAccountTipCluster(cluster)
                .rapidTipRepeats(rapidRepeats)
                .build();
    }
}
