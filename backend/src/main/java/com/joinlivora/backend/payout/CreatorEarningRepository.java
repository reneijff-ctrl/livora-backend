package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreatorEarningRepository extends JpaRepository<CreatorEarning, UUID> {
    List<CreatorEarning> findAllByCreatorOrderByCreatedAtDesc(User creator);

    @Query("SELECT SUM(e.amountNet) FROM CreatorEarning e WHERE e.creator = :creator AND e.createdAt >= :since")
    BigDecimal sumNetEarningsByCreatorAndSince(@Param("creator") User creator, @Param("since") Instant since);

    @Query("SELECT COUNT(e) FROM CreatorEarning e WHERE e.creator = :creator AND e.source = :source")
    long countByCreatorAndSource(@Param("creator") User creator, @Param("source") EarningSource source);
}
