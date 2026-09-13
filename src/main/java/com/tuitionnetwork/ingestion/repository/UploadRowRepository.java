package com.tuitionnetwork.ingestion.repository;

import com.tuitionnetwork.ingestion.domain.UploadRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadRowRepository extends JpaRepository<UploadRow, UUID> {
    List<UploadRow> findByCsvUploadIdOrderByRowNumberAsc(UUID csvUploadId);
    List<UploadRow> findByCsvUploadIdAndStatusIgnoreCaseOrderByRowNumberAsc(UUID csvUploadId, String status);
    List<UploadRow> findByCsvUploadIdAndStatusInOrderByRowNumberAsc(UUID csvUploadId, List<String> statuses);
}
