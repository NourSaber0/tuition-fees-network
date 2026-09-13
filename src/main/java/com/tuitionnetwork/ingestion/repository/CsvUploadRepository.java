package com.tuitionnetwork.ingestion.repository;

import com.tuitionnetwork.ingestion.domain.CsvUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CsvUploadRepository extends JpaRepository<CsvUpload, UUID> {
    List<CsvUpload> findByInstitutionId(UUID institutionId);
    List<CsvUpload> findByInstitutionIdOrderByUploadedAtDesc(UUID institutionId);
    Optional<CsvUpload> findByInstitutionIdAndFileHash(UUID institutionId, String fileHash);
    Optional<CsvUpload> findByIdAndInstitutionId(UUID id, UUID institutionId);
}
