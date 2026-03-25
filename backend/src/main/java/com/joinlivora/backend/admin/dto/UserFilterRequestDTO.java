package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.UserStatus;
import lombok.Data;

@Data
public class UserFilterRequestDTO {
    private String email;
    private Role role;
    private UserStatus status;
}
