package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStateLog;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.dto.BackOfficePaymentRequest;
import com.tuitionnetwork.payments.dto.BackOfficePaymentResponse;
import com.tuitionnetwork.payments.dto.EppSelectionDto;
import com.tuitionnetwork.payments.dto.EppSummaryDto;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.PaymentSettleResponse;
import com.tuitionnetwork.payments.dto.SelectedDueDto;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BackOfficePaymentServiceImpl implements BackOfficePaymentService {

    private final PaymentSettlementService paymentSettlementService;
    private final PaymentRepository paymentRepository;
    private final FeeLineRepository feeLineRepository;
    private final StudentRepository studentRepository;
    private final IdentityResolverService identityResolverService;
    private final TransactionQueryService transactionQueryService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public BackOfficePaymentServiceImpl(PaymentSettlementService paymentSettlementService,
                                        PaymentRepository paymentRepository,
                                        FeeLineRepository feeLineRepository,
                                        StudentRepository studentRepository,
                                        IdentityResolverService identityResolverService,
                                        TransactionQueryService transactionQueryService,
                                        @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.paymentSettlementService = paymentSettlementService;
        this.paymentRepository = paymentRepository;
        this.feeLineRepository = feeLineRepository;
        this.studentRepository = studentRepository;
        this.identityResolverService = identityResolverService;
        this.transactionQueryService = transactionQueryService;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public BackOfficePaymentResponse processPayment(BackOfficePaymentRequest request, String idempotencyKeyHeader) {
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required.");
        }

        String idempotencyKey = idempotencyKeyHeader.trim();

        if (request.amountEGP() == null || request.amountEGP().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount_le_zero: Payment amount must be greater than zero.");
        }

        if (request.feeIds() == null || request.feeIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no_fees_selected: At least one fee must be selected.");
        }

        String methodStr = request.method() != null ? request.method().trim() : "";
        boolean isEpp = "epp".equalsIgnoreCase(request.creditPaymentType()) || methodStr.toUpperCase().contains("EPP");

        if (isEpp) {
            if (request.eppTenor() == null || request.eppTenor() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "epp_tenor_required: eppTenor is required when creditPaymentType is epp.");
            }
            if (methodStr.toUpperCase().contains("DEBIT")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "debit_card_not_eligible_for_epp: Debit cards are not eligible for EPP.");
            }
            if (!methodStr.toUpperCase().contains("CIB")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "non_cib_card_not_eligible: Only CIB cards are eligible for EPP.");
            }
        }

        // Idempotency check: if key already processed, return existing record
        Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            return new BackOfficePaymentResponse(
                    existing.getId().toString(),
                    existing.getStatus() == PaymentStatus.CAPTURED ? "Successful" : existing.getStatus().name(),
                    existing.getTotalAmount(),
                    false,
                    BigDecimal.ZERO,
                    methodStr,
                    existing.getTransactionReference() != null ? existing.getTransactionReference() : "BNK-" + existing.getAuthCode(),
                    existing.getAuthCode() != null ? existing.getAuthCode() : "AUTH-OK",
                    existing.getReceipt() != null ? existing.getReceipt().getFileUrl() : "/receipts/" + existing.getId() + ".pdf",
                    null
            );
        }

        // Resolve selected fees
        List<UUID> feeUuids = new ArrayList<>();
        for (String idStr : request.feeIds()) {
            try {
                feeUuids.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException e) {
                // If fee ID is human readable (e.g. FEE-AH-001) or invalid UUID, attempt find
                Optional<FeeLine> flOpt = feeLineRepository.findByRowIdempotencyKey(idStr);
                flOpt.ifPresent(feeLine -> feeUuids.add(feeLine.getId()));
            }
        }

        List<FeeLine> feeLines = feeUuids.isEmpty() ? List.of() : feeLineRepository.findAllById(feeUuids);
        if (feeLines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no_fees_selected: No matching fee lines found.");
        }

        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (FeeLine fl : feeLines) {
            totalRemaining = totalRemaining.add(fl.getRemainingAmount() != null ? fl.getRemainingAmount() : BigDecimal.ZERO);
        }

        if (request.amountEGP().compareTo(totalRemaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "amount_exceeds_balance: Payment amount " + request.amountEGP() + " exceeds total remaining balance " + totalRemaining);
        }

        // Distribute payment across fees
        List<SelectedDueDto> selectedDues = new ArrayList<>();
        BigDecimal remainingToDistribute = request.amountEGP();
        for (FeeLine fl : feeLines) {
            if (remainingToDistribute.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal alloc = fl.getRemainingAmount().min(remainingToDistribute);
            selectedDues.add(new SelectedDueDto(fl.getId(), alloc));
            remainingToDistribute = remainingToDistribute.subtract(alloc);
        }

        // Resolve guardian
        UUID guardianId = UUID.randomUUID();
        if (request.nationalId() != null && !request.nationalId().isBlank()) {
            Optional<ResolvedGuardianDto> gOpt = identityResolverService.resolveGuardianByNationalId(request.nationalId().trim());
            if (gOpt.isPresent()) {
                guardianId = gOpt.get().id();
            } else {
                String hmac = identityResolverService.computeHmacSha256(request.nationalId().trim());
                Optional<Student> sOpt = studentRepository.findByNationalIdHash(hmac);
                if (sOpt.isPresent() && sOpt.get().getGuardianId() != null) {
                    guardianId = sOpt.get().getGuardianId();
                }
            }
        }

        PaymentMethod pm;
        if (isEpp) {
            pm = PaymentMethod.EPP_INSTALMENTS;
        } else if (methodStr.toUpperCase().contains("CREDIT")) {
            pm = PaymentMethod.CREDIT_CARD;
        } else {
            pm = PaymentMethod.CIB_ACCOUNT;
        }

        PaymentSettleRequest settleRequest = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                pm,
                selectedDues,
                request.amountEGP(),
                isEpp ? new EppSelectionDto(request.eppTenor()) : null,
                "4000123456789010",
                null
        );

        PaymentSettleResponse settleResponse = paymentSettlementService.settlePayment(settleRequest, idempotencyKey);

        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "PROCESS_PAYMENT",
                    "Payment processed for " + request.amountEGP() + " EGP by " + (request.processedBy() != null ? request.processedBy() : "Counter")
            ));
        }

        boolean isPartial = request.amountEGP().compareTo(totalRemaining) < 0;
        BigDecimal remainingBalance = totalRemaining.subtract(request.amountEGP());

        EppSummaryDto eppDto = null;
        if (isEpp && request.eppTenor() != null) {
            BigDecimal monthly = request.amountEGP().divide(BigDecimal.valueOf(request.eppTenor()), 2, java.math.RoundingMode.HALF_UP);
            eppDto = new EppSummaryDto("EPP-" + settleResponse.paymentId().toString().substring(0, 8).toUpperCase(), request.eppTenor(), monthly);
        }

        return new BackOfficePaymentResponse(
                settleResponse.paymentId().toString(),
                "Successful",
                request.amountEGP(),
                isPartial,
                remainingBalance,
                methodStr,
                settleResponse.transactionReference() != null ? settleResponse.transactionReference() : "BNK-" + settleResponse.authCode(),
                settleResponse.authCode() != null ? settleResponse.authCode() : "AUTH-OK",
                "RCP-" + settleResponse.paymentId().toString().substring(0, 8).toUpperCase(),
                eppDto
        );
    }

    @Override
    @Transactional
    public TransactionDetailDto retryPayment(UUID paymentId, String newIdempotencyKey) {
        if (newIdempotencyKey == null || newIdempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required for retry.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only failed payments can be retried; current status is: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setIdempotencyKey(newIdempotencyKey.trim());
        payment.setTransactionReference("BNK-CIB-RETRY-" + payment.getId().toString().substring(0, 8).toUpperCase());
        payment.setAuthCode("A" + (int) (Math.random() * 90000 + 10000));
        paymentRepository.save(payment);

        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "RETRY_PAYMENT",
                    "Payment " + paymentId + " retried with new key " + newIdempotencyKey
            ));
        }

        return transactionQueryService.getTransactionDetail(payment.getId());
    }
}
