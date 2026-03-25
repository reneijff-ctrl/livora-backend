package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminResponseDTO {
    private Long id;
    private String email;
    private Role role;
    private UserStatus status;
    private FraudRiskLevel fraudRiskLevel;
    private boolean payoutsEnabled;
    private boolean shadowbanned;
    private Instant createdAt;
}
