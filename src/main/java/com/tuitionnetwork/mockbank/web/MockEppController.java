package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.domain.CardLib;
import com.tuitionnetwork.mockbank.dto.AmountDto;
import com.tuitionnetwork.mockbank.dto.BackofficePaymentResponse;
import com.tuitionnetwork.mockbank.dto.CardPaymentResponse;
import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

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
        String cardId = request.cardId();

        if (paymentId != null && !paymentId.isBlank() && cardId != null && !cardId.isBlank()) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REQUEST", "Cannot provide both payment_id and card_id");
        }

        CardPaymentResponse payment = null;
        if (paymentId != null && !paymentId.isBlank()) {
            payment = store.getPayment(paymentId)
                    .orElseGet(() -> store.getBackofficePayment(paymentId).map(bop -> {
                        return new CardPaymentResponse(
                                bop.paymentId(), bop.status(), bop.approved(), bop.amount(),
                                bop.capturedAmount(), new CardPaymentResponse.CardInfo(bop.source().maskedNumber(), bop.source().scheme(), bop.source().productType()),
                                null, null, bop.responseCode(), bop.responseMessage(), bop.eppPlanId()
                        );
                    }).orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId)));

            if (store.isPaymentConverted(paymentId)) {
                throw new MockBankException(HttpStatus.CONFLICT, "ALREADY_CONVERTED", "That payment already has an EPP plan");
            }

            if (payment.card() == null || !"CREDIT".equalsIgnoreCase(payment.card().type())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_ELIGIBLE", "Only credit cards are eligible for EPP instalment plans");
            }

            if (!"CAPTURED".equals(payment.status()) && !"POSTED".equals(payment.status())) {
                throw new MockBankException(HttpStatus.CONFLICT, "PAYMENT_NOT_CONVERTIBLE", "Payment is not in a convertible state: " + payment.status());
            }

            principal = payment.capturedAmount() != null && payment.capturedAmount().compareTo(BigDecimal.ZERO) > 0
                    ? payment.capturedAmount()
                    : payment.amount();
        } else if (cardId != null && !cardId.isBlank()) {
            // EPP from card limit directly
            if (request.amount() == null || request.amount().value() == null) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Amount is required for card_id");
            }
            principal = request.amount().value();
            
            CustomerLookupResponse foundCustomer = null;
            CustomerLookupResponse.CardDto foundCard = null;
            
            for (String nid : List.of("29805150101023", "30103222103442", "29511020204536", "29001301202283", "30007091301775", "28809252506666")) {
                Optional<CustomerLookupResponse> optC = store.getCustomerByNationalId(nid);
                if (optC.isPresent()) {
                    CustomerLookupResponse c = optC.get();
                    for (CustomerLookupResponse.CardDto card : c.cards()) {
                        if (card.cardId().equals(cardId)) {
                            foundCustomer = c;
                            foundCard = card;
                            break;
                        }
                    }
                    if (foundCustomer != null) break;
                }
            }
            
            if (foundCustomer == null) {
                throw new MockBankException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Card not found: " + cardId);
            }
            if ("DEBIT".equalsIgnoreCase(foundCard.type())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_ELIGIBLE", "Debit cards are not eligible for EPP instalment plans");
            }
            if ("EXPIRED".equals(foundCard.status())) {
                throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_EXPIRED", "Card expired", Map.of("response_code", "54"));
            }
            if (!"ACTIVE".equals(foundCard.status())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_BLOCKED", "Card is " + foundCard.status());
            }
            if (foundCard.availableLimit().compareTo(principal) < 0) {
                throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Card declined", Map.of("response_code", "51"));
            }
            
            // Deduct limit
            List<CustomerLookupResponse.CardDto> newCards = new ArrayList<>();
            for (CustomerLookupResponse.CardDto c : foundCustomer.cards()) {
                if (c.cardId().equals(foundCard.cardId())) {
                    newCards.add(new CustomerLookupResponse.CardDto(
                            c.cardId(), c.maskedNumber(), c.scheme(), c.type(), c.holderName(), c.expiry(), c.status(),
                            c.creditLimit(), c.availableLimit().subtract(principal), c.linkedAccountId()
                    ));
                } else {
                    newCards.add(c);
                }
            }
            CustomerLookupResponse updatedCustomer = new CustomerLookupResponse(foundCustomer.customerId(), foundCustomer.nationalId(), foundCustomer.fullNameEn(), foundCustomer.fullNameAr(), foundCustomer.mobile(), foundCustomer.status(), foundCustomer.accounts(), newCards);
            store.updateCustomer(updatedCustomer);
            
        } else if (request.amount() != null && request.amount().value() != null) {
            if (request.card() != null && request.card().number() != null) {
                String cardType = CardLib.detectCardType(request.card().number());
                if ("DEBIT".equalsIgnoreCase(cardType)) {
                    throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_ELIGIBLE", "Debit cards are not eligible for EPP instalment plans");
                }
            }
            principal = request.amount().value();
        } else {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Either payment_id, card_id or amount must be supplied");
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
                installmentAmt = quote.totalPayable().subtract(runningTotal);
            } else {
                runningTotal = runningTotal.add(installmentAmt);
            }
            schedule.add(new EppPlanDetailResponse.InstallmentItem(i, dueDate, installmentAmt, "DUE"));
        }

        String finalCardId = cardId;
        if (finalCardId == null && paymentId != null) {
            Optional<BackofficePaymentResponse> bopOpt = store.getBackofficePayment(paymentId);
            if (bopOpt.isPresent() && "CARD".equals(bopOpt.get().source().type())) {
                finalCardId = bopOpt.get().sourceId();
            }
        }

        Map<String, String> customerMap = null;
        if (finalCardId != null) {
            String cId = null;
            if ("card_mona_visa".equals(finalCardId) || "card_mona_debit".equals(finalCardId)) cId = "cif_100001";
            if (cId != null) customerMap = Map.of("customer_id", cId);
        }

        EppPlanDetailResponse plan = new EppPlanDetailResponse(
                planId,
                "ACTIVE",
                paymentId,
                finalCardId,
                customerMap,
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
        
        if (paymentId != null && !paymentId.isBlank()) {
            CardPaymentResponse updated = new CardPaymentResponse(
                    payment.paymentId(), payment.status(), payment.approved(), payment.amount(),
                    payment.capturedAmount(), payment.card(), payment.authCode(), payment.rrn(),
                    payment.responseCode(), payment.responseMessage(), planId
            );
            store.savePayment(updated);
            
            store.getBackofficePayment(paymentId).ifPresent(bop -> {
                BackofficePaymentResponse updatedBop = new BackofficePaymentResponse(
                        bop.paymentId(), bop.status(), bop.approved(), bop.amount(),
                        bop.capturedAmount(), bop.reference(), bop.customerId(), bop.sourceId(),
                        bop.source(), bop.responseCode(), bop.responseMessage(), planId
                );
                store.saveBackofficePayment(updatedBop);
            });
        }
        
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
                .map(plan -> {
                    if (plan.paymentId() != null) {
                        store.getBackofficePayment(plan.paymentId()).ifPresent(p -> {
                            BackofficePaymentResponse updated = new BackofficePaymentResponse(
                                p.paymentId(), p.status(), p.approved(), p.amount(), p.capturedAmount(),
                                p.reference(), p.customerId(), p.sourceId(), p.source(),
                                p.responseCode(), p.responseMessage(), null
                            );
                            store.saveBackofficePayment(updated);
                        });
                        store.getPayment(plan.paymentId()).ifPresent(p -> {
                            CardPaymentResponse updated = new CardPaymentResponse(
                                p.paymentId(), p.status(), p.approved(), p.amount(), p.capturedAmount(),
                                p.card(), p.authCode(), p.rrn(), p.responseCode(), p.responseMessage(), null
                            );
                            store.savePayment(updated);
                        });
                    } else if (plan.cardId() != null) {
                        String cid = plan.customer() != null ? plan.customer().get("customer_id") : null;
                        if (cid != null) {
                            store.getCustomerById(cid).ifPresent(c -> {
                                java.util.List<CustomerLookupResponse.CardDto> newCards = new java.util.ArrayList<>();
                                for (CustomerLookupResponse.CardDto card : c.cards()) {
                                    if (card.cardId().equals(plan.cardId())) {
                                        newCards.add(new CustomerLookupResponse.CardDto(
                                                card.cardId(), card.maskedNumber(), card.scheme(), card.type(), card.holderName(), card.expiry(), card.status(),
                                                card.creditLimit(), card.availableLimit().add(plan.principal()), card.linkedAccountId()
                                        ));
                                    } else {
                                        newCards.add(card);
                                    }
                                }
                                store.updateCustomer(new CustomerLookupResponse(c.customerId(), c.nationalId(), c.fullNameEn(), c.fullNameAr(), c.mobile(), c.status(), c.accounts(), newCards));
                            });
                        }
                    }
                    return ResponseEntity.ok(plan);
                })
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
