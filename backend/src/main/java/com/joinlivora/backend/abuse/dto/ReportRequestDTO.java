package com.joinlivora.backend.abuse.dto;

import com.joinlivora.backend.abuse.model.ReportReason;
import lombok.Data;

import java.util.UUID;

@Data
public class ReportRequestDTO {
    private Long targetUserId;
    private UUID targetStreamId;
    private ReportReason reason;
}
