package com.tuitionnetwork.ingestion.repository;

import com.tuitionnetwork.ingestion.domain.UploadError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadErrorRepository extends JpaRepository<UploadError, UUID> {
    List<UploadError> findByCsvUploadId(UUID csvUploadId);
}
