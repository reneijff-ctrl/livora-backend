package com.joinlivora.backend.streaming;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StreamRoomRepository extends JpaRepository<StreamRoom, UUID> {
    Optional<StreamRoom> findByCreator(User creator);
    Optional<StreamRoom> findByCreatorEmail(String email);
    java.util.List<StreamRoom> findAllByIsLiveTrue();
}
