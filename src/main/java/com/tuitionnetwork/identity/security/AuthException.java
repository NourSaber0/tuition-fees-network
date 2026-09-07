package com.tuitionnetwork.identity.security;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object details;

    public AuthException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public AuthException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details != null ? details : Map.of();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
