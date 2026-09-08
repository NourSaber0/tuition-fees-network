package com.tuitionnetwork.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.settings.domain.FeeTypeSetting;
import com.tuitionnetwork.settings.domain.SystemSetting;
import com.tuitionnetwork.settings.dto.CreateFeeTypeRequest;
import com.tuitionnetwork.settings.dto.EppSettingsDto;
import com.tuitionnetwork.settings.dto.FeeTypeSettingDto;
import com.tuitionnetwork.settings.dto.InstitutionSettingsDto;
import com.tuitionnetwork.settings.dto.NotificationSettingsDto;
import com.tuitionnetwork.settings.dto.UpdateFeeTypeRequest;
import com.tuitionnetwork.settings.repository.FeeTypeSettingRepository;
import com.tuitionnetwork.settings.repository.SystemSettingRepository;
import com.tuitionnetwork.settings.service.SettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceImplTest {

    private SystemSettingRepository systemSettingRepository;
    private FeeTypeSettingRepository feeTypeSettingRepository;
    private AuditLogRepository auditLogRepository;
    private SettingsServiceImpl service;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        systemSettingRepository = mock(SystemSettingRepository.class);
        feeTypeSettingRepository = mock(FeeTypeSettingRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        service = new SettingsServiceImpl(systemSettingRepository, feeTypeSettingRepository,
                auditLogRepository, mapper);

        when(systemSettingRepository.save(any(SystemSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(feeTypeSettingRepository.save(any(FeeTypeSetting.class))).thenAnswer(inv -> {
            FeeTypeSetting f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
    }

    // ── Fee types ─────────────────────────────────────────────────────────────

    @Test
    void getFeeTypes_seedsDefaultsWhenEmpty() {
        when(feeTypeSettingRepository.count()).thenReturn(0L);
        when(feeTypeSettingRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                new FeeTypeSetting("Tuition Fee", "TUITION", false, true)));

        List<FeeTypeSettingDto> result = service.getFeeTypes();

        verify(feeTypeSettingRepository).saveAll(any());
        assertEquals(1, result.size());
    }

    @Test
    void getFeeTypes_doesNotReseedWhenPopulated() {
        when(feeTypeSettingRepository.count()).thenReturn(7L);
        when(feeTypeSettingRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        service.getFeeTypes();

        verify(feeTypeSettingRepository, never()).saveAll(any());
    }

    @Test
    void createFeeType_duplicateCode_conflict() {
        when(feeTypeSettingRepository.findByCodeIgnoreCase("LAB"))
                .thenReturn(Optional.of(new FeeTypeSetting("Lab", "LAB", false, true)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFeeType(new CreateFeeTypeRequest("Lab Fee", "lab", true, null)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void createFeeType_persistsUppercasedCode_andAudits() {
        when(feeTypeSettingRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());

        FeeTypeSettingDto dto = service.createFeeType(new CreateFeeTypeRequest("  Meals  ", "meals", true, null));

        assertEquals("MEALS", dto.code());
        assertEquals("Meals", dto.name());
        assertTrue(dto.active());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void updateFeeType_unknownId_notFound() {
        UUID id = UUID.randomUUID();
        when(feeTypeSettingRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.updateFeeType(id, new UpdateFeeTypeRequest(null, null, null, false)));
    }

    @Test
    void updateFeeType_appliesOnlyProvidedFields() {
        FeeTypeSetting f = new FeeTypeSetting("Book Fee", "BOOKS", false, true);
        f.setId(UUID.randomUUID());
        when(feeTypeSettingRepository.findById(f.getId())).thenReturn(Optional.of(f));

        FeeTypeSettingDto dto = service.updateFeeType(f.getId(),
                new UpdateFeeTypeRequest(null, null, true, false));

        assertEquals("BOOKS", dto.code());       // unchanged
        assertEquals("Book Fee", dto.name());    // unchanged
        assertTrue(dto.taxable());
        assertFalse(dto.active());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void updateFeeType_codeCollisionWithAnotherRow_conflict() {
        FeeTypeSetting target = new FeeTypeSetting("Book Fee", "BOOKS", false, true);
        target.setId(UUID.randomUUID());
        FeeTypeSetting other = new FeeTypeSetting("Uniform", "UNIFORM", false, true);
        other.setId(UUID.randomUUID());

        when(feeTypeSettingRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(feeTypeSettingRepository.findByCodeIgnoreCase("UNIFORM")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateFeeType(target.getId(),
                        new UpdateFeeTypeRequest(null, "uniform", null, null)));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── Payment statuses ─────────────────────────────────────────────────────

    @Test
    void getPaymentStatuses_returnsReferenceList() {
        var statuses = service.getPaymentStatuses();
        assertEquals(6, statuses.size());
        assertTrue(statuses.stream().anyMatch(s -> s.status().equals("Pending") && !s.terminal()));
        assertTrue(statuses.stream().anyMatch(s -> s.status().equals("Successful") && s.terminal()));
    }

    // ── EPP configuration ───────────────────────────────────────────────────

    @Test
    void getEpp_absent_returnsDefaultsAndPersists() {
        when(systemSettingRepository.findBySettingKey("epp")).thenReturn(Optional.empty());

        EppSettingsDto dto = service.getEpp();

        assertEquals(5_000L, dto.minAmountEGP());
        assertEquals(2, dto.maxPlansPerStudent());
        verify(systemSettingRepository).save(any(SystemSetting.class));
    }

    @Test
    void getEpp_present_readsStoredValue() throws Exception {
        Map<Integer, Boolean> tenors = new LinkedHashMap<>();
        tenors.put(6, true);
        Map<Integer, Integer> rates = new LinkedHashMap<>();
        rates.put(6, 9);
        EppSettingsDto stored = new EppSettingsDto(tenors, 1000L, 250000L, rates, 0.5, 400L, true, 3);
        when(systemSettingRepository.findBySettingKey("epp"))
                .thenReturn(Optional.of(new SystemSetting("epp", mapper.writeValueAsString(stored))));

        EppSettingsDto dto = service.getEpp();
        assertEquals(250000L, dto.maxAmountEGP());
        assertEquals(3, dto.maxPlansPerStudent());
        assertTrue(dto.requireApproval());
    }

    @Test
    void updateEpp_maxNotGreaterThanMin_badRequest() {
        EppSettingsDto bad = withDefaultsBut(d -> new EppSettingsDto(
                d.tenors(), 10_000L, 10_000L, d.interestRatePct(),
                d.adminFeeRatePct(), d.adminFeeCapEGP(), d.requireApproval(), d.maxPlansPerStudent()));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.updateEpp(bad));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateEpp_rateOutOfRange_badRequest() {
        EppSettingsDto d = EppSettingsDto.defaults();
        d.interestRatePct().put(12, 150);
        assertThrows(ResponseStatusException.class, () -> service.updateEpp(d));
    }

    @Test
    void updateEpp_noTenorEnabled_badRequest() {
        EppSettingsDto d = EppSettingsDto.defaults();
        d.tenors().replaceAll((k, v) -> false);
        assertThrows(ResponseStatusException.class, () -> service.updateEpp(d));
    }

    @Test
    void updateEpp_valid_persistsAndAudits() {
        service.updateEpp(EppSettingsDto.defaults());
        verify(systemSettingRepository).save(any(SystemSetting.class));
        org.mockito.ArgumentCaptor<AuditLog> captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("CRITICAL", captor.getValue().getSeverity());
    }

    // ── Notification / institution settings ─────────────────────────────────

    @Test
    void getNotifications_absent_returnsDefaults() {
        when(systemSettingRepository.findBySettingKey("notifications")).thenReturn(Optional.empty());
        NotificationSettingsDto dto = service.getNotifications();
        assertTrue(dto.channels().email());
        assertFalse(dto.channels().sms());
    }

    @Test
    void updateNotifications_nullSection_badRequest() {
        NotificationSettingsDto bad = new NotificationSettingsDto(null, null);
        assertThrows(ResponseStatusException.class, () -> service.updateNotifications(bad));
    }

    @Test
    void updateNotifications_valid_persistsAndAuditsCritical() {
        service.updateNotifications(NotificationSettingsDto.defaults());
        org.mockito.ArgumentCaptor<AuditLog> captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("CRITICAL", captor.getValue().getSeverity());
    }

    @Test
    void getInstitutionSettings_absent_returnsDefaults() {
        when(systemSettingRepository.findBySettingKey("institutions")).thenReturn(Optional.empty());
        InstitutionSettingsDto dto = service.getInstitutionSettings();
        assertTrue(dto.requireDualApproval());
        assertEquals(5_000, dto.maxStudentsPerUpload());
    }

    @Test
    void updateInstitutionSettings_noFormatAllowed_badRequest() {
        InstitutionSettingsDto bad = new InstitutionSettingsDto(
                true, false, true, 100, 12,
                new InstitutionSettingsDto.UploadFormats(false, false, false));
        assertThrows(ResponseStatusException.class, () -> service.updateInstitutionSettings(bad));
    }

    @Test
    void updateInstitutionSettings_valid_persistsAndAudits() {
        service.updateInstitutionSettings(InstitutionSettingsDto.defaults());
        verify(systemSettingRepository).save(any(SystemSetting.class));
        org.mockito.ArgumentCaptor<AuditLog> captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("CRITICAL", captor.getValue().getSeverity());
    }

    private EppSettingsDto withDefaultsBut(java.util.function.Function<EppSettingsDto, EppSettingsDto> fn) {
        return fn.apply(EppSettingsDto.defaults());
    }
}
