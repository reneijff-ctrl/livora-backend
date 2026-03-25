package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatorPublicDto {
    private Long id;
    private String displayName;
    private String profileImageUrl;
    private String bio;
}
