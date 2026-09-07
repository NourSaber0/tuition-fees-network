package com.tuitionnetwork.identity;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.dto.InstitutionDetailDto;
import com.tuitionnetwork.identity.dto.InstitutionSummaryDto;
import com.tuitionnetwork.identity.dto.RegisterInstitutionRequest;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.service.InstitutionManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstitutionManagementServiceImplTest {

    private InstitutionRepository institutionRepository;
    private InstitutionManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        institutionRepository = mock(InstitutionRepository.class);
        service = new InstitutionManagementServiceImpl(institutionRepository);
        // save() echoes its argument back
        when(institutionRepository.save(any(Institution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private RegisterInstitutionRequest sampleRequest() {
        return new RegisterInstitutionRequest(
                "  Nile International School  ",
                InstitutionType.SCHOOL,
                "International",
                "Cairo",
                "Dr. Ahmad Fawzy",
                "+20 2 2516 0000",
                "Admin@CIS.edu.eg",
                "MOEDU-SCH-2026-0831",
                850,
                null,
                null);
    }

    private Institution existing(RegistrationStatus regStatus, AccountStatus accountStatus) {
        Institution i = new Institution();
        i.setId(UUID.randomUUID());
        i.setName("Existing School");
        i.setCode("SCH-042");
        i.setInstitutionType(InstitutionType.SCHOOL);
        i.setRegistrationNumber("MOEDU-SCH-2024-0042");
        i.setRegistrationStatus(regStatus);
        i.setAccountStatus(accountStatus);
        return i;
    }

    @Test
    void register_startsPendingInactive_trimsInput_andGeneratesCode() {
        when(institutionRepository.findByRegistrationNumber("MOEDU-SCH-2026-0831")).thenReturn(Optional.empty());
        when(institutionRepository.countByInstitutionType(InstitutionType.SCHOOL)).thenReturn(0L);
        when(institutionRepository.findByCode(anyString())).thenReturn(Optional.empty());

        InstitutionDetailDto dto = service.register(sampleRequest());

        assertEquals("Nile International School", dto.name());
        assertEquals("admin@cis.edu.eg", dto.email());
        assertEquals("SCH-001", dto.code());
        assertEquals(RegistrationStatus.PENDING, dto.registrationStatus());
        assertEquals(AccountStatus.INACTIVE, dto.accountStatus());
    }

    @Test
    void register_duplicateRegistrationNumber_conflict() {
        when(institutionRepository.findByRegistrationNumber("MOEDU-SCH-2026-0831"))
                .thenReturn(Optional.of(existing(RegistrationStatus.APPROVED, AccountStatus.ACTIVE)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.register(sampleRequest()));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void register_universityGeneratesUniPrefixedCode() {
        RegisterInstitutionRequest uni = new RegisterInstitutionRequest(
                "Cairo University", InstitutionType.UNIVERSITY, "Public", "Giza",
                "Prof. Mohamed El-Khatib", "+20 2 3567 8000", "finance@cu.edu.eg",
                "MOHE-UNI-2026-0101", 18400, null, null);
        when(institutionRepository.findByRegistrationNumber(anyString())).thenReturn(Optional.empty());
        when(institutionRepository.countByInstitutionType(InstitutionType.UNIVERSITY)).thenReturn(4L);
        when(institutionRepository.findByCode(anyString())).thenReturn(Optional.empty());

        assertEquals("UNI-005", service.register(uni).code());
    }

    @Test
    void approve_fromPending_becomesApprovedAndActive_clearsRejectionReason() {
        Institution inst = existing(RegistrationStatus.PENDING, AccountStatus.INACTIVE);
        inst.setRejectionReason("stale reason");
        when(institutionRepository.findById(inst.getId())).thenReturn(Optional.of(inst));

        InstitutionDetailDto dto = service.approve(inst.getId());

        assertEquals(RegistrationStatus.APPROVED, dto.registrationStatus());
        assertEquals(AccountStatus.ACTIVE, dto.accountStatus());
        assertNull(dto.rejectionReason());
    }

    @Test
    void approve_whenAlreadyApproved_conflict() {
        Institution inst = existing(RegistrationStatus.APPROVED, AccountStatus.ACTIVE);
        when(institutionRepository.findById(inst.getId())).thenReturn(Optional.of(inst));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approve(inst.getId()));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void reject_blankReason_badRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reject(UUID.randomUUID(), "   "));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void reject_fromUnderReview_becomesRejected_withReason() {
        Institution inst = existing(RegistrationStatus.UNDER_REVIEW, AccountStatus.INACTIVE);
        when(institutionRepository.findById(inst.getId())).thenReturn(Optional.of(inst));

        InstitutionDetailDto dto = service.reject(inst.getId(), "  Incomplete documentation  ");

        assertEquals(RegistrationStatus.REJECTED, dto.registrationStatus());
        assertEquals(AccountStatus.INACTIVE, dto.accountStatus());
        assertEquals("Incomplete documentation", dto.rejectionReason());
    }

    @Test
    void reject_whenAlreadyApproved_conflict() {
        Institution inst = existing(RegistrationStatus.APPROVED, AccountStatus.ACTIVE);
        when(institutionRepository.findById(inst.getId())).thenReturn(Optional.of(inst));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reject(inst.getId(), "too late"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void activate_requiresApprovedRegistration() {
        Institution inst = existing(RegistrationStatus.PENDING, AccountStatus.INACTIVE);
        when(institutionRepository.findById(inst.getId())).thenReturn(Optional.of(inst));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.activate(inst.getId()));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void activate_thenDeactivate_flipsAccountStatus() {
        Institution inst = existing(RegistrationStatus.APPROVED, AccountStatus.INACTIVE);
        when(institutionRepository.findById(inst.getId())).thenReturn(Optional.of(inst));

        assertEquals(AccountStatus.ACTIVE, service.activate(inst.getId()).accountStatus());
        assertEquals(AccountStatus.INACTIVE, service.deactivate(inst.getId()).accountStatus());
    }

    @Test
    void get_unknownId_notFound() {
        UUID id = UUID.randomUUID();
        when(institutionRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.get(id));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void list_wrapsRepositoryPageIntoPageResponse() {
        Institution inst = existing(RegistrationStatus.APPROVED, AccountStatus.ACTIVE);
        when(institutionRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(inst), PageRequest.of(0, 25), 1));

        PageResponse<InstitutionSummaryDto> page = service.list(null, null, null, null, 0, 25);

        assertEquals(1, page.data().size());
        assertEquals(1, page.total());
        assertEquals(0, page.page());
        assertEquals("SCH-042", page.data().get(0).code());
    }
}
