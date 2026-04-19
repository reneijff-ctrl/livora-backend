package com.joinlivora.backend.chargeback.repository;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargebackCaseRepository extends JpaRepository<ChargebackCase, UUID> {
    Optional<ChargebackCase> findByPaymentIntentId(String paymentIntentId);
    Optional<ChargebackCase> findByStripeDisputeId(String stripeDisputeId);
    long countByUserId(UUID userId);
    List<ChargebackCase> findAllByUserId(UUID userId);
    List<ChargebackCase> findAllByDeviceFingerprint(String deviceFingerprint);
    List<ChargebackCase> findAllByIpAddress(String ipAddress);
    List<ChargebackCase> findAllByPaymentMethodFingerprint(String paymentMethodFingerprint);
    List<ChargebackCase> findAllByCreatorId(Long creatorId);
}
