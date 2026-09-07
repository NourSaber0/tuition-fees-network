package com.tuitionnetwork.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record ApiErrorResponse(ApiError error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, Object details) {}

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message, Map.of()));
    }

    public static ApiErrorResponse of(String code, String message, Object details) {
        return new ApiErrorResponse(new ApiError(code, message, details != null ? details : Map.of()));
    }
}
