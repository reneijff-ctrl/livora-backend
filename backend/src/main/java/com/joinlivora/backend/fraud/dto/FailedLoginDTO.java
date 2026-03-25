package com.joinlivora.backend.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedLoginDTO {
    private String email;
    private Instant timestamp;
    private String ipAddress;
    private String userAgent;
}
