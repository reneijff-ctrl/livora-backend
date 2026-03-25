package com.joinlivora.backend.chargeback.repository;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargebackCaseRepository extends JpaRepository<ChargebackCase, UUID> {
    Optional<ChargebackCase> findByPaymentIntentId(String paymentIntentId);
    long countByUserId(UUID userId);
}
