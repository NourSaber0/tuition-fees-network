package com.tuitionnetwork.audit.service;

import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogService {
    Page<AuditLogDto> search(UUID actorId, String actorType, String action, LocalDateTime from, LocalDateTime to, Pageable pageable);
    AuditLogDto getById(UUID id);
    AuditLogStatsDto stats(LocalDateTime from, LocalDateTime to);
    List<String> roles();
    byte[] export(UUID actorId, String actorType, String action, LocalDateTime from, LocalDateTime to);
}
