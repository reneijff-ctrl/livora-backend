package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {
    List<UserBadge> findAllByUser(User user);
}
