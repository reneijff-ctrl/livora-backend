package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.*;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.model.ProfileVisibility;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Handles mapping between CreatorProfile entities and various DTOs/response objects.
 * Extracted from CreatorProfileService to separate mapping concerns.
 */
@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CreatorProfileMapper {

    private final CreatorRepository creatorRepository;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final com.joinlivora.backend.livestream.repository.LivestreamSessionRepository livestreamSessionRepository;
    private final com.joinlivora.backend.creator.repository.CreatorPostRepository creatorPostRepository;
    private final UserRepository userRepository;
    private final CreatorFollowService followService;
    private final OnlineStatusService onlineStatusService;
    private final LiveViewerCounterService viewerCounterService;
    private final com.joinlivora.backend.monetization.TipGoalService tipGoalService;

    private String formatImageUrl(String url) {
        return UrlUtils.sanitizeUrl(url);
    }

    boolean isCreatorOnline(CreatorProfile profile) {
        return creatorRepository.findByUser_Id(profile.getUser().getId())
                .map(c -> onlineStatusService.isOnline(c.getId()))
                .orElse(false);
    }

    boolean isCreatorLive(CreatorProfile profile) {
        boolean isLive = !streamRepository.findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(profile.getUser()).isEmpty();
        if (!isLive) {
            log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {}, checking legacy sessions", profile.getUser().getId());
            return livestreamSessionRepository.existsByCreator_IdAndStatus(
                profile.getUser().getId(),
                com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE
            );
        }
        return true;
    }

    private com.joinlivora.backend.monetization.TipGoal getStructuredActiveGoal(Long userId, boolean isLive) {
        if (!isLive) return null;
        try {
            java.util.Optional<com.joinlivora.backend.monetization.TipGoal> goal = tipGoalService.getActiveGoal(userId);
            if (goal.isPresent()) return goal.get();

            return tipGoalService.getActiveGoalGroupEvent(userId)
                    .map(event -> com.joinlivora.backend.monetization.TipGoal.builder()
                            .title(event.getTitle())
                            .targetAmount(event.getTargetAmount())
                            .currentAmount(event.getCurrentAmount())
                            .active(true)
                            .creatorId(userId)
                            .build())
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error fetching active goal for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    public CreatorProfileDTO mapToDTO(CreatorProfile profile) {
        java.util.List<com.joinlivora.backend.streaming.Stream> liveStreams = streamRepository.findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(profile.getUser());
        com.joinlivora.backend.streaming.Stream stream = liveStreams.isEmpty() ? null : liveStreams.get(0);
        if (stream == null && isCreatorLive(profile)) {
            log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in mapToDTO", profile.getUser().getId());
        }

        return mapToDTO(profile, stream);
    }

    public CreatorProfileDTO mapToDTO(CreatorProfile profile, com.joinlivora.backend.streaming.Stream stream) {
        User user = profile.getUser();
        Long creatorId = creatorRepository.findByUser_Id(user.getId())
                .map(Creator::getId)
                .orElse(profile.getId());

        String streamKey = stream != null ? stream.getStreamKey() : null;
        String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                             (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

        boolean isLive = isCreatorLive(profile);
        com.joinlivora.backend.monetization.TipGoal activeGoal = getStructuredActiveGoal(user.getId(), isLive);

        return CreatorProfileDTO.builder()
                .id(profile.getId())
                .creatorId(creatorId)
                .userId(user.getId())
                .displayName(profile.getDisplayName())
                .username(profile.getUsername())
                .bio(profile.getBio())
                .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                .bannerUrl(formatImageUrl(profile.getBannerUrl()))
                .realName(profile.getRealName())
                .birthDate(profile.getBirthDate())
                .gender(profile.getGender())
                .interestedIn(profile.getInterestedIn())
                .languages(profile.getLanguages())
                .location(profile.getLocation())
                .bodyType(profile.getBodyType())
                .heightCm(profile.getHeightCm())
                .weightKg(profile.getWeightKg())
                .ethnicity(profile.getEthnicity())
                .hairColor(profile.getHairColor())
                .eyeColor(profile.getEyeColor())
                .onlyfansUrl(profile.getOnlyfansUrl())
                .throneUrl(profile.getThroneUrl())
                .wishlistUrl(profile.getWishlistUrl())
                .twitterUrl(profile.getTwitterUrl())
                .instagramUrl(profile.getInstagramUrl())
                .showAge(profile.isShowAge())
                .showLocation(profile.isShowLocation())
                .showLanguages(profile.isShowLanguages())
                .showBodyType(profile.isShowBodyType())
                .showEthnicity(profile.isShowEthnicity())
                .showHeightWeight(profile.isShowHeightWeight())
                .status(profile.getStatus())
                .visibility(profile.getVisibility())
                .isOnline(isCreatorOnline(profile))
                .isLive(isLive)
                .viewerCount(viewerCounterService.getViewerCount(user.getId()))
                .activeStreamThumbnailUrl(thumbnailUrl)
                .goalTitle(activeGoal != null ? activeGoal.getTitle() : null)
                .goalTargetTokens(activeGoal != null ? activeGoal.getTargetAmount() : null)
                .goalCurrentTokens(activeGoal != null ? activeGoal.getCurrentAmount() : null)
                .createdAt(profile.getCreatedAt())
                .followerCount(followService.getFollowerCount(user))
                .streamTitle(stream != null ? stream.getTitle() : null)
                .streamStartedAt(stream != null ? stream.getStartedAt() : null)
                .isPaid(stream != null && stream.isPaid())
                .admissionPrice(stream != null ? stream.getAdmissionPrice() : null)
                .streamCategory(stream != null ? stream.getStreamCategory() : null)
                .build();
    }

    public CreatorProfileDTO mapToDTO(CreatorProfile profile, User user) {
        if (profile != null) {
            return mapToDTO(profile);
        }
        String username = user.getUsername();
        return CreatorProfileDTO.builder()
                .id(user.getId())
                .displayName(username)
                .username(username)
                .bio("")
                .status(ProfileStatus.DRAFT)
                .visibility(ProfileVisibility.PRIVATE)
                .createdAt(user.getCreatedAt())
                .build();
    }

    public PublicCreatorProfileResponse mapToPublicResponse(CreatorProfile profile) {
        User user = profile.getUser();
        Long creatorId = creatorRepository.findByUser_Id(user.getId())
                .map(Creator::getId)
                .orElse(profile.getId());

        String username = profile.getUsername() != null ? profile.getUsername() :
                (user.getUsername() != null ? user.getUsername() : "user_" + user.getId());
        String displayName = profile.getDisplayName() != null ? profile.getDisplayName() : username;

        Long currentUserId = getCurrentUserId();
        boolean isOwner = currentUserId != null && currentUserId.equals(user.getId());
        
        boolean followedByCurrentUser = false;
        if (currentUserId != null && !isOwner) {
            User currentUser = userRepository.findById(currentUserId).orElse(null);
            followedByCurrentUser = followService.isFollowing(currentUser, user);
        }

        long streamCount = livestreamSessionRepository.countByCreator_Id(user.getId());
        double rating = 4.8; // Mock rating for now

        java.util.List<com.joinlivora.backend.streaming.Stream> liveStreams = streamRepository.findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(user);
        com.joinlivora.backend.streaming.Stream stream = liveStreams.isEmpty() ? null : liveStreams.get(0);
        if (stream == null && isCreatorLive(profile)) {
            log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in public profile", user.getId());
        }

        String streamKey = stream != null ? stream.getStreamKey() : null;

        String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                             (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

        boolean isLive = isCreatorLive(profile);
        com.joinlivora.backend.monetization.TipGoal activeGoal = getStructuredActiveGoal(user.getId(), isLive);

        return PublicCreatorProfileResponse.builder()
                .creatorId(creatorId)
                .userId(user.getId())
                .username(username)
                .displayName(displayName)
                .bio(profile.getBio())
                .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                .bannerUrl(formatImageUrl(profile.getBannerUrl()))
                .isOwner(isOwner)
                .followedByCurrentUser(followedByCurrentUser)
                .isOnline(isCreatorOnline(profile))
                .isLive(isLive)
                .viewerCount(viewerCounterService.getViewerCount(user.getId()))
                .activeStreamThumbnailUrl(streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null)
                .goalTitle(activeGoal != null ? activeGoal.getTitle() : null)
                .goalTargetTokens(activeGoal != null ? activeGoal.getTargetAmount() : null)
                .goalCurrentTokens(activeGoal != null ? activeGoal.getCurrentAmount() : null)
                .rating(rating)
                .streamCount(streamCount)
                .gender(profile.getGender())
                .interestedIn(profile.getInterestedIn())
                .languages(profile.getLanguages())
                .location(profile.getLocation())
                .bodyType(profile.getBodyType())
                .ethnicity(profile.getEthnicity())
                .eyeColor(profile.getEyeColor())
                .hairColor(profile.getHairColor())
                .heightCm(profile.getHeightCm())
                .weightKg(profile.getWeightKg())
                .birthDate(profile.getBirthDate())
                .showAge(profile.isShowAge())
                .showLocation(profile.isShowLocation())
                .showLanguages(profile.isShowLanguages())
                .showBodyType(profile.isShowBodyType())
                .showEthnicity(profile.isShowEthnicity())
                .showHeightWeight(profile.isShowHeightWeight())
                .build();
    }

    public PublicCreatorInfoResponse mapToInfoResponse(CreatorProfile profile) {
        User user = profile.getUser();
        Long creatorId = creatorRepository.findByUser_Id(user.getId())
                .map(Creator::getId)
                .orElse(profile.getId());

        long followers = followService.getFollowerCount(user);
        long postCount = creatorPostRepository.countByCreator(user);
        long streamCount = livestreamSessionRepository.countByCreator_Id(user.getId());
        double rating = 4.8; // Mock rating for now
        boolean isOnline = isCreatorOnline(profile);
        boolean isLive = isCreatorLive(profile);
        long viewerCount = viewerCounterService.getViewerCount(user.getId());

        Long currentUserId = getCurrentUserId();
        boolean isOwner = currentUserId != null && currentUserId.equals(user.getId());

        boolean followedByCurrentUser = false;
        if (currentUserId != null && !isOwner) {
            User currentUser = userRepository.findById(currentUserId).orElse(null);
            followedByCurrentUser = followService.isFollowing(currentUser, user);
        }

        java.util.Map<String, String> socialLinks = new java.util.HashMap<>();
        if (profile.getOnlyfansUrl() != null && !profile.getOnlyfansUrl().isBlank()) socialLinks.put("onlyfans", profile.getOnlyfansUrl());
        if (profile.getThroneUrl() != null && !profile.getThroneUrl().isBlank()) socialLinks.put("throne", profile.getThroneUrl());
        if (profile.getWishlistUrl() != null && !profile.getWishlistUrl().isBlank()) socialLinks.put("wishlist", profile.getWishlistUrl());
        if (profile.getTwitterUrl() != null && !profile.getTwitterUrl().isBlank()) socialLinks.put("twitter", profile.getTwitterUrl());
        if (profile.getInstagramUrl() != null && !profile.getInstagramUrl().isBlank()) socialLinks.put("instagram", profile.getInstagramUrl());

        java.util.List<com.joinlivora.backend.streaming.Stream> liveStreams2 = streamRepository.findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(user);
        com.joinlivora.backend.streaming.Stream stream = liveStreams2.isEmpty() ? null : liveStreams2.get(0);
        if (stream == null && isCreatorLive(profile)) {
            log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in public info", user.getId());
        }

        String streamKey = stream != null ? stream.getStreamKey() : null;

        String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                             (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

        com.joinlivora.backend.monetization.TipGoal activeGoal = getStructuredActiveGoal(user.getId(), isLive);

        return PublicCreatorInfoResponse.builder()
                .creatorId(creatorId)
                .userId(user.getId())
                .username(profile.getUsername() != null ? profile.getUsername() : "user_" + user.getId())
                .displayName(profile.getDisplayName())
                .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                .bio(profile.getBio())
                .bannerUrl(formatImageUrl(profile.getBannerUrl()))
                .followerCount(followers)
                .postCount(postCount)
                .streamCount(streamCount)
                .rating(rating)
                .isOnline(isOnline)
                .isLive(isLive)
                .viewerCount(viewerCount)
                .activeStreamThumbnailUrl(thumbnailUrl)
                .goalTitle(activeGoal != null ? activeGoal.getTitle() : null)
                .goalTargetTokens(activeGoal != null ? activeGoal.getTargetAmount() : null)
                .goalCurrentTokens(activeGoal != null ? activeGoal.getCurrentAmount() : null)
                .isOwner(isOwner)
                .followedByCurrentUser(followedByCurrentUser)
                .socialLinks(socialLinks)
                .gender(profile.getGender())
                .interestedIn(profile.getInterestedIn())
                .languages(profile.getLanguages())
                .location(profile.getLocation())
                .bodyType(profile.getBodyType())
                .ethnicity(profile.getEthnicity())
                .eyeColor(profile.getEyeColor())
                .hairColor(profile.getHairColor())
                .heightCm(profile.getHeightCm())
                .weightKg(profile.getWeightKg())
                .birthDate(profile.getBirthDate())
                .showAge(profile.isShowAge())
                .showLocation(profile.isShowLocation())
                .showLanguages(profile.isShowLanguages())
                .showBodyType(profile.isShowBodyType())
                .showEthnicity(profile.isShowEthnicity())
                .showHeightWeight(profile.isShowHeightWeight())
                .build();
    }
}
