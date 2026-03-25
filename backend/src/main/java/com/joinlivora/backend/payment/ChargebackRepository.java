package com.joinlivora.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository("paymentChargebackRepository")
public interface ChargebackRepository extends JpaRepository<Chargeback, UUID> {
    List<Chargeback> findAllByUserId(UUID userId);
    List<Chargeback> findAllByCreatorId(Long creatorId);
    List<Chargeback> findAllByTransactionId(UUID transactionId);
    Optional<Chargeback> findByStripeChargeId(String stripeChargeId);
    Optional<Chargeback> findByStripeDisputeId(String stripeDisputeId);
    List<Chargeback> findAllByDeviceFingerprint(String deviceFingerprint);
    List<Chargeback> findAllByIpAddress(String ipAddress);
    List<Chargeback> findAllByPaymentMethodFingerprint(String paymentMethodFingerprint);
}
