package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.creator.dto.CreatorPostResponse;
import com.joinlivora.backend.creator.dto.ExplorePostResponse;
import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.creator.model.CreatorProfile;
import com.joinlivora.backend.creator.model.PostLike;
import com.joinlivora.backend.creator.repository.CreatorPostRepository;
import com.joinlivora.backend.creator.repository.CreatorProfileRepository;
import com.joinlivora.backend.creator.repository.PostLikeRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatorPostService {

    private final CreatorPostRepository creatorPostRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;
    private final PostLikeRepository postLikeRepository;

    @Transactional
    public CreatorPost createPost(User creator, String title, String content) {
        CreatorPost post = CreatorPost.builder()
                .creator(creator)
                .title(title)
                .content(content)
                .build();
        return creatorPostRepository.save(post);
    }

    @Transactional(readOnly = true)
    public List<CreatorPostResponse> getPostsForCreator(String identifier, User currentUser) {
        Optional<CreatorProfile> profile;
        try {
            Long profileId = Long.parseLong(identifier);
            profile = creatorProfileRepository.findById(profileId);
        } catch (NumberFormatException e) {
            profile = creatorProfileRepository.findByUsername(identifier);
        }

        if (profile.isEmpty() || 
            profile.get().getUser().getRole() != com.joinlivora.backend.user.Role.CREATOR ||
            profile.get().getStatus() != com.joinlivora.backend.creator.model.ProfileStatus.ACTIVE ||
            profile.get().getVisibility() != com.joinlivora.backend.creator.model.ProfileVisibility.PUBLIC ||
            profile.get().getUser().isShadowbanned()) {
            return java.util.Collections.emptyList();
        }

        List<CreatorPost> posts = creatorPostRepository.findByCreator_IdOrderByCreatedAtDesc(profile.get().getUser().getId());
        return mapToResponses(posts, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<CreatorPostResponse> getFeed(User user, Pageable pageable) {
        Page<CreatorPost> posts = creatorPostRepository.findFeedByUser(user, pageable);
        return mapToResponses(posts, user);
    }

    @Transactional(readOnly = true)
    public Page<CreatorPostResponse> getPublicPosts(Pageable pageable, User currentUser) {
        Page<CreatorPost> posts = creatorPostRepository.findAll(pageable);
        return mapToResponses(posts, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ExplorePostResponse> getExplorePosts(Pageable pageable) {
        Page<CreatorPost> posts = creatorPostRepository.findExplorePosts(pageable);
        
        if (posts.isEmpty()) {
            return Page.empty(pageable);
        }

        List<User> creators = posts.stream()
                .map(CreatorPost::getCreator)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, CreatorProfile> profiles = creatorProfileRepository.findAllByUserIn(creators).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return posts.map(post -> {
            User creatorUser = post.getCreator();
            CreatorProfile profile = profiles.get(creatorUser.getId());
            Long creatorId = creatorRepository.findByUser_Id(creatorUser.getId())
                    .map(com.joinlivora.backend.creator.model.Creator::getId)
                    .orElse(profile != null ? profile.getId() : null);

            return ExplorePostResponse.builder()
                    .postId(post.getId())
                    .content(post.getContent())
                    .createdAt(post.getCreatedAt())
                    .creatorId(creatorId)
                    .creatorDisplayName(profile != null ? profile.getDisplayName() : null)
                    .creatorUsername(profile != null ? profile.getUsername() : null)
                    .creatorProfileImageUrl(profile != null ? UrlUtils.sanitizeUrl(profile.getAvatarUrl()) : null)
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public CreatorPostResponse getPost(UUID postId, User currentUser) {
        CreatorPost post = creatorPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        
        User creatorUser = post.getCreator();
        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElse(null);
        Long creatorId = creatorRepository.findByUser_Id(creatorUser.getId())
                .map(com.joinlivora.backend.creator.model.Creator::getId)
                .orElse(profile != null ? profile.getId() : null);

        long likeCount = postLikeRepository.countByPostId(postId);
        boolean likedByMe = currentUser != null && postLikeRepository.existsByUserAndPost(currentUser, post);
        
        return CreatorPostResponse.builder()
                .id(post.getId())
                .creatorId(creatorId)
                .displayName(profile != null ? profile.getDisplayName() : null)
                .username(profile != null ? profile.getUsername() : null)
                .avatarUrl(profile != null ? UrlUtils.sanitizeUrl(profile.getAvatarUrl()) : null)
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .likeCount(likeCount)
                .likedByMe(likedByMe)
                .build();
    }

    @Transactional
    public void likePost(User user, UUID postId) {
        CreatorPost post = creatorPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        
        if (!postLikeRepository.existsByUserAndPost(user, post)) {
            PostLike like = PostLike.builder()
                    .user(user)
                    .post(post)
                    .build();
            postLikeRepository.save(like);
        }
    }

    @Transactional
    public void unlikePost(User user, UUID postId) {
        CreatorPost post = creatorPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        postLikeRepository.deleteByUserAndPost(user, post);
    }

    @Transactional(readOnly = true)
    public long getLikeCount(UUID postId) {
        return postLikeRepository.countByPostId(postId);
    }

    @Transactional(readOnly = true)
    public CreatorPostResponse mapToResponse(CreatorPost post) {
        User creatorUser = post.getCreator();
        CreatorProfile profile = creatorProfileRepository.findByUser(creatorUser).orElse(null);
        Long creatorId = creatorRepository.findByUser_Id(creatorUser.getId())
                .map(com.joinlivora.backend.creator.model.Creator::getId)
                .orElse(profile != null ? profile.getId() : null);

        long likeCount = postLikeRepository.countByPostId(post.getId());
        
        return CreatorPostResponse.builder()
                .id(post.getId())
                .creatorId(creatorId)
                .displayName(profile != null ? profile.getDisplayName() : null)
                .username(profile != null ? profile.getUsername() : null)
                .avatarUrl(profile != null ? UrlUtils.sanitizeUrl(profile.getAvatarUrl()) : null)
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .likeCount(likeCount)
                .likedByMe(false) // Assuming new post or not liked by default for singular mapping if not provided
                .build();
    }

    private Page<CreatorPostResponse> mapToResponses(Page<CreatorPost> posts, User currentUser) {
        List<CreatorPostResponse> content = mapToResponses(posts.getContent(), currentUser);
        return new org.springframework.data.domain.PageImpl<>(content, posts.getPageable(), posts.getTotalElements());
    }

    private List<CreatorPostResponse> mapToResponses(List<CreatorPost> posts, User currentUser) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<User> creators = posts.stream()
                .map(CreatorPost::getCreator)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, CreatorProfile> profiles = creatorProfileRepository.findAllByUserIn(creators).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        java.util.Set<UUID> likedPostIds = currentUser != null 
                ? postLikeRepository.findLikedPostIdsByUserAndPosts(currentUser, posts)
                : java.util.Collections.emptySet();

        Map<UUID, Long> likeCounts = postLikeRepository.countLikesForPosts(posts).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return posts.stream().map(post -> {
            CreatorProfile profile = profiles.get(post.getCreator().getId());
            return CreatorPostResponse.builder()
                    .id(post.getId())
                    .creatorId(profile != null ? profile.getId() : null)
                    .displayName(profile != null ? profile.getDisplayName() : null)
                    .username(profile != null ? profile.getUsername() : null)
                    .avatarUrl(profile != null ? UrlUtils.sanitizeUrl(profile.getAvatarUrl()) : null)
                    .title(post.getTitle())
                    .content(post.getContent())
                    .createdAt(post.getCreatedAt())
                    .likeCount(likeCounts.getOrDefault(post.getId(), 0L))
                    .likedByMe(likedPostIds.contains(post.getId()))
                    .build();
        }).collect(Collectors.toList());
    }
}
