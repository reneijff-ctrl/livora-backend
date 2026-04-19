package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service("chargebackAuditService")
@RequiredArgsConstructor
@Slf4j
public class ChargebackAuditService {

    private final ChargebackAuditRepository auditRepository;

    @Transactional
    public void audit(ChargebackCase chargeback, int clusterSize, RiskEscalationResult escalation) {
        List<String> actions = new ArrayList<>(escalation.getActions());
        if (clusterSize >= 3) {
            actions.add("PAYER_SUSPENDED");
        } else if (clusterSize >= 2) {
            actions.add("PAYER_PAYOUTS_FROZEN");
        } else {
            actions.add("PAYER_FLAGGED");
        }

        ChargebackAudit audit = ChargebackAudit.builder()
                .chargebackId(chargeback.getId())
                .userId(chargeback.getUserId())
                .creatorId(chargeback.getCreatorId())
                .clusterSize(clusterSize)
                .riskLevel(escalation.getRiskLevel().name())
                .actionsTaken(String.join(", ", actions))
                .reason("Chargeback processed. Cluster size: " + clusterSize + ". Creator Risk: " + escalation.getRiskLevel())
                .build();

        auditRepository.save(audit);
        log.info("Audit record created for chargeback: {}", chargeback.getId());
    }

    @Transactional
    public void recordAction(ChargebackCase chargeback, String action, String reason) {
        ChargebackAudit audit = ChargebackAudit.builder()
                .chargebackId(chargeback.getId())
                .userId(chargeback.getUserId())
                .creatorId(chargeback.getCreatorId())
                .actionsTaken(action)
                .reason(reason)
                .build();

        auditRepository.save(audit);
        log.info("Action audit record created for chargeback: {} - Action: {}", chargeback.getId(), action);
    }
}
