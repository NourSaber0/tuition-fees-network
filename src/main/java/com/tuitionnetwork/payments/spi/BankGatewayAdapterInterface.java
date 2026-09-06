package com.tuitionnetwork.payments.spi;

import java.math.BigDecimal;

public interface BankGatewayAdapterInterface {

    GatewayResponse chargeCard(BigDecimal amount, String idempotencyKey);

    boolean verifyCibAccount(String accountNumber);

    EppPlanResponse generateEppSchedule(BigDecimal principal, int tenorMonths);
}
