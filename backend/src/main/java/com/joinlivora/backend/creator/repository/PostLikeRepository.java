package com.joinlivora.backend.creator.repository;

import com.joinlivora.backend.creator.model.CreatorPost;
import com.joinlivora.backend.creator.model.PostLike;
import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    long countByPostId(UUID postId);
    boolean existsByUserAndPost(User user, CreatorPost post);
    Optional<PostLike> findByUserAndPost(User user, CreatorPost post);
    void deleteByUserAndPost(User user, CreatorPost post);

    @org.springframework.data.jpa.repository.Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user = :user AND pl.post IN :posts")
    java.util.Set<UUID> findLikedPostIdsByUserAndPosts(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("posts") java.util.Collection<CreatorPost> posts);

    @org.springframework.data.jpa.repository.Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl WHERE pl.post IN :posts GROUP BY pl.post.id")
    java.util.List<Object[]> countLikesForPosts(@org.springframework.data.repository.query.Param("posts") java.util.Collection<CreatorPost> posts);
}
