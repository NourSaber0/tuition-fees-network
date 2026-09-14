package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record EppQuoteResponse(
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("quotes") List<QuoteItem> quotes
) {
    public record QuoteItem(
            @JsonProperty("tenor_months") int tenorMonths,
            @JsonProperty("annual_rate") BigDecimal annualRate,
            @JsonProperty("interest_amount") BigDecimal interestAmount,
            @JsonProperty("admin_fee") BigDecimal adminFee,
            @JsonProperty("total_payable") BigDecimal totalPayable,
            @JsonProperty("monthly_installment") BigDecimal monthlyInstallment
    ) {}
}
