package com.joinlivora.backend.user;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.LegacyCreatorProfile;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceModerationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;

    @InjectMocks
    private UserService userService;

    private User admin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");

        targetUser = new User();
        targetUser.setId(2L);
        targetUser.setEmail("creator@test.com");
        targetUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void suspendUser_ShouldChangeStatusAndLogEvent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        userService.suspendUser(2L, admin, "Rules violation", "127.0.0.1", "TestAgent");

        assertEquals(UserStatus.SUSPENDED, targetUser.getStatus());
        verify(userRepository).save(targetUser);
        verify(auditService).logEvent(
                eq(new UUID(0L, 1L)),
                eq(AuditService.ACCOUNT_SUSPENDED),
                eq("USER"),
                eq(new UUID(0L, 2L)),
                any(),
                eq("127.0.0.1"),
                eq("TestAgent")
        );
    }

    @Test
    void unsuspendUser_ShouldChangeStatusAndLogEvent() {
        targetUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        userService.unsuspendUser(2L, admin, "Appeal accepted", "127.0.0.1", "TestAgent");

        assertEquals(UserStatus.ACTIVE, targetUser.getStatus());
        verify(userRepository).save(targetUser);
        verify(auditService).logEvent(
                eq(new UUID(0L, 1L)),
                eq(AuditService.ACCOUNT_UNSUSPENDED),
                eq("USER"),
                eq(new UUID(0L, 2L)),
                any(),
                eq("127.0.0.1"),
                eq("TestAgent")
        );
    }

    @Test
    void shadowbanCreator_ShouldSetFlagAndLogEvent() {
        UUID profileId = UUID.randomUUID();
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder()
                .id(profileId)
                .user(targetUser)
                .build();
        
        when(creatorProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        userService.shadowbanCreator(profileId, admin, "Shadowy business", "127.0.0.1", "TestAgent");

        assertTrue(targetUser.isShadowbanned());
        verify(userRepository).save(targetUser);
        verify(auditService).logEvent(
                eq(new UUID(0L, 1L)),
                eq(AuditService.USER_SHADOWBANNED),
                eq("USER"),
                eq(new UUID(0L, 2L)),
                any(),
                eq("127.0.0.1"),
                eq("TestAgent")
        );
    }
}








