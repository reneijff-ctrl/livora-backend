package com.joinlivora.backend.wallet;

import com.joinlivora.backend.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface UserWalletRepository extends JpaRepository<UserWallet, UUID> {
    Optional<UserWallet> findByUserId(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM UserWallet b WHERE b.userId = :user")
    Optional<UserWallet> findByUserIdWithLock(User user);
}
