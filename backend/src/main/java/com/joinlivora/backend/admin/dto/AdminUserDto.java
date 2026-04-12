package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.user.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String email;
    private String username;
    private AdminRole adminRole;
}
