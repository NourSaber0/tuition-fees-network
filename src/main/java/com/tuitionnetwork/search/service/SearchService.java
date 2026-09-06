package com.tuitionnetwork.search.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.dto.StudentFeeLineDto;
import com.tuitionnetwork.billing.service.BillingFeeQueryService;
import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import com.tuitionnetwork.identity.dto.ResolvedStudentDto;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.search.dto.FeeLineItemResponse;
import com.tuitionnetwork.search.dto.GuardianDuesResponse;
import com.tuitionnetwork.search.dto.StudentDuesResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final IdentityResolverService identityResolverService;
    private final BillingFeeQueryService billingFeeQueryService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public SearchService(IdentityResolverService identityResolverService,
                         BillingFeeQueryService billingFeeQueryService,
                         AuditLogRepository auditLogRepository) {
        this.identityResolverService = identityResolverService;
        this.billingFeeQueryService = billingFeeQueryService;
        this.auditLogRepository = auditLogRepository;
    }

    public SearchService(IdentityResolverService identityResolverService,
                         BillingFeeQueryService billingFeeQueryService) {
        this(identityResolverService, billingFeeQueryService, null);
    }

    public GuardianDuesResponse searchDuesByNationalId(String rawNationalId) {
        if (rawNationalId == null || rawNationalId.trim().isEmpty()) {
            throw new IllegalArgumentException("X-Guardian-National-Id header is required");
        }

        String cleanedNationalId = rawNationalId.trim();
        String hashedNationalId = identityResolverService.computeHmacSha256(cleanedNationalId);

        Optional<ResolvedGuardianDto> guardianOpt = identityResolverService.resolveGuardianByNationalId(cleanedNationalId);
        if (guardianOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guardian not found for given National ID");
        }

        ResolvedGuardianDto guardian = guardianOpt.get();

        // Privacy Tracking: Enforce AUDIT_LOG insertion every time a National ID search is performed
        if (auditLogRepository != null) {
            String targetResource = hashedNationalId != null ? hashedNationalId : guardian.nationalIdHmac();
            AuditLog auditLog = new AuditLog(
                    guardian.id(),
                    "Guardian",
                    "Search_National_ID",
                    targetResource
            );
            auditLogRepository.save(auditLog);
        }

        List<ResolvedStudentDto> students = guardian.students() != null ? guardian.students() : Collections.emptyList();
        List<UUID> studentIds = students.stream().map(ResolvedStudentDto::studentId).toList();

        List<StudentFeeLineDto> openFees = billingFeeQueryService.findOpenFeesByStudentIds(studentIds);

        Map<UUID, List<StudentFeeLineDto>> feesByStudentId = openFees.stream()
                .collect(Collectors.groupingBy(StudentFeeLineDto::studentId));

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        List<StudentDuesResponse> studentResponses = new ArrayList<>();

        for (ResolvedStudentDto student : students) {
            List<StudentFeeLineDto> studentFeeLines = feesByStudentId.getOrDefault(student.studentId(), Collections.emptyList());

            List<FeeLineItemResponse> feeItems = studentFeeLines.stream()
                    .map(f -> new FeeLineItemResponse(
                            f.feeLineId(),
                            f.feeType(),
                            f.collectionPeriod(),
                            f.totalAmount(),
                            f.remainingAmount(),
                            f.status()
                    ))
                    .toList();

            BigDecimal studentOutstanding = feeItems.stream()
                    .map(FeeLineItemResponse::remainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalOutstanding = totalOutstanding.add(studentOutstanding);

            studentResponses.add(new StudentDuesResponse(
                    student.studentId(),
                    student.studentName(),
                    student.institutionName(),
                    feeItems
            ));
        }

        return new GuardianDuesResponse(
                guardian.fullName(),
                totalOutstanding,
                studentResponses
        );
    }
}
