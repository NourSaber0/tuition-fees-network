package com.tuitionnetwork.reconciliation.config;

import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Profile("!prod")
@Order(50)
public class ReconciliationDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationDataInitializer.class);

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationExceptionRepository exceptionRepository;

    public ReconciliationDataInitializer(
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository) {
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Override
    public void run(String... args) {
        if (runRepository.count() == 0) {
            log.info("Seeding initial Reconciliation runs and exceptions for Back-Office demo...");

            // Run 1: Cairo American College - Matched
            ReconciliationRun run1 = new ReconciliationRun("Matched");
            run1.setInstitution("Cairo American College");
            run1.setInstitutionType("School");
            run1.setRunDate(LocalDate.now());
            run1.setTxCount(412);
            run1.setTotalTransactions(412);
            run1.setMatchedCount(412);
            run1.setExceptionCount(0);
            run1.setBankAmountEGP(824000L);
            run1.setSystemAmountEGP(824000L);
            run1.setSchoolAmountEGP(824000L);
            run1.setCreatedAt(LocalDateTime.now().minusHours(8));
            run1 = runRepository.save(run1);

            // Run 2: The American University in Cairo - Matched
            ReconciliationRun run2 = new ReconciliationRun("Matched");
            run2.setInstitution("The American University in Cairo");
            run2.setInstitutionType("University");
            run2.setRunDate(LocalDate.now());
            run2.setTxCount(520);
            run2.setTotalTransactions(520);
            run2.setMatchedCount(520);
            run2.setExceptionCount(0);
            run2.setBankAmountEGP(1560000L);
            run2.setSystemAmountEGP(1560000L);
            run2.setSchoolAmountEGP(1560000L);
            run2.setCreatedAt(LocalDateTime.now().minusHours(6));
            run2 = runRepository.save(run2);

            // Run 3: Modern English School Cairo - Exception
            ReconciliationRun run3 = new ReconciliationRun("Exception");
            run3.setInstitution("Modern English School Cairo");
            run3.setInstitutionType("School");
            run3.setRunDate(LocalDate.now());
            run3.setTxCount(352);
            run3.setTotalTransactions(352);
            run3.setMatchedCount(346);
            run3.setExceptionCount(3);
            run3.setBankAmountEGP(704000L);
            run3.setSystemAmountEGP(708500L);
            run3.setSchoolAmountEGP(708500L);
            run3.setCreatedAt(LocalDateTime.now().minusHours(2));
            run3 = runRepository.save(run3);

            // Exception 1: Amount Mismatch (High Priority)
            ReconciliationException ex1 = new ReconciliationException();
            ex1.setReconciliationRunId(run3.getId());
            ex1.setTxRef("TXN-20260907-8842");
            ex1.setInstitution("Modern English School Cairo");
            ex1.setInstitutionType("School");
            ex1.setBankAmountEGP(4500L);
            ex1.setSystemAmountEGP(9000L);
            ex1.setSchoolAmountEGP(9000L);
            ex1.setDifferenceEGP(4500L);
            ex1.setType("Amount Mismatch");
            ex1.setDate(LocalDate.now());
            ex1.setStatus("Open");
            ex1.setPriority("High");
            ex1.setAssignedTo("Rania Mostafa");
            ex1.setTxStatus("SUCCESS");
            ex1.setPayMethod("CREDIT_CARD");
            ex1.setBankRef("CIB-SWIFT-991204");
            ex1.setBankStatus("SETTLED");
            ex1.setSettlementDate(LocalDate.now());
            ex1.setFeeRef("FEE-MES-2026-0012");
            ex1.setCollectionDate(LocalDate.now());
            ex1.setReason("Partial settlement recorded at gateway batch vs full tuition fee invoiced.");
            ex1.setCreatedAt(LocalDateTime.now().minusHours(2));
            exceptionRepository.save(ex1);

            // Exception 2: Late Settlement (Medium Priority)
            ReconciliationException ex2 = new ReconciliationException();
            ex2.setReconciliationRunId(run1.getId());
            ex2.setTxRef("TXN-20260907-3319");
            ex2.setInstitution("Cairo American College");
            ex2.setInstitutionType("School");
            ex2.setBankAmountEGP(12000L);
            ex2.setSystemAmountEGP(12000L);
            ex2.setSchoolAmountEGP(12000L);
            ex2.setDifferenceEGP(0L);
            ex2.setType("Late Settlement");
            ex2.setDate(LocalDate.now());
            ex2.setStatus("Under Investigation");
            ex2.setPriority("Medium");
            ex2.setAssignedTo("Tarek Al-Mansoor");
            ex2.setTxStatus("SUCCESS");
            ex2.setPayMethod("DEBIT_CARD");
            ex2.setBankRef("CIB-SWIFT-882190");
            ex2.setBankStatus("PENDING");
            ex2.setSettlementDate(LocalDate.now());
            ex2.setFeeRef("FEE-CAC-2026-9901");
            ex2.setCollectionDate(LocalDate.now());
            ex2.setReason("Cutoff delay on host card clearing network.");
            ex2.setCreatedAt(LocalDateTime.now().minusHours(3));
            exceptionRepository.save(ex2);

            // Exception 3: Duplicate Submission (High Priority)
            ReconciliationException ex3 = new ReconciliationException();
            ex3.setReconciliationRunId(run2.getId());
            ex3.setTxRef("TXN-20260907-1102");
            ex3.setInstitution("The American University in Cairo");
            ex3.setInstitutionType("University");
            ex3.setBankAmountEGP(0L);
            ex3.setSystemAmountEGP(15000L);
            ex3.setSchoolAmountEGP(15000L);
            ex3.setDifferenceEGP(15000L);
            ex3.setType("Duplicate Submission");
            ex3.setDate(LocalDate.now());
            ex3.setStatus("Escalated");
            ex3.setPriority("High");
            ex3.setAssignedTo("Mohamed Ali");
            ex3.setTxStatus("PENDING");
            ex3.setPayMethod("EPP");
            ex3.setBankRef("CIB-SWIFT-001294");
            ex3.setBankStatus("REJECTED");
            ex3.setSettlementDate(LocalDate.now());
            ex3.setFeeRef("FEE-AUC-2026-4410");
            ex3.setCollectionDate(LocalDate.now());
            ex3.setReason("Duplicate ledger posting detected from SIS API push.");
            ex3.setCreatedAt(LocalDateTime.now().minusHours(4));
            exceptionRepository.save(ex3);

            log.info("Reconciliation initial data seeded successfully.");
        }
    }
}
