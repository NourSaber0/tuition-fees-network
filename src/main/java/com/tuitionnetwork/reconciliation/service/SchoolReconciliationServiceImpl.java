package com.tuitionnetwork.reconciliation.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.dto.InstitutionSettlementDto;
import com.tuitionnetwork.identity.dto.InstitutionSettlementSummaryDto;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.dto.SchoolReconciliationSummaryDto;
import com.tuitionnetwork.reconciliation.dto.SchoolReconciliationTransactionDto;
import com.tuitionnetwork.reconciliation.dto.SchoolReconciliationTransactionListResponse;
import com.tuitionnetwork.reconciliation.dto.SchoolSettlementListResponse;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchoolReconciliationServiceImpl implements SchoolReconciliationService {

    private final InstitutionRepository institutionRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationExceptionRepository exceptionRepository;

    public SchoolReconciliationServiceImpl(
            InstitutionRepository institutionRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            PaymentRepository paymentRepository,
            StudentRepository studentRepository,
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository) {
        this.institutionRepository = institutionRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.paymentRepository = paymentRepository;
        this.studentRepository = studentRepository;
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolReconciliationSummaryDto getSummary(UUID institutionId) {
        Institution institution = requireInstitution(institutionId);

        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(institutionId);
        List<ReconciliationException> allExceptions = exceptionRepository.findAll();

        Set<UUID> exceptionPaymentIds = allExceptions.stream()
                .map(ReconciliationException::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> exceptionTxRefs = allExceptions.stream()
                .map(ReconciliationException::getTxRef)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        long totalReconciled = 0;
        long totalUnreconciled = 0;
        long totalPending = 0;
        long grossCollectedEGP = 0;
        long pendingPayoutEGP = 0;
        LocalDate lastSettlementDate = null;

        if (allocations != null && !allocations.isEmpty()) {
            Map<UUID, Payment> uniquePayments = new LinkedHashMap<>();
            Map<UUID, Long> paymentAllocatedTotals = new HashMap<>();

            for (PaymentAllocation pa : allocations) {
                Payment payment = pa.getPayment();
                if (payment != null && payment.getId() != null) {
                    uniquePayments.putIfAbsent(payment.getId(), payment);
                    long allocated = pa.getAmountApplied() != null ? pa.getAmountApplied().longValue() : 0L;
                    paymentAllocatedTotals.merge(payment.getId(), allocated, Long::sum);
                }
            }

            for (Payment p : uniquePayments.values()) {
                long amount = paymentAllocatedTotals.getOrDefault(p.getId(),
                        p.getTotalAmount() != null ? p.getTotalAmount().longValue() : 0L);

                LocalDate txDate = p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : LocalDate.now();

                boolean isException = exceptionPaymentIds.contains(p.getId())
                        || (p.getTransactionReference() != null && exceptionTxRefs.contains(p.getTransactionReference().toLowerCase()));

                if (isException || p.getStatus() == PaymentStatus.FAILED) {
                    totalUnreconciled++;
                } else if (p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.AUTHORIZED) {
                    totalPending++;
                    pendingPayoutEGP += amount;
                } else if (p.getStatus() == PaymentStatus.CAPTURED) {
                    totalReconciled++;
                    grossCollectedEGP += amount;
                    if (lastSettlementDate == null || txDate.isAfter(lastSettlementDate)) {
                        lastSettlementDate = txDate;
                    }
                }
            }
        }

        // Also check ReconciliationRuns matching this institution
        List<ReconciliationRun> runs = findRunsForInstitution(institution);
        if ((allocations == null || allocations.isEmpty()) && !runs.isEmpty()) {
            for (ReconciliationRun run : runs) {
                totalReconciled += (run.getMatchedCount() != null ? run.getMatchedCount() : 0);
                totalUnreconciled += (run.getExceptionCount() != null ? run.getExceptionCount() : 0);
                if ("Pending".equalsIgnoreCase(run.getStatus())) {
                    totalPending++;
                }
                long runGross = (run.getSchoolAmountEGP() != null && run.getSchoolAmountEGP() > 0)
                        ? run.getSchoolAmountEGP()
                        : (run.getSystemAmountEGP() != null ? run.getSystemAmountEGP() : 0L);
                grossCollectedEGP += runGross;

                LocalDate rDate = run.getRunDate() != null
                        ? run.getRunDate()
                        : (run.getCreatedAt() != null ? run.getCreatedAt().toLocalDate() : LocalDate.now());
                if (lastSettlementDate == null || rDate.isAfter(lastSettlementDate)) {
                    lastSettlementDate = rDate;
                }
            }
        }

        long cibFeeEGP = Math.round(grossCollectedEGP * 0.02);
        long netSettledEGP = grossCollectedEGP - cibFeeEGP;

        if (lastSettlementDate == null) {
            lastSettlementDate = LocalDate.now();
        }

        return new SchoolReconciliationSummaryDto(
                totalReconciled,
                totalUnreconciled,
                totalPending,
                grossCollectedEGP,
                cibFeeEGP,
                netSettledEGP,
                pendingPayoutEGP,
                lastSettlementDate
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolReconciliationTransactionListResponse getTransactions(
            UUID institutionId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String studentId,
            String paymentId,
            int page,
            int pageSize) {

        Institution institution = requireInstitution(institutionId);
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(institutionId);

        List<ReconciliationException> allExceptions = exceptionRepository.findAll();
        Set<UUID> exceptionPaymentIds = allExceptions.stream()
                .map(ReconciliationException::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> exceptionTxRefs = allExceptions.stream()
                .map(ReconciliationException::getTxRef)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Map<UUID, Student> studentCache = new HashMap<>();
        List<SchoolReconciliationTransactionDto> filtered = new ArrayList<>();

        if (allocations != null) {
            for (PaymentAllocation pa : allocations) {
                Payment payment = pa.getPayment();
                FeeLine fee = pa.getFeeLine();
                if (payment == null) {
                    continue;
                }

                UUID sId = (fee != null) ? fee.getStudentId() : null;
                Student student = (sId != null) ? studentCache.computeIfAbsent(sId, id -> studentRepository.findById(id).orElse(null)) : null;

                LocalDate txDate = payment.getCreatedAt() != null ? payment.getCreatedAt().toLocalDate() : LocalDate.now();

                // Determine reconciliation status
                boolean isException = exceptionPaymentIds.contains(payment.getId())
                        || (payment.getTransactionReference() != null && exceptionTxRefs.contains(payment.getTransactionReference().toLowerCase()));

                String reconStatus;
                if (isException || payment.getStatus() == PaymentStatus.FAILED) {
                    reconStatus = "Unreconciled";
                } else if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.AUTHORIZED) {
                    reconStatus = "Pending";
                } else {
                    reconStatus = "Reconciled";
                }

                // Apply Filters
                if (dateFrom != null && txDate.isBefore(dateFrom)) {
                    continue;
                }
                if (dateTo != null && txDate.isAfter(dateTo)) {
                    continue;
                }
                if (status != null && !status.isBlank() && !reconStatus.equalsIgnoreCase(status.trim())) {
                    continue;
                }

                String pRef = payment.getTransactionReference() != null && !payment.getTransactionReference().isBlank()
                        ? payment.getTransactionReference()
                        : payment.getId().toString();

                if (paymentId != null && !paymentId.isBlank()) {
                    String queryP = paymentId.trim().toLowerCase();
                    boolean matchP = pRef.toLowerCase().contains(queryP)
                            || payment.getId().toString().toLowerCase().contains(queryP);
                    if (!matchP) {
                        continue;
                    }
                }

                String stuRef = student != null && student.getStudentRef() != null ? student.getStudentRef()
                        : (student != null ? student.getId().toString() : (sId != null ? sId.toString() : "STU-UNKNOWN"));

                if (studentId != null && !studentId.isBlank()) {
                    String queryS = studentId.trim().toLowerCase();
                    boolean matchS = stuRef.toLowerCase().contains(queryS)
                            || (student != null && student.getFullName() != null && student.getFullName().toLowerCase().contains(queryS))
                            || (sId != null && sId.toString().toLowerCase().contains(queryS));
                    if (!matchS) {
                        continue;
                    }
                }

                String feeRef = (fee != null) ? fee.getId().toString() : "FEE-UNKNOWN";
                long amount = pa.getAmountApplied() != null ? pa.getAmountApplied().longValue()
                        : (payment.getTotalAmount() != null ? payment.getTotalAmount().longValue() : 0L);

                filtered.add(new SchoolReconciliationTransactionDto(
                        pRef,
                        stuRef,
                        feeRef,
                        amount,
                        txDate,
                        reconStatus
                ));
            }
        }

        // Sort by date descending
        filtered.sort(Comparator.comparing(SchoolReconciliationTransactionDto::date).reversed());

        int resolvedSize = pageSize > 0 ? pageSize : 25;
        int resolvedPage = Math.max(0, page);
        int total = filtered.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / resolvedSize);

        int fromIndex = Math.min(resolvedPage * resolvedSize, total);
        int toIndex = Math.min(fromIndex + resolvedSize, total);
        List<SchoolReconciliationTransactionDto> pagedData = filtered.subList(fromIndex, toIndex);

        return new SchoolReconciliationTransactionListResponse(
                pagedData,
                total,
                resolvedPage,
                resolvedSize,
                totalPages
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolSettlementListResponse getSettlements(
            UUID institutionId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            int page,
            int pageSize) {

        Institution institution = requireInstitution(institutionId);
        List<InstitutionSettlementDto> cycles = new ArrayList<>();

        // 1. Check ReconciliationRuns matching this institution
        List<ReconciliationRun> runs = findRunsForInstitution(institution);
        for (ReconciliationRun run : runs) {
            LocalDate date = run.getRunDate() != null
                    ? run.getRunDate()
                    : (run.getCreatedAt() != null ? run.getCreatedAt().toLocalDate() : LocalDate.now());

            long gross = (run.getSchoolAmountEGP() != null && run.getSchoolAmountEGP() > 0)
                    ? run.getSchoolAmountEGP()
                    : (run.getSystemAmountEGP() != null ? run.getSystemAmountEGP() : 0L);

            if (gross <= 0 && run.getBankAmountEGP() != null) {
                gross = run.getBankAmountEGP();
            }

            long cibFee = Math.round(gross * 0.02);
            long net = gross - cibFee;
            String cycleStatus = "Matched".equalsIgnoreCase(run.getStatus()) ? "Completed" : run.getStatus();
            String runSuffix = run.getId().toString().substring(0, 8).toUpperCase();

            cycles.add(new InstitutionSettlementDto(
                    "SET-" + (institution.getCode() != null ? institution.getCode() : "SCH") + "-" + runSuffix,
                    date,
                    gross,
                    cibFee,
                    net,
                    cycleStatus,
                    "TX-" + date.toString().replace("-", "") + "-" + runSuffix,
                    "RECON-" + date.toString().replace("-", "")
            ));
        }

        // 2. Also check daily settled payments if no runs exist or to aggregate payments
        if (cycles.isEmpty()) {
            List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(institutionId);
            List<ReconciliationException> allExceptions = exceptionRepository.findAll();
            Set<UUID> exceptionPaymentIds = allExceptions.stream()
                    .map(ReconciliationException::getPaymentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> exceptionTxRefs = allExceptions.stream()
                    .map(ReconciliationException::getTxRef)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            Map<LocalDate, Long> dailyGross = new TreeMap<>(Collections.reverseOrder());

            if (allocations != null) {
                for (PaymentAllocation pa : allocations) {
                    Payment p = pa.getPayment();
                    boolean isException = p != null && (exceptionPaymentIds.contains(p.getId())
                            || (p.getTransactionReference() != null && exceptionTxRefs.contains(p.getTransactionReference().toLowerCase())));

                    if (p != null && p.getStatus() == PaymentStatus.CAPTURED && !isException) {
                        LocalDate d = p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : LocalDate.now();
                        long amt = pa.getAmountApplied() != null ? pa.getAmountApplied().longValue() : 0L;
                        dailyGross.merge(d, amt, Long::sum);
                    }
                }
            }

            int index = 1;
            for (Map.Entry<LocalDate, Long> entry : dailyGross.entrySet()) {
                LocalDate d = entry.getKey();
                long gross = entry.getValue();
                long cibFee = Math.round(gross * 0.02);
                long net = gross - cibFee;
                String batchCode = String.format("%04d", index++);

                cycles.add(new InstitutionSettlementDto(
                        "SET-" + (institution.getCode() != null ? institution.getCode() : "SCH") + "-" + batchCode,
                        d,
                        gross,
                        cibFee,
                        net,
                        "Completed",
                        "TX-" + d.toString().replace("-", "") + "-" + batchCode,
                        "RECON-" + d.toString().replace("-", "")
                ));
            }
        }

        // Apply filters
        List<InstitutionSettlementDto> filtered = cycles.stream()
                .filter(c -> dateFrom == null || !c.date().isBefore(dateFrom))
                .filter(c -> dateTo == null || !c.date().isAfter(dateTo))
                .filter(c -> status == null || status.isBlank() || c.status().equalsIgnoreCase(status.trim()))
                .sorted(Comparator.comparing(InstitutionSettlementDto::date).reversed())
                .collect(Collectors.toList());

        long totalSettled = filtered.stream().mapToLong(InstitutionSettlementDto::netEGP).sum();
        LocalDate lastDate = filtered.stream()
                .map(InstitutionSettlementDto::date)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        InstitutionSettlementSummaryDto summary = new InstitutionSettlementSummaryDto(
                totalSettled,
                filtered.size(),
                lastDate
        );

        int resolvedSize = pageSize > 0 ? pageSize : 25;
        int resolvedPage = Math.max(0, page);
        int total = filtered.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / resolvedSize);

        int fromIndex = Math.min(resolvedPage * resolvedSize, total);
        int toIndex = Math.min(fromIndex + resolvedSize, total);
        List<InstitutionSettlementDto> pagedData = filtered.subList(fromIndex, toIndex);

        return new SchoolSettlementListResponse(
                pagedData,
                total,
                resolvedPage,
                resolvedSize,
                totalPages,
                summary
        );
    }

    private Institution requireInstitution(UUID institutionId) {
        if (institutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "institution_id_required: Institution ID is required");
        }
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "institution_not_found: Institution not found: " + institutionId));
    }

    private List<ReconciliationRun> findRunsForInstitution(Institution institution) {
        if (runRepository == null) {
            return Collections.emptyList();
        }
        return runRepository.findAll().stream()
                .filter(r -> r.getInstitution() != null &&
                        (r.getInstitution().equalsIgnoreCase(institution.getName())
                                || r.getInstitution().toLowerCase().contains(institution.getName().toLowerCase())
                                || "All Registered Institutions".equalsIgnoreCase(r.getInstitution())))
                .collect(Collectors.toList());
    }
}