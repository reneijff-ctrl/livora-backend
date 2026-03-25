package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorVerification;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorVerificationServiceTest {

    @Mock
    private CreatorVerificationRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private CreatorProfileRepository creatorProfileRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private CreatorVerificationService service;

    private User user;
    private Creator creator;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        creator = Creator.builder().id(1L).user(user).build();
    }

    @Test
    void updateIdImage_ShouldUpdateExistingVerification() {
        CreatorVerification verification = new CreatorVerification();
        verification.setCreator(creator);
        
        when(repository.findByCreator_User_Id(1L)).thenReturn(Optional.of(verification));
        
        service.updateIdImage(1L, "/path/to/id.jpg");
        
        assertEquals("/path/to/id.jpg", verification.getIdDocumentUrl());
        verify(repository).save(verification);
    }

    @Test
    void updateSelfieImage_ShouldUpdateExistingVerification() {
        CreatorVerification verification = new CreatorVerification();
        verification.setCreator(creator);
        
        when(repository.findByCreator_User_Id(1L)).thenReturn(Optional.of(verification));
        
        service.updateSelfieImage(1L, "/path/to/selfie.jpg");
        
        assertEquals("/path/to/selfie.jpg", verification.getSelfieDocumentUrl());
        verify(repository).save(verification);
    }

    @Test
    void updateIdImage_ShouldCreateNewVerification_ifNotExists() {
        when(repository.findByCreator_User_Id(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorRepository.findByUser_Id(1L)).thenReturn(Optional.of(creator));
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        
        service.updateIdImage(1L, "/path/to/id.jpg");
        
        verify(repository).save(any(CreatorVerification.class));
    }
}








