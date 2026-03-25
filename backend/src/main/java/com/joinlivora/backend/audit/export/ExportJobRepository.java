package com.joinlivora.backend.audit.export;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {
}
