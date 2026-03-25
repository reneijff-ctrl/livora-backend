package com.joinlivora.backend.analytics;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaderboardEntryRepository extends JpaRepository<LeaderboardEntry, UUID> {
    List<LeaderboardEntry> findAllByPeriodOrderByRankAsc(LeaderboardPeriod period);
    List<LeaderboardEntry> findAllByPeriodAndReferenceDateOrderByRankAsc(LeaderboardPeriod period, LocalDate referenceDate);
    List<LeaderboardEntry> findAllByPeriodAndReferenceDateOrderByRankAsc(LeaderboardPeriod period, LocalDate referenceDate, Pageable pageable);
    
    List<LeaderboardEntry> findAllByPeriodAndReferenceDateAndCategoryOrderByRankAsc(LeaderboardPeriod period, LocalDate referenceDate, String category, Pageable pageable);

    @Query("SELECT e FROM LeaderboardEntry e WHERE e.period = :period AND e.referenceDate = :referenceDate AND e.category IS NULL ORDER BY e.rank ASC")
    List<LeaderboardEntry> findGlobalByPeriodAndReferenceDateOrderByRankAsc(LeaderboardPeriod period, LocalDate referenceDate, Pageable pageable);

    List<LeaderboardEntry> findAllByCreatorIdAndPeriod(UUID creatorId, LeaderboardPeriod period);
    void deleteByPeriodAndReferenceDate(LeaderboardPeriod period, LocalDate referenceDate);

    @Query("SELECT MAX(e.referenceDate) FROM LeaderboardEntry e WHERE e.period = :period")
    Optional<LocalDate> findMaxReferenceDateByPeriod(LeaderboardPeriod period);
}
