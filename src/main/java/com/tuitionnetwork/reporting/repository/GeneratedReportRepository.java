package com.tuitionnetwork.reporting.repository;

import com.tuitionnetwork.reporting.domain.GeneratedReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, UUID> {

    Page<GeneratedReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<GeneratedReport> findByReportIdOrderByCreatedAtDesc(String reportId, Pageable pageable);

    Page<GeneratedReport> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId, Pageable pageable);

    Page<GeneratedReport> findByInstitutionIdAndReportIdOrderByCreatedAtDesc(UUID institutionId, String reportId, Pageable pageable);

    java.util.Optional<GeneratedReport> findFirstByReportIdOrderByCreatedAtDesc(String reportId);

    java.util.Optional<GeneratedReport> findFirstByInstitutionIdAndReportIdOrderByCreatedAtDesc(UUID institutionId, String reportId);
}
