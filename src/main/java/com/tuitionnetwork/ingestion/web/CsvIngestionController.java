package com.tuitionnetwork.ingestion.web;

import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.ingestion.dto.IngestionReportResponse;
import com.tuitionnetwork.ingestion.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institutions/{id}/dues/upload")
public class CsvIngestionController {

    private final IngestionService ingestionService;
    private final InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    public CsvIngestionController(IngestionService ingestionService,
                                  @Autowired(required = false) InstitutionAdminRepository institutionAdminRepository) {
        this.ingestionService = ingestionService;
        this.institutionAdminRepository = institutionAdminRepository;
    }

    public CsvIngestionController(IngestionService ingestionService) {
        this(ingestionService, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INSTITUTION_ADMIN')")
    public ResponseEntity<IngestionReportResponse> uploadDuesCsv(
            @PathVariable("id") UUID institutionId,
            @RequestParam("file") MultipartFile file) {

        verifyInstitutionAccess(institutionId);
        IngestionReportResponse report = ingestionService.processCsvUpload(institutionId, file);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(PendingBusinessRuleException.class)
    public ResponseEntity<Map<String, String>> handlePendingBusinessRule(PendingBusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "error", "PENDING_BUSINESS_RULE",
                        "message", ex.getMessage()
                ));
    }

    private void verifyInstitutionAccess(UUID requestedInstitutionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && institutionAdminRepository != null) {
            institutionAdminRepository.findByEmail(auth.getName().trim().toLowerCase()).ifPresent(admin -> {
                if (admin.getInstitutionId() != null && !admin.getInstitutionId().equals(requestedInstitutionId)) {
                    throw new PendingBusinessRuleException(
                            "Pending Business Rule: Cross-Institution Data Bleed is strictly prohibited. " +
                            "Administrator for institution '" + admin.getInstitutionId() + "' cannot upload dues for institution '" + requestedInstitutionId + "'."
                    );
                }
            });
        }
    }
}
