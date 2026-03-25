package com.joinlivora.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatViolationLogRepository extends JpaRepository<ChatViolationLog, Long> {
    List<ChatViolationLog> findAllByUserId(Long userId);
    List<ChatViolationLog> findAllByCreatorId(Long creatorId);
}
