package com.tuitionnetwork.ingestion.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.ingestion.domain.CsvUpload;
import com.tuitionnetwork.ingestion.domain.UploadError;
import com.tuitionnetwork.ingestion.domain.UploadRow;
import com.tuitionnetwork.ingestion.dto.FeeUploadDetailDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadResponseDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadRowDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadSummaryDto;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import com.tuitionnetwork.ingestion.repository.UploadRowRepository;
import com.tuitionnetwork.ingestion.util.CsvFileParser;
import com.tuitionnetwork.ingestion.util.XlsxParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SchoolFeeUploadServiceImpl implements SchoolFeeUploadService {

    private static final Logger log = LoggerFactory.getLogger(SchoolFeeUploadServiceImpl.class);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");

    private final CsvUploadRepository csvUploadRepository;
    private final UploadRowRepository uploadRowRepository;
    private final UploadErrorRepository uploadErrorRepository;
    private final FeeLineRepository feeLineRepository;
    private final StudentRepository studentRepository;
    private final IdentityResolverService identityResolverService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public SchoolFeeUploadServiceImpl(
            CsvUploadRepository csvUploadRepository,
            UploadRowRepository uploadRowRepository,
            UploadErrorRepository uploadErrorRepository,
            FeeLineRepository feeLineRepository,
            @Autowired(required = false) StudentRepository studentRepository,
            @Autowired(required = false) IdentityResolverService identityResolverService,
            @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.csvUploadRepository = csvUploadRepository;
        this.uploadRowRepository = uploadRowRepository;
        this.uploadErrorRepository = uploadErrorRepository;
        this.feeLineRepository = feeLineRepository;
        this.studentRepository = studentRepository;
        this.identityResolverService = identityResolverService;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public String getCsvTemplate() {
        return """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1 2026/27,Tuition,18000.00,Term 1 2026/27,2026-10-15
                STU-002,Books - Term 1 2026/27,Books,2500.00,Term 1 2026/27,2026-10-15
                STU-003,Bus - Term 1 2026/27,Bus,4000.00,Term 1 2026/27,2026-10-15
                STU-004,Activities - Term 1 2026/27,Activity,1500.00,Term 1 2026/27,2026-10-15
                """;
    }

    @Override
    @Transactional
    public FeeUploadResponseDto processUpload(UUID schoolId, MultipartFile file, UUID actorId) {
        validateFileNotNullOrEmpty(file);

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().trim() : "upload.csv";
        boolean isXlsx = isExcelFile(fileName);
        boolean isCsv = isCsvFile(fileName);

        if (!isCsv && !isXlsx) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unsupported_file_type: Unsupported file type. Only CSV and Excel (.xlsx) files are supported");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file: " + e.getMessage());
        }

        String fileHash = computeSha256(fileBytes);

        // US-37: Duplicate upload check
        if (csvUploadRepository.findByInstitutionIdAndFileHash(schoolId, fileHash).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "duplicate_upload: Duplicate file upload detected for this school");
        }

        List<List<String>> allRows;
        try {
            if (isXlsx) {
                allRows = XlsxParser.parse(new ByteArrayInputStream(fileBytes));
            } else {
                allRows = CsvFileParser.parse(new ByteArrayInputStream(fileBytes));
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + e.getMessage());
        }

        if (allRows == null || allRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File contains no data");
        }

        // Determine format and header
        List<String> firstRow = allRows.get(0);
        boolean hasHeader = isHeaderRow(firstRow);
        boolean isLegacyFormat = detectLegacyFormat(firstRow, hasHeader);

        int startIdx = hasHeader ? 1 : 0;
        int dataRowCount = allRows.size() - startIdx;
        if (dataRowCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File contains no data rows");
        }

        CsvUpload upload = new CsvUpload();
        upload.setInstitutionId(schoolId);
        upload.setFileName(fileName);
        upload.setFileHash(fileHash);
        upload.setFormat(isLegacyFormat ? "LEGACY_5_COL" : "SIS_6_COL");
        upload.setTotalRows(dataRowCount);
        upload.setStatus("Processing");
        upload.setUploadedAt(LocalDateTime.now());
        upload = csvUploadRepository.save(upload);

        int acceptedCount = 0;
        int rejectedCount = 0;

        for (int i = startIdx; i < allRows.size(); i++) {
            int rowNumber = i - startIdx + 1;
            List<String> rowTokens = allRows.get(i);
            String rawRowData = String.join(",", rowTokens);

            RowProcessingResult result;
            if (isLegacyFormat) {
                result = processLegacyRow(schoolId, rowTokens, rowNumber);
            } else {
                result = processSisRow(schoolId, rowTokens, rowNumber);
            }

            if (result.isValid()) {
                // Create FeeLine
                FeeLine feeLine = createFeeLineIfAbsent(schoolId, result);
                UploadRow uploadRow = new UploadRow(
                        upload,
                        rowNumber,
                        result.studentRef(),
                        result.feeName(),
                        result.category(),
                        result.amountEGP(),
                        result.dueDate(),
                        result.term(),
                        "Accepted",
                        null,
                        rawRowData,
                        feeLine.getId()
                );
                uploadRowRepository.save(uploadRow);
                acceptedCount++;
            } else {
                UploadRow uploadRow = new UploadRow(
                        upload,
                        rowNumber,
                        result.studentRef(),
                        result.feeName(),
                        result.category(),
                        result.amountEGP(),
                        result.dueDate(),
                        result.term(),
                        "Rejected",
                        result.errorReason(),
                        rawRowData,
                        null
                );
                uploadRowRepository.save(uploadRow);

                UploadError error = new UploadError(upload, rowNumber, result.errorReason(), rawRowData);
                uploadErrorRepository.save(error);
                rejectedCount++;
            }
        }

        upload.setAcceptedRows(acceptedCount);
        upload.setFailedRows(rejectedCount);
        upload.setStatus(computeUploadStatus(dataRowCount, acceptedCount, rejectedCount));
        csvUploadRepository.save(upload);

        logAudit(actorId, schoolId, "UPLOAD_FEES",
                "Uploaded fees file '" + fileName + "' (Total: " + dataRowCount + ", Accepted: " + acceptedCount + ", Rejected: " + rejectedCount + ")");

        return new FeeUploadResponseDto(upload.getId().toString(), upload.getStatus(),
                "Processed " + dataRowCount + " rows: " + acceptedCount + " accepted, " + rejectedCount + " rejected");
    }

    @Override
    @Transactional(readOnly = true)
    public FeeUploadDetailDto getUploadDetail(UUID schoolId, String uploadId) {
        CsvUpload upload = findUploadForSchool(schoolId, uploadId);
        int validRows = upload.getAcceptedRows();
        int invalidRows = upload.getFailedRows();
        return new FeeUploadDetailDto(
                upload.getId().toString(),
                upload.getStatus(),
                upload.getTotalRows(),
                validRows,
                invalidRows,
                upload.getAcceptedRows(),
                upload.getFailedRows(),
                upload.getUploadedAt(),
                upload.getFileName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeUploadRowDto> getUploadRows(UUID schoolId, String uploadId, String status) {
        CsvUpload upload = findUploadForSchool(schoolId, uploadId);
        List<UploadRow> rows = uploadRowRepository.findByCsvUploadIdOrderByRowNumberAsc(upload.getId());

        if (status != null && !status.isBlank()) {
            String filter = status.trim().toUpperCase();
            rows = rows.stream()
                    .filter(r -> {
                        String st = r.getStatus() != null ? r.getStatus().toUpperCase() : "";
                        if (filter.equals("ACCEPTED") || filter.equals("VALID")) {
                            return st.equals("ACCEPTED") || st.equals("VALID");
                        }
                        if (filter.equals("REJECTED") || filter.equals("INVALID")) {
                            return st.equals("REJECTED") || st.equals("INVALID");
                        }
                        return st.equalsIgnoreCase(filter);
                    })
                    .toList();
        }

        return rows.stream().map(this::toRowDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeUploadRowDto> getUploadErrors(UUID schoolId, String uploadId) {
        return getUploadRows(schoolId, uploadId, "Rejected");
    }

    @Override
    @Transactional(readOnly = true)
    public String exportUploadErrorsCsv(UUID schoolId, String uploadId) {
        List<FeeUploadRowDto> errors = getUploadErrors(schoolId, uploadId);
        StringBuilder sb = new StringBuilder();
        sb.append("rowNumber,studentRef,feeName,category,amountEGP,dueDate,errorReason\n");
        for (FeeUploadRowDto err : errors) {
            sb.append(err.rowNumber()).append(",")
                    .append(escapeCsv(err.studentRef())).append(",")
                    .append(escapeCsv(err.feeName())).append(",")
                    .append(escapeCsv(err.category())).append(",")
                    .append(err.amountEGP() != null ? err.amountEGP() : "").append(",")
                    .append(err.dueDate() != null ? err.dueDate() : "").append(",")
                    .append(escapeCsv(err.errorReason())).append("\n");
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public FeeUploadDetailDto resubmitUpload(UUID schoolId, String uploadId, MultipartFile file, UUID actorId) {
        validateFileNotNullOrEmpty(file);
        CsvUpload upload = findUploadForSchool(schoolId, uploadId);

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().trim() : upload.getFileName();
        boolean isXlsx = isExcelFile(fileName);
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file: " + e.getMessage());
        }

        List<List<String>> allRows;
        try {
            if (isXlsx) {
                allRows = XlsxParser.parse(new ByteArrayInputStream(fileBytes));
            } else {
                allRows = CsvFileParser.parse(new ByteArrayInputStream(fileBytes));
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse file: " + e.getMessage());
        }

        if (allRows == null || allRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File contains no data");
        }

        boolean hasHeader = isHeaderRow(allRows.get(0));
        boolean isLegacyFormat = "LEGACY_5_COL".equalsIgnoreCase(upload.getFormat()) || detectLegacyFormat(allRows.get(0), hasHeader);

        int startIdx = hasHeader ? 1 : 0;
        List<UploadRow> existingRows = uploadRowRepository.findByCsvUploadIdOrderByRowNumberAsc(upload.getId());

        int newlyAccepted = 0;

        for (int i = startIdx; i < allRows.size(); i++) {
            int rowNumber = i - startIdx + 1;
            List<String> rowTokens = allRows.get(i);
            String rawRowData = String.join(",", rowTokens);

            RowProcessingResult result = isLegacyFormat
                    ? processLegacyRow(schoolId, rowTokens, rowNumber)
                    : processSisRow(schoolId, rowTokens, rowNumber);

            // Find matching row if correcting by rowNumber or studentRef
            Optional<UploadRow> matchingExisting = existingRows.stream()
                    .filter(r -> r.getRowNumber() == rowNumber)
                    .findFirst();

            if (result.isValid()) {
                // US-36: Ensure we never duplicate fee lines for rows already accepted in prior passes
                boolean alreadyAccepted = matchingExisting.isPresent() && "Accepted".equalsIgnoreCase(matchingExisting.get().getStatus());
                if (alreadyAccepted) {
                    continue; // Skip creating duplicate fees
                }

                FeeLine feeLine = createFeeLineIfAbsent(schoolId, result);

                if (matchingExisting.isPresent()) {
                    UploadRow r = matchingExisting.get();
                    r.setStudentRef(result.studentRef());
                    r.setFeeName(result.feeName());
                    r.setCategory(result.category());
                    r.setAmountEGP(result.amountEGP());
                    r.setDueDate(result.dueDate());
                    r.setTerm(result.term());
                    r.setStatus("Accepted");
                    r.setErrorReason(null);
                    r.setFeeLineId(feeLine.getId());
                    r.setRawRowData(rawRowData);
                    uploadRowRepository.save(r);
                } else {
                    UploadRow newRow = new UploadRow(
                            upload,
                            rowNumber,
                            result.studentRef(),
                            result.feeName(),
                            result.category(),
                            result.amountEGP(),
                            result.dueDate(),
                            result.term(),
                            "Accepted",
                            null,
                            rawRowData,
                            feeLine.getId()
                    );
                    uploadRowRepository.save(newRow);
                }
                newlyAccepted++;
            } else {
                if (matchingExisting.isPresent()) {
                    UploadRow r = matchingExisting.get();
                    r.setStatus("Rejected");
                    r.setErrorReason(result.errorReason());
                    r.setRawRowData(rawRowData);
                    uploadRowRepository.save(r);
                } else {
                    UploadRow newRow = new UploadRow(
                            upload,
                            rowNumber,
                            result.studentRef(),
                            result.feeName(),
                            result.category(),
                            result.amountEGP(),
                            result.dueDate(),
                            result.term(),
                            "Rejected",
                            result.errorReason(),
                            rawRowData,
                            null
                    );
                    uploadRowRepository.save(newRow);
                }
            }
        }

        // Recompute totals
        List<UploadRow> updatedRows = uploadRowRepository.findByCsvUploadIdOrderByRowNumberAsc(upload.getId());
        int total = updatedRows.size();
        int accepted = (int) updatedRows.stream().filter(r -> "Accepted".equalsIgnoreCase(r.getStatus())).count();
        int rejected = (int) updatedRows.stream().filter(r -> "Rejected".equalsIgnoreCase(r.getStatus())).count();

        upload.setTotalRows(total);
        upload.setAcceptedRows(accepted);
        upload.setFailedRows(rejected);
        upload.setStatus(computeUploadStatus(total, accepted, rejected));
        csvUploadRepository.save(upload);

        logAudit(actorId, schoolId, "RESUBMIT_FEES",
                "Resubmitted fee upload " + upload.getId() + " (Total: " + total + ", Accepted: " + accepted + ", Rejected: " + rejected + ")");

        return getUploadDetail(schoolId, upload.getId().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeUploadSummaryDto> getUploadHistory(UUID schoolId) {
        List<CsvUpload> uploads = csvUploadRepository.findByInstitutionIdOrderByUploadedAtDesc(schoolId);
        return uploads.stream().map(u -> new FeeUploadSummaryDto(
                u.getId().toString(),
                u.getUploadedAt(),
                u.getFileName(),
                u.getStatus(),
                u.getTotalRows(),
                u.getAcceptedRows(),
                u.getFailedRows()
        )).toList();
    }

    // --- Validation and Helper Logic ---

    private RowProcessingResult processSisRow(UUID schoolId, List<String> tokens, int rowNumber) {
        if (tokens.size() < 6) {
            return RowProcessingResult.invalid(rowNumber, null, null, null, null, null, null,
                    "Invalid column count: expected 6 fields, found " + tokens.size());
        }

        String studentRef = tokens.get(0).trim();
        String feeName = tokens.get(1).trim();
        String categoryStr = tokens.get(2).trim();
        String amountStr = tokens.get(3).trim();
        String term = tokens.get(4).trim();
        String dueDateStr = tokens.get(5).trim();

        if (studentRef.isBlank()) {
            return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, null, null, term,
                    "studentRef is required");
        }

        if (feeName.isBlank()) {
            return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, null, null, term,
                    "feeName is required");
        }

        FeeType feeType = parseFeeType(categoryStr);
        if (feeType == null) {
            return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, null, null, term,
                    "Invalid category: '" + categoryStr + "'. Allowed: Tuition, Books, Activity, Bus");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, amount, null, term,
                        "Amount must be greater than 0: " + amountStr);
            }
        } catch (Exception e) {
            return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, null, null, term,
                    "Malformed numeric amount: " + amountStr);
        }

        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(dueDateStr);
        } catch (Exception e) {
            return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, amount, null, term,
                    "Invalid due date format: '" + dueDateStr + "'. Expected YYYY-MM-DD");
        }

        if (term.isBlank()) {
            term = "Term 1 2026/27";
        }

        // Student resolution and validation
        Optional<Student> studentOpt = findStudent(schoolId, studentRef);
        UUID studentId;
        if (studentOpt.isPresent()) {
            Student s = studentOpt.get();
            if (!schoolId.equals(s.getInstitutionId())) {
                return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, amount, dueDate, term,
                        "cross_school_access: Student belongs to another school");
            }
            if ("Inactive".equalsIgnoreCase(s.getStatus())) {
                return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, amount, dueDate, term,
                        "student_inactive: Cannot create fee for deactivated student");
            }
            studentId = s.getId();
        } else if (studentRepository != null && studentRepository.countByInstitutionId(schoolId) > 0) {
            return RowProcessingResult.invalid(rowNumber, studentRef, feeName, categoryStr, amount, dueDate, term,
                    "Student reference not found in school roster: " + studentRef);
        } else {
            studentId = UUID.nameUUIDFromBytes(("student-" + schoolId + "-" + studentRef).getBytes());
        }

        return RowProcessingResult.valid(rowNumber, studentId, studentRef, feeName, toCategoryDisplayName(feeType),
                feeType, amount, dueDate, term);
    }

    private RowProcessingResult processLegacyRow(UUID schoolId, List<String> tokens, int rowNumber) {
        if (tokens.size() < 5) {
            return RowProcessingResult.invalid(rowNumber, null, null, null, null, null, null,
                    "Invalid column count: expected 5 fields, found " + tokens.size());
        }

        String nationalId = tokens.get(0).trim();
        String feeTypeStr = tokens.get(1).trim();
        String amountStr = tokens.get(2).trim();
        String currency = tokens.get(3).trim();
        String collectionPeriod = tokens.get(4).trim();

        if (!nationalId.matches("^\\d{14}$")) {
            return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, null, null, collectionPeriod,
                    "Invalid National ID: must be exactly 14 digits");
        }

        FeeType feeType = parseFeeType(feeTypeStr);
        if (feeType == null) {
            return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, null, null, collectionPeriod,
                    "Invalid fee type: '" + feeTypeStr + "'. Allowed: Tuition, Bus subscription, Books & materials, Activities");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, amount, null, collectionPeriod,
                        "Amount must be greater than 0: " + amountStr);
            }
        } catch (Exception e) {
            return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, null, null, collectionPeriod,
                    "Malformed numeric amount: " + amountStr);
        }

        if (!"EGP".equalsIgnoreCase(currency)) {
            return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, amount, null, collectionPeriod,
                    "Invalid currency: '" + currency + "'. Only EGP is supported");
        }

        if (collectionPeriod.isBlank() || isCollectionPeriodInPast(collectionPeriod)) {
            return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, amount, null, collectionPeriod,
                    "Collection period is in the past or invalid: '" + collectionPeriod + "'");
        }

        // Student resolution
        Optional<Student> studentOpt = findStudent(schoolId, nationalId);
        UUID studentId;
        if (studentOpt.isPresent()) {
            Student s = studentOpt.get();
            if ("Inactive".equalsIgnoreCase(s.getStatus())) {
                return RowProcessingResult.invalid(rowNumber, nationalId, null, feeTypeStr, amount, null, collectionPeriod,
                        "student_inactive: Cannot create fee for deactivated student");
            }
            studentId = s.getId();
        } else {
            studentId = UUID.nameUUIDFromBytes(("student-" + nationalId).getBytes());
        }

        LocalDate dueDate = LocalDate.now().plusMonths(3);
        String feeName = feeType.getDisplayName() + " - " + collectionPeriod;

        return RowProcessingResult.valid(rowNumber, studentId, nationalId, feeName, toCategoryDisplayName(feeType),
                feeType, amount, dueDate, collectionPeriod);
    }

    private FeeLine createFeeLineIfAbsent(UUID schoolId, RowProcessingResult result) {
        String termKey = result.term() != null ? result.term().trim().toUpperCase() : "GENERAL";
        String rowKey = "ROW-" + schoolId + "-" + result.studentId() + "-" + result.feeType().name() + "-" + termKey;

        Optional<FeeLine> existingByKey = feeLineRepository.findByRowIdempotencyKey(rowKey);
        if (existingByKey.isPresent()) {
            return existingByKey.get();
        }

        FeeLine feeLine = new FeeLine(
                schoolId,
                result.studentId(),
                result.feeType(),
                result.amountEGP(),
                result.amountEGP(),
                result.term(),
                result.dueDate()
        );
        feeLine.setPaidAmount(BigDecimal.ZERO);
        feeLine.setStatus(FeeStatus.OUTSTANDING);
        feeLine.setCurrency("EGP");
        feeLine.setRowIdempotencyKey(rowKey);
        return feeLineRepository.save(feeLine);
    }

    private Optional<Student> findStudent(UUID schoolId, String studentRef) {
        if (studentRepository == null) return Optional.empty();

        // 1. By schoolId and studentRef
        Optional<Student> byRef = studentRepository.findByInstitutionIdAndStudentRef(schoolId, studentRef);
        if (byRef.isPresent()) return byRef;

        // 2. By UUID
        try {
            UUID id = UUID.fromString(studentRef);
            Optional<Student> byId = studentRepository.findById(id);
            if (byId.isPresent()) return byId;
        } catch (Exception ignored) {
        }

        // 3. By National ID hash
        if (identityResolverService != null && studentRef.matches("^\\d{14}$")) {
            try {
                String hash = identityResolverService.computeHmacSha256(studentRef);
                Optional<Student> byHash = studentRepository.findByNationalIdHash(hash);
                if (byHash.isPresent()) return byHash;
            } catch (Exception ignored) {
            }
        }

        return Optional.empty();
    }

    private CsvUpload findUploadForSchool(UUID schoolId, String uploadId) {
        UUID id = parseUploadId(uploadId);
        CsvUpload upload = csvUploadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee upload not found: " + uploadId));

        if (!schoolId.equals(upload.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cross_school_access: Upload belongs to another school");
        }
        return upload;
    }

    private UUID parseUploadId(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload ID is required");
        }
        String clean = uploadId.trim();
        if (clean.toUpperCase().startsWith("UPL-")) {
            clean = clean.substring(4);
        }
        try {
            return UUID.fromString(clean);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee upload not found: " + uploadId);
        }
    }

    private boolean isHeaderRow(List<String> row) {
        if (row == null || row.isEmpty()) return false;
        String line = String.join(" ", row).toLowerCase();
        return line.contains("studentref") || line.contains("category") || line.contains("amountegp")
                || line.contains("duedate") || line.contains("national_id") || line.contains("nationalid")
                || line.contains("fee_type") || line.contains("feetype");
    }

    private boolean detectLegacyFormat(List<String> row, boolean hasHeader) {
        if (hasHeader) {
            String line = String.join(" ", row).toLowerCase();
            if (line.contains("national_id") || line.contains("nationalid") || line.contains("fee_type") || line.contains("feetype")) {
                return true;
            }
            if (line.contains("studentref") || line.contains("amountegp")) {
                return false;
            }
        }
        return row.size() == 5;
    }

    private FeeType parseFeeType(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toUpperCase();
        if (clean.contains("TUITION")) return FeeType.TUITION;
        if (clean.contains("BUS")) return FeeType.BUS;
        if (clean.contains("BOOK")) return FeeType.BOOKS;
        if (clean.contains("ACTIV")) return FeeType.ACTIVITIES;
        return null;
    }

    private String toCategoryDisplayName(FeeType type) {
        if (type == null) return "Tuition";
        return switch (type) {
            case TUITION -> "Tuition";
            case BOOKS -> "Books";
            case ACTIVITIES -> "Activity";
            case BUS -> "Bus";
        };
    }

    private boolean isCollectionPeriodInPast(String collectionPeriod) {
        Matcher matcher = YEAR_PATTERN.matcher(collectionPeriod);
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int currentYear = LocalDate.now().getYear();
            if (year < currentYear) {
                return true;
            }
        }
        return false;
    }

    private String computeUploadStatus(int total, int accepted, int rejected) {
        if (total == 0 || rejected == 0) {
            return "Completed";
        }
        if (accepted > 0 && rejected > 0) {
            return "Completed with Errors";
        }
        return "Failed";
    }

    private FeeUploadRowDto toRowDto(UploadRow row) {
        return new FeeUploadRowDto(
                row.getRowNumber(),
                row.getStudentRef(),
                row.getFeeName(),
                row.getCategory(),
                row.getAmountEGP(),
                row.getDueDate(),
                row.getStatus(),
                row.getErrorReason()
        );
    }

    private void validateFileNotNullOrEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File cannot be null or empty");
        }
    }

    private boolean isExcelFile(String fileName) {
        return fileName.toLowerCase().endsWith(".xlsx");
    }

    private boolean isCsvFile(String fileName) {
        return fileName.toLowerCase().endsWith(".csv");
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private void logAudit(UUID actorId, UUID institutionId, String action, String details) {
        if (auditLogRepository != null) {
            AuditLog auditLog = new AuditLog(institutionId, actorId != null ? actorId.toString() : "SYSTEM", action, details);
            auditLogRepository.save(auditLog);
        }
    }

    private record RowProcessingResult(
            int rowNumber,
            UUID studentId,
            String studentRef,
            String feeName,
            String category,
            FeeType feeType,
            BigDecimal amountEGP,
            LocalDate dueDate,
            String term,
            boolean isValid,
            String errorReason
    ) {
        public static RowProcessingResult valid(int rowNumber, UUID studentId, String studentRef, String feeName,
                                                String category, FeeType feeType, BigDecimal amountEGP,
                                                LocalDate dueDate, String term) {
            return new RowProcessingResult(rowNumber, studentId, studentRef, feeName, category, feeType,
                    amountEGP, dueDate, term, true, null);
        }

        public static RowProcessingResult invalid(int rowNumber, String studentRef, String feeName,
                                                  String category, BigDecimal amountEGP, LocalDate dueDate,
                                                  String term, String errorReason) {
            return new RowProcessingResult(rowNumber, null, studentRef, feeName, category, null,
                    amountEGP, dueDate, term, false, errorReason);
        }
    }
}
