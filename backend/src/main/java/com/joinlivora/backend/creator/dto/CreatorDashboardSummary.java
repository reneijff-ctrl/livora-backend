package com.joinlivora.backend.creator.dto;

import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.user.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorDashboardSummary {
    private Long id;
    private String publicIdentifier;
    private String displayName;
    private BigDecimal totalEarnings;
    private UserStatus accountStatus;
    private ProfileStatus profileStatus;
}
