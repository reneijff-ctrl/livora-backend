package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.*;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.presence.repository.CreatorPresenceRepository;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles creator search, discovery, filtering, and sorting.
 * Extracted from CreatorProfileService to separate search/discovery concerns.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CreatorSearchService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorPresenceRepository creatorPresenceRepository;
    private final CreatorRepository creatorRepository;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final com.joinlivora.backend.livestream.repository.LivestreamSessionRepository livestreamSessionRepository;
    private final com.joinlivora.backend.payout.CreatorEarningRepository earningRepository;
    private final OnlineStatusService onlineStatusService;
    private final LiveViewerCounterService viewerCounterService;
    private final com.joinlivora.backend.monetization.TipGoalService tipGoalService;
    private final com.joinlivora.backend.creator.follow.service.CreatorFollowService followService;
    private final Environment environment;

    private boolean isDevOrTest() {
        if (environment == null) return false;
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        return profiles.contains("dev") || profiles.contains("test") || profiles.isEmpty();
    }

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

    com.joinlivora.backend.monetization.TipGoal getStructuredActiveGoal(Long userId, boolean isLive) {
        if (!isLive) return null;
        try {
            Optional<com.joinlivora.backend.monetization.TipGoal> goal = tipGoalService.getActiveGoal(userId);
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

    @Transactional(readOnly = true)
    public Page<PublicCreatorDiscoveryResponse> getCreatorsDiscovery(Pageable pageable) {
        Instant threshold = Instant.now().minusSeconds(60);
        boolean includeOffline = isDevOrTest();
        Page<CreatorProfile> profiles = creatorPresenceRepository.findOnlineCreators(threshold, includeOffline, pageable);

        if (profiles.isEmpty()) {
            return Page.empty(pageable);
        }

        List<User> users = profiles.stream().map(CreatorProfile::getUser).collect(Collectors.toList());
        
        Map<Long, com.joinlivora.backend.streaming.Stream> streamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(users).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), Creator::getId));

        return profiles.map(profile -> {
            com.joinlivora.backend.streaming.Stream stream = streamMap.get(profile.getUser().getId());
            String streamKey = stream != null ? stream.getStreamKey() : null;
            
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
        
        Map<Long, com.joinlivora.backend.streaming.Stream> streamMap = streamRepository.findAllByCreatorInAndIsLiveTrue(users).stream()
                .collect(Collectors.toMap(s -> s.getCreator().getId(), s -> s, (a, b) -> a));

        Map<Long, Long> userToCreatorIdMap = creatorRepository.findAllByUserIn(users).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), Creator::getId));

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
                .map(profile -> mapToCreatorProfileDTO(profile, allStreamMap.get(profile.getUser().getId())))
                .toList();

        return new PageImpl<>(content, pageable, sortedProfiles.size());
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

    // --- DTO mapping helper used by getCreators for search results ---

    private CreatorProfileDTO mapToCreatorProfileDTO(CreatorProfile profile, com.joinlivora.backend.streaming.Stream stream) {
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
                .streamTitle(stream != null ? stream.getTitle() : null)
                .streamStartedAt(stream != null ? stream.getStartedAt() : null)
                .isPaid(stream != null && stream.isPaid())
                .admissionPrice(stream != null ? stream.getAdmissionPrice() : null)
                .streamCategory(stream != null ? stream.getStreamCategory() : null)
                .build();
    }

    // --- Filter helpers ---

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
        if (stream == null) return false;
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
        if (sort.equalsIgnoreCase("followers")) {
            return profiles.stream().sorted((a, b) -> {
                long aFollowers = followService.getFollowerCount(a.getUser());
                long bFollowers = followService.getFollowerCount(b.getUser());
                return Long.compare(bFollowers, aFollowers);
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
}
