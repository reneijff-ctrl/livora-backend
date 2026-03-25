package com.joinlivora.backend.creator.repository;

import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorPostRepository extends JpaRepository<CreatorPost, UUID> {
    List<CreatorPost> findByCreator_IdOrderByCreatedAtDesc(Long creatorId);
    long countByCreator(User creator);

    @Query("SELECT cp FROM CreatorPost cp JOIN CreatorProfile prof ON cp.creator = prof.user WHERE prof.username = :username ORDER BY cp.createdAt DESC")
    List<CreatorPost> findByCreatorUsername(@Param("username") String username);

    @Query("SELECT cp FROM CreatorPost cp " +
           "WHERE cp.creator IN (SELECT f.creator FROM CreatorFollow f WHERE f.follower = :user) " +
           "ORDER BY cp.createdAt DESC")
    Page<CreatorPost> findFeedByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT cp FROM CreatorPost cp JOIN cp.creator u JOIN CreatorProfile prof ON u = prof.user " +
           "WHERE u.role = com.joinlivora.backend.user.Role.CREATOR " +
           "AND u.shadowbanned = false " +
           "AND prof.status = com.joinlivora.backend.creator.model.ProfileStatus.ACTIVE " +
           "AND prof.visibility = com.joinlivora.backend.creator.model.ProfileVisibility.PUBLIC")
    Page<CreatorPost> findExplorePosts(Pageable pageable);
}
