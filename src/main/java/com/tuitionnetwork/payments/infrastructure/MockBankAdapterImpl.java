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
    private final MockBankBackOfficeClient mockBankBackOfficeClient;

    public MockBankAdapterImpl(SettingsService settingsService, MockBankEppClient mockBankEppClient, MockBankBackOfficeClient mockBankBackOfficeClient) {
        this.settingsService = settingsService;
        this.mockBankEppClient = mockBankEppClient;
        this.mockBankBackOfficeClient = mockBankBackOfficeClient;
    }

    @Override
    public GatewayResponse chargeCard(BigDecimal amount, String idempotencyKey) {
        return mockBankBackOfficeClient.processCardPayment(
                "4111111111111111", // Dummy card number for mock registration
                "Frontend Portal User", 
                12, 2030, "123", 
                amount,
                "29805150101023", // Default mock national ID
                idempotencyKey
        );
    }

    @Override
    public boolean verifyCibAccount(String accountNumber) {
        return accountNumber != null && !accountNumber.trim().isEmpty();
    }

    @Override
    public EppPlanResponse generateEppSchedule(BigDecimal principal, int tenorMonths, String transactionReference) {
        MockBankEppClient.EppPlanResponse planResponse = mockBankEppClient.createPlan(transactionReference, tenorMonths);
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
