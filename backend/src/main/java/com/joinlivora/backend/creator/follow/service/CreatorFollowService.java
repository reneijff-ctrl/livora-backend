package com.joinlivora.backend.creator.follow.service;

import com.joinlivora.backend.creator.follow.entity.CreatorFollow;
import com.joinlivora.backend.creator.follow.repository.CreatorFollowRepository;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorFollowService {

    private final CreatorFollowRepository creatorFollowRepository;

    @Transactional
    @CacheEvict(
        value = {"creatorExplore", "creatorHomepage"},
        allEntries = true
    )
    public boolean follow(User follower, User creator) {
        if (creator == null) {
            throw new ResourceNotFoundException("Creator not found");
        }
        if (follower.getId().equals(creator.getId())) {
            throw new BusinessException("A user cannot follow themselves");
        }
        if (!creatorFollowRepository.existsByFollowerAndCreator(follower, creator)) {
            CreatorFollow follow = CreatorFollow.builder()
                    .follower(follower)
                    .creator(creator)
                    .build();
            creatorFollowRepository.save(follow);
            return true;
        }
        return false;
    }

    @Transactional
    @CacheEvict(
        value = {"creatorExplore", "creatorHomepage"},
        allEntries = true
    )
    public void unfollow(User follower, User creator) {
        if (creator == null) {
            throw new ResourceNotFoundException("Creator not found");
        }
        creatorFollowRepository.deleteByFollowerAndCreator(follower, creator);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(User follower, User creator) {
        if (follower == null || creator == null) {
            return false;
        }
        return creatorFollowRepository.existsByFollowerAndCreator(follower, creator);
    }

    @Transactional(readOnly = true)
    public long getFollowerCount(User creator) {
        if (creator == null) {
            throw new ResourceNotFoundException("Creator not found");
        }
        return creatorFollowRepository.countByCreator(creator);
    }
}
