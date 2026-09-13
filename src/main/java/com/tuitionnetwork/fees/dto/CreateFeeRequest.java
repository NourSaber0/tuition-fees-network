package com.tuitionnetwork.fees.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFeeRequest(
        @NotBlank(message = "studentId is required")
        String studentId,

        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "category is required")
        String category,

        @NotNull(message = "amountEGP is required")
        @DecimalMin(value = "0.01", message = "amountEGP must be greater than zero")
        BigDecimal amountEGP,

        String term,

        @NotNull(message = "due_date_required")
        LocalDate dueDate
) {
}
