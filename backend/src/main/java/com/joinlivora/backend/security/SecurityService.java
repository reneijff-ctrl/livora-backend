package com.joinlivora.backend.security;

import com.joinlivora.backend.admin.service.AdminPermissionService;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final AdminPermissionService adminPermissionService;

    public boolean isActiveCreator(Long userId) {
        return creatorProfileRepository.findByUserId(userId)
                .map(profile -> profile.getStatus() == ProfileStatus.ACTIVE)
                .orElse(false);
    }

    public boolean hasPermission(String permission) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal user)) return false;

        return adminPermissionService.hasPermission(user, Permission.valueOf(permission));
    }
}
