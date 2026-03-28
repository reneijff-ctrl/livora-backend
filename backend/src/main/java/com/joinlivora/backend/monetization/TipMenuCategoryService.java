package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipMenuCategoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipMenuCategoryService {

    private final TipMenuCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<TipMenuCategoryDto> getCategories(Long creatorId) {
        return categoryRepository.findAllByCreatorIdOrderBySortOrder(creatorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TipMenuCategory> getEnabledCategories(Long creatorId) {
        return categoryRepository.findAllByCreatorIdAndEnabledTrueOrderBySortOrder(creatorId);
    }

    @Transactional
    public TipMenuCategoryDto createCategory(Long creatorId, TipMenuCategoryDto dto) {
        if (categoryRepository.countByCreatorId(creatorId) >= 10) {
            throw new RuntimeException("Maximum of 10 categories reached.");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Category title is required.");
        }
        if (dto.getTitle().length() > 100) {
            throw new RuntimeException("Category title must be less than 100 characters.");
        }

        int nextOrder = (int) categoryRepository.countByCreatorId(creatorId);

        TipMenuCategory category = TipMenuCategory.builder()
                .creatorId(creatorId)
                .title(dto.getTitle().trim())
                .sortOrder(nextOrder)
                .enabled(true)
                .build();

        return mapToDto(categoryRepository.save(category));
    }

    @Transactional
    public TipMenuCategoryDto updateCategory(Long creatorId, UUID id, TipMenuCategoryDto dto) {
        TipMenuCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        if (!category.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Not authorized to update this category.");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Category title is required.");
        }
        if (dto.getTitle().length() > 100) {
            throw new RuntimeException("Category title must be less than 100 characters.");
        }

        category.setTitle(dto.getTitle().trim());
        if (dto.getSortOrder() >= 0) {
            category.setSortOrder(dto.getSortOrder());
        }
        category.setEnabled(dto.isEnabled());

        return mapToDto(categoryRepository.save(category));
    }

    @Transactional
    public TipMenuCategoryDto toggleEnabled(Long creatorId, UUID id) {
        TipMenuCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        if (!category.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Not authorized to update this category.");
        }

        category.setEnabled(!category.isEnabled());
        return mapToDto(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long creatorId, UUID id) {
        TipMenuCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        if (!category.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Not authorized to delete this category.");
        }

        categoryRepository.delete(category);
    }

    @Transactional
    public TipMenuCategoryDto reorderCategory(Long creatorId, UUID id, int newOrder) {
        TipMenuCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        if (!category.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Not authorized to reorder this category.");
        }

        category.setSortOrder(newOrder);
        return mapToDto(categoryRepository.save(category));
    }

    private TipMenuCategoryDto mapToDto(TipMenuCategory category) {
        return TipMenuCategoryDto.builder()
                .id(category.getId())
                .creatorId(category.getCreatorId())
                .title(category.getTitle())
                .sortOrder(category.getSortOrder())
                .enabled(category.isEnabled())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
