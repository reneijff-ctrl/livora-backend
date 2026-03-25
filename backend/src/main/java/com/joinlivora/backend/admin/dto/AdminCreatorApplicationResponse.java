package com.joinlivora.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatorApplicationResponse {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String status;
    private boolean termsAccepted;
    private boolean ageVerified;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String reviewNotes;
}
