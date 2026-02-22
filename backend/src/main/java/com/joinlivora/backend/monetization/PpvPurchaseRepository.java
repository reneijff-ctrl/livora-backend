package com.joinlivora.backend.monetization;

import com.joinlivora.backend.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PpvPurchaseRepository extends JpaRepository<PpvPurchase, UUID> {
    Optional<PpvPurchase> findByPpvContentAndUserAndStatus(PpvContent ppvContent, User user, PpvPurchaseStatus status);
    Optional<PpvPurchase> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<PpvPurchase> findByClientRequestId(String clientRequestId);
    boolean existsByPpvContent_IdAndUser_IdAndStatus(UUID ppvContentId, Long userId, PpvPurchaseStatus status);

    @Query("SELECT p FROM PpvPurchase p JOIN FETCH p.user JOIN FETCH p.ppvContent ORDER BY p.purchasedAt DESC")
    List<PpvPurchase> findAllWithDetails();

    @Query("SELECT p.ppvContent.id, p.ppvContent.title, SUM(p.amount) " +
           "FROM PpvPurchase p " +
           "WHERE p.ppvContent.creator = :creator AND p.status = com.joinlivora.backend.monetization.PpvPurchaseStatus.PAID " +
           "GROUP BY p.ppvContent.id, p.ppvContent.title " +
           "ORDER BY SUM(p.amount) DESC")
    List<Object[]> findTopContentByCreator(@Param("creator") User creator, Pageable pageable);

    @Query("SELECT p.ppvContent.id, p.ppvContent.title, SUM(p.amount) " +
           "FROM PpvPurchase p " +
           "WHERE p.ppvContent.creator = :creator AND p.status = com.joinlivora.backend.monetization.PpvPurchaseStatus.PAID " +
           "AND p.purchasedAt BETWEEN :start AND :end " +
           "GROUP BY p.ppvContent.id, p.ppvContent.title")
    List<Object[]> findContentRevenueByCreatorAndPeriod(@Param("creator") User creator, @Param("start") java.time.Instant start, @Param("end") java.time.Instant end);
}
