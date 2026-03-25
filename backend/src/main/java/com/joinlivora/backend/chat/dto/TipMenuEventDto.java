package com.joinlivora.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipMenuEventDto {
    @Builder.Default
    private String type = "TIP_MENU";
    private List<TipActionDto> actions;
    private List<TipCategoryDto> categories;
    private List<TipActionDto> uncategorized;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TipActionDto {
        private Long amount;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TipCategoryDto {
        private String title;
        private List<TipActionDto> actions;
    }
}
