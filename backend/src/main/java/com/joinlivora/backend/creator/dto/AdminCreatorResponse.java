package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.ProfileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatorResponse {
    private Long userId;
    private String email;
    private String displayName;
    private String username;
    private ProfileStatus status;
}
