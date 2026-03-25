package com.joinlivora.backend.creator;

import com.joinlivora.backend.creator.service.CreatorProfileInitializer;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorProfileInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreatorProfileService creatorProfileService;

    @InjectMocks
    private CreatorProfileInitializer initializer;

    @Test
    void initialize_ShouldCallinitializeCreatorProfile_OnlyForCreators() {
        // Arrange
        User creator = new User();
        creator.setId(1L);
        creator.setRole(Role.CREATOR);

        User admin = new User();
        admin.setId(2L);
        admin.setRole(Role.ADMIN);

        // Mock creators page
        when(userRepository.findAllByRole(eq(Role.CREATOR), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(creator)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        
        // Act
        initializer.initialize();

        // Assert
        verify(creatorProfileService).initializeCreatorProfile(creator);
        verify(creatorProfileService, never()).initializeCreatorProfile(admin);
        verify(userRepository, never()).findAllByRole(eq(Role.ADMIN), any(PageRequest.class));
    }
}








