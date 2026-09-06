package com.tuitionnetwork.billing.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.dto.CreateFeeLineCommand;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingFeeCommandServiceImpl implements BillingFeeCommandService {

    private final FeeLineRepository feeLineRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final StudentRepository studentRepository;
    private final IdentityResolverService identityResolverService;

    @Autowired
    public BillingFeeCommandServiceImpl(
            FeeLineRepository feeLineRepository,
            @Autowired(required = false) PaymentAllocationRepository paymentAllocationRepository,
            @Autowired(required = false) StudentRepository studentRepository,
            @Autowired(required = false) IdentityResolverService identityResolverService) {
        this.feeLineRepository = feeLineRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.studentRepository = studentRepository;
        this.identityResolverService = identityResolverService;
    }

    public BillingFeeCommandServiceImpl(FeeLineRepository feeLineRepository) {
        this(feeLineRepository, null, null, null);
    }

    @Override
    @Transactional
    public List<UUID> createFeeLines(UUID institutionId, List<CreateFeeLineCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        List<UUID> createdOrExistingIds = new ArrayList<>();

        for (CreateFeeLineCommand cmd : commands) {
            FeeType feeType = parseFeeType(cmd.feeType());
            UUID studentId = cmd.studentId();

            if (studentId == null && studentRepository != null && identityResolverService != null && cmd.nationalId() != null) {
                try {
                    String hmac = identityResolverService.computeHmacSha256(cmd.nationalId());
                    Optional<Student> studentOpt = studentRepository.findByNationalIdHash(hmac);
                    if (studentOpt.isPresent()) {
                        studentId = studentOpt.get().getId();
                    }
                } catch (Exception ignored) {}
            }

            if (studentId == null) {
                studentId = UUID.nameUUIDFromBytes(("student-" + cmd.nationalId()).getBytes());
            }

            String rowKey = cmd.rowIdempotencyKey();
            if (rowKey == null || rowKey.isBlank()) {
                rowKey = "ROW-" + institutionId + "-" + studentId + "-" + feeType.name() + "-" + cmd.collectionPeriod();
            }

            // Check if fee line already exists by rowIdempotencyKey
            Optional<FeeLine> existingByKey = feeLineRepository.findByRowIdempotencyKey(rowKey);
            if (existingByKey.isPresent()) {
                createdOrExistingIds.add(existingByKey.get().getId());
                continue;
            }

            // Idempotent Check: Check if duplicate fee line already exists for this institution, student, feeType, and collectionPeriod
            Optional<FeeLine> existingOpt = feeLineRepository.findByInstitutionIdAndStudentIdAndFeeTypeAndCollectionPeriod(
                    institutionId, studentId, feeType, cmd.collectionPeriod()
            );

            if (existingOpt.isPresent()) {
                FeeLine existing = existingOpt.get();
                if (existing.getRowIdempotencyKey() == null) {
                    existing.setRowIdempotencyKey(rowKey);
                    feeLineRepository.save(existing);
                }
                createdOrExistingIds.add(existing.getId());
                continue;
            }

            LocalDate dueDate = cmd.dueDate() != null ? cmd.dueDate() : LocalDate.now().plusMonths(3);

            FeeLine feeLine = new FeeLine(
                    institutionId,
                    studentId,
                    feeType,
                    cmd.amount(),
                    cmd.amount(),
                    cmd.collectionPeriod(),
                    dueDate
            );
            feeLine.setPaidAmount(BigDecimal.ZERO);
            feeLine.setStatus(FeeStatus.OUTSTANDING);
            feeLine.setCurrency(cmd.currency() != null ? cmd.currency() : "EGP");
            feeLine.setRowIdempotencyKey(rowKey);

            FeeLine saved = feeLineRepository.save(feeLine);
            createdOrExistingIds.add(saved.getId());
        }

        return createdOrExistingIds;
    }

    @Override
    @Transactional
    public void cancelFeeLine(UUID institutionId, UUID feeLineId) {
        FeeLine feeLine = feeLineRepository.findById(feeLineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee line not found: " + feeLineId));

        // Cross-institution verification
        if (!feeLine.getInstitutionId().equals(institutionId)) {
            throw new PendingBusinessRuleException(
                    "Pending Business Rule: Cross-Institution Data Bleed is strictly prohibited. " +
                    "Institution '" + institutionId + "' cannot cancel fee line belonging to institution '" + feeLine.getInstitutionId() + "'."
            );
        }

        // Guardrail 5: Mid-Year EPP Cancellation check
        // If an institution attempts to cancel or unwind a fee line that is actively locked in a 12-month or 18-month EPP schedule
        if (paymentAllocationRepository != null) {
            List<PaymentAllocation> allocations = paymentAllocationRepository.findByFeeLineId(feeLineId);
            for (PaymentAllocation allocation : allocations) {
                Payment payment = allocation.getPayment();
                if (payment != null && payment.getEppSchedule() != null) {
                    Integer tenor = payment.getEppSchedule().getTenorMonths();
                    if (tenor != null && tenor >= 12) {
                        throw new PendingBusinessRuleException(
                                "Pending Business Rule: Mid-Year EPP Cancellation is undefined. " +
                                "FeeLine " + feeLineId + " is actively locked in a " + tenor + "-month EPP schedule and cannot be cancelled due to student withdrawal."
                        );
                    }
                }
            }
        }

        feeLine.setStatus(FeeStatus.CANCELLED);
        feeLineRepository.save(feeLine);
    }

    private FeeType parseFeeType(String rawFeeType) {
        if (rawFeeType == null) {
            return FeeType.TUITION;
        }
        String clean = rawFeeType.trim().toUpperCase();
        if (clean.contains("BUS")) return FeeType.BUS;
        if (clean.contains("BOOK")) return FeeType.BOOKS;
        if (clean.contains("ACTIV")) return FeeType.ACTIVITIES;
        return FeeType.TUITION;
    }
}
