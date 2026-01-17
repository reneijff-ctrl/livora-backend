package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TokenBalanceRepository extends JpaRepository<TokenBalance, UUID> {
    Optional<TokenBalance> findByUser(User user);
}
