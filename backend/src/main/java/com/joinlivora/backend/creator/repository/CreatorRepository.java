package com.joinlivora.backend.creator.repository;

import com.joinlivora.backend.creator.dto.CreatorPublicDto;
import com.joinlivora.backend.creator.model.Creator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreatorRepository extends JpaRepository<Creator, Long> {

    boolean existsByUser_Id(Long userId);
    Optional<Creator> findByUser_Id(Long userId);
    List<Creator> findAllByUserIn(java.util.Collection<com.joinlivora.backend.user.User> users);

    @Query("SELECT u.username FROM Creator c JOIN c.user u WHERE c.user.id = :userId")
    Optional<String> findUsernameByUserId(Long userId);

    @Query("""
    SELECT new com.joinlivora.backend.creator.dto.CreatorPublicDto(
      c.id,
      u.displayName,
      c.profileImageUrl,
      c.bio
    )
    FROM Creator c
    JOIN c.user u
    WHERE c.active = true
    """)
    List<CreatorPublicDto> findAllPublicCreators();
}
