package com.tuitionnetwork.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.DemoApplication;
import com.tuitionnetwork.billing.domain.DeadlinePolicy;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.fees.service.FeeAutomatedRulesEngine;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.notifications.domain.SchoolNotification;
import com.tuitionnetwork.notifications.dto.SchoolNotificationPreferencesDto;
import com.tuitionnetwork.notifications.repository.SchoolNotificationRepository;
import com.tuitionnetwork.notifications.service.SchoolNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
public class SchoolNotificationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private SchoolNotificationRepository schoolNotificationRepository;

    @Autowired
    private SchoolNotificationService schoolNotificationService;

    @Autowired
    private FeeAutomatedRulesEngine feeAutomatedRulesEngine;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private Institution schoolA;
    private Institution schoolB;

    private InstitutionAdmin adminA;
    private InstitutionAdmin financeA;
    private InstitutionAdmin adminB;

    private String tokenAdminA;
    private String tokenFinanceA;
    private String tokenAdminB;

    private Student studentA1;
    private Student studentB1;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        schoolNotificationRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        // 1. Schools
        schoolA = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        schoolB = new Institution("Alexandria National School", "SCH-002", "SCHOOL_ABSORBS");
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        // 2. School Admins & Finance
        adminA = new InstitutionAdmin(schoolA.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg", "Password123!", "School Admin");
        adminA.setStatus("Active");
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Dina Fouad", "dina.fouad@cis.edu.eg", "Finance@2026", "School Finance");
        financeA.setStatus("Active");
        financeA = institutionAdminRepository.save(financeA);

        adminB = new InstitutionAdmin(schoolB.getId(), "Tarek Samy", "tarek@ans.edu.eg", "Password123!", "School Admin");
        adminB.setStatus("Active");
        adminB = institutionAdminRepository.save(adminB);

        // 3. Tokens
        tokenAdminA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminA.getId(), adminA.getEmail(), adminA.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenFinanceA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                financeA.getId(), financeA.getEmail(), financeA.getName(),
                UserRole.ROLE_SCHOOL_FINANCE,
                List.of(UserRole.ROLE_SCHOOL_FINANCE, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenAdminB = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminB.getId(), adminB.getEmail(), adminB.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolB.getId()
        ));

        // 4. Students
        studentA1 = new Student(
                null, schoolA.getId(), "hash_s1", "enc_s1", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "STU-0231", "Grade 10", "A",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        studentA1 = studentRepository.save(studentA1);

        studentB1 = new Student(
                null, schoolB.getId(), "hash_s2", "enc_s2", "Salma Ahmed",
                LocalDate.of(2011, 3, 20), "STU-0401", "Grade 9", "B",
                "Ahmed Hassan", "+20 10 9876 5432", "a.hassan@example.com"
        );
        studentB1 = studentRepository.save(studentB1);
    }

    // ── 9.1 GET /notifications ───────────────────────────────────────────────

    @Test
    @DisplayName("9.1 GET /notifications: Returns empty list when school has no notifications")
    void testListNotifications_empty() throws Exception {
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("9.1 GET /notifications: Returns list with full row shape matching Contract 9.1")
    void testListNotifications_fullShape() throws Exception {
        schoolNotificationService.createNotification(
                schoolA.getId(),
                "reminder",
                "Payment reminder sent",
                "Reminder: Yousef Adel's Tuition fee of 18,000 EGP is due in 1 week on 2026-09-15.",
                "Yousef Adel",
                studentA1.getId(),
                "Tuition",
                18000L,
                LocalDate.of(2026, 9, 15),
                7,
                "Sent",
                "FEE-0231-01"
        );

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id", startsWith("NTF-")))
                .andExpect(jsonPath("$.data[0].type").value("reminder"))
                .andExpect(jsonPath("$.data[0].title").value("Payment reminder sent"))
                .andExpect(jsonPath("$.data[0].description", containsString("Yousef Adel's Tuition fee")))
                .andExpect(jsonPath("$.data[0].studentName").value("Yousef Adel"))
                .andExpect(jsonPath("$.data[0].feeType").value("Tuition"))
                .andExpect(jsonPath("$.data[0].feeAmountEGP").value(18000))
                .andExpect(jsonPath("$.data[0].dueDate").value("2026-09-15"))
                .andExpect(jsonPath("$.data[0].daysUntilDue").value(7))
                .andExpect(jsonPath("$.data[0].notificationStatus").value("Sent"))
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[0].relatedId").value("FEE-0231-01"));
    }

    @Test
    @DisplayName("9.1 GET /notifications: Filter by type (payment, reminder, penalty, upload)")
    void testListNotifications_filterByType() throws Exception {
        schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "Reminder 1", "Desc", "Student 1", null, "Tuition", 5000L, null, 7, "Sent", null);
        schoolNotificationService.createNotification(
                schoolA.getId(), "penalty", "Penalty 1", "Desc", "Student 2", null, "Tuition", 250L, null, 0, "Sent", null);
        schoolNotificationService.createNotification(
                schoolA.getId(), "payment", "Payment 1", "Desc", "Student 3", null, "Bus", 4000L, null, 0, "Sent", null);

        // Filter reminder
        mockMvc.perform(get("/notifications?type=reminder")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("reminder"))
                .andExpect(jsonPath("$.unreadCount").value(3));

        // Filter penalty
        mockMvc.perform(get("/notifications?type=penalty")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("penalty"));

        // Filter payment
        mockMvc.perform(get("/notifications?type=payment")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("payment"));
    }

    @Test
    @DisplayName("9.1 GET /notifications: Filter by read status and pagination")
    void testListNotifications_filterByReadAndPagination() throws Exception {
        SchoolNotification n1 = schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "Reminder 1", "Desc", null, null, null, null, null, null, null, null);
        SchoolNotification n2 = schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "Reminder 2", "Desc", null, null, null, null, null, null, null, null);
        SchoolNotification n3 = schoolNotificationService.createNotification(
                schoolA.getId(), "penalty", "Penalty 1", "Desc", null, null, null, null, null, null, null, null);

        // Mark n1 as read
        schoolNotificationService.markAsRead(schoolA.getId(), n1.getNotificationRef());

        // Read = true filter
        mockMvc.perform(get("/notifications?read=true")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(n1.getNotificationRef()))
                .andExpect(jsonPath("$.data[0].read").value(true));

        // Read = false filter
        mockMvc.perform(get("/notifications?read=false")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.unreadCount").value(2));

        // Pagination: pageSize=1, page=0
        mockMvc.perform(get("/notifications?page=0&pageSize=1")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // ── 9.2 GET /notifications/unread-count ──────────────────────────────────

    @Test
    @DisplayName("9.2 GET /notifications/unread-count: Accurate badge count")
    void testGetUnreadCount() throws Exception {
        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        SchoolNotification n1 = schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "Reminder 1", "Desc", null, null, null, null, null, null, null, null);
        schoolNotificationService.createNotification(
                schoolA.getId(), "penalty", "Penalty 1", "Desc", null, null, null, null, null, null, null, null);

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        // Mark 1 as read
        schoolNotificationService.markAsRead(schoolA.getId(), n1.getNotificationRef());

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    // ── 9.3 POST & PATCH /notifications/{id}/read ─────────────────────────────

    @Test
    @DisplayName("9.3 POST & PATCH /notifications/{id}/read: Mark read by Ref (NTF-xxxx) and UUID")
    void testMarkAsRead_byRefAndUuid() throws Exception {
        SchoolNotification n1 = schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "R1", "D1", null, null, null, null, null, null, null, null);
        SchoolNotification n2 = schoolNotificationService.createNotification(
                schoolA.getId(), "penalty", "P1", "D1", null, null, null, null, null, null, null, null);

        // POST /notifications/{ref}/read
        mockMvc.perform(post("/notifications/" + n1.getNotificationRef() + "/read")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(n1.getNotificationRef()))
                .andExpect(jsonPath("$.read").value(true));

        // PATCH /notifications/{uuid}/read
        mockMvc.perform(patch("/notifications/" + n2.getId().toString() + "/read")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(n2.getNotificationRef()))
                .andExpect(jsonPath("$.read").value(true));

        // Verify unread count is now 0
        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("9.3 POST /notifications/{id}/read: Returns 404 for non-existent notification")
    void testMarkAsRead_notFound() throws Exception {
        mockMvc.perform(post("/notifications/NTF-9999/read")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isNotFound());
    }

    // ── 9.4 POST /notifications/read-all ─────────────────────────────────────

    @Test
    @DisplayName("9.4 POST /notifications/read-all: Mark all read in bulk")
    void testMarkAllRead() throws Exception {
        schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "R1", "D1", null, null, null, null, null, null, null, null);
        schoolNotificationService.createNotification(
                schoolA.getId(), "penalty", "P1", "D1", null, null, null, null, null, null, null, null);
        schoolNotificationService.createNotification(
                schoolA.getId(), "payment", "Pay1", "D1", null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/notifications/read-all")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // ── 9.5 DELETE /notifications/{id} ───────────────────────────────────────

    @Test
    @DisplayName("9.5 DELETE /notifications/{id}: Dismiss notification (HTTP 204)")
    void testDismissNotification() throws Exception {
        SchoolNotification n1 = schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "R1", "D1", null, null, null, null, null, null, null, null);

        mockMvc.perform(delete("/notifications/" + n1.getNotificationRef())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isNoContent());

        // Verify it was deleted
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    // ── 9.6 GET /notifications/reminders ─────────────────────────────────────

    @Test
    @DisplayName("9.6 GET /notifications/reminders: Dedicated parent reminders feed with status filter")
    void testListReminders_dedicatedFeed() throws Exception {
        schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "Reminder 1", "Due soon", "Student 1", null, "Tuition", 15000L,
                LocalDate.now().plusDays(7), 7, "Sent", "FEE-01");
        schoolNotificationService.createNotification(
                schoolA.getId(), "reminder", "Reminder 2", "Scheduled", "Student 2", null, "Bus", 3000L,
                LocalDate.now().plusDays(7), 7, "Scheduled", "FEE-02");
        schoolNotificationService.createNotification(
                schoolA.getId(), "penalty", "Penalty 1", "Overdue", "Student 3", null, "Tuition", 500L,
                LocalDate.now().minusDays(10), 0, "Sent", "FEE-03");

        // Reminders endpoint should ONLY return reminders (not penalties)
        mockMvc.perform(get("/notifications/reminders")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].type", everyItem(equalTo("reminder"))));

        // Filter status = Scheduled
        mockMvc.perform(get("/notifications/reminders?status=Scheduled")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].notificationStatus").value("Scheduled"));
    }

    // ── Preferences: GET & PUT /notifications/preferences ───────────────────

    @Test
    @DisplayName("Preferences: GET and PUT channel toggles")
    void testNotificationPreferences() throws Exception {
        // GET preferences
        mockMvc.perform(get("/notifications/preferences")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(true));

        // PUT preferences
        SchoolNotificationPreferencesDto updateDto = new SchoolNotificationPreferencesDto(
                Map.of("inApp", true, "email", false)
        );

        mockMvc.perform(put("/notifications/preferences")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(false));

        // Verify GET reflects update
        mockMvc.perform(get("/notifications/preferences")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.email").value(false));
    }

    // ── Multi-Tenant Isolation ───────────────────────────────────────────────

    @Test
    @DisplayName("Multi-Tenant Isolation: School A cannot see or mutate School B's notifications")
    void testMultiTenantIsolation() throws Exception {
        SchoolNotification notifB = schoolNotificationService.createNotification(
                schoolB.getId(), "reminder", "School B Notice", "Private to School B", null, null, null, null, null, null, null, null);

        // 1. School A listing notifications does NOT see School B's notification
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 2. School A attempting to read School B's notification -> 403 Forbidden
        mockMvc.perform(post("/notifications/" + notifB.getNotificationRef() + "/read")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        // 3. School A attempting to dismiss School B's notification -> 403 Forbidden
        mockMvc.perform(delete("/notifications/" + notifB.getNotificationRef())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        // 4. Passing cross-school institutionId query param -> 403 Forbidden
        mockMvc.perform(get("/notifications?institutionId=" + schoolB.getId())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    // ── Security & Roles ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Security: 401 Unauthorized for unauthenticated requests")
    void testSecurity_unauthenticated() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/notifications/NTF-001/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Roles: School Finance can view and mark notifications as read")
    void testSchoolFinanceAccess() throws Exception {
        schoolNotificationService.createNotification(
                schoolA.getId(), "payment", "Payment received", "5,000 EGP", null, null, null, null, null, null, null, null);

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    // ── Server-Generated Rules Engines Integration ───────────────────────────

    @Test
    @DisplayName("Server-Generated: Engine A (Penalties) and Engine B (Reminders) write notifications automatically")
    void testAutomatedEnginesGenerateNotifications() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Fee for Engine A (Overdue > 7 days)
        FeeLine overdueFee = new FeeLine(
                schoolA.getId(), studentA1.getId(), FeeType.TUITION, new BigDecimal("10000.00"), new BigDecimal("10000.00"), "Overdue Term", today.minusDays(10)
        );
        feeLineRepository.save(overdueFee);

        // 2. Fee for Engine B (Exactly 7 days until due)
        FeeLine approachingFee = new FeeLine(
                schoolA.getId(), studentA1.getId(), FeeType.TUITION, new BigDecimal("12000.00"), new BigDecimal("12000.00"), "Approaching Term", today.plusDays(7)
        );
        feeLineRepository.save(approachingFee);

        // Run Engine A
        int penalized = feeAutomatedRulesEngine.evaluateTuitionPenalties(today);
        org.junit.jupiter.api.Assertions.assertTrue(penalized >= 1);

        // Run Engine B
        int reminded = feeAutomatedRulesEngine.dispatchApproachingReminders(today);
        org.junit.jupiter.api.Assertions.assertTrue(reminded >= 1);

        // Verify notifications exist in school A feed
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.type == 'penalty')]").exists())
                .andExpect(jsonPath("$.data[?(@.type == 'reminder')]").exists());
    }
}
