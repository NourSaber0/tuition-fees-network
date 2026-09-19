package com.tuitionnetwork.billing;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.dto.FeeDeadlineSnapshot;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.billing.service.FeeDeadlineService;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
@Transactional
class FeeDeadlineIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private FeeDeadlineService feeDeadlineService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private IdentityResolverService identityResolverService;

    private MockMvc mockMvc;
    private Institution institution;
    private Student student;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        institution = institutionRepository.save(new Institution("Nile International School", "NIS-" + UUID.randomUUID(), "ABSORBED"));

        String nationalId = "2990101" + (1000000 + new java.util.Random().nextInt(8999999));
        String hmac = identityResolverService.computeHmacSha256(nationalId);
        Guardian guardian = guardianRepository.save(new Guardian(hmac, "enc", "Test Guardian", "guardian@example.com", "+201000000000", "pass", true));
        student = studentRepository.save(new Student(guardian.getId(), institution.getId(), "shmac-" + UUID.randomUUID(), "senc", "Test Student", LocalDate.of(2012, 1, 1)));
    }

    private FeeLine feeLine(BigDecimal total, BigDecimal remaining, LocalDate dueDate) {
        FeeLine fl = new FeeLine();
        fl.setInstitutionId(institution.getId());
        fl.setStudentId(student.getId());
        fl.setFeeType(FeeType.TUITION);
        fl.setTotalAmount(total);
        fl.setPaidAmount(total.subtract(remaining));
        fl.setRemainingAmount(remaining);
        fl.setStatus(remaining.compareTo(BigDecimal.ZERO) <= 0 ? FeeStatus.PAID : FeeStatus.OUTSTANDING);
        fl.setCollectionPeriod("Term 1 - 2026");
        fl.setDueDate(dueDate);
        return feeLineRepository.save(fl);
    }

    @Test
    void snapshot_isFullyPaid_hasNoPenalty_evenWhenPastDue() {
        FeeLine fl = feeLine(new BigDecimal("5000.00"), BigDecimal.ZERO, LocalDate.now().minusDays(30));
        FeeDeadlineSnapshot snapshot = feeDeadlineService.computeSnapshot(fl);

        assertEquals("PAID", snapshot.priority().name());
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(snapshot.penaltyEGP()));
    }

    @Test
    void snapshot_penaltyIsBasedOnRemainingBalance_notOriginalAmount() {
        // 10,000 EGP fee already paid down to 2,000 EGP outstanding -> 5% of 2,000, not 10,000
        FeeLine fl = feeLine(new BigDecimal("10000.00"), new BigDecimal("2000.00"), LocalDate.now().minusDays(3));
        FeeDeadlineSnapshot snapshot = feeDeadlineService.computeSnapshot(fl);

        assertEquals("OVERDUE", snapshot.priority().name());
        assertEquals(0, new BigDecimal("100.00").compareTo(snapshot.penaltyEGP()));
        assertEquals(0, new BigDecimal("2100.00").compareTo(snapshot.totalDueEGP()));
    }

    @Test
    void applyPenaltyIfDue_isIdempotent_neverCompounds() {
        FeeLine fl = feeLine(new BigDecimal("8000.00"), new BigDecimal("8000.00"), LocalDate.now().minusDays(1));

        BigDecimal firstApplication = feeDeadlineService.applyPenaltyIfDue(fl);
        assertEquals(0, new BigDecimal("400.00").compareTo(firstApplication));
        assertNotNull(fl.getPenaltyAppliedAt());

        var appliedAtFirst = fl.getPenaltyAppliedAt();

        BigDecimal secondApplication = feeDeadlineService.applyPenaltyIfDue(fl);
        assertEquals(0, new BigDecimal("400.00").compareTo(secondApplication));
        assertEquals(appliedAtFirst, fl.getPenaltyAppliedAt());
    }

    @Test
    void applyPenaltyIfDue_doesNothing_whenNotOverdue() {
        FeeLine fl = feeLine(new BigDecimal("8000.00"), new BigDecimal("8000.00"), LocalDate.now().plusDays(5));

        BigDecimal penalty = feeDeadlineService.applyPenaltyIfDue(fl);
        assertEquals(0, BigDecimal.ZERO.compareTo(penalty));
        assertNull(fl.getPenaltyAppliedAt());
    }

    @Test
    void customerFees_snapshotReflectsOverdueFee() {
        // The GET /customers/fees HTTP path is already covered by BackOfficePaymentIntegrationTest;
        // here we verify the deadline snapshot it now embeds per fee is computed correctly.
        FeeLine fl = feeLine(new BigDecimal("6000.00"), new BigDecimal("6000.00"), LocalDate.now().minusDays(2));

        FeeDeadlineSnapshot snapshot = feeDeadlineService.computeSnapshot(fl);
        assertEquals("OVERDUE", snapshot.priority().name());
        assertEquals(0, new BigDecimal("300.00").compareTo(snapshot.penaltyEGP()));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void updateDueDate_changesDateAndRecomputesPriority() throws Exception {
        FeeLine fl = feeLine(new BigDecimal("4000.00"), new BigDecimal("4000.00"), LocalDate.now().plusDays(45));

        mockMvc.perform(patch("/api/v1/fees/{id}/due-date", fl.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"" + LocalDate.now().plusDays(3) + "\",\"reason\":\"Guardian requested extension\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void updateDueDate_unknownFeeLine_returnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/fees/{id}/due-date", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"2026-12-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void applyPenaltiesBatch_appliesToAllOverdueUnpenalizedFees() throws Exception {
        feeLine(new BigDecimal("5000.00"), new BigDecimal("5000.00"), LocalDate.now().minusDays(1)); // -> 250 penalty
        feeLine(new BigDecimal("3000.00"), new BigDecimal("3000.00"), LocalDate.now().minusDays(10)); // -> 150 penalty
        feeLine(new BigDecimal("1000.00"), new BigDecimal("1000.00"), LocalDate.now().plusDays(5)); // not overdue, skipped

        mockMvc.perform(post("/api/v1/internal/fees/apply-penalties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.penaltiesAppliedEGP").value(400.00));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void dashboardDeadlineSummary_reflectsOverdueAndUrgentFees() throws Exception {
        feeLine(new BigDecimal("5000.00"), new BigDecimal("5000.00"), LocalDate.now().minusDays(1)); // overdue
        feeLine(new BigDecimal("3000.00"), new BigDecimal("3000.00"), LocalDate.now()); // due today -> urgent

        mockMvc.perform(get("/api/v1/dashboard/deadline-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.dueToday").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.priorityQueue").isArray())
                .andExpect(jsonPath("$.priorityQueue[0].priority").value("OVERDUE"));
    }
}
