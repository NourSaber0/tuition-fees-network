package com.tuitionnetwork.seed;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.IntegrationStatus;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifSeverity;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.EppInstallment;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.infrastructure.MockBankBackOfficeClient;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.EppInstallmentRepository;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Comprehensive Demo Data Seeder.
 * Idempotently populates realistic demo entities on non-production runs:
 * - Bank employees for all 4 back-office roles (ahmed.ops, admin, finance, recon, mohamed.ali)
 * - Egyptian Institutions (Nile International School, Al-Rowad Language School, Cairo British Academy)
 * - Institution Admins
 * - Guardians & Students with deterministic HMAC hashes
 * - Fee lines across various periods, fee types, and payment statuses
 * - Settled Payments, Allocations, and Digital Crypto Receipts
 * - 12-Month EPP Schedules with installments
 * - 3-Way Reconciliation Run and Sample Exceptions
 * - Back-Office Notifications & Audit Logs
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.demo-seeder.enabled", havingValue = "true")
@Order(100)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final BankEmployeeRepository bankEmployeeRepository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionAdminRepository institutionAdminRepository;
    private final GuardianRepository guardianRepository;
    private final StudentRepository studentRepository;
    private final FeeLineRepository feeLineRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceiptRepository receiptRepository;
    private final EPPScheduleRepository eppScheduleRepository;
    private final EppInstallmentRepository eppInstallmentRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ReconciliationExceptionRepository reconciliationExceptionRepository;
    private final BackOfficeNotificationRepository backOfficeNotificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdentityResolverService identityResolverService;
    private final MockBankBackOfficeClient mockBankClient;

    public DemoDataSeeder(
            BankEmployeeRepository bankEmployeeRepository,
            InstitutionRepository institutionRepository,
            InstitutionAdminRepository institutionAdminRepository,
            GuardianRepository guardianRepository,
            StudentRepository studentRepository,
            FeeLineRepository feeLineRepository,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            ReceiptRepository receiptRepository,
            EPPScheduleRepository eppScheduleRepository,
            EppInstallmentRepository eppInstallmentRepository,
            ReconciliationRunRepository reconciliationRunRepository,
            ReconciliationExceptionRepository reconciliationExceptionRepository,
            BackOfficeNotificationRepository backOfficeNotificationRepository,
            AuditLogRepository auditLogRepository,
            IdentityResolverService identityResolverService,
            MockBankBackOfficeClient mockBankClient) {
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.institutionRepository = institutionRepository;
        this.institutionAdminRepository = institutionAdminRepository;
        this.guardianRepository = guardianRepository;
        this.studentRepository = studentRepository;
        this.feeLineRepository = feeLineRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.receiptRepository = receiptRepository;
        this.eppScheduleRepository = eppScheduleRepository;
        this.eppInstallmentRepository = eppInstallmentRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.reconciliationExceptionRepository = reconciliationExceptionRepository;
        this.backOfficeNotificationRepository = backOfficeNotificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.identityResolverService = identityResolverService;
        this.mockBankClient = mockBankClient;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedBankEmployees();

        if (institutionRepository.findByCode("NIS-01").isPresent()) {
            log.info("Demo institution NIS-01 already exists. Skipping demo data seeding.");
            return;
        }

        log.info("Starting comprehensive demo data seeding...");
        seedDemoData();
        log.info("Comprehensive demo data seeding completed successfully.");
    }

    private void seedBankEmployees() {
        createEmployeeIfMissing("mohamed.ali@cibeg.com", "USR-001", "Mohamed Ali", "CIB@2026", "Operations", "bank-admin", "+20 10 0000 4821");
        createEmployeeIfMissing("ahmed.ops@cib.eg", "ahmed.ops", "Ahmed Operations", "Password123!", "Operations", "bank-operations", "+20 10 1234 7890");
        createEmployeeIfMissing("admin@cib.eg", "admin", "Sara Admin", "Admin123!", "IT & Security", "bank-admin", "+20 10 9876 5432");
        createEmployeeIfMissing("finance@cib.eg", "finance", "Tarek Finance", "Finance123!", "Finance", "bank-finance", "+20 10 5551 2345");
        createEmployeeIfMissing("recon@cib.eg", "recon", "Layla Reconciliation", "Recon123!", "Settlement", "bank-reconciliation", "+20 10 7778 8990");
    }

    private void createEmployeeIfMissing(String email, String employeeId, String name, String password, String dept, String role, String phone) {
        if (bankEmployeeRepository.findByEmailOrEmployeeId(employeeId).isEmpty()
                && bankEmployeeRepository.findByEmailOrEmployeeId(email).isEmpty()) {
            BankEmployee emp = new BankEmployee(name, email, employeeId, password, dept, role);
            emp.setPhone(phone);
            emp.setStatus("Active");
            bankEmployeeRepository.save(emp);
            log.info("Seeded demo bank employee: {} ({}) with role [{}]", name, employeeId, role);
        }
    }

    private void seedDemoData() {
        // 1. Institutions
        Institution nile = new Institution("Nile International School", "NIS-01", "Family");
        nile.setInstitutionType(InstitutionType.SCHOOL);
        nile.setSubType("International");
        nile.setCity("Cairo");
        nile.setPrincipalName("Dr. Magdy Yacoub");
        nile.setPhone("+20 2 2735 1234");
        nile.setEmail("contact@nis.edu.eg");
        nile.setRegistrationNumber("MOEDU-SCH-2026-0831");
        nile.setStudentCount(1250);
        nile.setRegistrationStatus(RegistrationStatus.APPROVED);
        nile.setAccountStatus(AccountStatus.ACTIVE);
        nile.setIntegrationStatus(IntegrationStatus.INTEGRATED);
        nile = institutionRepository.save(nile);

        Institution rowad = new Institution("Al-Rowad Language School", "RLS-02", "School");
        rowad.setInstitutionType(InstitutionType.SCHOOL);
        rowad.setSubType("Language");
        rowad.setCity("Giza");
        rowad.setPrincipalName("Mrs. Nadia Mansour");
        rowad.setPhone("+20 2 3345 6789");
        rowad.setEmail("info@rowad.edu.eg");
        rowad.setRegistrationNumber("MOEDU-SCH-2026-0942");
        rowad.setStudentCount(850);
        rowad.setRegistrationStatus(RegistrationStatus.APPROVED);
        rowad.setAccountStatus(AccountStatus.ACTIVE);
        rowad.setIntegrationStatus(IntegrationStatus.INTEGRATED);
        rowad = institutionRepository.save(rowad);

        Institution cba = new Institution("Cairo British Academy", "CBA-03", "Family");
        cba.setInstitutionType(InstitutionType.SCHOOL);
        cba.setSubType("British");
        cba.setCity("New Cairo");
        cba.setPrincipalName("Mr. Richard Sterling");
        cba.setPhone("+20 2 2811 5566");
        cba.setEmail("admissions@cba.edu.eg");
        cba.setRegistrationNumber("MOEDU-SCH-2026-1105");
        cba.setStudentCount(420);
        cba.setRegistrationStatus(RegistrationStatus.PENDING);
        cba.setAccountStatus(AccountStatus.INACTIVE);
        cba.setIntegrationStatus(IntegrationStatus.NOT_INTEGRATED);
        cba = institutionRepository.save(cba);

        // 2. Institution Admins
        InstitutionAdmin adminNile = new InstitutionAdmin(nile.getId(), "Mariam Admin", "admin@nis.edu.eg", "Password123!", "Finance");
        institutionAdminRepository.save(adminNile);

        InstitutionAdmin adminRowad = new InstitutionAdmin(rowad.getId(), "Tarek Admin", "admin@rowad.edu.eg", "Password123!", "IT");
        institutionAdminRepository.save(adminRowad);

        // 3. Guardians
        String monaNid = "29805150101023";
        String monaHmac = identityResolverService.computeHmacSha256(monaNid);
        Guardian mona = new Guardian(monaHmac, "enc_" + monaNid, "Mona Samir Abdelrahman", "mona.samir@example.com", "01001234567", "Password123!", true);
        mona = guardianRepository.save(mona);

        String ahmedNid = "29511020204536";
        String ahmedHmac = identityResolverService.computeHmacSha256(ahmedNid);
        Guardian ahmed = new Guardian(ahmedHmac, "enc_" + ahmedNid, "Ahmed Tarek Mahmoud", "ahmed.tarek@example.com", "01009876543", "Password123!", true);
        ahmed = guardianRepository.save(ahmed);

        String nourNid = "30103222103442";
        String nourHmac = identityResolverService.computeHmacSha256(nourNid);
        Guardian nour = new Guardian(nourHmac, "enc_" + nourNid, "Nour Khaled Fahmy", "nour.khaled@example.com", "01001234568", "Password123!", true);
        nour = guardianRepository.save(nour);

        String youssefParentNid = "30007091301775";
        String youssefParentHmac = identityResolverService.computeHmacSha256(youssefParentNid);
        Guardian youssefParent = new Guardian(youssefParentHmac, "enc_" + youssefParentNid, "Youssef Adel Nabil", "youssef.adel@example.com", "01045678901", "Password123!", true);
        youssefParent = guardianRepository.save(youssefParent);

        String hebaNid = "28809252506666";
        String hebaHmac = identityResolverService.computeHmacSha256(hebaNid);
        Guardian heba = new Guardian(hebaHmac, "enc_" + hebaNid, "Heba Mostafa Zaki", "heba.mostafa@example.com", "01056789012", "Password123!", true);
        heba = guardianRepository.save(heba);

        // 4. Students
        String saraNid = "31205150101042";
        Student sara = new Student(mona.getId(), nile.getId(), identityResolverService.computeHmacSha256(saraNid), "enc_" + saraNid, "Sara Ahmed", LocalDate.of(2012, 5, 15));
        sara.setGrade("Grade 10");
        sara.setStudentRef("STU-NIS-001");
        sara = studentRepository.save(sara);

        String omarNid = "31509200102035";
        Student omar = new Student(mona.getId(), nile.getId(), identityResolverService.computeHmacSha256(omarNid), "enc_" + omarNid, "Omar Ahmed", LocalDate.of(2015, 9, 20));
        omar.setGrade("Grade 5");
        omar.setStudentRef("STU-NIS-002");
        omar = studentRepository.save(omar);

        String youssefNid = "31403100103017";
        Student youssef = new Student(ahmed.getId(), rowad.getId(), identityResolverService.computeHmacSha256(youssefNid), "enc_" + youssefNid, "Youssef Ahmed", LocalDate.of(2014, 3, 10));
        youssef.setGrade("Grade 7");
        youssef.setStudentRef("STU-ROW-001");
        youssef = studentRepository.save(youssef);

        // 5. FeeLines
        // Sara - Tuition Term 1 (Paid)
        FeeLine f1 = new FeeLine(nile.getId(), sara.getId(), FeeType.TUITION, new BigDecimal("25000.00"), BigDecimal.ZERO, "Term 1 · 2026", LocalDate.now().minusMonths(4));
        f1.setPaidAmount(new BigDecimal("25000.00"));
        f1.setStatus(FeeStatus.PAID);
        f1.setRowIdempotencyKey("SEED-FEE-001");
        f1 = feeLineRepository.save(f1);

        // Sara - Tuition Term 2 (Outstanding, due in 15 days)
        FeeLine f2 = new FeeLine(nile.getId(), sara.getId(), FeeType.TUITION, new BigDecimal("25000.00"), new BigDecimal("25000.00"), "Term 2 · 2026", LocalDate.now().plusDays(15));
        f2.setRowIdempotencyKey("SEED-FEE-002");
        f2 = feeLineRepository.save(f2);

        // Sara - Bus Term 2 (Partially Paid, due in 3 days)
        FeeLine f3 = new FeeLine(nile.getId(), sara.getId(), FeeType.BUS, new BigDecimal("6000.00"), new BigDecimal("4000.00"), "Term 2 · 2026", LocalDate.now().plusDays(3));
        f3.setPaidAmount(new BigDecimal("2000.00"));
        f3.setStatus(FeeStatus.PARTIALLY_PAID);
        f3.setRowIdempotencyKey("SEED-FEE-003");
        f3 = feeLineRepository.save(f3);

        // Omar - Books (Outstanding, due in 30 days)
        FeeLine f4 = new FeeLine(nile.getId(), omar.getId(), FeeType.BOOKS, new BigDecimal("3500.00"), new BigDecimal("3500.00"), "Term 2 · 2026", LocalDate.now().plusDays(30));
        f4.setRowIdempotencyKey("SEED-FEE-004");
        f4 = feeLineRepository.save(f4);

        // Youssef - Tuition Term 2 (Overdue by 12 days, penalty applied)
        FeeLine f5 = new FeeLine(rowad.getId(), youssef.getId(), FeeType.TUITION, new BigDecimal("18000.00"), new BigDecimal("18000.00"), "Term 2 · 2026", LocalDate.now().minusDays(12));
        f5.setPenaltyAmountEGP(new BigDecimal("900.00"));
        f5.setPenaltyAppliedAt(LocalDateTime.now().minusDays(2));
        f5.setRowIdempotencyKey("SEED-FEE-005");
        f5 = feeLineRepository.save(f5);

        // 6. Payments, Allocations & Receipts
        GatewayResponse gatewayResp1 = mockBankClient.processCardPayment(
                "4111111111111111",
                mona.getName(),
                12, 2030, "123",
                new BigDecimal("25000.00"),
                monaNid,
                "IDEMP-SEED-PAY-001"
        );

        Payment p1 = new Payment(mona.getId(), new BigDecimal("25000.00"), PaymentMethod.CREDIT_CARD, "IDEMP-SEED-PAY-001");
        p1.setStatus(PaymentStatus.CAPTURED);
        p1.setTransactionReference(gatewayResp1.transactionReference());
        p1.setAuthCode(gatewayResp1.authCode());
        p1 = paymentRepository.save(p1);

        PaymentAllocation alloc1 = new PaymentAllocation(p1, f1, new BigDecimal("25000.00"));
        paymentAllocationRepository.save(alloc1);

        Receipt r1 = new Receipt(p1, "sha256_sig_" + UUID.randomUUID(), "/receipts/rec_20260515_001.pdf");
        receiptRepository.save(r1);

        Payment p2 = new Payment(mona.getId(), new BigDecimal("2000.00"), PaymentMethod.CIB_ACCOUNT, "IDEMP-SEED-PAY-002");
        p2.setStatus(PaymentStatus.CAPTURED);
        p2.setTransactionReference("TXN-20260601-002");
        p2.setAuthCode("AUTH-11029");
        p2 = paymentRepository.save(p2);

        PaymentAllocation alloc2 = new PaymentAllocation(p2, f3, new BigDecimal("2000.00"));
        paymentAllocationRepository.save(alloc2);

        // 7. EPP Schedule & Installments
        EPPSchedule epp = new EPPSchedule(
                p1,
                12,
                new BigDecimal("25000.00"),
                new BigDecimal("0.14"),
                new BigDecimal("3500.00"),
                new BigDecimal("250.00"),
                new BigDecimal("28750.00"),
                new BigDecimal("2395.83")
        );
        epp = eppScheduleRepository.save(epp);

        for (int i = 1; i <= 12; i++) {
            String installmentStatus = i <= 2 ? "PAID" : "PENDING";
            EppInstallment inst = new EppInstallment(
                    epp,
                    i,
                    new BigDecimal("2395.83"),
                    LocalDateTime.now().minusMonths(2).plusMonths(i),
                    installmentStatus
            );
            eppInstallmentRepository.save(inst);
        }

        // 8. Reconciliation Run & Exceptions
        ReconciliationRun run = new ReconciliationRun();
        run.setInstitution("Nile International School");
        run.setInstitutionType("International School");
        run.setRunDate(LocalDate.now());
        run.setTxCount(16);
        run.setBankAmountEGP(350000L);
        run.setSystemAmountEGP(350500L);
        run.setSchoolAmountEGP(350000L);
        run.setStatus("Exceptions Found");
        run.setTotalTransactions(16);
        run.setMatchedCount(14);
        run.setExceptionCount(2);
        run = reconciliationRunRepository.save(run);

        ReconciliationException ex1 = new ReconciliationException();
        ex1.setReconciliationRunId(run.getId());
        ex1.setPaymentId(p1.getId());
        ex1.setTxRef("TXN-20260908-EX01");
        ex1.setInstitution("Nile International School");
        ex1.setInstitutionType("International School");
        ex1.setBankAmountEGP(25000L);
        ex1.setSystemAmountEGP(25500L);
        ex1.setSchoolAmountEGP(25000L);
        ex1.setDifferenceEGP(500L);
        ex1.setType("Amount Mismatch");
        ex1.setStatus("Under Investigation");
        ex1.setAssignedTo("Layla Reconciliation");
        ex1.setPriority("High");
        reconciliationExceptionRepository.save(ex1);

        ReconciliationException ex2 = new ReconciliationException();
        ex2.setReconciliationRunId(run.getId());
        ex2.setTxRef("TXN-20260908-EX02");
        ex2.setInstitution("Nile International School");
        ex2.setInstitutionType("International School");
        ex2.setBankAmountEGP(6000L);
        ex2.setSystemAmountEGP(0L);
        ex2.setSchoolAmountEGP(6000L);
        ex2.setDifferenceEGP(6000L);
        ex2.setType("Missing in Gateway");
        ex2.setStatus("Open");
        ex2.setPriority("Medium");
        reconciliationExceptionRepository.save(ex2);

        // 9. Back-Office Notifications
        BackOfficeNotification n1 = new BackOfficeNotification();
        n1.setNotifType(NotifType.RECON_EXCEPTION);
        n1.setSeverity(NotifSeverity.HIGH);
        n1.setTitle("Reconciliation Exceptions Detected");
        n1.setBody("Daily reconciliation run for Nile International School completed with 2 exceptions requiring investigation.");
        n1.setMeta("Run: NIS-01 · Status: Exceptions Found");
        n1.setReadFlag(false);
        backOfficeNotificationRepository.save(n1);

        BackOfficeNotification n2 = new BackOfficeNotification();
        n2.setNotifType(NotifType.NEW_INSTITUTION);
        n2.setSeverity(NotifSeverity.LOW);
        n2.setTitle("New Institution Application");
        n2.setBody("Cairo British Academy has submitted registration documents for onboarding review.");
        n2.setMeta("Institution: CBA-03");
        n2.setReadFlag(false);
        backOfficeNotificationRepository.save(n2);

        // 9.5 Cairo International School & Students (School Portal Demo)
        seedCairoInternationalSchool();

        // 10. Audit Log
        AuditLog audit = new AuditLog(
                null,
                "SYSTEM",
                "SYSTEM_STARTUP",
                "ALL",
                "INFO",
                "System",
                "0",
                null,
                "Demo data seeder initialized successfully",
                "127.0.0.1",
                "System"
        );
        auditLogRepository.save(audit);
    }

    private void seedCairoInternationalSchool() {
        if (institutionRepository.findByCode("SCH-001").isPresent()) {
            return;
        }

        Institution cis = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        cis.setInstitutionType(InstitutionType.SCHOOL);
        cis.setSubType("International");
        cis.setCity("Cairo");
        cis.setPrincipalName("Dr. Hassan Mostafa");
        cis.setPhone("+20 2 2516 0000");
        cis.setEmail("info@cis.edu.eg");
        cis.setRegistrationNumber("MOEDU-SCH-2024-0112");
        cis.setStudentCount(850);
        cis.setRegistrationStatus(RegistrationStatus.APPROVED);
        cis.setAccountStatus(AccountStatus.ACTIVE);
        cis.setIntegrationStatus(IntegrationStatus.INTEGRATED);
        cis = institutionRepository.save(cis);

        // Admins
        InstitutionAdmin adminHassan = new InstitutionAdmin(cis.getId(), "Dr. Hassan Mostafa", "hassan.mostafa@cis.edu.eg", "Password123!", "School Admin");
        institutionAdminRepository.save(adminHassan);

        InstitutionAdmin adminAmr = new InstitutionAdmin(cis.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg", "Password123!", "School Admin");
        institutionAdminRepository.save(adminAmr);

        InstitutionAdmin financeDina = new InstitutionAdmin(cis.getId(), "Dina Fouad", "dina.fouad@cis.edu.eg", "Finance@2026", "School Finance");
        institutionAdminRepository.save(financeDina);

        // 10 Active Students matching Figma screenshot exactly:
        // STU-001: Ahmed Hassan (Grade 10) - Total: 54,000 | Paid: 36,000 | Outstanding: 18,000
        seedCisStudent(cis, "STU-001", "Ahmed Hassan", "Grade 10", "A", "Hassan Ahmed", "+20 10 1234 5678", "hassan.ahmed@example.com", "Active", null, null, 54000, 36000);

        // STU-002: Sara Mohamed (Grade 8) - Total: 54,000 | Paid: 54,000 | Outstanding: 0
        seedCisStudent(cis, "STU-002", "Sara Mohamed", "Grade 8", "B", "Mohamed Tarek", "+20 10 2345 6789", "m.tarek@example.com", "Active", null, null, 54000, 54000);

        // STU-003: Omar Ali (Grade 11) - Total: 54,000 | Paid: 18,000 | Outstanding: 36,000
        seedCisStudent(cis, "STU-003", "Omar Ali", "Grade 11", "A", "Ali Mostafa", "+20 10 3456 7890", "ali.mostafa@example.com", "Active", null, null, 54000, 18000);

        // STU-004: Nadia Saleh (Grade 7) - Total: 48,000 | Paid: 48,000 | Outstanding: 0
        seedCisStudent(cis, "STU-004", "Nadia Saleh", "Grade 7", "C", "Saleh Ibrahim", "+20 10 4567 8901", "saleh.ibrahim@example.com", "Active", null, null, 48000, 48000);

        // STU-005: Fatma Khalil (Grade 9) - Total: 54,000 | Paid: 27,000 | Outstanding: 27,000
        seedCisStudent(cis, "STU-005", "Fatma Khalil", "Grade 9", "B", "Khalil Mahmoud", "+20 10 5678 9012", "khalil.m@example.com", "Active", null, null, 54000, 27000);

        // STU-006: Yousef Adel (Grade 10) - Total: 50,000 | Paid: 25,000 | Outstanding: 25,000
        seedCisStudent(cis, "STU-006", "Yousef Adel", "Grade 10", "B", "Adel Mostafa", "+20 10 6789 0123", "adel.mostafa@example.com", "Active", null, null, 50000, 25000);

        // STU-007: Laila Samir (Grade 8) - Total: 52,000 | Paid: 52,000 | Outstanding: 0
        seedCisStudent(cis, "STU-007", "Laila Samir", "Grade 8", "A", "Samir Fathy", "+20 10 7890 1234", "samir.fathy@example.com", "Active", null, null, 52000, 52000);

        // STU-008: Karim Mahmoud (Grade 12) - Total: 60,000 | Paid: 40,000 | Outstanding: 20,000
        seedCisStudent(cis, "STU-008", "Karim Mahmoud", "Grade 12", "A", "Mahmoud Nabil", "+20 10 8901 2345", "m.nabil@example.com", "Active", null, null, 60000, 40000);

        // STU-009: Mona Khaled (Grade 11) - Total: 54,000 | Paid: 36,000 | Outstanding: 18,000
        seedCisStudent(cis, "STU-009", "Mona Khaled", "Grade 11", "C", "Khaled Fahmy", "+20 10 9012 3456", "khaled.fahmy@example.com", "Active", null, null, 54000, 36000);

        // STU-010: Tarek Ibrahim (Grade 9) - Total: 48,000 | Paid: 48,000 | Outstanding: 0
        seedCisStudent(cis, "STU-010", "Tarek Ibrahim", "Grade 9", "B", "Ibrahim Youssef", "+20 10 0123 4567", "ibrahim.y@example.com", "Active", null, null, 48000, 48000);

        // 1 Deactivated Student:
        // STU-011: Nour Kamal (Grade 8) - Inactive, Reason: "Withdrawn by Guardian"
        seedCisStudent(cis, "STU-011", "Nour Kamal", "Grade 8", "A", "Kamal Nour", "+20 10 1122 3344", "kamal.nour@example.com", "Inactive", LocalDate.of(2026, 7, 14), "Withdrawn by Guardian", 30000, 30000);
        
        log.info("Seeded Cairo International School with Dr. Hassan Mostafa, 10 active students, and 1 deactivated student.");
    }

    private void seedCisStudent(Institution cis, String studentRef, String name, String grade, String section,
                                String parentName, String parentPhone, String parentEmail,
                                String status, LocalDate deactDate, String deactReason,
                                int totalAmount, int paidAmount) {
        String rawNid = "30" + UUID.randomUUID().toString().replaceAll("[^0-9]", "");
        if (rawNid.length() < 14) {
            rawNid = String.format("%-14s", rawNid).replace(' ', '1');
        } else if (rawNid.length() > 14) {
            rawNid = rawNid.substring(0, 14);
        }
        String nidHash = identityResolverService.computeHmacSha256(rawNid);
        String nidEnc = "enc_" + rawNid;

        // Guardian
        String guardianHmac = identityResolverService.computeHmacSha256(parentEmail);
        Guardian guardian = guardianRepository.findByEmail(parentEmail).orElseGet(() -> {
            Guardian g = new Guardian(guardianHmac, "enc_" + parentEmail, parentName, parentEmail, parentPhone, "Password123!", true);
            return guardianRepository.save(g);
        });

        Student student = new Student(
                guardian.getId(),
                cis.getId(),
                nidHash,
                nidEnc,
                name,
                LocalDate.of(2012, 1, 1),
                studentRef,
                grade,
                section,
                parentName,
                parentPhone,
                parentEmail
        );
        student.setStatus(status);
        student.setDeactivatedDate(deactDate);
        student.setDeactivationReason(deactReason);
        student = studentRepository.save(student);

        BigDecimal totalBd = new BigDecimal(totalAmount);
        BigDecimal paidBd = new BigDecimal(paidAmount);
        BigDecimal remainingBd = totalBd.subtract(paidBd);

        if (paidBd.compareTo(BigDecimal.ZERO) > 0 && remainingBd.compareTo(BigDecimal.ZERO) > 0) {
            FeeLine term1 = new FeeLine(cis.getId(), student.getId(), FeeType.TUITION, paidBd, BigDecimal.ZERO, "Term 1 · 2026", LocalDate.now().minusMonths(3));
            term1.setPaidAmount(paidBd);
            term1.setStatus(FeeStatus.PAID);
            term1.setRowIdempotencyKey("SEED-CIS-" + studentRef + "-T1");
            feeLineRepository.save(term1);

            FeeLine term2 = new FeeLine(cis.getId(), student.getId(), FeeType.TUITION, remainingBd, remainingBd, "Term 2 · 2026", LocalDate.now().plusDays(20));
            term2.setPaidAmount(BigDecimal.ZERO);
            term2.setStatus(FeeStatus.OUTSTANDING);
            term2.setRowIdempotencyKey("SEED-CIS-" + studentRef + "-T2");
            feeLineRepository.save(term2);
        } else if (remainingBd.compareTo(BigDecimal.ZERO) == 0) {
            FeeLine fullYear = new FeeLine(cis.getId(), student.getId(), FeeType.TUITION, totalBd, BigDecimal.ZERO, "Annual · 2026", LocalDate.now().minusMonths(4));
            fullYear.setPaidAmount(totalBd);
            fullYear.setStatus(FeeStatus.PAID);
            fullYear.setRowIdempotencyKey("SEED-CIS-" + studentRef + "-FULL");
            feeLineRepository.save(fullYear);
        } else {
            FeeLine fullYear = new FeeLine(cis.getId(), student.getId(), FeeType.TUITION, totalBd, totalBd, "Annual · 2026", LocalDate.now().plusDays(15));
            fullYear.setPaidAmount(BigDecimal.ZERO);
            fullYear.setStatus(FeeStatus.OUTSTANDING);
            fullYear.setRowIdempotencyKey("SEED-CIS-" + studentRef + "-UNPAID");
            feeLineRepository.save(fullYear);
        }
    }
}
