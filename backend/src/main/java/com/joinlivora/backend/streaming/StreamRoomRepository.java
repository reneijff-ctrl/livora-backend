package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StreamRoomRepository extends JpaRepository<StreamRoom, UUID> {
    Optional<StreamRoom> findByCreator_Id(Long creatorUserId);
    Optional<StreamRoom> findByCreator(User creator);
    Optional<StreamRoom> findByCreatorEmail(String email);
    java.util.List<StreamRoom> findAllByIsLiveTrue();
    java.util.List<StreamRoom> findAllByCreatorIn(java.util.Collection<User> creators);

    @Modifying
    @Query("UPDATE StreamRoom s SET s.viewerCount = GREATEST(0, s.viewerCount + :delta) WHERE s.id = :id")
    void updateViewerCount(@Param("id") UUID id, @Param("delta") int delta);
}
