package com.tuitionnetwork.ingestion.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.dto.CreateFeeLineCommand;
import com.tuitionnetwork.billing.service.BillingFeeCommandService;
import com.tuitionnetwork.ingestion.domain.CsvUpload;
import com.tuitionnetwork.ingestion.domain.IngestionBatch;
import com.tuitionnetwork.ingestion.domain.UploadError;
import com.tuitionnetwork.ingestion.dto.IngestionReportResponse;
import com.tuitionnetwork.ingestion.dto.RowValidationError;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.IngestionBatchRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");

    private final CsvUploadRepository csvUploadRepository;
    private final UploadErrorRepository uploadErrorRepository;
    private final IngestionBatchRepository batchRepository;
    private final BillingFeeCommandService billingFeeCommandService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public IngestionService(CsvUploadRepository csvUploadRepository,
                            UploadErrorRepository uploadErrorRepository,
                            IngestionBatchRepository batchRepository,
                            BillingFeeCommandService billingFeeCommandService,
                            AuditLogRepository auditLogRepository) {
        this.csvUploadRepository = csvUploadRepository;
        this.uploadErrorRepository = uploadErrorRepository;
        this.batchRepository = batchRepository;
        this.billingFeeCommandService = billingFeeCommandService;
        this.auditLogRepository = auditLogRepository;
    }

    public IngestionService(CsvUploadRepository csvUploadRepository,
                            UploadErrorRepository uploadErrorRepository,
                            IngestionBatchRepository batchRepository,
                            BillingFeeCommandService billingFeeCommandService) {
        this(csvUploadRepository, uploadErrorRepository, batchRepository, billingFeeCommandService, null);
    }

    public IngestionService(IngestionBatchRepository batchRepository,
                            BillingFeeCommandService billingFeeCommandService) {
        this(null, null, batchRepository, billingFeeCommandService, null);
    }

    @Transactional
    public IngestionReportResponse processCsvUpload(UUID institutionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be null or empty");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "dues_upload.csv";
        String fileHash = computeSha256(file);

        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV file contains no data");
        }

        List<RowValidationError> errors = new ArrayList<>();
        List<CreateFeeLineCommand> validCommands = new ArrayList<>();
        Set<String> seenNationalIds = new HashSet<>();

        int totalDataRows = 0;
        int lineNumber = 0;

        for (String rawLine : lines) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Check and skip CSV header row
            if (lineNumber == 1 && isHeaderRow(line)) {
                continue;
            }

            totalDataRows++;
            String[] tokens = parseCsvLine(line);

            if (tokens.length < 5) {
                errors.add(new RowValidationError(lineNumber, "N/A", "Invalid CSV column count: expected 5 fields", rawLine));
                continue;
            }

            String nationalId = tokens[0].trim();
            String rawFeeType = tokens[1].trim();
            String rawAmount = tokens[2].trim();
            String currency = tokens[3].trim();
            String collectionPeriod = tokens[4].trim();

            // 1. Validate National ID
            if (!nationalId.matches("^\\d{14}$")) {
                errors.add(new RowValidationError(lineNumber, nationalId, "Invalid National ID: must be exactly 14 digits", rawLine));
                continue;
            }

            // 2. Validate Duplicate National ID in the same submission
            if (!seenNationalIds.add(nationalId)) {
                errors.add(new RowValidationError(lineNumber, nationalId, "Duplicate National ID in the same submission", rawLine));
                continue;
            }

            // 3. Validate Fee Type
            if (!isValidFeeType(rawFeeType)) {
                errors.add(new RowValidationError(lineNumber, nationalId,
                        "Invalid Fee Type: '" + rawFeeType + "'. Allowed: Tuition, Bus subscription, Books & materials, Activities", rawLine));
                continue;
            }

            // 4. Validate Amount
            BigDecimal amount;
            try {
                amount = new BigDecimal(rawAmount);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new RowValidationError(lineNumber, nationalId, "Amount must be greater than 0: " + rawAmount, rawLine));
                    continue;
                }
            } catch (Exception e) {
                errors.add(new RowValidationError(lineNumber, nationalId, "Malformed numeric amount: " + rawAmount, rawLine));
                continue;
            }

            // 5. Validate Currency
            if (!"EGP".equalsIgnoreCase(currency)) {
                errors.add(new RowValidationError(lineNumber, nationalId, "Invalid currency: '" + currency + "'. Only EGP is supported", rawLine));
                continue;
            }

            // 6. Validate Collection Period (Cannot be in the past)
            if (collectionPeriod.isEmpty() || isCollectionPeriodInPast(collectionPeriod)) {
                errors.add(new RowValidationError(lineNumber, nationalId, "Collection period is in the past or invalid: '" + collectionPeriod + "'", rawLine));
                continue;
            }

            // 7. Compute deterministic row_idempotency_key (Hash of Student + Inst + Type + Period)
            String rowIdempotencyKey = computeRowIdempotencyKey(nationalId, institutionId, rawFeeType, collectionPeriod);

            validCommands.add(new CreateFeeLineCommand(
                    nationalId,
                    null,
                    rawFeeType,
                    amount,
                    "EGP",
                    collectionPeriod,
                    LocalDate.now().plusMonths(3),
                    rowIdempotencyKey
            ));
        }

        int failedRows = errors.size();
        int successfulRows = validCommands.size();

        // Send valid rows to Billing module for creation (Idempotent by Billing design)
        if (!validCommands.isEmpty()) {
            billingFeeCommandService.createFeeLines(institutionId, validCommands);
        }

        // Save CSV_UPLOAD and UPLOAD_ERROR entities
        CsvUpload savedUpload = null;
        if (csvUploadRepository != null) {
            CsvUpload csvUpload = new CsvUpload(institutionId, fileName, totalDataRows, failedRows);
            savedUpload = csvUploadRepository.save(csvUpload);

            if (uploadErrorRepository != null && !errors.isEmpty()) {
                List<UploadError> uploadErrors = new ArrayList<>();
                for (RowValidationError error : errors) {
                    uploadErrors.add(new UploadError(
                            savedUpload,
                            error.lineNumber(),
                            error.reason(),
                            error.rawLine()
                    ));
                }
                uploadErrorRepository.saveAll(uploadErrors);
            }
        }

        // Save IngestionBatch Entity
        UUID batchId = null;
        if (batchRepository != null) {
            IngestionBatch batch = new IngestionBatch(
                    institutionId,
                    fileName,
                    fileHash,
                    totalDataRows,
                    successfulRows,
                    failedRows
            );
            IngestionBatch savedBatch = batchRepository.save(batch);
            batchId = savedBatch.getId();
        } else if (savedUpload != null) {
            batchId = savedUpload.getId();
        }

        // Privacy Tracking: Record AUDIT_LOG for CSV upload
        if (auditLogRepository != null) {
            AuditLog auditLog = new AuditLog(
                    institutionId,
                    "INSTITUTION_ADMIN",
                    "UPLOAD_DUES_CSV",
                    fileName + " (Total: " + totalDataRows + ", Succeeded: " + successfulRows + ", Failed: " + failedRows + ")"
            );
            auditLogRepository.save(auditLog);
        }

        log.info("Ingestion completed for institution {}: {} total, {} succeeded, {} failed (Persisted {} upload errors)",
                institutionId, totalDataRows, successfulRows, failedRows, errors.size());

        return new IngestionReportResponse(
                batchId != null ? batchId : (savedUpload != null ? savedUpload.getId() : UUID.randomUUID()),
                institutionId,
                fileName,
                totalDataRows,
                successfulRows,
                failedRows,
                errors,
                java.time.LocalDateTime.now()
        );
    }

    /**
     * Computes deterministic row_idempotency_key (Hash of Student+Inst+Type+Period)
     */
    public String computeRowIdempotencyKey(String studentIdentifier, UUID institutionId, String feeType, String collectionPeriod) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (studentIdentifier != null ? studentIdentifier.trim() : "") + "|" +
                    (institutionId != null ? institutionId.toString() : "") + "|" +
                    (feeType != null ? feeType.trim().toUpperCase() : "") + "|" +
                    (collectionPeriod != null ? collectionPeriod.trim().toUpperCase() : "");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "ROW-" + UUID.randomUUID();
        }
    }

    private boolean isHeaderRow(String line) {
        String lower = line.toLowerCase();
        return lower.contains("national_id") || lower.contains("fee_type") || lower.contains("nationalid");
    }

    private boolean isValidFeeType(String feeType) {
        if (feeType == null || feeType.isBlank()) return false;
        String clean = feeType.trim().toLowerCase();
        return clean.contains("tuition") ||
               clean.contains("bus") ||
               clean.contains("book") ||
               clean.contains("activ");
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

    private String[] parseCsvLine(String line) {
        return line.split(",", -1);
    }

    private List<String> readLines(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded CSV file", e);
        }
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "HASH-" + UUID.randomUUID();
        }
    }
}
