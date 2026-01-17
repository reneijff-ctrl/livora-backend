package com.joinlivora.backend.token;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CreatorEarningsRepository extends JpaRepository<CreatorEarnings, UUID> {
    Optional<CreatorEarnings> findByUser(User user);
}
