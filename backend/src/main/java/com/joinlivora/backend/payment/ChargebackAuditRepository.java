package com.joinlivora.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChargebackAuditRepository extends JpaRepository<ChargebackAudit, UUID> {
}
