package com.joinlivora.backend.payout.freeze;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutFreezeRepository extends JpaRepository<PayoutFreeze, Long> {

    Optional<PayoutFreeze> findByCreatorIdAndActiveTrue(Long creatorId);
    long countByActiveTrue();
}
