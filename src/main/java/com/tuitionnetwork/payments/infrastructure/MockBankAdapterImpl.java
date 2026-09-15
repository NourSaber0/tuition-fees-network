package com.tuitionnetwork.payments.infrastructure;

import com.tuitionnetwork.payments.domain.EppPricing;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.dto.CibAccountDto;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import com.tuitionnetwork.payments.spi.EppPlanResponse;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

    /**
     * There is no real core-banking account ledger behind this system - accounts are
     * generated deterministically from the national ID (same ID always returns the same
     * accounts) so a back-office operator can pick one to debit before the call to
     * {@link #verifyCibAccount}. Every customer has a Current Account; roughly half also
     * have a Savings Account, based on the seed's parity.
     */
    @Override
    public List<CibAccountDto> listAccounts(String nationalId) {
        String clean = nationalId != null ? nationalId.trim() : "";
        Random seeded = new Random(clean.hashCode());

        List<CibAccountDto> accounts = new ArrayList<>();
        accounts.add(new CibAccountDto(
                formatAccountNumber(1, seeded),
                "Current Account",
                randomBalance(seeded),
                "EGP",
                "ACTIVE"
        ));

        if (seeded.nextBoolean()) {
            accounts.add(new CibAccountDto(
                    formatAccountNumber(2, seeded),
                    "Savings Account",
                    randomBalance(seeded),
                    "EGP",
                    "ACTIVE"
            ));
        }
        return accounts;
    }

    private String formatAccountNumber(int prefix, Random seeded) {
        long digits = Math.abs(seeded.nextLong()) % 1_000_000_000L;
        return String.format("%d%09d", prefix, digits);
    }

    private BigDecimal randomBalance(Random seeded) {
        int wholeEGP = 5_000 + seeded.nextInt(495_000);
        return BigDecimal.valueOf(wholeEGP);
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
