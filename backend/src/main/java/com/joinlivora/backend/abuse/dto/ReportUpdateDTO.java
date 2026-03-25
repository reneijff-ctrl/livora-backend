package com.joinlivora.backend.abuse.dto;

import com.joinlivora.backend.abuse.model.ReportStatus;
import lombok.Data;

@Data
public class ReportUpdateDTO {
    private ReportStatus status;
}
