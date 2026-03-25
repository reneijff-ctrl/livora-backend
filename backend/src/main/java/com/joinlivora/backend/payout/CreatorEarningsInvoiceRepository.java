package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorEarningsInvoiceRepository extends JpaRepository<CreatorEarningsInvoice, UUID> {
    List<CreatorEarningsInvoice> findByCreatorId(Long creatorId);
}
