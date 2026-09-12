package com.tuitionnetwork.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchoolFeeUploadStatus(
        String lastUploadStatus,
        String lastUploadAt,
        boolean pendingResubmission
) {}
