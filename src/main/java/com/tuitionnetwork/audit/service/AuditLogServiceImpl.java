package com.tuitionnetwork.audit.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;

    @Autowired
    public AuditLogServiceImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<AuditLogDto> search(java.util.UUID actorId, String actorType, String action, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        // simple implementation: filter in memory for combinations not natively supported by repo
        List<AuditLog> all;
        if (from != null && to != null) {
            all = repository.findByTimestampBetween(from, to);
        } else {
            all = repository.findAll();
        }

        Stream<AuditLog> s = all.stream();
        if (actorId != null) s = s.filter(a -> Objects.equals(a.getActorId(), actorId));
        if (actorType != null) s = s.filter(a -> actorType.equals(a.getActorType()));
        if (action != null) s = s.filter(a -> action.equals(a.getAction()));

        List<AuditLogDto> dtos = s.map(this::toDto).collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtos.size());
        List<AuditLogDto> pageList = start > end ? Collections.emptyList() : dtos.subList(start, end);
        return new PageImpl<>(pageList, pageable, dtos.size());
    }

    @Override
    public AuditLogDto getById(UUID id) {
        return repository.findById(id).map(this::toDto).orElse(null);
    }

    @Override
    public AuditLogStatsDto stats(LocalDateTime from, LocalDateTime to) {
        List<AuditLog> list = (from != null && to != null) ? repository.findByTimestampBetween(from, to) : repository.findAll();
        Map<String, Long> bySeverity = list.stream().collect(Collectors.groupingBy(AuditLog::getSeverity, Collectors.counting()));
        return new AuditLogStatsDto(list.size(), bySeverity);
    }

    @Override
    public List<String> roles() {
        // derive distinct actorType values
        return repository.findAll().stream().map(AuditLog::getActorType).filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
    }

    @Override
    public byte[] export(UUID actorId, String actorType, String action, LocalDateTime from, LocalDateTime to) {
        List<AuditLog> list = (from != null && to != null) ? repository.findByTimestampBetween(from, to) : repository.findAll();
        Stream<AuditLog> s = list.stream();
        if (actorId != null) s = s.filter(a -> Objects.equals(a.getActorId(), actorId));
        if (actorType != null) s = s.filter(a -> actorType.equals(a.getActorType()));
        if (action != null) s = s.filter(a -> action.equals(a.getAction()));

        List<AuditLog> filtered = s.collect(Collectors.toList());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintWriter pw = new PrintWriter(baos)) {
            pw.println("auditId,actorId,actorType,action,targetResource,timestamp,severity");
            for (AuditLog a : filtered) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s\n",
                        a.getAuditId(),
                        a.getActorId(),
                        escapeCsv(a.getActorType()),
                        escapeCsv(a.getAction()),
                        escapeCsv(a.getTargetResource()),
                        a.getTimestamp(),
                        a.getSeverity());
            }
            pw.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to export CSV", e);
        }
    }

    private String escapeCsv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"" ) || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(a.getAuditId(), a.getActorId(), a.getActorType(), a.getAction(), a.getTargetResource(), a.getTimestamp(), a.getSeverity());
    }
}
