package com.tuitionnetwork.ingestion.repository;

import com.tuitionnetwork.ingestion.domain.CsvUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CsvUploadRepository extends JpaRepository<CsvUpload, UUID> {
    List<CsvUpload> findByInstitutionId(UUID institutionId);
}
