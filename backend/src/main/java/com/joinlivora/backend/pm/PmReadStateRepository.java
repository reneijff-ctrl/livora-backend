package com.joinlivora.backend.pm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PmReadStateRepository extends JpaRepository<PmReadState, Long> {

    Optional<PmReadState> findByRoomIdAndUserId(Long roomId, Long userId);

    List<PmReadState> findByUserId(Long userId);
}
