package com.tuitionnetwork.billing.web;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.dto.StudentFeeLineDto;
import com.tuitionnetwork.billing.service.BillingFeeCommandService;
import com.tuitionnetwork.billing.service.BillingFeeQueryService;
import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institutions/{id}")
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
public class InstitutionDuesController {

    private final BillingFeeQueryService billingFeeQueryService;
    private final BillingFeeCommandService billingFeeCommandService;
    private final AuditLogRepository auditLogRepository;
    private final InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    public InstitutionDuesController(BillingFeeQueryService billingFeeQueryService,
                                     BillingFeeCommandService billingFeeCommandService,
                                     AuditLogRepository auditLogRepository,
                                     @Autowired(required = false) InstitutionAdminRepository institutionAdminRepository) {
        this.billingFeeQueryService = billingFeeQueryService;
        this.billingFeeCommandService = billingFeeCommandService;
        this.auditLogRepository = auditLogRepository;
        this.institutionAdminRepository = institutionAdminRepository;
    }

    public InstitutionDuesController(BillingFeeQueryService billingFeeQueryService,
                                     AuditLogRepository auditLogRepository) {
        this(billingFeeQueryService, null, auditLogRepository, null);
    }

    @GetMapping("/dues")
    public ResponseEntity<List<StudentFeeLineDto>> getInstitutionDues(@PathVariable("id") UUID institutionId) {
        verifyInstitutionAccess(institutionId);
        List<StudentFeeLineDto> fees = billingFeeQueryService.findFeesByInstitution(institutionId);

        if (auditLogRepository != null) {
            AuditLog auditLog = new AuditLog(
                    institutionId,
                    "INSTITUTION_ADMIN",
                    "VIEW_INSTITUTION_DUES",
                    "Institution Dues (Count: " + fees.size() + ")"
            );
            auditLogRepository.save(auditLog);
        }

        return ResponseEntity.ok(fees);
    }

    @GetMapping("/students/{studentId}/dues")
    public ResponseEntity<List<StudentFeeLineDto>> getStudentDuesAtInstitution(
            @PathVariable("id") UUID institutionId,
            @PathVariable("studentId") UUID studentId) {

        verifyInstitutionAccess(institutionId);
        List<StudentFeeLineDto> fees = billingFeeQueryService.findFeesByInstitutionAndStudent(institutionId, studentId);

        if (auditLogRepository != null) {
            AuditLog auditLog = new AuditLog(
                    institutionId,
                    "INSTITUTION_ADMIN",
                    "VIEW_STUDENT_DUES",
                    "Student: " + studentId + " (Count: " + fees.size() + ")"
            );
            auditLogRepository.save(auditLog);
        }

        return ResponseEntity.ok(fees);
    }

    @PostMapping("/dues/{feeLineId}/cancel")
    public ResponseEntity<Map<String, String>> cancelFeeLineAtInstitution(
            @PathVariable("id") UUID institutionId,
            @PathVariable("feeLineId") UUID feeLineId) {

        verifyInstitutionAccess(institutionId);
        if (billingFeeCommandService != null) {
            billingFeeCommandService.cancelFeeLine(institutionId, feeLineId);
        }

        if (auditLogRepository != null) {
            AuditLog auditLog = new AuditLog(
                    institutionId,
                    "INSTITUTION_ADMIN",
                    "CANCEL_FEE_LINE",
                    "FeeLine: " + feeLineId
            );
            auditLogRepository.save(auditLog);
        }

        return ResponseEntity.ok(Map.of(
                "status", "CANCELLED",
                "message", "Fee line " + feeLineId + " has been successfully cancelled."
        ));
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
                            "Administrator for institution '" + admin.getInstitutionId() + "' cannot access data for institution '" + requestedInstitutionId + "'."
                    );
                }
            });
        }
    }
}
