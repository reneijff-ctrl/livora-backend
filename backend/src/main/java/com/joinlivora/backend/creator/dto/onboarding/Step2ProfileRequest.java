package com.joinlivora.backend.creator.dto.onboarding;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Step2ProfileRequest {
    private String realName;
    private LocalDate birthDate;
    private String gender;
    private String interestedIn;
    private String languages;
    private String location;
    private String bodyType;
    private Integer heightCm;
    private Integer weightKg;
    private String ethnicity;
    private String hairColor;
    private String eyeColor;
}
