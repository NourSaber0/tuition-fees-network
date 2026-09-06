package com.tuitionnetwork.ingestion.repository;

import com.tuitionnetwork.ingestion.domain.IngestionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, UUID> {

    List<IngestionBatch> findByInstitutionId(UUID institutionId);

    Optional<IngestionBatch> findByInstitutionIdAndFileHash(UUID institutionId, String fileHash);
}
