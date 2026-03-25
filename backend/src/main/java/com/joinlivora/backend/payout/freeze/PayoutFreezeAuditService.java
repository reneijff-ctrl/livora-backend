package com.joinlivora.backend.payout.freeze;

import com.joinlivora.backend.payout.freeze.dto.CsvExportResult;
import com.joinlivora.backend.security.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayoutFreezeAuditService {

    private final PayoutFreezeAuditRepository auditRepository;

    public List<PayoutFreezeAuditLog> getAuditForCreator(Long creatorId) {
        return auditRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId);
    }

    public Page<PayoutFreezeAuditLog> getAllAudit(Pageable pageable) {
        return auditRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public String generateCsvForCreator(Long creatorId) {
        return generateCsvForCreator(creatorId, null, null);
    }

    public String generateCsvForCreator(Long creatorId, Instant from, Instant to) {
        List<PayoutFreezeAuditLog> logs;
        if (from != null && to != null) {
            logs = auditRepository.findByCreatorIdAndCreatedAtBetweenOrderByCreatedAtDesc(creatorId, from, to);
        } else {
            logs = getAuditForCreator(creatorId);
        }
        return buildCsv(logs);
    }

    public String generateGlobalCsv(Instant from, Instant to) {
        List<PayoutFreezeAuditLog> logs;
        if (from != null && to != null) {
            logs = auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
        } else {
            logs = auditRepository.findAllByOrderByCreatedAtDesc();
        }
        return buildCsv(logs);
    }

    public CsvExportResult generateSignedGlobalCsv(Instant from, Instant to) {
        String csv = generateGlobalCsv(from, to);
        String hash = HashUtil.sha256(csv);
        return new CsvExportResult(csv, hash);
    }

    private String buildCsv(List<PayoutFreezeAuditLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("creator,action,reason,adminId,createdAt\n");

        for (PayoutFreezeAuditLog log : logs) {
            sb.append(log.getCreatorId()).append(",")
                    .append(log.getAction()).append(",")
                    .append(escapeCsv(log.getReason())).append(",")
                    .append(log.getAdminId() != null ? log.getAdminId() : "").append(",")
                    .append(log.getCreatedAt()).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
