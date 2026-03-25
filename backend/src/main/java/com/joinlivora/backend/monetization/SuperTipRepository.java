package com.joinlivora.backend.monetization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SuperTipRepository extends JpaRepository<SuperTip, UUID> {
    java.util.Optional<SuperTip> findByClientRequestId(String clientRequestId);
}
