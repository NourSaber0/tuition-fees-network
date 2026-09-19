package com.tuitionnetwork.search;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.dto.StudentFeeLineDto;
import com.tuitionnetwork.billing.service.BillingFeeQueryService;
import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import com.tuitionnetwork.identity.dto.ResolvedStudentDto;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.search.dto.GuardianDuesResponse;
import com.tuitionnetwork.search.service.SearchService;
import com.tuitionnetwork.search.web.DuesSearchController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DuesSearchControllerTest {

    @Test
    void searchGuardianDues_successfullyReturnsAggregatedDues_andRecordsAuditLog() {
        IdentityResolverService identityService = mock(IdentityResolverService.class);
        BillingFeeQueryService billingService = mock(BillingFeeQueryService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

        SearchService searchService = new SearchService(identityService, billingService, auditLogRepository);
        DuesSearchController controller = new DuesSearchController(searchService);

        String nationalId = "29001011234567";
        String hashedNationalId = "hmac-sha256-hash-12345";
        UUID guardianId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();

        ResolvedGuardianDto guardianDto = new ResolvedGuardianDto(
                guardianId,
                "Ahmed Saber",
                hashedNationalId,
                "enc123",
                "ahmed@example.com",
                "+201012345678",
                true, "acc_123", "card_123",
                List.of(new ResolvedStudentDto(studentId, "Sara Ahmed", instId, "Nile International School"))
        );

        when(identityService.computeHmacSha256(eq(nationalId)))
                .thenReturn(hashedNationalId);
        when(identityService.resolveGuardianByNationalId(eq(nationalId)))
                .thenReturn(Optional.of(guardianDto));

        StudentFeeLineDto feeLineDto = new StudentFeeLineDto(
                UUID.randomUUID(),
                studentId,
                instId,
                "Tuition",
                "Term 2 · 2026",
                new BigDecimal("18000.00"),
                BigDecimal.ZERO,
                new BigDecimal("18000.00"),
                "EGP",
                "OUTSTANDING",
                LocalDate.now().plusMonths(1)
        );

        when(billingService.findOpenFeesByStudentIds(anyList()))
                .thenReturn(List.of(feeLineDto));

        ResponseEntity<GuardianDuesResponse> response = controller.searchGuardianDues(nationalId, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Ahmed Saber", response.getBody().guardianName());
        assertEquals(new BigDecimal("18000.00"), response.getBody().totalOutstandingEGP());
        assertEquals(1, response.getBody().students().size());
        assertEquals("Sara Ahmed", response.getBody().students().get(0).studentName());
        assertEquals("Nile International School", response.getBody().students().get(0).institutionName());
        assertEquals(1, response.getBody().students().get(0).dues().size());
        assertEquals("Tuition", response.getBody().students().get(0).dues().get(0).feeType());
        assertEquals(new BigDecimal("18000.00"), response.getBody().students().get(0).dues().get(0).remainingAmount());

        // Verify AUDIT_LOG insertion
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog savedLog = auditCaptor.getValue();
        assertNotNull(savedLog);
        assertEquals(guardianId, savedLog.getActorId());
        assertEquals("Guardian", savedLog.getActorType());
        assertEquals("Search_National_ID", savedLog.getAction());
        assertEquals(hashedNationalId, savedLog.getTargetResource());
    }

    @Test
    void searchGuardianDues_returnsBadRequestWhenNationalIdMissing() {
        IdentityResolverService identityService = mock(IdentityResolverService.class);
        BillingFeeQueryService billingService = mock(BillingFeeQueryService.class);
        SearchService searchService = new SearchService(identityService, billingService);
        DuesSearchController controller = new DuesSearchController(searchService);

        ResponseEntity<GuardianDuesResponse> response = controller.searchGuardianDues(null, null);

        assertEquals(400, response.getStatusCode().value());
    }
}
