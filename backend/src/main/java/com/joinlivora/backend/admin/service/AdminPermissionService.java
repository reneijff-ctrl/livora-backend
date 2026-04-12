package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.AdminRole;
import com.joinlivora.backend.user.Permission;
import com.joinlivora.backend.user.Role;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class AdminPermissionService {

    private static final Map<AdminRole, Set<Permission>> ROLE_PERMISSIONS = Map.of(
        AdminRole.CEO, EnumSet.allOf(Permission.class),

        AdminRole.ADMIN, EnumSet.of(
            Permission.CREATOR_APPROVE,
            Permission.CREATOR_REJECT,
            Permission.CREATOR_SUSPEND,
            Permission.CREATOR_VIEW,
            Permission.REPORTS_VIEW,
            Permission.STREAMS_MODERATE
        ),

        AdminRole.MODERATOR, EnumSet.of(
            Permission.CREATOR_APPROVE,
            Permission.CREATOR_REJECT,
            Permission.CREATOR_SUSPEND,
            Permission.CREATOR_VIEW
        ),

        AdminRole.SUPPORT, EnumSet.of(
            Permission.CREATOR_VIEW,
            Permission.REPORTS_VIEW
        )
    );

    public boolean hasPermission(UserPrincipal user, Permission permission) {
        if (user.getRole() != Role.ADMIN) return false;

        AdminRole adminRole = user.getAdminRole() != null ? user.getAdminRole() : AdminRole.ADMIN;

        return ROLE_PERMISSIONS
            .getOrDefault(adminRole, Set.of())
            .contains(permission);
    }

    public Set<Permission> getPermissions(UserPrincipal user) {
        if (user.getRole() != Role.ADMIN) return Set.of();

        AdminRole adminRole = user.getAdminRole() != null ? user.getAdminRole() : AdminRole.ADMIN;

        return ROLE_PERMISSIONS.getOrDefault(adminRole, Set.of());
    }
}
