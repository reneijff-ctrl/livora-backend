package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("chargebackCorrelationService")
@RequiredArgsConstructor
@Slf4j
public class ChargebackCorrelationService {

    private final ChargebackCaseRepository chargebackCaseRepository;
    private final PaymentRepository paymentRepository;

    public int analyze(ChargebackCase chargeback) {
        if (chargeback == null) return 0;
        log.info("Analyzing correlation for chargeback: {}", chargeback.getId());
        return analyzeOriginal(chargeback);
    }

    private int analyzeOriginal(ChargebackCase chargeback) {
        int score = 0;
        if (chargeback.getDeviceFingerprint() != null) {
            long count = chargebackCaseRepository.findAllByDeviceFingerprint(chargeback.getDeviceFingerprint()).stream()
                    .filter(c -> !c.getId().equals(chargeback.getId()))
                    .count();
            if (count > 0) score += 30;
        }
        if (chargeback.getIpAddress() != null) {
            long count = chargebackCaseRepository.findAllByIpAddress(chargeback.getIpAddress()).stream()
                    .filter(c -> !c.getId().equals(chargeback.getId()))
                    .count();
            if (count > 0) score += 20;
        }
        if (chargeback.getPaymentMethodFingerprint() != null) {
            long count = chargebackCaseRepository.findAllByPaymentMethodFingerprint(chargeback.getPaymentMethodFingerprint()).stream()
                    .filter(c -> !c.getId().equals(chargeback.getId()))
                    .count();
            if (count > 0) score += 40;
        }
        if (chargeback.getTransactionId() != null) {
            Optional<Payment> paymentOpt = paymentRepository.findById(chargeback.getTransactionId());
            if (paymentOpt.isPresent() && paymentOpt.get().getCreator() != null) {
                Long creatorId = paymentOpt.get().getCreator().getId();
                long count = chargebackCaseRepository.findAllByCreatorId(creatorId).stream()
                        .filter(c -> !c.getId().equals(chargeback.getId()))
                        .count();
                if (count > 0) score += 10;
            }
        }
        return Math.min(score, 100);
    }

    public List<ChargebackCase> findCorrelatedChargebacks(ChargebackCase chargeback) {
        Set<UUID> ids = new HashSet<>();

        if (chargeback.getDeviceFingerprint() != null) {
            ids.addAll(chargebackCaseRepository.findAllByDeviceFingerprint(chargeback.getDeviceFingerprint()).stream()
                    .map(ChargebackCase::getId)
                    .collect(Collectors.toSet()));
        }

        if (chargeback.getIpAddress() != null) {
            ids.addAll(chargebackCaseRepository.findAllByIpAddress(chargeback.getIpAddress()).stream()
                    .map(ChargebackCase::getId)
                    .collect(Collectors.toSet()));
        }

        if (chargeback.getPaymentMethodFingerprint() != null) {
            ids.addAll(chargebackCaseRepository.findAllByPaymentMethodFingerprint(chargeback.getPaymentMethodFingerprint()).stream()
                    .map(ChargebackCase::getId)
                    .collect(Collectors.toSet()));
        }

        if (chargeback.getTransactionId() != null) {
            paymentRepository.findById(chargeback.getTransactionId()).ifPresent(payment -> {
                if (payment.getCreator() != null) {
                    ids.addAll(chargebackCaseRepository.findAllByCreatorId(payment.getCreator().getId()).stream()
                            .map(ChargebackCase::getId)
                            .collect(Collectors.toSet()));
                }
            });
        }

        ids.remove(chargeback.getId());
        return chargebackCaseRepository.findAllById(ids);
    }
}
