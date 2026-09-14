package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MockBankErrorResponse(
        ErrorDetail error,
        String request_id
) {
    public record ErrorDetail(
            String code,
            String message,
            Map<String, Object> details
    ) {}

    public static MockBankErrorResponse of(String code, String message, Map<String, Object> details) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new MockBankErrorResponse(new ErrorDetail(code, message, details != null ? details : Map.of()), requestId);
    }

    public static MockBankErrorResponse of(String code, String message) {
        return of(code, message, Map.of());
    }
}
