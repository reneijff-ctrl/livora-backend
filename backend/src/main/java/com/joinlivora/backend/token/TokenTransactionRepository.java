package com.joinlivora.backend.token;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import com.joinlivora.backend.user.User;

public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, UUID> {
    List<TokenTransaction> findAllByUserOrderByCreatedAtDesc(User user);
}
