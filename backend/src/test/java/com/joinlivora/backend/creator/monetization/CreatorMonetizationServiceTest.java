package com.joinlivora.backend.creator.monetization;

import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CreatorMonetizationServiceTest {

    @Mock
    private CreatorMonetizationRepository repository;

    @InjectMocks
    private CreatorMonetizationService service;

    private CreatorProfile profile;

    @BeforeEach
    void setUp() {
        profile = new CreatorProfile();
        profile.setId(1L);
        profile.setUsername("test_creator");
    }

    @Test
    void getOrCreateForCreator_ShouldReturnExisting_WhenFound() {
        // Given
        CreatorMonetization existing = CreatorMonetization.builder()
                .creator(profile)
                .subscriptionPrice(new BigDecimal("15.00"))
                .tipEnabled(false)
                .build();
        when(repository.findByCreator(profile)).thenReturn(Optional.of(existing));

        // When
        CreatorMonetization result = service.getOrCreateForCreator(profile);

        // Then
        assertEquals(existing, result);
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateForCreator_ShouldCreateNew_WhenNotFound() {
        // Given
        when(repository.findByCreator(profile)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CreatorMonetization result = service.getOrCreateForCreator(profile);

        // Then
        assertNotNull(result);
        assertEquals(profile, result.getCreator());
        assertEquals(0, new BigDecimal("9.99").compareTo(result.getSubscriptionPrice()));
        assertTrue(result.isTipEnabled());
        verify(repository).save(any());
    }

    @Test
    void createDefaultMonetization_ShouldSaveAndReturn() {
        // Given
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CreatorMonetization result = service.createDefaultMonetization(profile);

        // Then
        assertNotNull(result);
        assertEquals(profile, result.getCreator());
        verify(repository).save(any());
    }

    @Test
    void save_ShouldCallRepository() {
        // Given
        CreatorMonetization monetization = new CreatorMonetization();
        when(repository.save(monetization)).thenReturn(monetization);

        // When
        CreatorMonetization result = service.save(monetization);

        // Then
        assertEquals(monetization, result);
        verify(repository).save(monetization);
    }
}








