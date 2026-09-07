package com.tuitionnetwork.audit;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.audit.service.AuditLogServiceImpl;
import com.tuitionnetwork.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogServiceImplTest {

    private AuditLogRepository repository;
    private AuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogServiceImpl(repository);
    }

    private AuditLog log(String actorType, String action, String target, String severity, LocalDateTime ts) {
        AuditLog a = new AuditLog(UUID.randomUUID(), actorType, action, target, severity);
        a.setAuditId(UUID.randomUUID());
        a.setTimestamp(ts != null ? ts : LocalDateTime.now());
        a.setEntity("Transaction");
        a.setEntityId("TX-100");
        a.setUserName("admin@cibeg.com");
        a.setIpAddress("192.168.1.1");
        return a;
    }

    @Test
    void search_filtersBySearchKeyword() {
        AuditLog a1 = log("BANK_ADMIN", "RECON_APPROVE", "Batch-001", "INFO", LocalDateTime.now());
        AuditLog a2 = log("OPS_SUPERVISOR", "PAYMENT_CAPTURE", "TX-2026", "CRITICAL", LocalDateTime.now());
        when(repository.findAll()).thenReturn(List.of(a1, a2));

        PageResponse<AuditLogDto> resp = service.search("BATCH", null, null, null, null, 0, 25);

        assertEquals(1, resp.data().size());
        assertEquals("RECON_APPROVE", resp.data().get(0).getAction());
        assertEquals(1, resp.total());
    }

    @Test
    void search_filtersByRoleAndSeverity() {
        AuditLog a1 = log("BANK_ADMIN", "ACTION1", "T1", "CRITICAL", LocalDateTime.now());
        AuditLog a2 = log("OPS_SUPERVISOR", "ACTION2", "T2", "CRITICAL", LocalDateTime.now());
        AuditLog a3 = log("BANK_ADMIN", "ACTION3", "T3", "INFO", LocalDateTime.now());
        when(repository.findAll()).thenReturn(List.of(a1, a2, a3));

        PageResponse<AuditLogDto> resp = service.search(null, "BANK_ADMIN", "CRITICAL", null, null, 0, 10);

        assertEquals(1, resp.data().size());
        assertEquals("ACTION1", resp.data().get(0).getAction());
        assertEquals("critical", resp.data().get(0).getSeverity());
    }

    @Test
    void search_filtersByDateRange() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 12, 0);
        AuditLog a1 = log("BANK_ADMIN", "A1", "T1", "INFO", t1);
        AuditLog a2 = log("BANK_ADMIN", "A2", "T2", "INFO", t2);
        when(repository.findByTimestampBetween(any(), any())).thenReturn(List.of(a1, a2));

        PageResponse<AuditLogDto> resp = service.search(null, null, null, "2026-09-01", "2026-09-02", 0, 25);

        assertEquals(1, resp.data().size());
        assertEquals("A1", resp.data().get(0).getAction());
    }

    @Test
    void search_paginatesCorrectly() {
        AuditLog a1 = log("BANK_ADMIN", "A1", "T1", "INFO", LocalDateTime.now().minusMinutes(2));
        AuditLog a2 = log("BANK_ADMIN", "A2", "T2", "INFO", LocalDateTime.now().minusMinutes(1));
        when(repository.findAll()).thenReturn(List.of(a1, a2));

        PageResponse<AuditLogDto> p1 = service.search(null, null, null, null, null, 0, 1);
        assertEquals(1, p1.data().size());
        assertEquals(2, p1.total());
        assertEquals(2, p1.totalPages());

        PageResponse<AuditLogDto> p2 = service.search(null, null, null, null, null, 1, 1);
        assertEquals(1, p2.data().size());
    }

    @Test
    void getById_found_returnsDto() {
        UUID id = UUID.randomUUID();
        AuditLog a = log("BANK_ADMIN", "ACTION", "TARGET", "INFO", LocalDateTime.now());
        a.setAuditId(id);
        when(repository.findById(id)).thenReturn(Optional.of(a));

        AuditLogDto dto = service.getById(id);

        assertNotNull(dto);
        assertEquals(id, dto.getId());
        assertEquals("ACTION", dto.getAction());
        assertEquals("admin@cibeg.com", dto.getUser());
    }

    @Test
    void getById_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getById(id));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void stats_computesSeverityCounts() {
        AuditLog a1 = log("BANK_ADMIN", "A1", "T1", "CRITICAL", LocalDateTime.now());
        AuditLog a2 = log("BANK_ADMIN", "A2", "T2", "WARNING", LocalDateTime.now());
        AuditLog a3 = log("BANK_ADMIN", "A3", "T3", "INFO", LocalDateTime.now());
        AuditLog a4 = log("BANK_ADMIN", "A4", "T4", "INFO", LocalDateTime.now());
        when(repository.findAll()).thenReturn(List.of(a1, a2, a3, a4));

        AuditLogStatsDto stats = service.stats(null, null);

        assertEquals(4, stats.getTotal());
        assertEquals(1, stats.getCritical());
        assertEquals(1, stats.getWarning());
        assertEquals(2, stats.getInfo());
    }

    @Test
    void roles_returnsDistinctValuesPlusDefaults() {
        AuditLog a1 = log("CUSTOM_AUDITOR", "A1", "T1", "INFO", LocalDateTime.now());
        when(repository.findAll()).thenReturn(List.of(a1));

        List<String> roles = service.roles();

        assertTrue(roles.contains("CUSTOM_AUDITOR"));
        assertTrue(roles.contains("BANK_ADMIN"));
        assertTrue(roles.contains("OPS_SUPERVISOR"));
    }

    @Test
    void export_generatesValidCsv() {
        AuditLog a1 = log("BANK_ADMIN", "RECON_EXPORT", "Target", "INFO", LocalDateTime.now());
        when(repository.findAll()).thenReturn(List.of(a1));

        byte[] csv = service.export(null, null, null, null, null);

        String content = new String(csv, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("id,timestamp,user,role,action,entity,entityId,severity,ipAddress"));
        assertTrue(content.contains("RECON_EXPORT"));
        assertTrue(content.contains("admin@cibeg.com"));
    }
}
