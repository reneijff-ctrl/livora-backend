package com.joinlivora.backend.creator.dto.onboarding;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Step1BasicsRequest {
    private String username;
    private String displayName;
    private String bio;
    private String profilePicture;
}
