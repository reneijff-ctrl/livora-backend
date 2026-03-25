package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.CreatorApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorApplicationResponse {
    private CreatorApplicationStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String reviewNotes;
}
