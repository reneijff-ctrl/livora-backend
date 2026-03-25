package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.RiskFactorTemplate;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.repository.RiskExplanationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("riskExplanationService")
@RequiredArgsConstructor
@Slf4j
public class RiskExplanationService {

    private final RiskExplanationRepository repository;

    private static final Map<String, RiskFactorTemplate> FACTOR_TEMPLATES = Map.ofEntries(
            // Collusion Patterns
            Map.entry("REPEATED_TIPPING", new RiskFactorTemplate("Repeated tipping patterns from the same users", "MEDIUM", "last 30 days", "Collusion Detection")),
            Map.entry("CIRCULAR_TIPPING", new RiskFactorTemplate("Circular tipping detected (A -> B -> A)", "HIGH", "last 30 days", "Collusion Detection")),
            Map.entry("HIGH_TIP_LOW_ACTIVITY", new RiskFactorTemplate("High tipping volume with disproportionately low chat activity", "MEDIUM", "last 30 days", "Collusion Detection")),
            Map.entry("CLUSTER_FUNDING", new RiskFactorTemplate("Multiple accounts funding one creator in a cluster", "MEDIUM", "last 30 days", "Collusion Detection")),
            
            // AML Rules
            Map.entry("RAPID_PAYOUT_AFTER_TIPS", new RiskFactorTemplate("Payout requested shortly after receiving tips", "MEDIUM", "last 24 hours", "AML Engine")),
            Map.entry("REPEATED_PAYOUTS_TO_SAME_BANK", new RiskFactorTemplate("Multiple users paying out to the same bank account", "HIGH", "all time", "AML Engine")),
            Map.entry("NEW_ACCOUNT_PAYOUT", new RiskFactorTemplate("Payout requested by a recently created account", "LOW", "last 7 days", "AML Engine")),
            Map.entry("HIGH_PAYOUT_LOW_CHAT_RATIO", new RiskFactorTemplate("High payout amount compared to chat engagement", "MEDIUM", "last 30 days", "AML Engine")),
            
            // Trust Evaluation
            Map.entry("NEW_DEVICE", new RiskFactorTemplate("Action performed from a new device", "LOW", "current session", "Trust Evaluation")),
            Map.entry("NEW_IP", new RiskFactorTemplate("Action performed from a new IP address", "LOW", "current session", "Trust Evaluation")),
            Map.entry("VPN_PROXY", new RiskFactorTemplate("VPN or Proxy usage detected", "MEDIUM", "current session", "Trust Evaluation")),
            Map.entry("TOR_EXIT", new RiskFactorTemplate("Tor exit node usage detected", "HIGH", "current session", "Trust Evaluation")),
            Map.entry("DEVICE_MISMATCH", new RiskFactorTemplate("Device fingerprint used by multiple accounts", "MEDIUM", "all time", "Trust Evaluation")),
            Map.entry("VELOCITY_WARNING", new RiskFactorTemplate("High frequency of actions detected", "MEDIUM", "last 24 hours", "Velocity Engine")),
            Map.entry("COUNTRY_MISMATCH", new RiskFactorTemplate("Action performed from a different country than usual", "MEDIUM", "current session", "Trust Evaluation")),
            Map.entry("IP_MISMATCH", new RiskFactorTemplate("IP address mismatch for the same session", "HIGH", "current session", "Trust Evaluation")),
            Map.entry("CHARGEBACK", new RiskFactorTemplate("Chargeback initiated by creator", "CRITICAL", "all time", "Payments")),
            Map.entry("CHARGEBACK_CORRELATION", new RiskFactorTemplate("Account linked to multiple chargebacks", "HIGH", "all time", "Payments")),
            Map.entry("AML_HIGH_RISK", new RiskFactorTemplate("Extreme AML risk pattern detected", "CRITICAL", "all time", "AML Engine")),
            Map.entry("COLLUSION_DETECTED", new RiskFactorTemplate("High collusion risk with other users", "HIGH", "last 30 days", "Collusion Detection")),

            // New Requested Templates
            Map.entry("HIGH_CHARGEBACK_RATIO", new RiskFactorTemplate("High chargeback ratio", "HIGH", "last 14 days", "Payments")),
            Map.entry("FAILED_PAYMENT_ATTEMPTS", new RiskFactorTemplate("Multiple failed payment attempts from same IP", "MEDIUM", "last 24 hours", "Payments")),
            Map.entry("LOW_REPUTATION", new RiskFactorTemplate("Creator reputation below trusted threshold", "MEDIUM", "current", "Reputation System"))
    );

    /**
     * Generates and stores a risk explanation for system-automated decisions.
     */
    @Transactional
    public RiskExplanation generateSystemExplanation(RiskSubjectType subjectType, UUID subjectId, int riskScore, RiskDecision decision, Map<String, Object> factors) {
        String explanationText = buildExplanationFromFactors(factors);

        RiskExplanation explanation = RiskExplanation.builder()
                .subjectType(subjectType)
                .subjectId(subjectId)
                .riskScore(riskScore)
                .decision(decision)
                .factors(factors)
                .explanationText(explanationText)
                .build();

        log.info("SECURITY: Generated SYSTEM risk explanation for {} {}: Decision={}, Score={}", subjectType, subjectId, decision, riskScore);
        return repository.save(explanation);
    }

    /**
     * Generates and stores a risk explanation for administrative manual decisions.
     */
    @Transactional
    public RiskExplanation generateAdminExplanation(RiskSubjectType subjectType, UUID subjectId, int riskScore, RiskDecision decision, String customText, Map<String, Object> factors) {
        RiskExplanation explanation = RiskExplanation.builder()
                .subjectType(subjectType)
                .subjectId(subjectId)
                .riskScore(riskScore)
                .decision(decision)
                .factors(factors)
                .explanationText("ADMIN OVERRIDE: " + customText)
                .build();

        log.info("SECURITY: Generated ADMIN risk explanation for {} {}: Decision={}, Score={}", subjectType, subjectId, decision, riskScore);
        return repository.save(explanation);
    }

    /**
     * Retrieves a specific risk explanation by its ID.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<RiskExplanation> getExplanationById(UUID id) {
        return repository.findById(id);
    }

    /**
     * Retrieves the history of risk explanations for a given subject.
     */
    @Transactional(readOnly = true)
    public List<RiskExplanation> getExplanationsForSubject(UUID subjectId, RiskSubjectType subjectType) {
        return repository.findAllBySubjectIdAndSubjectTypeOrderByGeneratedAtDesc(subjectId, subjectType);
    }

    /**
     * Retrieves the latest risk explanation for a subject.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<RiskExplanation> getLatestExplanationForSubject(UUID subjectId, RiskSubjectType subjectType) {
        return repository.findAllBySubjectIdAndSubjectTypeOrderByGeneratedAtDesc(subjectId, subjectType)
                .stream()
                .findFirst();
    }

    private String buildExplanationFromFactors(Map<String, Object> factors) {
        if (factors == null || factors.isEmpty()) {
            return "No specific risk factors identified.";
        }

        String combined = factors.keySet().stream()
                .map(key -> {
                    RiskFactorTemplate template = FACTOR_TEMPLATES.get(key);
                    return template != null ? template.format() : key;
                })
                .collect(Collectors.joining(". "));
        
        return combined.isEmpty() ? "No specific risk factors identified." : combined + ".";
    }
}
