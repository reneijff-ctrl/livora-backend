package com.joinlivora.backend.monetization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TipGoalRepository extends JpaRepository<TipGoal, UUID> {
    Optional<TipGoal> findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(Long creatorId);

    Optional<TipGoal> findFirstByCreatorIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(Long creatorId, Integer orderIndex);

    List<TipGoal> findAllByCreatorIdOrderByOrderIndexAsc(Long creatorId);

    List<TipGoal> findAllByGroupIdOrderByTargetAmountAsc(UUID groupId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TipGoal g SET g.currentAmount = g.currentAmount + :amount WHERE g.id = :id")
    int incrementCurrentAmount(@Param("id") UUID id, @Param("amount") Long amount);
}
