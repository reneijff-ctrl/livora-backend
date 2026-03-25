package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutCreatorEarningsRepository extends JpaRepository<CreatorEarnings, UUID> {
    Optional<CreatorEarnings> findByCreator(User creator);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ce FROM PayoutCreatorEarnings ce WHERE ce.creator = :user")
    Optional<CreatorEarnings> findByUserWithLock(@Param("user") User user);

    Optional<CreatorEarnings> findByCreatorId(Long creatorId);
}
