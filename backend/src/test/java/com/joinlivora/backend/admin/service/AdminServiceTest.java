package com.joinlivora.backend.admin.service;

import com.joinlivora.backend.admin.dto.UserFilterRequestDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminService adminService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setPayoutsEnabled(true);
        user.setShadowbanned(false);
    }

    @Test
    void getUsers_shouldReturnPagedUsers() {
        UserFilterRequestDTO filter = new UserFilterRequestDTO();
        PageRequest pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(user));

        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);

        var result = adminService.getUsers(filter, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("test@example.com", result.getContent().get(0).getEmail());
    }

    @Test
    void updateUserStatus_shouldUpdateStatusAndPublishEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.updateUserStatus(1L, UserStatus.SUSPENDED);

        assertEquals(UserStatus.SUSPENDED, user.getStatus());
        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(any(com.joinlivora.backend.email.event.UserStatusChangedEvent.class));
    }

    @Test
    void shadowbanUser_shouldUpdateShadowbanned() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.shadowbanUser(1L, true);

        assertTrue(user.isShadowbanned());
        verify(userRepository).save(user);
    }

    @Test
    void togglePayouts_shouldUpdatePayoutsEnabledAndPublishEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.togglePayouts(1L, false);

        assertFalse(user.isPayoutsEnabled());
        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(any(com.joinlivora.backend.email.event.PayoutStatusChangedEvent.class));
    }

    @Test
    void forceLogout_shouldSetSessionsInvalidatedAt() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.forceLogout(1L);

        assertNotNull(user.getSessionsInvalidatedAt());
        verify(userRepository).save(user);
    }
}








