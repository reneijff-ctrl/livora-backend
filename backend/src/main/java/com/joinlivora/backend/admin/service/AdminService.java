package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.admin.dto.UserAdminResponseDTO;
import com.joinlivora.backend.admin.dto.UserFilterRequestDTO;
import com.joinlivora.backend.email.event.PayoutStatusChangedEvent;
import com.joinlivora.backend.email.event.UserStatusChangedEvent;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Page<UserAdminResponseDTO> getUsers(UserFilterRequestDTO filter, Pageable pageable) {
        Specification<User> spec = Specification.where(null);

        if (filter.getEmail() != null && !filter.getEmail().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("email"), "%" + filter.getEmail() + "%"));
        }
        if (filter.getRole() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), filter.getRole()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }

        return userRepository.findAll(spec, pageable).map(this::convertToAdminResponse);
    }

    public UserAdminResponseDTO getUser(Long userId) {
        return userRepository.findById(userId)
                .map(this::convertToAdminResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public void updateUserStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(status);
        userRepository.save(user);

        // Notify user about status change
        eventPublisher.publishEvent(new UserStatusChangedEvent(this, user, "Administrative action"));
    }

    @Transactional
    @CacheEvict(value = "publicContent", allEntries = true)
    public void shadowbanUser(Long userId, boolean shadowbanned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setShadowbanned(shadowbanned);
        userRepository.save(user);
    }

    @Transactional
    public void togglePayouts(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPayoutsEnabled(enabled);
        userRepository.save(user);

        // Notify user about payout status change
        String reason = enabled ? "Manual restoration by administrator" : "Manual lock by administrator";
        eventPublisher.publishEvent(new PayoutStatusChangedEvent(this, user, enabled, reason));
    }

    @Transactional
    public void forceLogout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setSessionsInvalidatedAt(Instant.now());
        userRepository.save(user);
    }

    private UserAdminResponseDTO convertToAdminResponse(User user) {
        return UserAdminResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .fraudRiskLevel(user.getFraudRiskLevel())
                .payoutsEnabled(user.isPayoutsEnabled())
                .shadowbanned(user.isShadowbanned())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
