package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.*;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.creator.monetization.CreatorMonetizationService;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.model.ProfileVisibility;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.presence.repository.CreatorPresenceRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorProfileServiceTest {

    @Mock
    private CreatorProfileRepository creatorProfileRepository;
    @Mock
    private CreatorPresenceRepository creatorPresenceRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private com.joinlivora.backend.creator.repository.CreatorPostRepository creatorPostRepository;
    @Mock
    private LegacyCreatorProfileRepository legacyCreatorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorFollowService followService;
    @Mock
    private com.joinlivora.backend.streaming.StreamRepository streamRepository;
    @Mock
    private com.joinlivora.backend.payout.CreatorEarningRepository earningRepository;
    @Mock
    private com.joinlivora.backend.payout.PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;
    @Mock
    private CreatorMonetizationService creatorMonetizationService;
    @Mock
    private com.joinlivora.backend.service.FileStorageService fileStorageService;
    @Mock
    private OnlineStatusService onlineStatusService;
    @Mock
    private com.joinlivora.backend.streaming.service.LiveViewerCounterService viewerCounterService;
    @Mock
    private org.springframework.core.env.Environment environment;

    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private CreatorProfileService creatorProfileService;

    private User creatorUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        creatorUser = new User();
        creatorUser.setId(1L);
        creatorUser.setEmail("creator@example.com");
        creatorUser.setUsername("creator");
        creatorUser.setRole(Role.CREATOR);

        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setEmail("user@example.com");
        regularUser.setUsername("regular");
        regularUser.setRole(Role.USER);

        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
    }

    private void mockUser(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    @Test
    void updateCreatorStatus_WhenProfilePending_ShouldAllowApproval() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creatorUser);
        profile.setStatus(ProfileStatus.PENDING);

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        creatorProfileService.updateCreatorStatus(1L, ProfileStatus.ACTIVE);

        assertEquals(ProfileStatus.ACTIVE, profile.getStatus());
        verify(creatorProfileRepository).save(profile);
    }

    @Test
    void updateCreatorStatus_WhenProfileDraft_ShouldRejectApproval() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creatorUser);
        profile.setStatus(ProfileStatus.DRAFT);

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThrows(IllegalStateException.class, () -> 
            creatorProfileService.updateCreatorStatus(1L, ProfileStatus.ACTIVE));
        
        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    void updateCreatorStatus_WhenProfileNotFound_ShouldThrowException() {
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            creatorProfileService.updateCreatorStatus(1L, ProfileStatus.ACTIVE));
    }

    @Test
    void getAdminCreators_ShouldReturnPagedResponse() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creatorUser);
        profile.setUsername("creator1");
        profile.setStatus(ProfileStatus.PENDING);

        org.springframework.data.domain.Page<CreatorProfile> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(profile));
        when(creatorProfileRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        org.springframework.data.domain.Page<com.joinlivora.backend.creator.dto.AdminCreatorResponse> result = 
            creatorProfileService.getAdminCreators(org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals("creator@example.com", result.getContent().get(0).getEmail());
        assertEquals(ProfileStatus.PENDING, result.getContent().get(0).getStatus());
    }

    @Test
    void getPublicProfile_ByUsername_WhenCreator_ShouldReturnProfile() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Creator Display");
        profile.setBio("Creator Bio");
        profile.setAvatarUrl("http://example.com/avatar.jpg");
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        
        Optional<PublicCreatorProfileResponse> result = creatorProfileService.getPublicProfile("creator_user");

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getCreatorId());
        assertEquals("creator_user", result.get().getUsername());
        assertEquals("Creator Display", result.get().getDisplayName());
        assertEquals("Creator Bio", result.get().getBio());
        assertEquals("http://example.com/avatar.jpg", result.get().getAvatarUrl());
    }

    @Test
    void getPublicProfile_WhenOwner_ShouldReturnIsOwnerTrue() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        
        UserPrincipal principal = new UserPrincipal(creatorUser);
        when(authentication.getPrincipal()).thenReturn(principal);

        Optional<PublicCreatorProfileResponse> result = creatorProfileService.getPublicProfile("creator_user");

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getCreatorId());
        assertTrue(result.get().isOwner());
    }

    @Test
    void getPublicProfile_WhenNotOwner_ShouldReturnIsOwnerFalse() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        
        UserPrincipal principal = new UserPrincipal(regularUser);
        when(authentication.getPrincipal()).thenReturn(principal);

        Optional<PublicCreatorProfileResponse> result = creatorProfileService.getPublicProfile("creator_user");

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getCreatorId());
        assertFalse(result.get().isOwner());
    }

    @Test
    void getPublicProfile_WhenFollowing_ShouldReturnFollowedByCurrentUserTrue() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        
        UserPrincipal principal = new UserPrincipal(regularUser);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(followService.isFollowing(regularUser, creatorUser)).thenReturn(true);

        Optional<PublicCreatorProfileResponse> result = creatorProfileService.getPublicProfile("creator_user");

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getCreatorId());
        assertTrue(result.get().isFollowedByCurrentUser());
    }

    @Test
    void getPublicProfile_WhenPendingAndDev_ShouldReturnProfile() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.PENDING);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        
        // environment is already mocked to return "dev" in setUp

        Optional<PublicCreatorProfileResponse> result = creatorProfileService.getPublicProfile("creator_user");

        assertTrue(result.isPresent());
        assertEquals(ProfileStatus.PENDING, profile.getStatus());
    }

    @Test
    void getPublicProfile_WhenPendingAndNotDev_ShouldReturnEmpty() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.PENDING);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        
        // Override environment mock for this test
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        Optional<PublicCreatorProfileResponse> result = creatorProfileService.getPublicProfile("creator_user");

        assertTrue(result.isEmpty());
    }

    @Test
    void getPublicProfile_NotFound_ShouldReturnEmpty() {
        when(creatorProfileRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertTrue(creatorProfileService.getPublicProfile("nonexistent").isEmpty());
    }

    @Test
    void getPublicCreatorsList_ShouldReturnOnlineCreators() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Creator Display");
        profile.setBio("Bio");
        profile.setAvatarUrl("http://avatar.jpg");

        when(creatorPresenceRepository.findOnlineCreators(any(Instant.class), anyBoolean()))
                .thenReturn(java.util.List.of(profile));

        java.util.List<PublicCreatorListResponse> result = creatorProfileService.getPublicCreatorsList();

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getCreatorId());
        assertEquals("Creator Display", result.get(0).getDisplayName());
        assertEquals("Bio", result.get(0).getBio());
        assertEquals("http://avatar.jpg", result.get(0).getAvatarUrl());
    }

    @Test
    void getPublicCreatorInfo_WhenCreatorAndActive_ShouldReturnInfo() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Creator Display");
        profile.setUsername("creator_user");
        profile.setBio("Bio");
        profile.setAvatarUrl("http://avatar.jpg");
        profile.setBannerUrl("http://banner.jpg");
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setVisibility(ProfileVisibility.PUBLIC);
        profile.setCreatedAt(Instant.now());

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));
        when(viewerCounterService.getViewerCount(creatorUser.getId())).thenReturn(150L);

        Optional<PublicCreatorInfoResponse> result = creatorProfileService.getPublicCreatorInfo("creator_user");

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getCreatorId());
        assertEquals("creator_user", result.get().getUsername());
        assertEquals("Creator Display", result.get().getDisplayName());
        assertEquals("http://avatar.jpg", result.get().getAvatarUrl());
        assertEquals("Bio", result.get().getBio());
        assertEquals("http://banner.jpg", result.get().getBannerUrl());
        assertEquals(150L, result.get().getViewerCount());
    }

    @Test
    void getPublicCreatorInfo_WhenCreatorButNotActive_ShouldReturnEmpty() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(creatorUser);
        profile.setUsername("creator_user");
        profile.setStatus(ProfileStatus.DRAFT);

        when(creatorProfileRepository.findByUsername("creator_user")).thenReturn(Optional.of(profile));

        Optional<PublicCreatorInfoResponse> result = creatorProfileService.getPublicCreatorInfo("creator_user");

        assertTrue(result.isEmpty());
    }

    @Test
    void getPublicCreatorInfo_WhenNotCreator_ShouldReturnEmpty() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUser(regularUser);
        profile.setUsername("regular_user");
        profile.setDisplayName("User Display");

        when(creatorProfileRepository.findByUsername("regular_user")).thenReturn(Optional.of(profile));

        Optional<PublicCreatorInfoResponse> result = creatorProfileService.getPublicCreatorInfo("regular_user");

        assertTrue(result.isEmpty());
    }

    @Test
    void getPublicCreatorInfo_WhenNotFound_ShouldReturnEmpty() {
        when(creatorProfileRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertTrue(creatorProfileService.getPublicCreatorInfo("nonexistent").isEmpty());
    }

    @Test
    void getProfileDTO_WhenProfileExists_ShouldReturnDTO() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Creator Display");
        profile.setBio("Creator Bio");
        profile.setAvatarUrl("http://example.com/avatar.jpg");
        profile.setUsername("creator_user");
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.of(profile));
        when(viewerCounterService.getViewerCount(creatorUser.getId())).thenReturn(150L);

        Optional<CreatorProfileDTO> result = creatorProfileService.getProfileDTO(creatorUser);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        assertEquals("creator_user", result.get().getUsername());
        assertEquals("Creator Display", result.get().getDisplayName());
        assertEquals("Creator Bio", result.get().getBio());
        assertEquals("http://example.com/avatar.jpg", result.get().getAvatarUrl());
        assertEquals(150L, result.get().getViewerCount());
    }

    @Test
    void getProfileDTO_WhenProfileDoesNotExist_ShouldReturnEmpty() {
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.empty());

        Optional<CreatorProfileDTO> result = creatorProfileService.getProfileDTO(creatorUser);

        assertTrue(result.isEmpty());
    }

    @Test
    void initializeCreatorProfile_WhenProfileExists_ShouldReturnExistingProfile() {
        CreatorProfile existingProfile = new CreatorProfile();
        existingProfile.setUser(creatorUser);
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.of(existingProfile));

        CreatorProfile result = creatorProfileService.initializeCreatorProfile(creatorUser);

        assertEquals(existingProfile, result);
        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    void initializeCreatorProfile_WhenProfileDoesNotExist_ShouldCreateAndReturnNewProfile() {
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.empty());
        when(creatorProfileRepository.save(any(CreatorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatorProfile result = creatorProfileService.initializeCreatorProfile(creatorUser);

        assertNotNull(result);
        assertEquals(creatorUser, result.getUser());
        assertEquals("creator", result.getDisplayName()); // "creator@example.com" split by @ is "creator"
        assertEquals("", result.getBio());
        assertNotNull(result.getCreatedAt());
        verify(creatorProfileRepository).save(any(CreatorProfile.class));
    }

    @Test
    void getMyProfile_ShouldReturnProfile() {
        mockUser(creatorUser);
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Creator Display");
        profile.setBio("Creator Bio");
        profile.setUsername("creator_user");
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        CreatorProfileDTO result = creatorProfileService.getMyProfile(1L);

        assertEquals(10L, result.getId());
        assertEquals("creator_user", result.getUsername());
        assertEquals("Creator Display", result.getDisplayName());
    }

    @Test
    void getMyProfile_WhenMissing_ShouldThrowNotFound() {
        mockUser(creatorUser);
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> creatorProfileService.getMyProfile(1L));
    }

    @Test
    void updateProfile_ShouldUpdateDisplayName() {
        mockUser(creatorUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(creatorUser));
        
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Old Name");

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .displayName("New Name")
                .build();

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.save(any(CreatorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatorProfileDTO result = creatorProfileService.updateProfile(1L, request);

        assertEquals("New Name", result.getDisplayName());
        verify(creatorProfileRepository).save(profile);
    }

    @Test
    void updateProfile_WithNulls_ShouldNotUpdateFields() {
        mockUser(creatorUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(creatorUser));

        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setDisplayName("Old Name");
        profile.setBio("Old Bio");
        profile.setAvatarUrl("http://old.com/img.jpg");

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder()
                .build();

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.save(any(CreatorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatorProfileDTO result = creatorProfileService.updateProfile(1L, request);

        assertEquals("Old Name", result.getDisplayName());
        assertEquals("Old Bio", result.getBio());
        assertEquals("http://old.com/img.jpg", result.getAvatarUrl());
    }

    @Test
    void updateProfile_WhenProfileMissing_ShouldThrowResourceNotFound() {
        mockUser(creatorUser);
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder().displayName("Name").build();
        assertThrows(ResourceNotFoundException.class, () -> 
            creatorProfileService.updateProfile(1L, request)
        );
    }

    @Test
    void updateProfile_AsNonCreator_ShouldThrowAccessDenied() {
        mockUser(regularUser);
        UpdateCreatorProfileRequest request = UpdateCreatorProfileRequest.builder().displayName("Name").build();
        // Since we now pass creator to service, the "ownership check" is done by ensureOwnership
        assertThrows(com.joinlivora.backend.exception.PermissionDeniedException.class, () -> 
            creatorProfileService.updateProfile(1L, request)
        );
    }

    @Test
    void publishProfile_ShouldWork_WhenFieldsPresent() {
        CreatorProfile profile = new CreatorProfile();
        profile.setId(10L);
        profile.setUser(creatorUser);
        profile.setUsername("test_user");
        profile.setBio("My Bio");
        profile.setStatus(ProfileStatus.DRAFT);
        profile.setVisibility(ProfileVisibility.PRIVATE);

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.save(any(CreatorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatorProfileDTO result = creatorProfileService.publishProfile(1L);

        assertEquals(ProfileStatus.ACTIVE, result.getStatus());
        assertEquals(ProfileVisibility.PUBLIC, result.getVisibility());
        verify(creatorProfileRepository).save(profile);
    }

    @Test
    void publishProfile_ShouldThrowException_WhenUsernameMissing() {
        CreatorProfile profile = new CreatorProfile();
        profile.setBio("My Bio");
        // username is null

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThrows(IllegalStateException.class, () -> creatorProfileService.publishProfile(1L));
    }

    @Test
    void publishProfile_ShouldThrowException_WhenBioMissing() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUsername("test_user");
        profile.setBio(""); // blank bio

        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThrows(IllegalStateException.class, () -> creatorProfileService.publishProfile(1L));
    }

    @Test
    void initializeCreatorProfile_WhenProfileAndEarningsMissing_ShouldCreateBoth() {
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.empty());
        when(payoutCreatorEarningsRepository.findByCreator(creatorUser)).thenReturn(Optional.empty());
        when(creatorProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        creatorProfileService.initializeCreatorProfile(creatorUser);

        verify(creatorProfileRepository).save(any(CreatorProfile.class));
        verify(payoutCreatorEarningsRepository).save(any(com.joinlivora.backend.payout.CreatorEarnings.class));
        verify(creatorMonetizationService).getOrCreateForCreator(any(CreatorProfile.class));
    }

    @Test
    void initializeCreatorProfile_WhenProfileExistsButEarningsMissing_ShouldCreateEarnings() {
        CreatorProfile profile = new CreatorProfile();
        profile.setUsername("existing_user");
        profile.setPublicHandle("existing_user");
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.of(profile));
        when(payoutCreatorEarningsRepository.findByCreator(creatorUser)).thenReturn(Optional.empty());

        creatorProfileService.initializeCreatorProfile(creatorUser);

        verify(creatorProfileRepository, never()).save(any());
        verify(payoutCreatorEarningsRepository).save(any(com.joinlivora.backend.payout.CreatorEarnings.class));
        verify(creatorMonetizationService).getOrCreateForCreator(profile);
    }

    @Test
    void getPublicIdentifier_WithUsername_ShouldReturnUsername() {
        CreatorProfile profile = CreatorProfile.builder()
                .user(creatorUser)
                .username("test-slug")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(creatorUser));
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        CreatorIdentifierDTO result = creatorProfileService.getPublicIdentifier(1L);

        assertEquals("test-slug", result.getIdentifier());
    }

    @Test
    void getPublicIdentifier_WithoutUsername_ShouldReturnUserPrefix() {
        CreatorProfile profile = CreatorProfile.builder()
                .user(creatorUser)
                .username(null)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(creatorUser));
        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.of(profile));
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        CreatorIdentifierDTO result = creatorProfileService.getPublicIdentifier(1L);

        assertEquals("user_1", result.getIdentifier());
    }

    @Test
    void getPublicIdentifier_UserNotFound_ShouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> creatorProfileService.getPublicIdentifier(1L));
    }

    @Test
    void getDashboardSummary_ShouldReturnPublicIdentifier() {
        creatorUser.setStatus(UserStatus.ACTIVE);

        CreatorProfile profile = CreatorProfile.builder()
                .id(10L)
                .user(creatorUser)
                .username("test-slug")
                .displayName("Test User")
                .status(ProfileStatus.ACTIVE)
                .build();

        when(creatorProfileRepository.findByUser(creatorUser)).thenReturn(Optional.of(profile));
        when(earningRepository.sumTotalNetEarningsByCreator(creatorUser)).thenReturn(new BigDecimal("100.00"));

        CreatorDashboardSummary result = creatorProfileService.getDashboardSummary(creatorUser);

        assertEquals(10L, result.getId());
        assertEquals("test-slug", result.getPublicIdentifier());
        assertEquals("Test User", result.getDisplayName());
        assertEquals(new BigDecimal("100.00"), result.getTotalEarnings());
    }
    @Test
    void getCreators_ShouldFilterByCategoryAndCountry() {
        User user1 = new User(); user1.setId(1L);
        User user2 = new User(); user2.setId(2L);
        User user3 = new User(); user3.setId(3L);

        CreatorProfile femaleUS = CreatorProfile.builder()
                .id(1L)
                .user(user1)
                .gender("female")
                .location("USA")
                .build();
        CreatorProfile maleUK = CreatorProfile.builder()
                .id(2L)
                .user(user2)
                .gender("male")
                .location("UK")
                .build();
        CreatorProfile other = CreatorProfile.builder()
                .id(3L)
                .user(user3)
                .gender("trans")
                .location(null)
                .build();

        when(creatorProfileRepository.findAll()).thenReturn(java.util.List.of(femaleUS, maleUK, other));
        lenient().when(creatorRepository.findByUser_Id(any())).thenReturn(Optional.empty());
        lenient().when(streamRepository.findAllByCreatorInAndIsLiveTrue(any())).thenReturn(java.util.Collections.emptyList());

        // Test Category filter
        java.util.List<CreatorProfileDTO> result = creatorProfileService.getCreators("women", null, "all", null, null, null, null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertEquals(1, result.size());
        assertEquals("female", result.get(0).getGender());

        // Test Country filter
        result = creatorProfileService.getCreators("featured", null, "UK", null, null, null, null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertEquals(1, result.size());
        assertEquals("UK", result.get(0).getLocation());

        // Test "other" country filter
        result = creatorProfileService.getCreators("featured", null, "other", null, null, null, null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertEquals(1, result.size());
        assertNull(result.get(0).getLocation());

        // Test combined filter
        result = creatorProfileService.getCreators("men", null, "UK", null, null, null, null, null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertEquals(1, result.size());
        assertEquals("male", result.get(0).getGender());
        assertEquals("UK", result.get(0).getLocation());
    }
}








