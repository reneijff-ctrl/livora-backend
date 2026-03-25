package com.joinlivora.backend.monetization;

import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HighlightedChatMessageRepository extends JpaRepository<HighlightedMessage, UUID> {

    List<HighlightedMessage> findByRoomIdOrderByCreatedAtDesc(Stream roomId);

    @Query("SELECT SUM(h.amount) FROM HighlightedMessage h WHERE h.roomId.creator = :creator AND h.createdAt >= :since")
    BigDecimal sumAmountByCreatorAndPeriod(@Param("creator") User creator, @Param("since") Instant since);

    Optional<HighlightedMessage> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<HighlightedMessage> findByClientRequestId(String clientRequestId);

    @Query("SELECT h.roomId.id, h.roomId.title, SUM(h.amount) " +
           "FROM HighlightedMessage h " +
           "WHERE h.roomId.creator = :creator AND h.status = com.joinlivora.backend.monetization.TipStatus.COMPLETED " +
           "GROUP BY h.roomId.id, h.roomId.title " +
           "ORDER BY SUM(h.amount) DESC")
    List<Object[]> findTopStreamsByCreator(@Param("creator") User creator, Pageable pageable);

    @Query("SELECT h.roomId.id, h.roomId.title, SUM(h.amount) " +
           "FROM HighlightedMessage h " +
           "WHERE h.roomId.creator = :creator AND h.status = com.joinlivora.backend.monetization.TipStatus.COMPLETED " +
           "AND h.createdAt BETWEEN :start AND :end " +
           "GROUP BY h.roomId.id, h.roomId.title")
    List<Object[]> findStreamRevenueByCreatorAndPeriod(@Param("creator") User creator, @Param("start") java.time.Instant start, @Param("end") java.time.Instant end);
}
