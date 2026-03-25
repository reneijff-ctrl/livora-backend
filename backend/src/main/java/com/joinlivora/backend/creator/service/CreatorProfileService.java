package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.*;
import com.joinlivora.backend.creator.follow.service.CreatorFollowService;
import com.joinlivora.backend.creator.monetization.CreatorMonetizationService;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorMonetization;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.ProfileStatus;
import com.joinlivora.backend.creator.model.ProfileVisibility;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.presence.repository.CreatorPresenceRepository;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.exception.PermissionDeniedException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payout.CreatorEarnings;
import com.joinlivora.backend.monetization.TipGoal;
import com.joinlivora.backend.payout.LegacyCreatorProfileRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.security.UserPrincipal;
import com.joinlivora.backend.service.StoreResult;
import com.joinlivora.backend.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CreatorProfileService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorPresenceRepository creatorPresenceRepository;
    private final CreatorRepository creatorRepository;
    private final com.joinlivora.backend.creator.repository.CreatorPostRepository creatorPostRepository;
    private final LegacyCreatorProfileRepository legacyCreatorProfileRepository;
    private final UserRepository userRepository;
    private final CreatorFollowService followService;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final com.joinlivora.backend.livestream.repository.LivestreamSessionRepository livestreamSessionRepository;
    private final com.joinlivora.backend.payout.CreatorEarningRepository earningRepository;
    private final com.joinlivora.backend.payout.PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;
    private final CreatorMonetizationService creatorMonetizationService;
    private final com.joinlivora.backend.service.FileStorageService fileStorageService;
    private final OnlineStatusService onlineStatusService;
    private final LiveViewerCounterService viewerCounterService;
    private final com.joinlivora.backend.monetization.TipGoalService tipGoalService;
    private final Environment environment;
    private final Random random = new Random();

    private boolean isDev() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private boolean isDevOrTest() {
        if (environment == null) return false;
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return profiles.contains("dev") || profiles.contains("test") || profiles.isEmpty();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminCreatorResponse> getAdminCreators(Pageable pageable) {
        return creatorProfileRepository.findAll(pageable)
                .map(profile -> AdminCreatorResponse.builder()
                        .userId(profile.getUser().getId())
                        .email(profile.getUser().getEmail())
                        .displayName(profile.getDisplayName())
                        .username(profile.getUsername())
                        .status(profile.getStatus())
                        .build());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<com.joinlivora.backend.creator.dto.AdminCreatorStripeStatusResponse> getAdminCreatorsStripeStatus(Pageable pageable) {
        return userRepository.findAllByRole(Role.CREATOR, pageable)
                .map(u -> com.joinlivora.backend.creator.dto.AdminCreatorStripeStatusResponse.builder()
                        .userId(u.getId())
                        .email(u.getEmail())
                        .stripeAccountId(u.getStripeAccountId())
                        .payoutsEnabled(u.isPayoutsEnabled())
                        .stripeOnboardingComplete(u.isStripeOnboardingComplete())
                        .build());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateCreatorStatus(Long userId, ProfileStatus newStatus) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));
        
        log.info("Admin updating creator profile status for user {}: {} -> {}", userId, profile.getStatus(), newStatus);

        // Reject approval attempts for DRAFT creators. Only PENDING can be approved.
        if (newStatus == ProfileStatus.ACTIVE && profile.getStatus() != ProfileStatus.PENDING) {
            throw new IllegalStateException("Only creators in PENDING status can be approved. Current status: " + profile.getStatus());
        }

        profile.setStatus(newStatus);
        if (newStatus == ProfileStatus.ACTIVE) {
            profile.setVisibility(ProfileVisibility.PUBLIC);
        }
        creatorProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public Page<PublicCreatorDiscoveryResponse> getCreatorsDiscovery(Pageable pageable) {
        Instant threshold = Instant.now().minusSeconds(60);
        boolean includeOffline = isDevOrTest();
        Page<CreatorProfile> profiles = creatorPresenceRepository.findOnlineCreators(threshold, includeOffline, pageable);

        if (profiles.isEmpty()) {
            return Page.empty(pageable);
        }

        List<User> users = profiles.stream().map(CreatorProfile::getUser).collect(Collectors.toList());
        
        // Use Unified StreamRepository for discovery mapping
        Map<Long, com.joinlivora.backend.streaming.Stream> streamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(users).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), com.joinlivora.backend.creator.model.Creator::getId));

        return profiles.map(profile -> {
            com.joinlivora.backend.streaming.Stream stream = streamMap.get(profile.getUser().getId());
            String streamKey = stream != null ? stream.getStreamKey() : null;
            
            // Fallback for metadata if unified stream is missing
            if (stream == null && isCreatorLive(profile)) {
                log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in discovery", profile.getUser().getId());
            }
            
            String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                                 (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

            Long creatorId = userToCreatorIdMap.getOrDefault(profile.getUser().getId(), profile.getId());

            boolean isLive = isCreatorLive(profile);
            com.joinlivora.backend.monetization.TipGoal activeGoal = getStructuredActiveGoal(profile.getUser().getId(), isLive);
            return PublicCreatorDiscoveryResponse.builder()
                    .userId(profile.getUser().getId())
                    .creatorId(creatorId)
                    .username(profile.getUsername() != null ? profile.getUsername() : "user_" + profile.getUser().getId())
                    .displayName(profile.getDisplayName())
                    .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                    .shortBio(profile.getBio())
                    .isOnline(isCreatorOnline(profile))
                    .isLive(isLive)
                    .viewerCount(viewerCounterService.getViewerCount(profile.getUser().getId()))
                    .activeStreamThumbnailUrl(thumbnailUrl)
                    .goalTitle(activeGoal != null ? activeGoal.getTitle() : null)
                    .goalTargetTokens(activeGoal != null ? activeGoal.getTargetAmount() : null)
                    .goalCurrentTokens(activeGoal != null ? activeGoal.getCurrentAmount() : null)
                    .createdAt(profile.getCreatedAt())
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public List<HomepageCreatorDto> getPublicCreatorsForHomepage() {
        Instant threshold = Instant.now().minusSeconds(60);
        boolean includeOffline = isDevOrTest();
        List<CreatorProfile> profiles = creatorPresenceRepository.findOnlineCreators(threshold, includeOffline);
        
        if (profiles.isEmpty()) return Collections.emptyList();

        List<User> users = profiles.stream().map(CreatorProfile::getUser).collect(Collectors.toList());
        
        // Use Unified StreamRepository for homepage mapping
        Map<Long, com.joinlivora.backend.streaming.Stream> streamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(users).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), com.joinlivora.backend.creator.model.Creator::getId));

        return profiles.stream()
                .map(profile -> {
                    com.joinlivora.backend.streaming.Stream stream = streamMap.get(profile.getUser().getId());
                    String streamKey = stream != null ? stream.getStreamKey() : null;
                    
                    if (stream == null && isCreatorLive(profile)) {
                        log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in homepage", profile.getUser().getId());
                    }

                    String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                                         (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

                    boolean isLive = isCreatorLive(profile);
                    com.joinlivora.backend.monetization.TipGoal activeGoal = getStructuredActiveGoal(profile.getUser().getId(), isLive);
                    return HomepageCreatorDto.builder()
                            .userId(profile.getUser().getId())
                            .creatorId(userToCreatorIdMap.getOrDefault(profile.getUser().getId(), profile.getId()))
                            .displayName(profile.getDisplayName())
                            .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                            .isOnline(isCreatorOnline(profile))
                            .isLive(isLive)
                            .activeStreamDescription(stream != null ? stream.getTitle() : null)
                            .activeStreamThumbnailUrl(thumbnailUrl)
                            .goalTitle(activeGoal != null ? activeGoal.getTitle() : null)
                            .goalTargetTokens(activeGoal != null ? activeGoal.getTargetAmount() : null)
                            .goalCurrentTokens(activeGoal != null ? activeGoal.getCurrentAmount() : null)
                            .viewerCount(viewerCounterService.getViewerCount(profile.getUser().getId()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String formatImageUrl(String url) {
        return UrlUtils.sanitizeUrl(url);
    }

    @Transactional(readOnly = true)
    public List<PublicCreatorListResponse> getPublicCreatorsList() {
        Instant threshold = Instant.now().minusSeconds(60);
        boolean includeOffline = isDevOrTest();
        List<CreatorProfile> profiles = creatorPresenceRepository.findOnlineCreators(threshold, includeOffline);

        if (profiles.isEmpty()) return Collections.emptyList();

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(
                profiles.stream().map(CreatorProfile::getUser).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(c -> c.getUser().getId(), Creator::getId));

        return profiles.stream()
                .map(profile -> PublicCreatorListResponse.builder()
                        .userId(profile.getUser().getId())
                        .creatorId(userToCreatorIdMap.getOrDefault(profile.getUser().getId(), profile.getId()))
                        .username(profile.getUsername() != null ? profile.getUsername() : "user_" + profile.getUser().getId())
                        .displayName(profile.getDisplayName())
                        .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                        .bio(profile.getBio())
                        .online(isCreatorOnline(profile))
                        .viewerCount(viewerCounterService.getViewerCount(profile.getUser().getId()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CreatorProfileDTO> getCreators(String category, String search, String country,
                                                Boolean liveOnly, String paidFilter, String sort,
                                                String bodyType, String hairColor, String eyeColor, String ethnicity,
                                                String interestedIn, String language,
                                                Pageable pageable) {
        List<CreatorProfile> allProfiles = creatorProfileRepository.findAll();

        // Pre-load all live streams for filtering/sorting
        List<User> allUsers = allProfiles.stream().map(CreatorProfile::getUser).collect(Collectors.toList());
        Map<Long, com.joinlivora.backend.streaming.Stream> allStreamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(allUsers).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        List<CreatorProfile> filteredProfiles = allProfiles.stream()
                .filter(c -> categoryMatches(c, category))
                .filter(c -> countryMatches(c, country))
                .filter(c -> searchMatches(c, search))
                .filter(c -> liveOnlyMatches(c, liveOnly))
                .filter(c -> paidFilterMatches(c, paidFilter, allStreamMap))
                .filter(c -> bodyTypeMatches(c, bodyType))
                .filter(c -> hairColorMatches(c, hairColor))
                .filter(c -> eyeColorMatches(c, eyeColor))
                .filter(c -> ethnicityMatches(c, ethnicity))
                .filter(c -> interestedInMatches(c, interestedIn))
                .filter(c -> languageMatches(c, language))
                .toList();

        // Sort the filtered results
        List<CreatorProfile> sortedProfiles = sortProfiles(filteredProfiles, sort, allStreamMap);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), sortedProfiles.size());

        List<CreatorProfile> pagedProfiles;
        if (start >= sortedProfiles.size()) {
            pagedProfiles = Collections.emptyList();
        } else {
            pagedProfiles = sortedProfiles.subList(start, end);
        }

        if (pagedProfiles.isEmpty()) return Page.empty(pageable);

        List<CreatorProfileDTO> content = pagedProfiles.stream()
                .map(profile -> mapToDTO(profile, allStreamMap.get(profile.getUser().getId())))
                .toList();

        return new org.springframework.data.domain.PageImpl<>(content, pageable, sortedProfiles.size());
    }

    private boolean searchMatches(CreatorProfile c, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        return (c.getDisplayName() != null && c.getDisplayName().toLowerCase().contains(q))
                || (c.getUsername() != null && c.getUsername().toLowerCase().contains(q));
    }

    private boolean categoryMatches(CreatorProfile c, String category) {
        if (category == null || category.equalsIgnoreCase("featured")) return true;
        if (c.getGender() == null) return false;

        return switch(category.toLowerCase()) {
            case "women" -> c.getGender().equalsIgnoreCase("female");
            case "men" -> c.getGender().equalsIgnoreCase("male");
            case "couples" -> c.getGender().equalsIgnoreCase("couple");
            case "trans" -> c.getGender().equalsIgnoreCase("trans");
            default -> true;
        };
    }

    private boolean countryMatches(CreatorProfile c, String country) {
        if (country == null || country.equalsIgnoreCase("all")) return true;
        if (country.equalsIgnoreCase("other")) {
            return c.getLocation() == null || c.getLocation().isBlank();
        }

        if (c.getLocation() == null) return false;

        return c.getLocation().equalsIgnoreCase(country);
    }

    private boolean liveOnlyMatches(CreatorProfile c, Boolean liveOnly) {
        if (liveOnly == null || !liveOnly) return true;
        return isCreatorLive(c);
    }

    private boolean paidFilterMatches(CreatorProfile c, String paidFilter, Map<Long, com.joinlivora.backend.streaming.Stream> streamMap) {
        if (paidFilter == null || paidFilter.equalsIgnoreCase("all")) return true;
        com.joinlivora.backend.streaming.Stream stream = streamMap.get(c.getUser().getId());
        if (stream == null) return false; // not live -> can't determine paid/free, exclude from paid/free filter
        if (paidFilter.equalsIgnoreCase("paid")) return stream.isPaid();
        if (paidFilter.equalsIgnoreCase("free")) return !stream.isPaid();
        return true;
    }

    private boolean bodyTypeMatches(CreatorProfile c, String bodyType) {
        if (bodyType == null || bodyType.equalsIgnoreCase("all")) return true;
        if (c.getBodyType() == null) return false;
        return c.getBodyType().equalsIgnoreCase(bodyType);
    }

    private boolean hairColorMatches(CreatorProfile c, String hairColor) {
        if (hairColor == null || hairColor.equalsIgnoreCase("all")) return true;
        if (c.getHairColor() == null) return false;
        return c.getHairColor().equalsIgnoreCase(hairColor);
    }

    private boolean eyeColorMatches(CreatorProfile c, String eyeColor) {
        if (eyeColor == null || eyeColor.equalsIgnoreCase("all")) return true;
        if (c.getEyeColor() == null) return false;
        return c.getEyeColor().equalsIgnoreCase(eyeColor);
    }

    private boolean ethnicityMatches(CreatorProfile c, String ethnicity) {
        if (ethnicity == null || ethnicity.equalsIgnoreCase("all")) return true;
        if (c.getEthnicity() == null) return false;
        return c.getEthnicity().equalsIgnoreCase(ethnicity);
    }

    private boolean interestedInMatches(CreatorProfile c, String interestedIn) {
        if (interestedIn == null || interestedIn.equalsIgnoreCase("all")) return true;
        if (c.getInterestedIn() == null || c.getInterestedIn().isBlank()) return false;
        return java.util.Arrays.stream(c.getInterestedIn().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(token -> token.equalsIgnoreCase(interestedIn));
    }

    private boolean languageMatches(CreatorProfile c, String language) {
        if (language == null || language.equalsIgnoreCase("all")) return true;
        if (c.getLanguages() == null || c.getLanguages().isBlank()) return false;
        return java.util.Arrays.stream(c.getLanguages().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(token -> token.equalsIgnoreCase(language));
    }

    private List<CreatorProfile> sortProfiles(List<CreatorProfile> profiles, String sort,
                                               Map<Long, com.joinlivora.backend.streaming.Stream> streamMap) {
        if (sort == null || sort.equalsIgnoreCase("relevance")) {
            // Default: live first sorted by viewer count desc, then offline
            return profiles.stream().sorted((a, b) -> {
                boolean aLive = isCreatorLive(a);
                boolean bLive = isCreatorLive(b);
                if (aLive != bLive) return aLive ? -1 : 1;
                if (aLive) {
                    long aViewers = viewerCounterService.getViewerCount(a.getUser().getId());
                    long bViewers = viewerCounterService.getViewerCount(b.getUser().getId());
                    return Long.compare(bViewers, aViewers);
                }
                return 0;
            }).collect(Collectors.toList());
        }
        if (sort.equalsIgnoreCase("viewers")) {
            return profiles.stream().sorted((a, b) -> {
                long aViewers = viewerCounterService.getViewerCount(a.getUser().getId());
                long bViewers = viewerCounterService.getViewerCount(b.getUser().getId());
                return Long.compare(bViewers, aViewers);
            }).collect(Collectors.toList());
        }
        if (sort.equalsIgnoreCase("newest")) {
            return profiles.stream().sorted((a, b) -> {
                com.joinlivora.backend.streaming.Stream aStream = streamMap.get(a.getUser().getId());
                com.joinlivora.backend.streaming.Stream bStream = streamMap.get(b.getUser().getId());
                Instant aStarted = aStream != null ? aStream.getStartedAt() : Instant.MIN;
                Instant bStarted = bStream != null ? bStream.getStartedAt() : Instant.MIN;
                return bStarted.compareTo(aStarted);
            }).collect(Collectors.toList());
        }
        if (sort.equalsIgnoreCase("alphabetical")) {
            return profiles.stream().sorted((a, b) -> {
                String aName = a.getDisplayName() != null ? a.getDisplayName() : (a.getUsername() != null ? a.getUsername() : "");
                String bName = b.getDisplayName() != null ? b.getDisplayName() : (b.getUsername() != null ? b.getUsername() : "");
                return aName.compareToIgnoreCase(bName);
            }).collect(Collectors.toList());
        }
        return profiles;
    }


    @Transactional(readOnly = true)
    public List<ExploreCreatorResponse> getExploreCreators() {
        Instant since = Instant.now().minusSeconds(60);
        boolean onlyLive = !isDevOrTest();
        Page<CreatorProfile> profilesPage = creatorProfileRepository.findExploreCreators(since, onlyLive, PageRequest.of(0, 100));
        List<CreatorProfile> profiles = profilesPage.getContent();
        
        if (profiles.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> users = profiles.stream()
                .map(CreatorProfile::getUser)
                .collect(Collectors.toList());

        Map<Long, BigDecimal> earningsMap = earningRepository.sumTotalEarningsForCreators(users).stream()
                .collect(Collectors.toMap(
                        obj -> (Long) obj[0],
                        obj -> (BigDecimal) obj[1]
                ));

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), Creator::getId));

        Map<Long, com.joinlivora.backend.streaming.Stream> streamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(users).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        return profiles.stream().map(profile -> {
            com.joinlivora.backend.streaming.Stream stream = streamMap.get(profile.getUser().getId());
            String streamKey = stream != null ? stream.getStreamKey() : null;

            if (stream == null && isCreatorLive(profile)) {
                log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in explore response", profile.getUser().getId());
            }

            String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                                 (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

            return ExploreCreatorResponse.builder()
                .userId(profile.getUser().getId())
                .creatorId(userToCreatorIdMap.getOrDefault(profile.getUser().getId(), profile.getId()))
                .displayName(profile.getDisplayName())
                .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                .totalEarned(earningsMap.getOrDefault(profile.getUser().getId(), BigDecimal.ZERO))
                .isOnline(isCreatorOnline(profile))
                .isLive(isCreatorLive(profile))
                .viewerCount(viewerCounterService.getViewerCount(profile.getUser().getId()))
                .activeStreamThumbnailUrl(thumbnailUrl)
                .build();
        })
        .collect(Collectors.toList());
}

    @Transactional(readOnly = true)
    public Page<ExploreCreatorDto> getExploreCreatorsList(Pageable pageable) {
        Instant since = Instant.now().minusSeconds(60);
        boolean onlyLive = !isDevOrTest();
        Page<CreatorProfile> profiles = creatorProfileRepository.findExploreCreators(since, onlyLive, pageable);
        
        if (profiles.isEmpty()) {
            return Page.empty(pageable);
        }

        List<User> users = profiles.stream()
                .map(CreatorProfile::getUser)
                .collect(Collectors.toList());

        Map<Long, BigDecimal> earningsMap = earningRepository.sumTotalEarningsForCreators(users).stream()
                .collect(Collectors.toMap(
                        obj -> (Long) obj[0],
                        obj -> (BigDecimal) obj[1]
                ));

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), Creator::getId));

        Map<Long, com.joinlivora.backend.streaming.Stream> streamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(users).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        return profiles.map(profile -> {
            com.joinlivora.backend.streaming.Stream stream = streamMap.get(profile.getUser().getId());
            String streamKey = stream != null ? stream.getStreamKey() : null;

            if (stream == null && isCreatorLive(profile)) {
                log.warn("STREAM_METADATA_FALLBACK: Unified Stream not found for creator {} in explore list", profile.getUser().getId());
            }

            String thumbnailUrl = stream != null && stream.getThumbnailUrl() != null ? stream.getThumbnailUrl() : 
                                 (streamKey != null ? "/thumbnails/" + streamKey + ".jpg" : null);

            boolean isLive = isCreatorLive(profile);
            com.joinlivora.backend.monetization.TipGoal activeGoal = getStructuredActiveGoal(profile.getUser().getId(), isLive);
            return ExploreCreatorDto.builder()
                .userId(profile.getUser().getId())
                .creatorId(userToCreatorIdMap.getOrDefault(profile.getUser().getId(), profile.getId()))
                .username(profile.getUsername())
                .displayName(profile.getDisplayName())
                .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                .profileImageUrl(formatImageUrl(profile.getAvatarUrl()))
                .bannerImageUrl(formatImageUrl(profile.getBannerUrl()))
                .shortBio(profile.getBio())
                .totalEarned(earningsMap.getOrDefault(profile.getUser().getId(), BigDecimal.ZERO))
                .isOnline(isCreatorOnline(profile))
                .isLive(isLive)
                .viewerCount(viewerCounterService.getViewerCount(profile.getUser().getId()))
                .activeStreamThumbnailUrl(thumbnailUrl)
                .goalTitle(activeGoal != null ? activeGoal.getTitle() : null)
                .goalTargetTokens(activeGoal != null ? activeGoal.getTargetAmount() : null)
                .goalCurrentTokens(activeGoal != null ? activeGoal.getCurrentAmount() : null)
                .build();
        });
    }

    @Transactional
    public Optional<CreatorProfile> resolveProfile(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        Optional<CreatorProfile> profile = Optional.empty();

        // 1. Try as Numeric ID (User ID preferred)
        try {
            Long numericId = Long.parseLong(identifier);
            profile = creatorProfileRepository.findByUserId(numericId);
            if (profile.isPresent()) {
                return profile;
            }

            // Try as Creator ID
            Optional<CreatorProfile> byCreatorId = creatorRepository.findById(numericId)
                    .flatMap(c -> creatorProfileRepository.findByUser(c.getUser()));
            if (byCreatorId.isPresent()) {
                return byCreatorId;
            }

            // Fallback to CreatorProfile ID
            profile = creatorProfileRepository.findById(numericId);
            if (profile.isPresent()) {
                return profile;
            }
        } catch (NumberFormatException ignored) {
        }

        // 2. Try as Slug (Username or PublicHandle)
        profile = creatorProfileRepository.findByUsername(identifier)
                .or(() -> creatorProfileRepository.findByPublicHandle(identifier));
        if (profile.isPresent()) {
            return profile;
        }

        // 3. Try to find User by slug (via email prefix) and return their profile if exists
        return userRepository.findCreatorByIdOrUsername(null, identifier)
                .flatMap(creatorProfileRepository::findByUser);
    }

    @Transactional(readOnly = true)
    public Optional<PublicCreatorProfileResponse> getPublicProfile(String identifier) {
        return resolveProfile(identifier)
                .filter(this::isVisibleToPublic)
                .map(this::mapToPublicResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PublicCreatorInfoResponse> getPublicCreatorInfo(String identifier) {
        return resolveProfile(identifier)
                .filter(this::isVisibleToPublic)
                .map(this::mapToInfoResponse);
    }

    public boolean isVisibleToPublic(CreatorProfile p) {
        if (p == null) return false;
        User u = p.getUser();
        
        // 1. Role must be CREATOR
        if (u.getRole() != Role.CREATOR) {
            return false;
        }

        // 2. Must NOT be shadowbanned
        if (u.isShadowbanned()) {
            return false;
        }

        // 3. Visibility must be PUBLIC
        if (p.getVisibility() != ProfileVisibility.PUBLIC) {
            return false;
        }

        // 4. Status must be ACTIVE
        if (p.getStatus() == ProfileStatus.ACTIVE) {
            return true;
        }

        // In Dev/Test, we allow PENDING and DRAFT for easier development/testing/preview
        return isDevOrTest() && (p.getStatus() == ProfileStatus.PENDING || p.getStatus() == ProfileStatus.DRAFT);
    }

    @Transactional(readOnly = true)
    public Optional<CreatorProfileByUsernameResponse> getProfileByUsername(String username) {
        return resolveProfile(username)
                .filter(this::isVisibleToPublic)
                .map(p -> {
                    long totalPosts = creatorPostRepository.countByCreator(p.getUser());
                    return CreatorProfileByUsernameResponse.builder()
                            .creatorId(p.getId())
                            .username(p.getUsername())
                            .displayName(p.getDisplayName())
                            .bio(p.getBio())
                            .profileImageUrl(formatImageUrl(p.getAvatarUrl()))
                            .bannerImageUrl(formatImageUrl(p.getBannerUrl()))
                            .createdAt(p.getCreatedAt())
                            .totalPosts(totalPosts)
                            .build();
                });
    }

    @Transactional
    public Optional<PublicCreatorProfileDto> getPublicProfileById(Long id) {
        // Resolve by User ID with auto-creation if it's a creator
        CreatorProfile profile = creatorProfileRepository.findByUserId(id)
                .orElseGet(() -> userRepository.findById(id)
                        .filter(u -> u.getRole() == Role.CREATOR)
                        .map(this::initializeCreatorProfile)
                        .orElse(null));

        // If not found by User ID, try as Profile ID
        if (profile == null) {
            profile = creatorProfileRepository.findById(id).orElse(null);
        }

        return Optional.ofNullable(profile)
                .filter(this::isVisibleToPublic)
                .map(p -> {
                    User user = p.getUser();
                    long followers = followService.getFollowerCount(user);
                    boolean isOnline = isCreatorOnline(p);
                    Long creatorId = creatorRepository.findByUser_Id(user.getId())
                            .map(Creator::getId)
                            .orElse(p.getId());

                    return PublicCreatorProfileDto.builder()
                            .creatorId(creatorId)
                            .displayName(p.getDisplayName())
                            .profileImageUrl(formatImageUrl(p.getAvatarUrl()))
                            .bannerImageUrl(formatImageUrl(p.getBannerUrl()))
                            .bio(p.getBio())
                            .socialLinks(Collections.emptyMap())
                            .totalFollowers(followers)
                            .isOnline(isOnline)
                            .build();
                });
    }


    @Transactional
    public Optional<CreatorDTO> getCreatorDTOByUserId(Long userId) {
        try {
            CreatorProfile profile = getCreatorByUserId(userId);
            User user = profile.getUser();

            long followers = followService.getFollowerCount(user);
            long posts = creatorPostRepository.countByCreator(user);

            BigDecimal earnings = earningRepository.sumTotalNetEarningsByCreator(user);
            if (earnings == null) earnings = BigDecimal.ZERO;

            BigDecimal tips = earningRepository.sumNetEarningsByCreatorAndSource(user, com.joinlivora.backend.payout.EarningSource.TIP);
            if (tips == null) tips = BigDecimal.ZERO;

            return Optional.of(CreatorDTO.builder()
                    .id(user.getId())
                    .displayName(profile.getDisplayName())
                    .bio(profile.getBio())
                    .avatarUrl(formatImageUrl(profile.getAvatarUrl()))
                    .followersCount(followers)
                    .postCount(posts)
                    .totalTips(tips)
                    .totalEarnings(earnings)
                    .build());
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<CreatorProfileDTO> getProfileDTO(User user) {
        return creatorProfileRepository.findByUser(user)
                .map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public CreatorStatsResponse getCreatorStats(User user) {
        long totalPosts = creatorPostRepository.countByCreator(user);

        BigDecimal totalEarnings = earningRepository.sumTotalNetEarningsByCreator(user);
        if (totalEarnings == null) totalEarnings = BigDecimal.ZERO;

        BigDecimal pendingBalance = payoutCreatorEarningsRepository.findByCreator(user)
                .map(com.joinlivora.backend.payout.CreatorEarnings::getPendingBalance)
                .orElseGet(() -> {
                    BigDecimal sum = earningRepository.sumPendingEarningsByCreator(user);
                    return sum != null ? sum : BigDecimal.ZERO;
                });

        return CreatorStatsResponse.builder()
                .totalPosts(totalPosts)
                .totalEarnings(totalEarnings)
                .pendingBalance(pendingBalance)
                .build();
    }

    @Transactional
    public CreatorEarningsDashboardResponse getCreatorEarningsDashboard(User creator) {
        CreatorEarnings earnings = payoutCreatorEarningsRepository.findByCreator(creator)
                .orElse(CreatorEarnings.builder()
                        .creator(creator)
                        .availableBalance(BigDecimal.ZERO)
                        .pendingBalance(BigDecimal.ZERO)
                        .totalEarned(BigDecimal.ZERO)
                        .build());

        BigDecimal totalFees = earningRepository.sumTotalFeesByCreator(creator);
        if (totalFees == null) totalFees = BigDecimal.ZERO;

        return CreatorEarningsDashboardResponse.builder()
                .availableBalance(earnings.getAvailableBalance())
                .totalEarned(earnings.getTotalEarned())
                .pendingBalance(earnings.getPendingBalance())
                .totalFees(totalFees)
                .build();
    }

    @Transactional(readOnly = true)
    public CreatorDashboardSummary getDashboardSummary(User user) {
        CreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + user.getEmail()));
        
        String displayName = profile.getDisplayName() != null ? profile.getDisplayName() :
                (user.getUsername() != null ? user.getUsername() : "User");

        String publicIdentifier = (profile.getUsername() != null && !profile.getUsername().isBlank())
                ? profile.getUsername()
                : "user_" + user.getId();

        BigDecimal totalEarnings = earningRepository.sumTotalNetEarningsByCreator(user);
        if (totalEarnings == null) totalEarnings = BigDecimal.ZERO;

        return CreatorDashboardSummary.builder()
                .id(profile.getId())
                .publicIdentifier(publicIdentifier)
                .displayName(displayName)
                .totalEarnings(totalEarnings)
                .accountStatus(user.getStatus())
                .profileStatus(profile.getStatus())
                .build();
    }

    public CreatorProfileDTO mapToDTO(CreatorProfile profile) {
        // Use Unified StreamRepository
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

    private PublicCreatorProfileResponse mapToPublicResponse(CreatorProfile profile) {
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

        // Use Unified StreamRepository
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

        // Use Unified StreamRepository
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

    private void ensureOwnership(Long userId, String message) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new PermissionDeniedException("You must be logged in to perform this action");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentUserId.equals(userId)) {
            throw new PermissionDeniedException(message);
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Optional<Long> getCreatorIdByUserId(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .map(Creator::getId);
    }

    @Transactional
    public CreatorProfile initializeCreatorProfile(User user) {
        if (user.getRole() != Role.CREATOR) {
            throw new ResourceNotFoundException("Creator role required for profile initialization");
        }
        
        java.util.Optional<CreatorProfile> existing = creatorProfileRepository.findByUser(user);
        if (existing.isPresent()) {
            CreatorProfile profile = existing.get();
            // Ensure Creator record exists (Standardized Creator identity)
            creatorRepository.findByUser_Id(user.getId())
                    .orElseGet(() -> creatorRepository.save(
                            Creator.builder()
                                    .user(user)
                                    .active(profile.getStatus() == ProfileStatus.ACTIVE)
                                    .profileImageUrl(profile.getAvatarUrl())
                                    .bio(profile.getBio())
                                    .build()
                    ));

            // Ensure CreatorEarnings balance exists (idempotent)
            payoutCreatorEarningsRepository.findByCreator(user)
                    .orElseGet(() -> payoutCreatorEarningsRepository.save(
                            com.joinlivora.backend.payout.CreatorEarnings.builder()
                                    .creator(user)
                                    .build()
                    ));
            // Ensure monetization config exists (idempotent)
            creatorMonetizationService.getOrCreateForCreator(profile);
            return profile;
        }

        // If profile does not exist yet, delegate to createDefaultProfile which performs necessary side effects
        return createDefaultProfile(user);
    }


    @Transactional(readOnly = true)
    public CreatorProfile getProfileById(Long id) {
        return creatorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found with ID: " + id));
    }

    @Transactional
    public CreatorProfile getCreatorByUserId(Long userId) {
        return creatorProfileRepository.findByUserId(userId)
                .orElseGet(() -> userRepository.findById(userId)
                        .filter(u -> u.getRole() == Role.CREATOR)
                        .map(this::initializeCreatorProfile)
                        .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId)));
    }

    @Transactional(readOnly = true)
    public User getUserByProfileId(Long profileId) {
        return getProfileById(profileId).getUser();
    }

    @Transactional(readOnly = true)
    public CreatorProfileDTO getMyProfile(Long userId) {
        ensureOwnership(userId, "You can only view your own profile settings");
        
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));
        
        return mapToDTO(profile);
    }

    @Transactional(readOnly = true)
    public CreatorIdentifierDTO getPublicIdentifier(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        CreatorProfile profileByUser = creatorProfileRepository.findByUser(user).orElse(null);
        java.util.Optional<CreatorProfile> profileByUserId = creatorProfileRepository.findByUserId(userId);
        CreatorProfile profile = profileByUser != null ? profileByUser : profileByUserId
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));

        String identifier = profile.getUsername();
        if (identifier == null || identifier.isBlank()) {
            identifier = "user_" + userId;
        }

        return new CreatorIdentifierDTO(identifier);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CreatorProfileDTO createProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        if (user.getRole() != Role.CREATOR) {
            throw new ResourceNotFoundException("Creator role required to create profile");
        }

        CreatorProfile profile = creatorProfileRepository.findByUser(user)
                .orElseGet(() -> createDefaultProfile(user));
        
        return mapToDTO(profile);
    }

    @Transactional
    public CreatorProfileDTO uploadImage(Long userId, org.springframework.web.multipart.MultipartFile file, String type) {
        ensureOwnership(userId, "You can only upload images to your own profile");
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));

        StoreResult result = fileStorageService.storeCreatorImage(userId, file, type);
        String imageUrl = result.getRelativePath();

        if ("PROFILE".equalsIgnoreCase(type)) {
            profile.setAvatarUrl(imageUrl);
        } else if ("BANNER".equalsIgnoreCase(type)) {
            profile.setBannerUrl(imageUrl);
        } else {
            throw new BusinessException("Invalid upload type. Must be PROFILE or BANNER.");
        }

        // Synchronize with LegacyCreatorProfile
        User user = profile.getUser();
        legacyCreatorProfileRepository.findByUser(user).ifPresent(legacy -> {
            if ("PROFILE".equalsIgnoreCase(type)) {
                legacy.setAvatarUrl(imageUrl);
            } else if ("BANNER".equalsIgnoreCase(type)) {
                legacy.setBannerUrl(imageUrl);
            }
            legacyCreatorProfileRepository.save(legacy);
        });

        CreatorProfile saved = creatorProfileRepository.save(profile);
        return mapToDTO(saved);
    }

    @Transactional
    public CreatorProfileDTO updateProfile(Long userId, UpdateCreatorProfileRequest request) {
        ensureOwnership(userId, "You can only edit your own profile");
        log.info("DEBUG: Starting creator profile update for authenticated user creator: {}. Incoming displayName: {}", userId, request.getDisplayName());

        // Load the CreatorProfile by creator
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));

        log.info("DEBUG: Found target creator profile for user creator: {}. Current displayName: {}",
            profile.getUser().getId(), profile.getDisplayName());

        // We still need the User entity for legacy synchronization logic
        User authenticatedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.getDisplayName() != null) {
            String displayName = request.getDisplayName().trim();
            profile.setDisplayName(displayName);
            authenticatedUser.setDisplayName(displayName);
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio().trim());
        }
        if (request.getProfileImageUrl() != null) {
            profile.setAvatarUrl(request.getProfileImageUrl().trim());
        }
        if (request.getBannerUrl() != null) {
            profile.setBannerUrl(request.getBannerUrl().trim());
        }

        // New fields
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String username = request.getUsername().trim();
            profile.setUsername(username);
            profile.setPublicHandle(username); // Keep public handle in sync with username
            authenticatedUser.setUsername(username);
        }
        userRepository.save(authenticatedUser);
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getInterestedIn() != null) profile.setInterestedIn(request.getInterestedIn());
        if (request.getLanguages() != null) profile.setLanguages(request.getLanguages());
        if (request.getLocation() != null) profile.setLocation(request.getLocation());
        if (request.getBodyType() != null) profile.setBodyType(request.getBodyType());
        if (request.getEthnicity() != null) profile.setEthnicity(request.getEthnicity());
        if (request.getHairColor() != null) profile.setHairColor(request.getHairColor());
        if (request.getEyeColor() != null) profile.setEyeColor(request.getEyeColor());
        if (request.getHeightCm() != null) profile.setHeightCm(request.getHeightCm());
        if (request.getWeightKg() != null) profile.setWeightKg(request.getWeightKg());
        if (request.getOnlyfansUrl() != null) profile.setOnlyfansUrl(request.getOnlyfansUrl());
        if (request.getThroneUrl() != null) profile.setThroneUrl(request.getThroneUrl());
        if (request.getWishlistUrl() != null) profile.setWishlistUrl(request.getWishlistUrl());
        if (request.getTwitterUrl() != null) profile.setTwitterUrl(request.getTwitterUrl());
        if (request.getInstagramUrl() != null) profile.setInstagramUrl(request.getInstagramUrl());

        // Visibility Toggles
        profile.setShowAge(request.isShowAge());
        profile.setShowLocation(request.isShowLocation());
        profile.setShowLanguages(request.isShowLanguages());
        profile.setShowBodyType(request.isShowBodyType());
        profile.setShowEthnicity(request.isShowEthnicity());
        profile.setShowHeightWeight(request.isShowHeightWeight());

        // Synchronize with LegacyCreatorProfile
        legacyCreatorProfileRepository.findByUser(authenticatedUser).ifPresent(legacy -> {
            if (request.getDisplayName() != null) {
                legacy.setDisplayName(request.getDisplayName().trim());
            }
            if (request.getBio() != null) {
                legacy.setBio(request.getBio().trim());
            }
            if (request.getProfileImageUrl() != null) {
                legacy.setAvatarUrl(request.getProfileImageUrl().trim());
            }
            if (request.getBannerUrl() != null) {
                legacy.setBannerUrl(request.getBannerUrl().trim());
            }
            if (request.getUsername() != null && !request.getUsername().isBlank()) {
                legacy.setUsername(request.getUsername().trim());
            }
            legacyCreatorProfileRepository.save(legacy);
        });

        CreatorProfile saved = creatorProfileRepository.save(profile);
        log.info("DEBUG: Creator profile entity saved for user creator: {}. Final persisted displayName: {}",
            saved.getUser().getId(), saved.getDisplayName());

        return mapToDTO(saved);
    }

    @Transactional
    public CreatorProfileDTO completeOnboarding(Long userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));

        if (profile.getStatus() != ProfileStatus.DRAFT) {
            throw new IllegalStateException("Profile must be in DRAFT state to complete onboarding");
        }

        if (profile.getDisplayName() == null || profile.getDisplayName().isBlank()) {
            throw new IllegalStateException("Display name is required to complete onboarding");
        }
        if (profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) {
            throw new IllegalStateException("Avatar is required to complete onboarding");
        }
        if (profile.getBio() == null || profile.getBio().isBlank()) {
            throw new IllegalStateException("Bio is required to complete onboarding");
        }

        profile.setStatus(ProfileStatus.PENDING);
        CreatorProfile saved = creatorProfileRepository.save(profile);
        log.info("Creator onboarding completed for user {}. Status set to PENDING.", userId);
        return mapToDTO(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CreatorProfileDTO publishProfile(Long userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + userId));

        if (profile.getUsername() == null || profile.getUsername().isBlank()) {
            throw new IllegalStateException("Username is required to publish profile");
        }
        if (profile.getBio() == null || profile.getBio().isBlank()) {
            throw new IllegalStateException("Bio is required to publish profile");
        }

        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setVisibility(ProfileVisibility.PUBLIC);

        CreatorProfile saved = creatorProfileRepository.save(profile);
        return mapToDTO(saved);
    }

    private CreatorProfile createDefaultProfile(User user) {
        String username = user.getUsername();

        CreatorProfile profile = CreatorProfile.builder()
                .user(user)
                .username(username)
                .publicHandle(username)
                .displayName(username)
                .bio("")
                .status(isDevOrTest() ? ProfileStatus.ACTIVE : ProfileStatus.DRAFT)
                .visibility(isDevOrTest() ? ProfileVisibility.PUBLIC : ProfileVisibility.PRIVATE)
                .createdAt(Instant.now())
                .build();
        CreatorProfile saved = creatorProfileRepository.save(profile);
        
        // Automatically create linked Creator record
        if (creatorRepository.findByUser_Id(user.getId()).isEmpty()) {
            creatorRepository.save(Creator.builder()
                    .user(user)
                    .active(saved.getStatus() == ProfileStatus.ACTIVE)
                    .profileImageUrl(saved.getAvatarUrl())
                    .bio(saved.getBio())
                    .build());
        }

        // Automatically create linked CreatorEarnings
        if (payoutCreatorEarningsRepository.findByCreator(user).isEmpty()) {
            createDefaultEarnings(user);
        }

        // Automatically create linked CreatorMonetization
        creatorMonetizationService.getOrCreateForCreator(saved);
        
        return saved;
    }

    private void createDefaultEarnings(User user) {
        log.info("Creating default CreatorEarnings for user: {}", user.getEmail());
        CreatorEarnings earnings = CreatorEarnings.builder()
                .creator(user)
                .build();
        payoutCreatorEarningsRepository.save(earnings);
    }

    @Transactional(readOnly = true)
    public long getFollowerCountStrict(String identifier) {
        return resolveProfile(identifier)
                .filter(this::isVisibleToPublic)
                .map(p -> followService.getFollowerCount(p.getUser()))
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));
    }

    private boolean isCreatorOnline(CreatorProfile profile) {
        return creatorRepository.findByUser_Id(profile.getUser().getId())
                .map(c -> onlineStatusService.isOnline(c.getId()))
                .orElse(false);
    }

    private boolean isCreatorLive(CreatorProfile profile) {
        // Try Unified Stream identity first
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
            // First try individual TipGoal (legacy)
            Optional<com.joinlivora.backend.monetization.TipGoal> goal = tipGoalService.getActiveGoal(userId);
            if (goal.isPresent()) return goal.get();

            // Fall back to active TipGoalGroup (goal groups used by live dashboard)
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
}
