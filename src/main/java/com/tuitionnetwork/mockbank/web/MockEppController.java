package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.domain.CardLib;
import com.tuitionnetwork.mockbank.dto.CardPaymentResponse;
import com.tuitionnetwork.mockbank.dto.EppCreateRequest;
import com.tuitionnetwork.mockbank.dto.EppPlanDetailResponse;
import com.tuitionnetwork.mockbank.dto.EppQuoteResponse;
import com.tuitionnetwork.mockbank.dto.MockBankException;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/epp")
public class MockEppController {

    private static final Set<Integer> ALLOWED_TENORS = Set.of(3, 6, 12, 18, 24);
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("500000");

    private final MockBankStore store;

    public MockEppController(MockBankStore store) {
        this.store = store;
    }

    @GetMapping("/quotes")
    public ResponseEntity<EppQuoteResponse> getQuotes(@RequestParam("amount") BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Amount must be greater than zero");
        }

        List<EppQuoteResponse.QuoteItem> quotes = new ArrayList<>();
        for (int tenor : List.of(3, 6, 12, 18, 24)) {
            quotes.add(calculateQuote(amount, tenor));
        }

        return ResponseEntity.ok(new EppQuoteResponse(amount, "EGP", quotes));
    }

    @PostMapping
    public ResponseEntity<EppPlanDetailResponse> createEppPlan(@RequestBody(required = false) EppCreateRequest request) {
        if (request == null) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request body is required");
        }

        if (request.tenorMonths() == null || !ALLOWED_TENORS.contains(request.tenorMonths())) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_TENOR", "Tenor must be 3, 6, 12, 18, or 24 months");
        }

        BigDecimal principal;
        String paymentId = request.paymentId();

        if (paymentId != null && !paymentId.isBlank()) {
            CardPaymentResponse payment = store.getPayment(paymentId)
                    .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

            if (store.isPaymentConverted(paymentId)) {
                throw new MockBankException(HttpStatus.CONFLICT, "ALREADY_CONVERTED", "That payment already has an EPP plan");
            }

            if (!"CAPTURED".equalsIgnoreCase(payment.status()) && !"AUTHORISED".equalsIgnoreCase(payment.status())) {
                throw new MockBankException(HttpStatus.CONFLICT, "PAYMENT_NOT_CONVERTIBLE", "Payment is not in a convertible state: " + payment.status());
            }

            if ("DEBIT".equalsIgnoreCase(payment.card().type())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_ELIGIBLE", "Debit cards are not eligible for EPP instalment plans");
            }

            principal = payment.capturedAmount() != null && payment.capturedAmount().compareTo(BigDecimal.ZERO) > 0
                    ? payment.capturedAmount()
                    : payment.amount();
        } else if (request.amount() != null) {
            if (request.card() != null && request.card().number() != null) {
                String cardType = CardLib.detectCardType(request.card().number());
                if ("DEBIT".equalsIgnoreCase(cardType)) {
                    throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_ELIGIBLE", "Debit cards are not eligible for EPP instalment plans");
                }
            }
            principal = request.amount();
        } else {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Either payment_id or amount must be supplied");
        }

        if (principal.compareTo(MIN_AMOUNT) < 0 || principal.compareTo(MAX_AMOUNT) > 0) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_OUT_OF_RANGE", "EPP principal must be between 1,000 and 500,000 EGP");
        }

        EppQuoteResponse.QuoteItem quote = calculateQuote(principal, request.tenorMonths());
        String planId = "epp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        LocalDate firstDueDate = LocalDate.now();
        LocalDate lastDueDate = firstDueDate.plusMonths(request.tenorMonths() - 1);

        List<EppPlanDetailResponse.InstallmentItem> schedule = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int i = 1; i <= request.tenorMonths(); i++) {
            LocalDate dueDate = firstDueDate.plusMonths(i - 1);
            BigDecimal installmentAmt = quote.monthlyInstallment();
            if (i == request.tenorMonths()) {
                // Last installment absorbs rounding difference
                installmentAmt = quote.totalPayable().subtract(runningTotal);
            } else {
                runningTotal = runningTotal.add(installmentAmt);
            }
            schedule.add(new EppPlanDetailResponse.InstallmentItem(i, dueDate, installmentAmt, "DUE"));
        }

        EppPlanDetailResponse plan = new EppPlanDetailResponse(
                planId,
                "ACTIVE",
                paymentId,
                principal,
                request.tenorMonths(),
                quote.annualRate(),
                quote.interestAmount(),
                quote.adminFee(),
                quote.totalPayable(),
                quote.monthlyInstallment(),
                firstDueDate,
                lastDueDate,
                schedule
        );

        store.saveEppPlan(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EppPlanDetailResponse> getEppPlan(@PathVariable("id") String id) {
        return store.getEppPlan(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "EPP_PLAN_NOT_FOUND", "EPP plan not found: " + id));
    }

    @GetMapping
    public ResponseEntity<List<EppPlanDetailResponse>> listEppPlans() {
        return ResponseEntity.ok(store.listEppPlans());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<EppPlanDetailResponse> cancelEppPlan(@PathVariable("id") String id) {
        return store.cancelEppPlan(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "EPP_PLAN_NOT_FOUND", "EPP plan not found: " + id));
    }

    private EppQuoteResponse.QuoteItem calculateQuote(BigDecimal principal, int tenor) {
        BigDecimal annualRate;
        BigDecimal adminFee;

        switch (tenor) {
            case 3 -> {
                annualRate = BigDecimal.ZERO;
                adminFee = BigDecimal.ZERO;
            }
            case 6 -> {
                annualRate = new BigDecimal("0.12");
                adminFee = principal.multiply(new BigDecimal("0.01")).min(new BigDecimal("500.00"));
            }
            case 12 -> {
                annualRate = new BigDecimal("0.14");
                adminFee = principal.multiply(new BigDecimal("0.01")).min(new BigDecimal("500.00"));
            }
            case 18 -> {
                annualRate = new BigDecimal("0.15");
                adminFee = principal.multiply(new BigDecimal("0.01")).min(new BigDecimal("500.00"));
            }
            case 24 -> {
                annualRate = new BigDecimal("0.16");
                adminFee = principal.multiply(new BigDecimal("0.01")).min(new BigDecimal("500.00"));
            }
            default -> throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_TENOR", "Unsupported tenor: " + tenor);
        }

        BigDecimal tenorFactor = BigDecimal.valueOf(tenor).divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        BigDecimal interestAmount = principal.multiply(annualRate).multiply(tenorFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPayable = principal.add(interestAmount).add(adminFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyInstallment = totalPayable.divide(BigDecimal.valueOf(tenor), 2, RoundingMode.HALF_UP);

        return new EppQuoteResponse.QuoteItem(tenor, annualRate, interestAmount, adminFee, totalPayable, monthlyInstallment);
    }
}
