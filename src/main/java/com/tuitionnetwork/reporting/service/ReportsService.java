package com.tuitionnetwork.reporting.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.reporting.dto.GenerateReportRequest;
import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;
import com.tuitionnetwork.reporting.dto.ReportHistoryEntry;
import com.tuitionnetwork.reporting.dto.ReportJobResponse;

import java.util.List;
import java.util.UUID;

/**
 * Reporting module (Phase 7). Generation is synchronous in the MVP: {@link #generate}
 * computes, persists a {@code GeneratedReport}, and returns it already {@code READY}
 * with an inline preview.
 */
public interface ReportsService {

    List<ReportCatalogueEntry> catalogue();

    List<ReportCatalogueEntry> catalogue(UUID schoolId);

    ReportJobResponse generate(GenerateReportRequest request);

    ReportJobResponse generateForSchool(GenerateReportRequest request, UUID schoolId);

    ReportJobResponse getJob(UUID jobId);

    ReportJobResponse getJobForSchool(UUID jobId, UUID schoolId);

    DownloadPayload download(UUID jobId);

    DownloadPayload downloadForSchool(UUID jobId, UUID schoolId);

    PageResponse<ReportHistoryEntry> history(String reportId, int page, int size);

    PageResponse<ReportHistoryEntry> historyForSchool(UUID schoolId, String reportId, int page, int size);

    /** File bytes + filename for {@code GET /reports/jobs/{jobId}/download}. */
    record DownloadPayload(String filename, byte[] content, String contentType) {
    }
}
