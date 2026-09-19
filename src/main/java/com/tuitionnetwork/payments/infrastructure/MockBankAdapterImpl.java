package com.tuitionnetwork.payments.infrastructure;

import com.tuitionnetwork.epp.infrastructure.MockBankEppClient;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import com.tuitionnetwork.payments.spi.EppPlanResponse;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import com.tuitionnetwork.settings.service.SettingsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockBankAdapterImpl implements BankGatewayAdapterInterface {

    private final SettingsService settingsService;
    private final MockBankEppClient mockBankEppClient;

    public MockBankAdapterImpl(SettingsService settingsService, MockBankEppClient mockBankEppClient) {
        this.settingsService = settingsService;
        this.mockBankEppClient = mockBankEppClient;
    }

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
        MockBankEppClient.EppPlanResponse planResponse = mockBankEppClient.createPlan(UUID.randomUUID().toString(), tenorMonths);
        BigDecimal totalRepayment = new BigDecimal(planResponse.total_repayment());
        BigDecimal interestAmount = totalRepayment.subtract(principal);
        
        return new EppPlanResponse(
                principal,
                planResponse.tenor_months(),
                BigDecimal.ZERO,
                interestAmount,
                BigDecimal.ZERO,
                totalRepayment,
                new BigDecimal(planResponse.monthly_installment())
        );
    }
}
