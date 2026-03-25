package com.joinlivora.backend.tip;

import com.joinlivora.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorTipRepository extends JpaRepository<DirectTip, UUID> {
    List<DirectTip> findAllByCreatorAndStatusOrderByCreatedAtDesc(User creator, TipStatus status);
}
