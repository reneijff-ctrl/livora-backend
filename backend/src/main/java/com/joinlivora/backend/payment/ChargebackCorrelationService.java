package com.joinlivora.backend.payment;

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

    private final ChargebackRepository chargebackRepository;
    private final PaymentRepository paymentRepository;

    public int analyze(Chargeback chargeback) {
        if (chargeback == null) return 0;

        log.info("Analyzing correlation for chargeback: {}", chargeback.getId());
        Set<UUID> correlatedIds = findCorrelatedChargebacks(chargeback).stream()
                .map(Chargeback::getId)
                .collect(Collectors.toSet());

        int score = 0;
        // Logic should match AutoFreezePolicyService/historical scores
        // Shared Device Fingerprint: +30
        // Shared IP Address: +20
        // Shared Payment Method (Card Fingerprint): +40
        // Same Creator Tipped: +10

        // We need to know WHICH signal triggered it.
        // Actually, the previous implementation did it one by one.

        if (chargeback.getDeviceFingerprint() != null) {
            if (chargebackRepository.findAllByDeviceFingerprint(chargeback.getDeviceFingerprint()).size() > 1) {
                score += 30;
            }
        }
        if (chargeback.getIpAddress() != null) {
            if (chargebackRepository.findAllByIpAddress(chargeback.getIpAddress()).size() > 1) {
                score += 20;
            }
        }
        if (chargeback.getPaymentMethodFingerprint() != null) {
            if (chargebackRepository.findAllByPaymentMethodFingerprint(chargeback.getPaymentMethodFingerprint()).size() > 1) {
                score += 40;
            }
        }
        if (chargeback.getTransactionId() != null) {
            paymentRepository.findById(chargeback.getTransactionId()).ifPresent(p -> {
                if (p.getCreator() != null) {
                    if (chargebackRepository.findAllByCreatorId(p.getCreator().getId()).size() > 1) {
                        // score += 10;
                    }
                }
            });
            // Re-evaluating based on original code
        }

        // To keep it simple and exactly as before:
        return analyzeOriginal(chargeback);
    }

    private int analyzeOriginal(Chargeback chargeback) {
        int score = 0;
        if (chargeback.getDeviceFingerprint() != null) {
            long count = chargebackRepository.findAllByDeviceFingerprint(chargeback.getDeviceFingerprint()).stream()
                    .filter(c -> !c.getId().equals(chargeback.getId()))
                    .count();
            if (count > 0) score += 30;
        }
        if (chargeback.getIpAddress() != null) {
            long count = chargebackRepository.findAllByIpAddress(chargeback.getIpAddress()).stream()
                    .filter(c -> !c.getId().equals(chargeback.getId()))
                    .count();
            if (count > 0) score += 20;
        }
        if (chargeback.getPaymentMethodFingerprint() != null) {
            long count = chargebackRepository.findAllByPaymentMethodFingerprint(chargeback.getPaymentMethodFingerprint()).stream()
                    .filter(c -> !c.getId().equals(chargeback.getId()))
                    .count();
            if (count > 0) score += 40;
        }
        if (chargeback.getTransactionId() != null) {
            Optional<Payment> paymentOpt = paymentRepository.findById(chargeback.getTransactionId());
            if (paymentOpt.isPresent() && paymentOpt.get().getCreator() != null) {
                Long creatorId = paymentOpt.get().getCreator().getId();
                long count = chargebackRepository.findAllByCreatorId(creatorId).stream()
                        .filter(c -> !c.getId().equals(chargeback.getId()))
                        .count();
                if (count > 0) score += 10;
            }
        }
        return Math.min(score, 100);
    }

    public List<Chargeback> findCorrelatedChargebacks(Chargeback chargeback) {
        Set<UUID> ids = new HashSet<>();

        if (chargeback.getDeviceFingerprint() != null) {
            ids.addAll(chargebackRepository.findAllByDeviceFingerprint(chargeback.getDeviceFingerprint()).stream()
                    .map(Chargeback::getId)
                    .collect(Collectors.toSet()));
        }

        if (chargeback.getIpAddress() != null) {
            ids.addAll(chargebackRepository.findAllByIpAddress(chargeback.getIpAddress()).stream()
                    .map(Chargeback::getId)
                    .collect(Collectors.toSet()));
        }

        if (chargeback.getPaymentMethodFingerprint() != null) {
            ids.addAll(chargebackRepository.findAllByPaymentMethodFingerprint(chargeback.getPaymentMethodFingerprint()).stream()
                    .map(Chargeback::getId)
                    .collect(Collectors.toSet()));
        }

        if (chargeback.getTransactionId() != null) {
            paymentRepository.findById(chargeback.getTransactionId()).ifPresent(payment -> {
                if (payment.getCreator() != null) {
                    ids.addAll(chargebackRepository.findAllByCreatorId(payment.getCreator().getId()).stream()
                            .map(Chargeback::getId)
                            .collect(Collectors.toSet()));
                }
            });
        }

        ids.remove(chargeback.getId());
        return chargebackRepository.findAllById(ids);
    }
}
