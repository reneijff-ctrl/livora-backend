package com.joinlivora.backend.monetization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipActionRepository extends JpaRepository<TipAction, UUID> {
    List<TipAction> findAllByCreatorId(Long creatorId);

    @Query("SELECT a FROM TipAction a WHERE a.creatorId = :creatorId AND a.enabled = true")
    List<TipAction> findAllEnabledByCreatorId(@Param("creatorId") Long creatorId);

    List<TipAction> findAllByCreatorIdAndAmountAndEnabledTrue(Long creatorId, Long amount);

    List<TipAction> findAllByCategoryIdOrderBySortOrder(UUID categoryId);

    List<TipAction> findAllByCreatorIdAndCategoryIdIsNullOrderBySortOrder(Long creatorId);

    @Query("SELECT a FROM TipAction a WHERE a.creatorId = :creatorId AND a.enabled = true ORDER BY a.sortOrder")
    List<TipAction> findAllEnabledByCreatorIdOrdered(@Param("creatorId") Long creatorId);

    long countByCreatorId(Long creatorId);
}
