package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.Chargeback;
import com.joinlivora.backend.fraud.ChargebackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("fraudChargebackRepository")
public interface ChargebackRepository extends JpaRepository<Chargeback, Long> {
    List<Chargeback> findByUser_Id(Long userId);
    Optional<Chargeback> findByStripeChargeId(String stripeChargeId);
    List<Chargeback> findByStatus(ChargebackStatus status);
}
