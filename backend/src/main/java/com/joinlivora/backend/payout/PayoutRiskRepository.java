package com.joinlivora.backend.payout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRiskRepository extends JpaRepository<PayoutRisk, UUID> {
    Page<PayoutRisk> findAllByUserIdOrderByLastEvaluatedAtDesc(Long userId, Pageable pageable);
    Optional<PayoutRisk> findFirstByUserIdOrderByLastEvaluatedAtDesc(Long userId);
}
