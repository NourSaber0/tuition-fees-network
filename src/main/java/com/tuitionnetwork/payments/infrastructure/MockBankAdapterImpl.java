package com.tuitionnetwork.payments.infrastructure;

import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import com.tuitionnetwork.payments.spi.EppPlanResponse;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Component
public class MockBankAdapterImpl implements BankGatewayAdapterInterface {

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.14");
    private static final BigDecimal ADMIN_FEE_RATE = new BigDecimal("0.01");
    private static final BigDecimal ADMIN_FEE_CAP = new BigDecimal("500.00");

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
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal must be greater than zero");
        }

        BigDecimal scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualInterestRate;
        BigDecimal interestAmount;
        BigDecimal adminFee;

        if (tenorMonths == 3) {
            annualInterestRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            interestAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            adminFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            annualInterestRate = ANNUAL_RATE.setScale(4, RoundingMode.HALF_UP);
            interestAmount = scaledPrincipal.multiply(ANNUAL_RATE)
                    .multiply(BigDecimal.valueOf(tenorMonths))
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal calculatedAdminFee = scaledPrincipal.multiply(ADMIN_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            adminFee = calculatedAdminFee.min(ADMIN_FEE_CAP);
        }

        BigDecimal totalPayable = scaledPrincipal.add(interestAmount).add(adminFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyInstalment = totalPayable.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);

        return new EppPlanResponse(
                scaledPrincipal,
                tenorMonths,
                annualInterestRate,
                interestAmount,
                adminFee,
                totalPayable,
                monthlyInstalment
        );
    }
}
