package com.joinlivora.backend.content.dto;

import com.joinlivora.backend.content.AccessLevel;
import com.joinlivora.backend.content.ContentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Thumbnail URL is required")
    private String thumbnailUrl;

    @NotBlank(message = "Media URL is required")
    private String mediaUrl;

    @NotNull(message = "Access level is required")
    private AccessLevel accessLevel;

    @NotNull(message = "Content type is required")
    private ContentType type; // PHOTO, VIDEO, CLIP

    @NotNull(message = "Unlock price is required")
    @Min(value = 10, message = "Minimum unlock price is 10 tokens")
    @Max(value = 5000, message = "Maximum unlock price is 5000 tokens")
    private Integer unlockPriceTokens;
}
