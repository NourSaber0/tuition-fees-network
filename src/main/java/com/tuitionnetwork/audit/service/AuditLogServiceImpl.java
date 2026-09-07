package com.tuitionnetwork.audit.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Stream;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;

    @Autowired
    public AuditLogServiceImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResponse<AuditLogDto> search(String search, String role, String severity,
                                            String dateFrom, String dateTo, int page, int pageSize) {
        LocalDateTime from = parseDateFrom(dateFrom);
        LocalDateTime to = parseDateTo(dateTo);

        List<AuditLog> all = (from != null && to != null)
                ? repository.findByTimestampBetween(from, to)
                : repository.findAll();

        Stream<AuditLog> s = all.stream();
        if (from != null) s = s.filter(a -> a.getTimestamp() != null && !a.getTimestamp().isBefore(from));
        if (to != null) s = s.filter(a -> a.getTimestamp() != null && !a.getTimestamp().isAfter(to));
        if (role != null && !role.isBlank()) {
            s = s.filter(a -> a.getActorType() != null && a.getActorType().equalsIgnoreCase(role.trim()));
        }
        if (severity != null && !severity.isBlank()) {
            s = s.filter(a -> a.getSeverity() != null && a.getSeverity().equalsIgnoreCase(severity.trim()));
        }
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            s = s.filter(a -> matchesSearch(a, q));
        }

        List<AuditLogDto> dtos = s
                .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AuditLogDto::from)
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int total = dtos.size();
        int totalPages = (int) Math.ceil((double) total / safeSize);

        int start = safePage * safeSize;
        int end = Math.min(start + safeSize, total);
        List<AuditLogDto> pageList = start >= total ? Collections.emptyList() : dtos.subList(start, end);

        return new PageResponse<>(pageList, safePage, safeSize, total, totalPages);
    }

    @Override
    public AuditLogDto getById(UUID id) {
        return repository.findById(id)
                .map(AuditLogDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found: " + id));
    }

    @Override
    public AuditLogStatsDto stats(String dateFrom, String dateTo) {
        LocalDateTime from = parseDateFrom(dateFrom);
        LocalDateTime to = parseDateTo(dateTo);

        List<AuditLog> list = (from != null && to != null)
                ? repository.findByTimestampBetween(from, to)
                : repository.findAll();

        Stream<AuditLog> s = list.stream();
        if (from != null) s = s.filter(a -> a.getTimestamp() != null && !a.getTimestamp().isBefore(from));
        if (to != null) s = s.filter(a -> a.getTimestamp() != null && !a.getTimestamp().isAfter(to));
        List<AuditLog> filtered = s.toList();

        long total = filtered.size();
        long critical = filtered.stream().filter(a -> "critical".equalsIgnoreCase(a.getSeverity())).count();
        long warning = filtered.stream().filter(a -> "warning".equalsIgnoreCase(a.getSeverity())).count();
        long info = filtered.stream().filter(a -> a.getSeverity() == null || "info".equalsIgnoreCase(a.getSeverity())).count();

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        bySeverity.put("CRITICAL", critical);
        bySeverity.put("WARNING", warning);
        bySeverity.put("INFO", info);

        return new AuditLogStatsDto(total, critical, warning, info, bySeverity);
    }

    @Override
    public List<String> roles() {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(List.of("BANK_ADMIN", "OPS_SUPERVISOR", "RECON_OFFICER", "SUPPORT_AGENT", "BANK_EMPLOYEE", "INSTITUTION_ADMIN", "SYSTEM"));
        repository.findAll().stream()
                .map(AuditLog::getActorType)
                .filter(Objects::nonNull)
                .filter(r -> !r.isBlank())
                .forEach(set::add);
        return new ArrayList<>(set);
    }

    @Override
    public byte[] export(String search, String role, String severity, String dateFrom, String dateTo) {
        LocalDateTime from = parseDateFrom(dateFrom);
        LocalDateTime to = parseDateTo(dateTo);

        List<AuditLog> list = (from != null && to != null)
                ? repository.findByTimestampBetween(from, to)
                : repository.findAll();

        Stream<AuditLog> s = list.stream();
        if (from != null) s = s.filter(a -> a.getTimestamp() != null && !a.getTimestamp().isBefore(from));
        if (to != null) s = s.filter(a -> a.getTimestamp() != null && !a.getTimestamp().isAfter(to));
        if (role != null && !role.isBlank()) {
            s = s.filter(a -> a.getActorType() != null && a.getActorType().equalsIgnoreCase(role.trim()));
        }
        if (severity != null && !severity.isBlank()) {
            s = s.filter(a -> a.getSeverity() != null && a.getSeverity().equalsIgnoreCase(severity.trim()));
        }
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            s = s.filter(a -> matchesSearch(a, q));
        }

        List<AuditLogDto> filtered = s
                .sorted(Comparator.comparing(AuditLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AuditLogDto::from)
                .toList();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintWriter pw = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            pw.println("id,timestamp,user,role,action,entity,entityId,severity,ipAddress");
            for (AuditLogDto a : filtered) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        a.getId(),
                        a.getTimestamp(),
                        escapeCsv(a.getUser()),
                        escapeCsv(a.getRole()),
                        escapeCsv(a.getAction()),
                        escapeCsv(a.getEntity()),
                        escapeCsv(a.getEntityId()),
                        escapeCsv(a.getSeverity()),
                        escapeCsv(a.getIpAddress()));
            }
            pw.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export audit logs CSV", e);
        }
    }

    private boolean matchesSearch(AuditLog a, String q) {
        if (a.getUserName() != null && a.getUserName().toLowerCase().contains(q)) return true;
        if (a.getActorId() != null && a.getActorId().toString().toLowerCase().contains(q)) return true;
        if (a.getAction() != null && a.getAction().toLowerCase().contains(q)) return true;
        if (a.getEntity() != null && a.getEntity().toLowerCase().contains(q)) return true;
        if (a.getEntityId() != null && a.getEntityId().toLowerCase().contains(q)) return true;
        if (a.getTargetResource() != null && a.getTargetResource().toLowerCase().contains(q)) return true;
        if (a.getActorType() != null && a.getActorType().toLowerCase().contains(q)) return true;
        return false;
    }

    private LocalDateTime parseDateFrom(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.contains("T")) return LocalDateTime.parse(dateStr);
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseDateTo(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.contains("T")) return LocalDateTime.parse(dateStr);
            return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
        } catch (Exception e) {
            return null;
        }
    }

    private String escapeCsv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
