package com.joinlivora.backend.payout;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {
    Optional<CreatorProfile> findByUser(User user);
}
