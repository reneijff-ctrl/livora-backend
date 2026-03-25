package com.joinlivora.backend.monetization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyTipLeaderboardRepository extends JpaRepository<WeeklyTipLeaderboard, UUID> {
    
    Optional<WeeklyTipLeaderboard> findByCreatorIdAndUsernameAndWeekNumberAndYear(
            Long creatorId, String username, Integer weekNumber, Integer year);
            
    List<WeeklyTipLeaderboard> findByCreatorIdAndWeekNumberAndYearOrderByTotalAmountDesc(
            Long creatorId, Integer weekNumber, Integer year);
}
