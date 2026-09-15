package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EppPlanDetailResponse(
        @JsonProperty("plan_id") String planId,
        @JsonProperty("status") String status,
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("card_id") String cardId,
        @JsonProperty("customer") java.util.Map<String, String> customer,
        @JsonProperty("principal") BigDecimal principal,
        @JsonProperty("tenor_months") int tenorMonths,
        @JsonProperty("annual_rate") BigDecimal annualRate,
        @JsonProperty("interest_amount") BigDecimal interestAmount,
        @JsonProperty("admin_fee") BigDecimal adminFee,
        @JsonProperty("total_payable") BigDecimal totalPayable,
        @JsonProperty("monthly_installment") BigDecimal monthlyInstallment,
        @JsonProperty("first_due_date") LocalDate firstDueDate,
        @JsonProperty("last_due_date") LocalDate lastDueDate,
        @JsonProperty("schedule") List<InstallmentItem> schedule
) {
    public record InstallmentItem(
            @JsonProperty("number") int number,
            @JsonProperty("due_date") LocalDate dueDate,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("status") String status
    ) {}
}
