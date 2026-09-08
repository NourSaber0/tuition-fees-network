package com.tuitionnetwork.audit.service;

import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.common.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface AuditLogService {

    PageResponse<AuditLogDto> search(String search, String role, String severity,
                                     String dateFrom, String dateTo, int page, int pageSize);

    AuditLogDto getById(UUID id);

    AuditLogStatsDto stats(String dateFrom, String dateTo);

    List<String> roles();

    byte[] export(String search, String role, String severity, String dateFrom, String dateTo);
}
