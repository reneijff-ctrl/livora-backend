package com.joinlivora.backend.creator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCreatorProfileRequest {
    
    @NotBlank(message = "Display name is required")
    @Size(max = 50, message = "Display name must not exceed 50 characters")
    @Pattern(regexp = "^[^<>]*$", message = "HTML tags are not allowed in display name")
    private String displayName;

    @Size(max = 300, message = "Bio must not exceed 300 characters")
    @Pattern(regexp = "^[^<>]*$", message = "HTML tags are not allowed in bio")
    private String bio;

    @Size(max = 512, message = "Profile image URL must not exceed 512 characters")
    private String profileImageUrl;

    @Size(max = 512, message = "Banner image URL must not exceed 512 characters")
    private String bannerUrl;

    private String username;
    private String gender;
    private String interestedIn;
    private String languages;
    private String location;
    private String bodyType;
    private String ethnicity;
    private String hairColor;
    private String eyeColor;
    private Integer heightCm;
    private Integer weightKg;

    @Size(max = 512, message = "OnlyFans URL must not exceed 512 characters")
    private String onlyfansUrl;

    @Size(max = 512, message = "Throne URL must not exceed 512 characters")
    private String throneUrl;

    @Size(max = 512, message = "Wishlist URL must not exceed 512 characters")
    private String wishlistUrl;

    @Size(max = 512, message = "Twitter URL must not exceed 512 characters")
    private String twitterUrl;

    @Size(max = 512, message = "Instagram URL must not exceed 512 characters")
    private String instagramUrl;

    private boolean showAge;
    private boolean showLocation;
    private boolean showLanguages;
    private boolean showBodyType;
    private boolean showEthnicity;
    private boolean showHeightWeight;

    // Support both naming conventions for better frontend compatibility
    public void setAvatarUrl(String avatarUrl) {
        this.profileImageUrl = avatarUrl;
    }

    public String getAvatarUrl() {
        return this.profileImageUrl;
    }
}
