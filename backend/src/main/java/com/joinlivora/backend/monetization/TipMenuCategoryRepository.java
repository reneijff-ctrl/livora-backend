package com.joinlivora.backend.monetization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TipMenuCategoryRepository extends JpaRepository<TipMenuCategory, UUID> {

    List<TipMenuCategory> findAllByCreatorIdOrderBySortOrder(Long creatorId);

    List<TipMenuCategory> findAllByCreatorIdAndEnabledTrueOrderBySortOrder(Long creatorId);

    long countByCreatorId(Long creatorId);
}
