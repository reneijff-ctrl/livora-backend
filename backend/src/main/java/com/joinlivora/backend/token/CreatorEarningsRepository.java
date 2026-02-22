package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface CreatorEarningsRepository extends JpaRepository<CreatorEarnings, UUID> {
    Optional<CreatorEarnings> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM CreatorEarnings e WHERE e.id = :id")
    Optional<CreatorEarnings> findByIdWithLock(UUID id);
}
