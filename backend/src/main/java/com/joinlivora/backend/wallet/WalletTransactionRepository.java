package com.joinlivora.backend.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import com.joinlivora.backend.user.User;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    List<WalletTransaction> findAllByUserIdOrderByCreatedAtDesc(User user);
    java.util.Optional<WalletTransaction> findByReferenceId(String referenceId);
}
