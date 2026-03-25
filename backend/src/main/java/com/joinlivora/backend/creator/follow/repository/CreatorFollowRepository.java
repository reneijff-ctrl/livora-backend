package com.joinlivora.backend.creator.follow.repository;

import com.joinlivora.backend.creator.follow.entity.CreatorFollow;
import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

@Repository
public interface CreatorFollowRepository extends JpaRepository<CreatorFollow, Long> {
    boolean existsByFollowerAndCreator(User follower, User creator);
    long countByCreator(User creator);
    void deleteByFollowerAndCreator(User follower, User creator);

    @Query("SELECT cf.follower.id FROM CreatorFollow cf WHERE cf.creator.id = :creatorId AND cf.follower.id IN :followerIds")
    Set<Long> findFollowerIdsByCreatorIdAndFollowerIds(@Param("creatorId") Long creatorId, @Param("followerIds") Collection<Long> followerIds);
}
