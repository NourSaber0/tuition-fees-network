package com.tuitionnetwork.payments.spi;

import com.tuitionnetwork.payments.dto.CibAccountDto;

import java.math.BigDecimal;
import java.util.List;

public interface BankGatewayAdapterInterface {

    GatewayResponse chargeCard(BigDecimal amount, String idempotencyKey);

    boolean verifyCibAccount(String accountNumber);

    /** The customer's CIB accounts, so a back-office operator can choose which one to debit. */
    List<CibAccountDto> listAccounts(String nationalId);

    EppPlanResponse generateEppSchedule(BigDecimal principal, int tenorMonths);
}
