package com.tuitionnetwork.common.exceptions;

public class PendingBusinessRuleException extends RuntimeException {

    public PendingBusinessRuleException(String message) {
        super(message);
    }

    public PendingBusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
