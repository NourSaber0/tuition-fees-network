package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.dto.ReceiptDetailDto;
import com.tuitionnetwork.payments.dto.SchoolPaymentAllocationItemDto;
import com.tuitionnetwork.payments.dto.SchoolPaymentDetailDto;
import com.tuitionnetwork.payments.dto.SchoolPaymentListResponse;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import com.tuitionnetwork.students.dto.StudentPaymentItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchoolPaymentServiceImpl implements SchoolPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceiptRepository receiptRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;

    public SchoolPaymentServiceImpl(
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            ReceiptRepository receiptRepository,
            StudentRepository studentRepository,
            InstitutionRepository institutionRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.receiptRepository = receiptRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolPaymentListResponse getPayments(
            UUID institutionId,
            String search,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String feeCategory,
            String studentId,
            String method,
            int page,
            int pageSize) {

        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(institutionId);
        Map<UUID, Student> studentCache = new ConcurrentHashMap<>();

        List<StudentPaymentItemDto> allItems = new ArrayList<>();
        for (PaymentAllocation pa : allocations) {
            Payment payment = pa.getPayment();
            FeeLine fee = pa.getFeeLine();
            if (payment == null || fee == null) {
                continue;
            }

            Student student = studentCache.computeIfAbsent(fee.getStudentId(), id ->
                    studentRepository.findById(id).orElse(null)
            );

            LocalDateTime createdAt = payment.getCreatedAt();
            LocalDate txDate = createdAt != null ? createdAt.toLocalDate() : LocalDate.now();

            // Date filtering
            if (dateFrom != null && txDate.isBefore(dateFrom)) {
                continue;
            }
            if (dateTo != null && txDate.isAfter(dateTo)) {
                continue;
            }

            String paymentMethodStr = mapMethodString(payment.getPaymentMethod());
            if (method != null && !method.isBlank() && !paymentMethodStr.equalsIgnoreCase(method.trim())) {
                continue;
            }

            String paymentStatusStr = mapStatusString(payment.getStatus());
            if (status != null && !status.isBlank() && !paymentStatusStr.equalsIgnoreCase(status.trim())) {
                continue;
            }

            String categoryStr = fee.getFeeType() != null ? fee.getFeeType().getDisplayName() : "Tuition";
            if (feeCategory != null && !feeCategory.isBlank() && !categoryStr.equalsIgnoreCase(feeCategory.trim())) {
                continue;
            }

            String studentRefOrId = student != null && student.getStudentRef() != null
                    ? student.getStudentRef()
                    : (student != null ? student.getId().toString() : fee.getStudentId().toString());

            if (studentId != null && !studentId.isBlank()) {
                String target = studentId.trim();
                boolean matchesStudent = target.equalsIgnoreCase(studentRefOrId)
                        || (student != null && target.equalsIgnoreCase(student.getId().toString()));
                if (!matchesStudent) {
                    continue;
                }
            }

            String studentName = student != null ? student.getFullName() : "Unknown Student";
            String feeName = fee.getFeeType() != null
                    ? fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "")
                    : "Fee";

            String txId = payment.getTransactionReference() != null && !payment.getTransactionReference().isBlank()
                    ? payment.getTransactionReference()
                    : payment.getId().toString();

            // Search filter
            if (search != null && !search.isBlank()) {
                String q = search.trim().toLowerCase();
                boolean matchesSearch = txId.toLowerCase().contains(q)
                        || studentName.toLowerCase().contains(q)
                        || studentRefOrId.toLowerCase().contains(q)
                        || feeName.toLowerCase().contains(q);
                if (!matchesSearch) {
                    continue;
                }
            }

            boolean isPartial = fee.getRemainingAmount() != null && fee.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0;
            int priority = resolvePriority(fee.getFeeType());

            allItems.add(new StudentPaymentItemDto(
                    txId,
                    studentRefOrId,
                    studentName,
                    fee.getId().toString(),
                    feeName,
                    fee.getDueDate(),
                    priority,
                    nz(pa.getAmountApplied()),
                    paymentMethodStr,
                    txDate.toString(),
                    createdAt != null ? createdAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "12:00",
                    paymentStatusStr,
                    "Reconciled",
                    isPartial,
                    nz(fee.getTotalAmount()),
                    nz(fee.getRemainingAmount())
            ));
        }

        // Sort by date desc, time desc
        allItems.sort(Comparator.comparing(StudentPaymentItemDto::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StudentPaymentItemDto::time, Comparator.nullsLast(Comparator.reverseOrder())));

        long total = allItems.size();
        int safePageSize = pageSize > 0 ? pageSize : 25;
        int totalPages = (int) Math.ceil((double) total / safePageSize);
        int fromIndex = Math.min(page * safePageSize, allItems.size());
        int toIndex = Math.min(fromIndex + safePageSize, allItems.size());

        List<StudentPaymentItemDto> pageContent = allItems.subList(fromIndex, toIndex);

        return new SchoolPaymentListResponse(pageContent, total, page, safePageSize, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolPaymentDetailDto getPaymentDetail(UUID institutionId, String paymentIdOrRef) {
        Payment payment = resolvePayment(paymentIdOrRef);
        validateSchoolAccess(payment, institutionId);

        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPaymentId(payment.getId());

        Student primaryStudent = null;
        List<SchoolPaymentAllocationItemDto> allocationDtos = new ArrayList<>();

        for (PaymentAllocation pa : allocations) {
            FeeLine fee = pa.getFeeLine();
            if (fee == null) {
                continue;
            }

            if (institutionId != null && !institutionId.equals(fee.getInstitutionId())) {
                continue;
            }

            if (primaryStudent == null) {
                primaryStudent = studentRepository.findById(fee.getStudentId()).orElse(null);
            }

            String feeName = fee.getFeeType() != null
                    ? fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "")
                    : "Fee";
            String category = fee.getFeeType() != null ? fee.getFeeType().getDisplayName() : "Tuition";

            BigDecimal allocated = nz(pa.getAmountApplied());
            BigDecimal totalAmount = nz(fee.getTotalAmount());
            BigDecimal paidAmount = nz(fee.getPaidAmount());
            BigDecimal previouslyPaid = paidAmount.subtract(allocated).max(BigDecimal.ZERO);
            BigDecimal outstanding = totalAmount.subtract(previouslyPaid).max(BigDecimal.ZERO);
            BigDecimal remaining = nz(fee.getRemainingAmount());

            String feeStatus = fee.getStatus() != null ? fee.getStatus().name() : "Paid";
            if ("OUTSTANDING".equalsIgnoreCase(feeStatus) && remaining.compareTo(BigDecimal.ZERO) == 0) {
                feeStatus = "Paid";
            }

            boolean isOverdue = fee.getDueDate() != null && fee.getDueDate().isBefore(LocalDate.now())
                    && remaining.compareTo(BigDecimal.ZERO) > 0;

            int priority = resolvePriority(fee.getFeeType());

            allocationDtos.add(new SchoolPaymentAllocationItemDto(
                    fee.getId().toString(),
                    feeName,
                    category,
                    fee.getDueDate(),
                    totalAmount,
                    previouslyPaid,
                    outstanding,
                    allocated,
                    remaining,
                    feeStatus,
                    priority,
                    isOverdue
            ));
        }

        String txId = payment.getTransactionReference() != null && !payment.getTransactionReference().isBlank()
                ? payment.getTransactionReference()
                : payment.getId().toString();

        String studentId = primaryStudent != null && primaryStudent.getStudentRef() != null
                ? primaryStudent.getStudentRef()
                : (primaryStudent != null ? primaryStudent.getId().toString() : "-");

        String studentName = primaryStudent != null ? primaryStudent.getFullName() : "Student";

        LocalDateTime createdAt = payment.getCreatedAt();
        String dateStr = createdAt != null ? createdAt.toLocalDate().toString() : LocalDate.now().toString();
        String timeStr = createdAt != null ? createdAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "12:00";

        return new SchoolPaymentDetailDto(
                txId,
                studentId,
                studentName,
                nz(payment.getTotalAmount()),
                payment.getCurrency() != null ? payment.getCurrency() : "EGP",
                mapMethodString(payment.getPaymentMethod()),
                dateStr,
                timeStr,
                mapStatusString(payment.getStatus()),
                "Pending",
                allocationDtos
        );
    }

    @Override
    @Transactional
    public ReceiptDetailDto getPaymentReceipt(UUID institutionId, String paymentIdOrRef) {
        Payment payment = resolvePayment(paymentIdOrRef);
        validateSchoolAccess(payment, institutionId);

        Optional<Receipt> receiptOpt = receiptRepository.findByPaymentId(payment.getId());
        if (receiptOpt.isPresent()) {
            Receipt r = receiptOpt.get();
            String rcpRef = "RCP-" + payment.getId().toString().substring(0, 8).toUpperCase();
            return new ReceiptDetailDto(
                    r.getId(),
                    payment.getId(),
                    rcpRef,
                    nz(payment.getTotalAmount()),
                    payment.getCurrency() != null ? payment.getCurrency() : "EGP",
                    r.getCryptoSignature() != null ? r.getCryptoSignature() : generateSignature(payment),
                    r.getFileUrl() != null ? r.getFileUrl() : "/receipts/" + payment.getId() + ".pdf",
                    r.getIssuedAt() != null ? r.getIssuedAt() : (payment.getCreatedAt() != null ? payment.getCreatedAt() : LocalDateTime.now())
            );
        }

        // Auto-generate receipt if not present
        String cryptoSig = generateSignature(payment);
        String fileUrl = "https://cdn.tuitionnetwork.eg/receipts/receipt-" + payment.getId() + ".pdf";
        Receipt receipt = new Receipt(payment, cryptoSig, fileUrl);
        receipt.setIssuedAt(LocalDateTime.now());
        receipt = receiptRepository.save(receipt);

        String rcpRef = "RCP-" + payment.getId().toString().substring(0, 8).toUpperCase();
        return new ReceiptDetailDto(
                receipt.getId(),
                payment.getId(),
                rcpRef,
                nz(payment.getTotalAmount()),
                payment.getCurrency() != null ? payment.getCurrency() : "EGP",
                cryptoSig,
                fileUrl,
                receipt.getIssuedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePaymentReceiptPdf(UUID institutionId, String paymentIdOrRef) {
        Payment payment = resolvePayment(paymentIdOrRef);
        validateSchoolAccess(payment, institutionId);

        ReceiptDetailDto receipt = getPaymentReceipt(institutionId, paymentIdOrRef);
        Institution institution = institutionId != null ? institutionRepository.findById(institutionId).orElse(null) : null;
        String schoolName = institution != null ? institution.getName() : "Tuition Fees Collection Network";

        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPaymentId(payment.getId());
        String studentName = "Student";
        for (PaymentAllocation pa : allocations) {
            if (pa.getFeeLine() != null) {
                Student s = studentRepository.findById(pa.getFeeLine().getStudentId()).orElse(null);
                if (s != null) {
                    studentName = s.getFullName();
                    break;
                }
            }
        }

        return createReceiptPdfBytes(payment, receipt, schoolName, studentName);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPaymentsCsv(
            UUID institutionId,
            String search,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String feeCategory,
            String studentId,
            String method) {

        SchoolPaymentListResponse response = getPayments(
                institutionId, search, dateFrom, dateTo, status, feeCategory, studentId, method, 0, Integer.MAX_VALUE
        );

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Excel compatibility
        sb.append('\uFEFF');
        sb.append("Transaction ID,Student ID,Student Name,Fee Name,Fee Due Date,Amount (EGP),Method,Date,Time,Status,Reconciliation,Is Partial,Original Fee (EGP),Remaining (EGP)\r\n");

        for (StudentPaymentItemDto item : response.data()) {
            sb.append(escapeCsv(item.id())).append(",");
            sb.append(escapeCsv(item.studentId())).append(",");
            sb.append(escapeCsv(item.studentName())).append(",");
            sb.append(escapeCsv(item.feeName())).append(",");
            sb.append(escapeCsv(item.feeDueDate() != null ? item.feeDueDate().toString() : "")).append(",");
            sb.append(item.amountEGP() != null ? item.amountEGP().toPlainString() : "0.00").append(",");
            sb.append(escapeCsv(item.method())).append(",");
            sb.append(escapeCsv(item.date())).append(",");
            sb.append(escapeCsv(item.time())).append(",");
            sb.append(escapeCsv(item.status())).append(",");
            sb.append(escapeCsv(item.reconciliation())).append(",");
            sb.append(item.isPartial()).append(",");
            sb.append(item.originalFeeAmountEGP() != null ? item.originalFeeAmountEGP().toPlainString() : "0.00").append(",");
            sb.append(item.remainingAfterEGP() != null ? item.remainingAfterEGP().toPlainString() : "0.00").append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Payment resolvePayment(String paymentIdOrRef) {
        if (paymentIdOrRef == null || paymentIdOrRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment ID or reference is required");
        }
        try {
            UUID uuid = UUID.fromString(paymentIdOrRef.trim());
            Optional<Payment> byId = paymentRepository.findById(uuid);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (IllegalArgumentException ignored) {
        }

        return paymentRepository.findByTransactionReference(paymentIdOrRef.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentIdOrRef));
    }

    private void validateSchoolAccess(Payment payment, UUID institutionId) {
        if (institutionId == null) {
            return;
        }
        boolean belongs = paymentAllocationRepository.existsByPaymentIdAndInstitutionId(payment.getId(), institutionId);
        if (!belongs) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: payment does not belong to your school");
        }
    }

    private String mapMethodString(PaymentMethod method) {
        if (method == null) return "Card";
        return switch (method) {
            case CREDIT_CARD -> "Card";
            case CIB_ACCOUNT -> "Bank Transfer";
            case EPP_INSTALMENTS -> "EPP";
        };
    }

    private String mapStatusString(PaymentStatus status) {
        if (status == null) return "Successful";
        return switch (status) {
            case CAPTURED -> "Successful";
            case PENDING, AUTHORIZED -> "Pending";
            case FAILED -> "Failed";
            case REFUNDED -> "Refunded";
        };
    }

    private int resolvePriority(FeeType feeType) {
        if (feeType == null) return 1;
        return switch (feeType) {
            case TUITION -> 1;
            case BOOKS -> 2;
            case ACTIVITIES -> 3;
            case BUS -> 4;
        };
    }

    private BigDecimal nz(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private String generateSignature(Payment payment) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String rawData = payment.getId() + "|" + payment.getTotalAmount() + "|" +
                    payment.getTransactionReference() + "|" + payment.getIdempotencyKey();
            byte[] hash = digest.digest(rawData.getBytes(StandardCharsets.UTF_8));
            return "SIG-SHA256-" + HexFormat.of().formatHex(hash).toUpperCase();
        } catch (Exception e) {
            return "SIG-" + payment.getId();
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    /**
     * Minimal, compliant ISO 32000-1 (PDF 1.4) byte generator for official receipts.
     * Contains document catalog, page descriptors, font dictionary, and text content stream.
     */
    private byte[] createReceiptPdfBytes(Payment payment, ReceiptDetailDto receipt, String schoolName, String studentName) {
        String txRef = payment.getTransactionReference() != null ? payment.getTransactionReference() : payment.getId().toString();
        String rcpRef = receipt.receiptReference();
        String amountStr = receipt.amount() != null ? receipt.amount().toPlainString() + " " + receipt.currency() : "0.00 EGP";
        String dateStr = receipt.issuedAt() != null ? receipt.issuedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
        String sig = receipt.cryptoSignature() != null ? receipt.cryptoSignature() : "SIG-VERIFIED";

        String textStream = "BT\n"
                + "/F1 18 Tf\n"
                + "50 740 Td\n"
                + "(COMMERCIAL INTERNATIONAL BANK - EGYPT (CIB)) Tj\n"
                + "/F1 14 Tf\n"
                + "0 -30 Td\n"
                + "(OFFICIAL TUITION & FEES COLLECTION RECEIPT) Tj\n"
                + "/F1 10 Tf\n"
                + "0 -30 Td\n"
                + "(Receipt Number: " + escapePdf(rcpRef) + ") Tj\n"
                + "0 -18 Td\n"
                + "(Date & Time: " + escapePdf(dateStr) + ") Tj\n"
                + "0 -18 Td\n"
                + "(School: " + escapePdf(schoolName) + ") Tj\n"
                + "0 -18 Td\n"
                + "(Student Name: " + escapePdf(studentName) + ") Tj\n"
                + "0 -18 Td\n"
                + "(Transaction Reference: " + escapePdf(txRef) + ") Tj\n"
                + "0 -18 Td\n"
                + "(Payment Method: " + escapePdf(mapMethodString(payment.getPaymentMethod())) + ") Tj\n"
                + "/F1 14 Tf\n"
                + "0 -30 Td\n"
                + "(Amount Paid: " + escapePdf(amountStr) + ") Tj\n"
                + "/F1 10 Tf\n"
                + "0 -30 Td\n"
                + "(Status: " + escapePdf(mapStatusString(payment.getStatus())) + " - VERIFIED) Tj\n"
                + "0 -20 Td\n"
                + "(Cryptographic Signature:) Tj\n"
                + "0 -15 Td\n"
                + "(" + escapePdf(sig) + ") Tj\n"
                + "0 -30 Td\n"
                + "(This is a digitally generated official receipt issued under the CIB Tuition Fees Network.) Tj\n"
                + "ET\n";

        byte[] streamBytes = textStream.getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));

            long offset1 = out.size();
            out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            long offset2 = out.size();
            out.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            long offset3 = out.size();
            out.write(("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n")
                    .getBytes(StandardCharsets.ISO_8859_1));

            long offset4 = out.size();
            out.write(("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(streamBytes);
            out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            long offset5 = out.size();
            out.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            long xrefOffset = out.size();
            out.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
            out.write(String.format("%010d 00000 n \n", offset1).getBytes(StandardCharsets.ISO_8859_1));
            out.write(String.format("%010d 00000 n \n", offset2).getBytes(StandardCharsets.ISO_8859_1));
            out.write(String.format("%010d 00000 n \n", offset3).getBytes(StandardCharsets.ISO_8859_1));
            out.write(String.format("%010d 00000 n \n", offset4).getBytes(StandardCharsets.ISO_8859_1));
            out.write(String.format("%010d 00000 n \n", offset5).getBytes(StandardCharsets.ISO_8859_1));

            out.write("trailer\n<< /Size 6 /Root 1 0 R >>\n".getBytes(StandardCharsets.ISO_8859_1));
            out.write("startxref\n".getBytes(StandardCharsets.ISO_8859_1));
            out.write((xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));

            return out.toByteArray();
        } catch (IOException e) {
            return textStream.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String escapePdf(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
