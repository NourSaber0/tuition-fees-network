package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerLookupResponse(
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("national_id") String nationalId,
        @JsonProperty("full_name_en") String fullNameEn,
        @JsonProperty("full_name_ar") String fullNameAr,
        @JsonProperty("mobile") String mobile,
        @JsonProperty("status") String status,
        @JsonProperty("accounts") List<AccountDto> accounts,
        @JsonProperty("cards") List<CardDto> cards
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountDto(
            @JsonProperty("account_id") String accountId,
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("type") String type,
            @JsonProperty("currency") String currency,
            @JsonProperty("available_balance") BigDecimal availableBalance,
            @JsonProperty("status") String status
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CardDto(
            @JsonProperty("card_id") String cardId,
            @JsonProperty("masked_number") String maskedNumber,
            @JsonProperty("scheme") String scheme,
            @JsonProperty("type") String type,
            @JsonProperty("holder_name") String holderName,
            @JsonProperty("expiry") String expiry,
            @JsonProperty("status") String status,
            @JsonProperty("credit_limit") BigDecimal creditLimit,
            @JsonProperty("available_limit") BigDecimal availableLimit,
            @JsonProperty("linked_account_id") String linkedAccountId
    ) {}
}
