package com.tuitionnetwork.ingestion;

import com.tuitionnetwork.billing.dto.CreateFeeLineCommand;
import com.tuitionnetwork.billing.service.BillingFeeCommandService;
import com.tuitionnetwork.ingestion.domain.CsvUpload;
import com.tuitionnetwork.ingestion.domain.IngestionBatch;
import com.tuitionnetwork.ingestion.domain.UploadError;
import com.tuitionnetwork.ingestion.dto.IngestionReportResponse;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.IngestionBatchRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import com.tuitionnetwork.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private CsvUploadRepository csvUploadRepository;
    private UploadErrorRepository uploadErrorRepository;
    private IngestionBatchRepository batchRepository;
    private BillingFeeCommandService billingFeeCommandService;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        csvUploadRepository = mock(CsvUploadRepository.class);
        uploadErrorRepository = mock(UploadErrorRepository.class);
        batchRepository = mock(IngestionBatchRepository.class);
        billingFeeCommandService = mock(BillingFeeCommandService.class);

        ingestionService = new IngestionService(
                csvUploadRepository,
                uploadErrorRepository,
                batchRepository,
                billingFeeCommandService
        );

        when(batchRepository.save(any(IngestionBatch.class))).thenAnswer(invocation -> {
            IngestionBatch b = invocation.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        when(csvUploadRepository.save(any(CsvUpload.class))).thenAnswer(invocation -> {
            CsvUpload u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    @Test
    void processCsvUpload_validCsv_succeedsAllRows() {
        UUID institutionId = UUID.randomUUID();
        String csvContent = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29802021234568,Bus subscription,4000.00,EGP,Term 2 · 2026
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "dues.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        IngestionReportResponse report = ingestionService.processCsvUpload(institutionId, file);

        assertNotNull(report);
        assertEquals(2, report.totalRows());
        assertEquals(2, report.successfulRows());
        assertEquals(0, report.failedRows());
        assertTrue(report.validationErrors().isEmpty());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateFeeLineCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(billingFeeCommandService).createFeeLines(eq(institutionId), captor.capture());
        assertEquals(2, captor.getValue().size());

        // Verify row_idempotency_key was computed for valid rows
        assertNotNull(captor.getValue().get(0).rowIdempotencyKey());
        assertFalse(captor.getValue().get(0).rowIdempotencyKey().isBlank());
        assertNotNull(captor.getValue().get(1).rowIdempotencyKey());
    }

    @Test
    void processCsvUpload_strictRowValidation_rejectsOnlyInvalidRowsAndReportsLineNumbersAndPersistsErrors() {
        UUID institutionId = UUID.randomUUID();
        String csvContent = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29803031234569,InvalidFeeType,5000.00,EGP,Term 2 · 2026
                29804041234570,Books & materials,-100.00,EGP,Term 2 · 2026
                29805051234571,Activities,2000.00,EGP,Term 1 · 2024
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid_dues.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );

        IngestionReportResponse report = ingestionService.processCsvUpload(institutionId, file);

        assertNotNull(report);
        assertEquals(5, report.totalRows());
        assertEquals(1, report.successfulRows());
        assertEquals(4, report.failedRows());
        assertEquals(4, report.validationErrors().size());

        // Error 1: Duplicate National ID in row 3
        assertEquals(3, report.validationErrors().get(0).lineNumber());
        assertTrue(report.validationErrors().get(0).reason().contains("Duplicate National ID"));

        // Error 2: Invalid Fee Type in row 4
        assertEquals(4, report.validationErrors().get(1).lineNumber());
        assertTrue(report.validationErrors().get(1).reason().contains("Invalid Fee Type"));

        // Error 3: Amount <= 0 in row 5
        assertEquals(5, report.validationErrors().get(2).lineNumber());
        assertTrue(report.validationErrors().get(2).reason().contains("Amount must be greater than 0"));

        // Error 4: Collection Period in the past (2024) in row 6
        assertEquals(6, report.validationErrors().get(3).lineNumber());
        assertTrue(report.validationErrors().get(3).reason().contains("Collection period is in the past"));

        // Verify valid row 1 was still dispatched to BillingFeeCommandService
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateFeeLineCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(billingFeeCommandService).createFeeLines(eq(institutionId), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertNotNull(captor.getValue().get(0).rowIdempotencyKey());

        // Verify UPLOAD_ERROR records were created and saved
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UploadError>> errorCaptor = ArgumentCaptor.forClass(List.class);
        verify(uploadErrorRepository).saveAll(errorCaptor.capture());
        assertEquals(4, errorCaptor.getValue().size());
        assertEquals(3, errorCaptor.getValue().get(0).getRowNumber());
        assertTrue(errorCaptor.getValue().get(0).getErrorMessage().contains("Duplicate National ID"));
    }
}
