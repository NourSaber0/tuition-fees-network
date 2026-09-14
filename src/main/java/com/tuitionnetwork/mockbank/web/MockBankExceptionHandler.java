package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.dto.MockBankErrorResponse;
import com.tuitionnetwork.mockbank.dto.MockBankException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.tuitionnetwork.mockbank")
public class MockBankExceptionHandler {

    @ExceptionHandler(MockBankException.class)
    public ResponseEntity<MockBankErrorResponse> handleMockBankException(MockBankException ex) {
        MockBankErrorResponse response = MockBankErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MockBankErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fields.put(error.getField(), error.getDefaultMessage())
        );
        MockBankErrorResponse response = MockBankErrorResponse.of(
                "VALIDATION_ERROR",
                "A field is missing or invalid",
                Map.of("fields", fields)
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }
}
