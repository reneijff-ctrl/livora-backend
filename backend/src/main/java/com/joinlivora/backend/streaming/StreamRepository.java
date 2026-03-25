package com.joinlivora.backend.streaming;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StreamRepository extends JpaRepository<Stream, UUID> {
    List<Stream> findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(Long creatorId);
    
    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.creator.id = :creatorId AND s.isLive = true
        ORDER BY s.startedAt DESC
    """)
    List<Stream> findAllByCreatorIdAndIsLiveTrueWithCreator(@Param("creatorId") Long creatorId);

    Optional<Stream> findByMediasoupRoomId(UUID mediasoupRoomId);
    
    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.mediasoupRoomId = :mediasoupRoomId
    """)
    Optional<Stream> findByMediasoupRoomIdWithCreator(@Param("mediasoupRoomId") UUID mediasoupRoomId);
    
    @Cacheable(value = "streams", key = "#streamId")
    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.id = :streamId
    """)
    Optional<Stream> findByIdWithCreator(@Param("streamId") UUID streamId);

    @Query(value = """
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.isLive = true
        """,
        countQuery = "SELECT count(s) FROM Stream s WHERE s.isLive = true")
    Page<Stream> findActiveStreamsWithUser(Pageable pageable);

    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.isLive = true
    """)
    java.util.List<Stream> findActiveStreamsWithUser();

    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.creator IN :creators AND s.isLive = true
    """)
    java.util.List<Stream> findAllByCreatorInAndIsLiveTrue(@Param("creators") java.util.Collection<com.joinlivora.backend.user.User> creators);

    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.creator = :creator AND s.isLive = true
        ORDER BY s.startedAt DESC
    """)
    List<Stream> findAllByCreatorAndIsLiveTrueOrderByStartedAtDesc(@Param("creator") com.joinlivora.backend.user.User creator);

    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.creator = :creator
    """)
    Optional<Stream> findByCreator(@Param("creator") com.joinlivora.backend.user.User creator);

    Optional<Stream> findByStreamKey(String streamKey);
    
    @Query("""
        SELECT s
        FROM Stream s
        JOIN FETCH s.creator
        WHERE s.streamKey = :streamKey
    """)
    Optional<Stream> findByStreamKeyWithCreator(@Param("streamKey") String streamKey);

    long countByCreatorIdAndIsLiveTrue(Long creatorId);
    long countByIsLiveTrue();
}
