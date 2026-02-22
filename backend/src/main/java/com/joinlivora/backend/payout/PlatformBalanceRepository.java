package com.joinlivora.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformBalanceRepository extends JpaRepository<PlatformBalance, UUID> {

    @Query("SELECT p FROM PlatformBalance p")
    Optional<PlatformBalance> findSingle();
}
