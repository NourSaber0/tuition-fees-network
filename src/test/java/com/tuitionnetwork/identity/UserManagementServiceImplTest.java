package com.tuitionnetwork.identity;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.dto.auth.ForgotPasswordRequest;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.dto.users.BankUserSummaryDto;
import com.tuitionnetwork.identity.dto.users.CreateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UserStatusResponse;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.identity.service.BankAuthService;
import com.tuitionnetwork.identity.service.UserManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private BankEmployeeRepository bankEmployeeRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private BankAuthService bankAuthService;

    private UserManagementServiceImpl service;

    private SecurityUserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        service = new UserManagementServiceImpl(bankEmployeeRepository, auditLogRepository, bankAuthService);
        testPrincipal = new SecurityUserPrincipal(
                UUID.randomUUID(),
                "admin@cibeg.com",
                "Mohamed Ali",
                UserRole.ROLE_BACK_OFFICE
        );
    }

    @Test
    void listUsers_paginationAndSorting() {
        BankEmployee e1 = new BankEmployee("Amr Diab", "amr@cibeg.com", "amr.diab", "hash", "Operations", "bank-operations");
        BankEmployee e2 = new BankEmployee("Bassem Youssef", "bassem@cibeg.com", "bassem.y", "hash", "Finance", "bank-finance");
        when(bankEmployeeRepository.findAll()).thenReturn(List.of(e2, e1));

        PageResponse<BankUserSummaryDto> page = service.listUsers(null, null, null, 0, 10);

        assertEquals(2, page.total());
        assertEquals(2, page.data().size());
        assertEquals("Amr Diab", page.data().get(0).name());
        assertEquals("Bassem Youssef", page.data().get(1).name());
    }

    @Test
    void listUsers_searchMatching() {
        BankEmployee e1 = new BankEmployee("Karim Adel", "karim@cibeg.com", "karim.adel", "hash", "Operations", "bank-operations");
        BankEmployee e2 = new BankEmployee("Tarek Nour", "tarek@cibeg.com", "tarek.nour", "hash", "Finance", "bank-finance");
        when(bankEmployeeRepository.findAll()).thenReturn(List.of(e1, e2));

        PageResponse<BankUserSummaryDto> page = service.listUsers("karim", null, null, 0, 10);

        assertEquals(1, page.total());
        assertEquals("Karim Adel", page.data().get(0).name());
    }

    @Test
    void getSummary_returnsActiveCountsForRoles() {
        BankEmployee e1 = new BankEmployee("Admin", "admin@cibeg.com", "admin", "hash", "IT", "bank-admin");
        BankEmployee e2 = new BankEmployee("Ops1", "ops1@cibeg.com", "ops1", "hash", "Ops", "bank-operations");
        BankEmployee e3 = new BankEmployee("Ops2", "ops2@cibeg.com", "ops2", "hash", "Ops", "bank-operations");
        e3.setStatus("Inactive");

        when(bankEmployeeRepository.findAll()).thenReturn(List.of(e1, e2, e3));

        Map<String, Long> summary = service.getSummary();

        assertEquals(1L, summary.get("Bank Admin"));
        assertEquals(1L, summary.get("Operations"));
        assertEquals(0L, summary.get("Finance"));
        assertEquals(0L, summary.get("Reconciliation"));
    }

    @Test
    void getUser_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(bankEmployeeRepository.findById(id)).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.getUser(id));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("user_not_found", ex.getCode());
    }

    @Test
    void createUser_autoGeneratesUsernameAndAudits() {
        CreateBankUserRequest req = new CreateBankUserRequest("Hossam Hassan", "hossam@cibeg.com", null, "bank-operations", null);
        when(bankEmployeeRepository.findByEmail("hossam@cibeg.com")).thenReturn(Optional.empty());
        when(bankEmployeeRepository.findByEmployeeId("hossam.hassan")).thenReturn(Optional.empty());
        when(bankEmployeeRepository.save(any(BankEmployee.class))).thenAnswer(invocation -> {
            BankEmployee emp = invocation.getArgument(0);
            emp.setId(UUID.randomUUID());
            return emp;
        });

        BankUserSummaryDto created = service.createUser(req, testPrincipal);

        assertNotNull(created.id());
        assertEquals("Hossam Hassan", created.name());
        assertEquals("hossam.hassan", created.username());
        assertEquals("Operations", created.department()); // defaulted from role
        assertEquals("Active", created.status());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertEquals("USER_CREATED", audit.getAction());
        assertEquals("User", audit.getEntity());
        assertEquals("INFO", audit.getSeverity());
        assertEquals("admin@cibeg.com", audit.getUserName());
    }

    @Test
    void createUser_validationErrors() {
        // Missing name
        assertThrows(AuthException.class, () ->
                service.createUser(new CreateBankUserRequest("", "valid@cibeg.com", null, null, null), testPrincipal));

        // Invalid email
        assertThrows(AuthException.class, () ->
                service.createUser(new CreateBankUserRequest("Name", "invalidemail", null, null, null), testPrincipal));

        // Duplicate email
        when(bankEmployeeRepository.findByEmail("dup@cibeg.com"))
                .thenReturn(Optional.of(new BankEmployee("Dup", "dup@cibeg.com", "dup", "hash", "Ops")));
        assertThrows(AuthException.class, () ->
                service.createUser(new CreateBankUserRequest("Name", "dup@cibeg.com", null, null, null), testPrincipal));
    }

    @Test
    void updateUser_successAndAudits() {
        UUID id = UUID.randomUUID();
        BankEmployee emp = new BankEmployee("Old Name", "old@cibeg.com", "old.username", "hash", "Ops", "bank-operations");
        emp.setId(id);
        when(bankEmployeeRepository.findById(id)).thenReturn(Optional.of(emp));
        when(bankEmployeeRepository.save(any(BankEmployee.class))).thenAnswer(i -> i.getArgument(0));

        UpdateBankUserRequest updateReq = new UpdateBankUserRequest("New Name", null, null, "bank-finance", "Finance Dept");
        BankUserSummaryDto updated = service.updateUser(id, updateReq, testPrincipal);

        assertEquals("New Name", updated.name());
        assertEquals("Finance", updated.role());
        assertEquals("Finance Dept", updated.department());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertEquals("USER_UPDATED", audit.getAction());
        assertEquals("User", audit.getEntity());
    }

    @Test
    void deactivateAndActivateUser_auditsCorrectly() {
        UUID id = UUID.randomUUID();
        BankEmployee emp = new BankEmployee("User", "u@cibeg.com", "u.name", "hash", "Ops", "bank-operations");
        emp.setId(id);
        when(bankEmployeeRepository.findById(id)).thenReturn(Optional.of(emp));
        when(bankEmployeeRepository.save(any(BankEmployee.class))).thenAnswer(i -> i.getArgument(0));

        // Deactivate
        UserStatusResponse deactResponse = service.deactivateUser(id, testPrincipal);
        assertEquals("Inactive", deactResponse.status());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog deactAudit = auditCaptor.getValue();
        assertEquals("USER_DEACTIVATED", deactAudit.getAction());
        assertEquals("WARNING", deactAudit.getSeverity());
        assertEquals("Active", deactAudit.getPrevValue());
        assertEquals("Inactive", deactAudit.getNewValue());

        // Activate
        UserStatusResponse actResponse = service.activateUser(id, testPrincipal);
        assertEquals("Active", actResponse.status());

        verify(auditLogRepository, times(2)).save(auditCaptor.capture());
        AuditLog actAudit = auditCaptor.getValue();
        assertEquals("USER_ACTIVATED", actAudit.getAction());
        assertEquals("INFO", actAudit.getSeverity());
        assertEquals("Inactive", actAudit.getPrevValue());
        assertEquals("Active", actAudit.getNewValue());
    }

    @Test
    void triggerPasswordReset_delegatesAndAuditsAud012() {
        UUID id = UUID.randomUUID();
        BankEmployee emp = new BankEmployee("User", "u@cibeg.com", "u.name", "hash", "Ops", "bank-operations");
        emp.setId(id);
        when(bankEmployeeRepository.findById(id)).thenReturn(Optional.of(emp));

        MessageResponse response = service.triggerPasswordReset(id, testPrincipal);

        assertEquals("Password reset email sent", response.message());
        verify(bankAuthService).forgotPassword(any(ForgotPasswordRequest.class));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertEquals("USER_PASSWORD_RESET_TRIGGERED", audit.getAction());
        assertEquals("WARNING", audit.getSeverity());
        assertTrue(audit.getTargetResource().contains("AUD-012"));
    }
}
