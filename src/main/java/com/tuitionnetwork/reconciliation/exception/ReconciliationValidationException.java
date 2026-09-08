package com.tuitionnetwork.reconciliation.exception;

public class ReconciliationValidationException extends RuntimeException {
    private final String code;

    public ReconciliationValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
