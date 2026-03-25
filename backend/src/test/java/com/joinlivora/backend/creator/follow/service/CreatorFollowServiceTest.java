package com.joinlivora.backend.creator.follow.service;

import com.joinlivora.backend.creator.follow.entity.CreatorFollow;
import com.joinlivora.backend.creator.follow.repository.CreatorFollowRepository;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorFollowServiceTest {

    @Mock
    private CreatorFollowRepository creatorFollowRepository;

    @InjectMocks
    private CreatorFollowService creatorFollowService;

    private User follower;
    private User creator;

    @BeforeEach
    void setUp() {
        follower = new User();
        follower.setId(1L);
        follower.setEmail("follower@test.com");

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");
    }

    @Test
    void follow_Success() {
        when(creatorFollowRepository.existsByFollowerAndCreator(follower, creator)).thenReturn(false);

        creatorFollowService.follow(follower, creator);

        verify(creatorFollowRepository, times(1)).save(any(CreatorFollow.class));
    }

    @Test
    void follow_SelfFollowing_ThrowsException() {
        User self = new User();
        self.setId(1L);

        assertThrows(BusinessException.class, () -> creatorFollowService.follow(self, self));
    }

    @Test
    void follow_Duplicate_ShouldDoNothing() {
        when(creatorFollowRepository.existsByFollowerAndCreator(follower, creator)).thenReturn(true);

        creatorFollowService.follow(follower, creator);

        verify(creatorFollowRepository, never()).save(any(CreatorFollow.class));
    }

    @Test
    void follow_NullCreator_ThrowsException() {
        assertThrows(ResourceNotFoundException.class, () -> creatorFollowService.follow(follower, null));
    }

    @Test
    void unfollow_Success() {
        creatorFollowService.unfollow(follower, creator);

        verify(creatorFollowRepository, times(1)).deleteByFollowerAndCreator(follower, creator);
    }

    @Test
    void isFollowing_True() {
        when(creatorFollowRepository.existsByFollowerAndCreator(follower, creator)).thenReturn(true);

        assertTrue(creatorFollowService.isFollowing(follower, creator));
    }

    @Test
    void getFollowerCount_Success() {
        when(creatorFollowRepository.countByCreator(creator)).thenReturn(10L);

        assertEquals(10L, creatorFollowService.getFollowerCount(creator));
    }
}








