package com.tuitionnetwork.seed;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = DemoApplication.class)
@TestPropertySource(properties = "app.demo-seeder.enabled=true")
class DemoDataSeederTest {

    @Autowired
    private BankEmployeeRepository bankEmployeeRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EPPScheduleRepository eppScheduleRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private BackOfficeNotificationRepository backOfficeNotificationRepository;

    @Test
    void seeder_populatesEntitiesWhenEnabled() {
        assertTrue(bankEmployeeRepository.findByEmailOrEmployeeId("ahmed.ops").isPresent(), "Ahmed Operations should be seeded");
        assertTrue(bankEmployeeRepository.findByEmailOrEmployeeId("admin").isPresent(), "Sara Admin should be seeded");
        assertTrue(bankEmployeeRepository.findByEmailOrEmployeeId("finance").isPresent(), "Tarek Finance should be seeded");
        assertTrue(bankEmployeeRepository.findByEmailOrEmployeeId("recon").isPresent(), "Layla Reconciliation should be seeded");

        assertTrue(institutionRepository.count() >= 2, "Demo institutions should be seeded");
        assertTrue(studentRepository.count() >= 2, "Demo students should be seeded");
        assertTrue(feeLineRepository.count() >= 3, "Demo fee lines should be seeded");
        assertTrue(paymentRepository.count() >= 1, "Demo payments should be seeded");
        assertTrue(eppScheduleRepository.count() >= 1, "Demo EPP schedule should be seeded");
        assertTrue(reconciliationRunRepository.count() >= 1, "Demo reconciliation run should be seeded");
        assertTrue(backOfficeNotificationRepository.count() >= 1, "Demo back-office notifications should be seeded");
    }
}
