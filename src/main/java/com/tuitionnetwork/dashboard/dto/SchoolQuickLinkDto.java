package com.tuitionnetwork.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchoolQuickLinkDto(
        String id,
        String label,
        String target,
        Integer badgeCount
) {}
