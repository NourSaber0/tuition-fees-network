package com.tuitionnetwork.identity;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.identity.service.IdentityResolverServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityResolverServiceImplTest {

    private GuardianRepository guardianRepository;
    private StudentRepository studentRepository;
    private InstitutionRepository institutionRepository;
    private AuditLogRepository auditLogRepository;
    private IdentityResolverServiceImpl resolverService;

    private static final String SECRET_KEY = "test-secret-key-32-characters-minimum!";

    @BeforeEach
    void setUp() {
        guardianRepository = mock(GuardianRepository.class);
        studentRepository = mock(StudentRepository.class);
        institutionRepository = mock(InstitutionRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);

        resolverService = new IdentityResolverServiceImpl(
                guardianRepository,
                studentRepository,
                institutionRepository,
                auditLogRepository,
                SECRET_KEY
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void computeHmacSha256_producesDeterministicHash() {
        String nationalId = "29001011234567";
        String hash1 = resolverService.computeHmacSha256(nationalId);
        String hash2 = resolverService.computeHmacSha256(nationalId);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void resolveGuardianByNationalId_foundInDatabase_returnsMappedDtoAndLogsAudit() {
        String nationalId = "29001011234567";
        String hmac = resolverService.computeHmacSha256(nationalId);

        UUID bankEmployeeId = UUID.randomUUID();
        SecurityUserPrincipal bankEmployee = new SecurityUserPrincipal(
                bankEmployeeId,
                "employee@cibeg.com",
                "Ahmed Employee",
                UserRole.ROLE_BACK_OFFICE
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                bankEmployee,
                null,
                List.of(new SimpleGrantedAuthority(UserRole.ROLE_BACK_OFFICE))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID guardianId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Guardian guardian = new Guardian(hmac, "enc-data", "Ahmed Saber", "ahmed@example.com", "+201012345678", "hash", true);
        guardian.setId(guardianId);

        Student student = new Student(guardianId, instId, "student-hmac", "student-enc", "Sara Ahmed", LocalDate.of(2010, 5, 15));
        student.setId(studentId);

        Institution institution = new Institution("Nile International School", "NILE-001", "PAID_BY_INSTITUTION");
        institution.setId(instId);

        when(guardianRepository.findByNationalIdHash(hmac)).thenReturn(Optional.of(guardian));
        when(studentRepository.findByGuardianId(guardianId)).thenReturn(List.of(student));
        when(institutionRepository.findAll()).thenReturn(List.of(institution));

        Optional<ResolvedGuardianDto> result = resolverService.resolveGuardianByNationalId(nationalId);

        assertTrue(result.isPresent());
        assertEquals(guardianId, result.get().id());
        assertEquals("Ahmed Saber", result.get().fullName());
        assertEquals(hmac, result.get().nationalIdHmac());
        assertEquals("ahmed@example.com", result.get().email());
        assertEquals(1, result.get().students().size());
        assertEquals("Sara Ahmed", result.get().students().get(0).studentName());
        assertEquals("Nile International School", result.get().students().get(0).institutionName());

        // Verify AUDIT_LOG record
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog savedAudit = auditCaptor.getValue();
        assertNotNull(savedAudit);
        assertEquals(bankEmployeeId, savedAudit.getActorId());
        assertEquals("BANK_EMPLOYEE", savedAudit.getActorType());
        assertEquals("SEARCH_NATIONAL_ID", savedAudit.getAction());
        assertEquals(hmac, savedAudit.getTargetResource());
        assertFalse(savedAudit.getTargetResource().contains(nationalId), "Must never log plaintext National ID");
    }

    @Test
    void resolveGuardianByNationalId_notFoundInDatabase_returnsEmptyOptionalAndStillLogsAudit() {
        String nationalId = "99999999999999";
        String hmac = resolverService.computeHmacSha256(nationalId);

        UUID bankEmployeeId = UUID.randomUUID();
        SecurityUserPrincipal bankEmployee = new SecurityUserPrincipal(
                bankEmployeeId,
                "ops@cibeg.com",
                "Ops Specialist",
                UserRole.ROLE_BACK_OFFICE
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                bankEmployee,
                null,
                List.of(new SimpleGrantedAuthority(UserRole.ROLE_BACK_OFFICE))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(guardianRepository.findByNationalIdHash(hmac)).thenReturn(Optional.empty());

        Optional<ResolvedGuardianDto> result = resolverService.resolveGuardianByNationalId(nationalId);

        assertTrue(result.isEmpty(), "Must return empty optional and never fallback to mock data");

        // Verify AUDIT_LOG was still recorded for search attempt
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog savedAudit = auditCaptor.getValue();
        assertNotNull(savedAudit);
        assertEquals(bankEmployeeId, savedAudit.getActorId());
        assertEquals("BANK_EMPLOYEE", savedAudit.getActorType());
        assertEquals("SEARCH_NATIONAL_ID", savedAudit.getAction());
        assertEquals(hmac, savedAudit.getTargetResource());
    }

    @Test
    void computeHmacSha256_nullOrBlank_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> resolverService.computeHmacSha256(null));
        assertThrows(IllegalArgumentException.class, () -> resolverService.computeHmacSha256("   "));
    }
}
