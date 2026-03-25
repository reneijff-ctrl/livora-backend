package com.joinlivora.backend.payouts.repository;

import com.joinlivora.backend.payouts.model.PayoutFreezeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutFreezeHistoryRepository extends JpaRepository<PayoutFreezeHistory, UUID> {
    List<PayoutFreezeHistory> findAllByCreatorIdOrderByCreatedAtDesc(UUID creatorId);
}
