package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.FraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudFlagRepository extends JpaRepository<FraudFlag, UUID> {
    List<FraudFlag> findByUserId(UUID userId);
    List<FraudFlag> findByStripeChargeId(String stripeChargeId);
}
