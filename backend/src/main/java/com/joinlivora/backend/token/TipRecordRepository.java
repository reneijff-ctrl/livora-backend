package com.joinlivora.backend.token;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TipRecordRepository extends JpaRepository<TipRecord, UUID> {
}
