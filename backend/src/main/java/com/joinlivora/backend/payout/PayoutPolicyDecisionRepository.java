package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PayoutPolicyDecisionRepository extends JpaRepository<PayoutPolicyDecision, UUID> {
}
