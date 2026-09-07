package com.tuitionnetwork.payments.infrastructure;

import com.tuitionnetwork.payments.domain.EppPricing;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import com.tuitionnetwork.payments.spi.EppPlanResponse;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockBankAdapterImpl implements BankGatewayAdapterInterface {

    @Override
    public GatewayResponse chargeCard(BigDecimal amount, String idempotencyKey) {
        String authCode = "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String txnRef = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return new GatewayResponse(PaymentStatus.CAPTURED, authCode, txnRef, "00", "Approved");
    }

    @Override
    public boolean verifyCibAccount(String accountNumber) {
        return accountNumber != null && !accountNumber.trim().isEmpty();
    }

    @Override
    public EppPlanResponse generateEppSchedule(BigDecimal principal, int tenorMonths) {
        EppPricing.Quote quote = EppPricing.calculate(principal, tenorMonths);

        return new EppPlanResponse(
                quote.principal(),
                quote.tenorMonths(),
                quote.annualInterestRate(),
                quote.interestAmount(),
                quote.adminFee(),
                quote.totalPayable(),
                quote.monthlyInstalment()
        );
    }
}
