package com.tuitionnetwork.ingestion.service;

import com.tuitionnetwork.ingestion.domain.CsvUpload;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionDetailDto;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionRowErrorDto;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionSummaryDto;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Read side of the institution-integration module: fee-upload history (US-14). */
@Service
public class IngestionQueryService {

    private final CsvUploadRepository csvUploadRepository;
    private final UploadErrorRepository uploadErrorRepository;

    public IngestionQueryService(CsvUploadRepository csvUploadRepository,
                                 UploadErrorRepository uploadErrorRepository) {
        this.csvUploadRepository = csvUploadRepository;
        this.uploadErrorRepository = uploadErrorRepository;
    }

    @Transactional(readOnly = true)
    public List<FeeSubmissionSummaryDto> listForInstitution(UUID institutionId) {
        return csvUploadRepository.findByInstitutionId(institutionId).stream()
                .sorted(Comparator.comparing(CsvUpload::getUploadedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(u -> new FeeSubmissionSummaryDto(
                        u.getId(), u.getFileName(), u.getTotalRows(),
                        successfulRows(u), u.getFailedRows(), status(u), u.getUploadedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FeeSubmissionDetailDto getForInstitution(UUID institutionId, UUID submissionId) {
        CsvUpload upload = csvUploadRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Fee submission not found: " + submissionId));

        if (!upload.getInstitutionId().equals(institutionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Fee submission " + submissionId + " does not belong to institution " + institutionId);
        }

        List<FeeSubmissionRowErrorDto> errors = uploadErrorRepository.findByCsvUploadId(upload.getId()).stream()
                .sorted(Comparator.comparingInt(e -> e.getRowNumber()))
                .map(e -> new FeeSubmissionRowErrorDto(e.getRowNumber(), e.getErrorMessage(), e.getRawRowData()))
                .toList();

        return new FeeSubmissionDetailDto(
                upload.getId(), upload.getInstitutionId(), upload.getFileName(),
                upload.getTotalRows(), successfulRows(upload), upload.getFailedRows(),
                status(upload), upload.getUploadedAt(), errors);
    }

    private static int successfulRows(CsvUpload u) {
        return Math.max(0, u.getTotalRows() - u.getFailedRows());
    }

    private static String status(CsvUpload u) {
        if (u.getFailedRows() <= 0) {
            return "PROCESSED";
        }
        return u.getFailedRows() >= u.getTotalRows() ? "REJECTED" : "PARTIAL";
    }
}
