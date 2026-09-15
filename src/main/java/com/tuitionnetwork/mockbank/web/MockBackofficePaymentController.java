package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.dto.AmountDto;
import com.tuitionnetwork.mockbank.dto.BackofficePaymentRequest;
import com.tuitionnetwork.mockbank.dto.BackofficePaymentResponse;
import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse;
import com.tuitionnetwork.mockbank.dto.MockBankException;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backoffice/payments")
public class MockBackofficePaymentController {

    private final MockBankStore store;

    public MockBackofficePaymentController(MockBankStore store) {
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<BackofficePaymentResponse> processBackofficePayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody BackofficePaymentRequest request) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<BackofficePaymentResponse> cached = store.getIdempotentBackofficePayment(idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.OK).body(cached.get());
            }
        }

        if (request == null || request.sourceId() == null || request.amount() == null || request.amount().value() == null) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Source ID and amount are required");
        }

        BigDecimal amount = request.amount().value();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Payment amount must be greater than zero");
        }

        CustomerLookupResponse foundCustomer = null;
        CustomerLookupResponse.AccountDto foundAccount = null;
        CustomerLookupResponse.CardDto foundCard = null;

        for (String nid : List.of("29805150101023", "30103222103442", "29511020204536", "29001301202283", "30007091301775", "28809252506666", "29999999994567")) {
            Optional<CustomerLookupResponse> optC = store.getCustomerByNationalId(nid);
            if (optC.isPresent()) {
                CustomerLookupResponse c = optC.get();
                for (CustomerLookupResponse.AccountDto acc : c.accounts()) {
                    if (acc.accountId().equals(request.sourceId())) {
                        foundCustomer = c;
                        foundAccount = acc;
                        break;
                    }
                }
                if (foundCustomer == null) {
                    for (CustomerLookupResponse.CardDto card : c.cards()) {
                        if (card.cardId().equals(request.sourceId())) {
                            foundCustomer = c;
                            foundCard = card;
                            break;
                        }
                    }
                }
                if (foundCustomer != null) break;
            }
        }

        if (foundCustomer == null) {
            throw new MockBankException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "No account or card found with ID: " + request.sourceId());
        }

        String paymentId = "bop_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        boolean capture = request.capture() == null || Boolean.TRUE.equals(request.capture());
        
        CustomerLookupResponse updatedCustomer = foundCustomer;
        BackofficePaymentResponse.PaymentSource sourceDetail;
        String finalStatus = "DECLINED";
        String responseCode = "00";
        String responseMessage = "Approved";

        if (foundAccount != null) {
            sourceDetail = new BackofficePaymentResponse.PaymentSource("ACCOUNT", foundAccount.type(), foundAccount.accountNumber(), null, null);
            if (!capture) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REQUEST", "Cannot authorise an account without capturing");
            }
            if ("FROZEN".equals(foundAccount.status()) || "DORMANT".equals(foundAccount.status())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE", "Account is " + foundAccount.status());
            }
            if (!"ACTIVE".equals(foundAccount.status())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE", "Account is " + foundAccount.status());
            }
            if (foundAccount.availableBalance().compareTo(amount) < 0) {
                BackofficePaymentResponse failed = new BackofficePaymentResponse(paymentId, "DECLINED", false, amount, BigDecimal.ZERO, request.reference(), updatedCustomer.customerId(), request.sourceId(), sourceDetail, "51", "Insufficient funds", null);
                store.saveBackofficePayment(failed);
                throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_FUNDS", "Insufficient funds", Map.of("response_code", "51", "payment_id", paymentId));
            }

            List<CustomerLookupResponse.AccountDto> newAccounts = new ArrayList<>();
            for (CustomerLookupResponse.AccountDto acc : updatedCustomer.accounts()) {
                if (acc.accountId().equals(foundAccount.accountId())) {
                    newAccounts.add(new CustomerLookupResponse.AccountDto(
                            acc.accountId(), acc.accountNumber(), acc.type(), acc.currency(),
                            acc.availableBalance().subtract(amount), acc.status()
                    ));
                } else {
                    newAccounts.add(acc);
                }
            }
            updatedCustomer = new CustomerLookupResponse(updatedCustomer.customerId(), updatedCustomer.nationalId(), updatedCustomer.fullNameEn(), updatedCustomer.fullNameAr(), updatedCustomer.mobile(), updatedCustomer.status(), newAccounts, updatedCustomer.cards());
            
            sourceDetail = new BackofficePaymentResponse.PaymentSource("ACCOUNT", foundAccount.type(), foundAccount.accountNumber(), null, null);
            finalStatus = "POSTED";

        } else {
            sourceDetail = new BackofficePaymentResponse.PaymentSource("CARD", foundCard.type(), null, foundCard.maskedNumber(), foundCard.scheme());

            if ("EXPIRED".equals(foundCard.status())) {
                BackofficePaymentResponse failed = new BackofficePaymentResponse(paymentId, "DECLINED", false, amount, BigDecimal.ZERO, request.reference(), updatedCustomer.customerId(), request.sourceId(), sourceDetail, "54", "Card expired", null);
                store.saveBackofficePayment(failed);
                throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_EXPIRED", "Card expired", Map.of("response_code", "54", "payment_id", paymentId));
            }
            if ("ISSUER_UNAVAILABLE".equals(foundCard.status())) {
                BackofficePaymentResponse failed = new BackofficePaymentResponse(paymentId, "FAILED", false, amount, BigDecimal.ZERO, request.reference(), updatedCustomer.customerId(), request.sourceId(), sourceDetail, "91", "Issuer unavailable", null);
                store.saveBackofficePayment(failed);
                throw new MockBankException(HttpStatus.BAD_GATEWAY, "ISSUER_UNAVAILABLE", "Issuer unavailable", Map.of("response_code", "91", "payment_id", paymentId, "retryable", true));
            }
            if (!"ACTIVE".equals(foundCard.status())) {
                throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_BLOCKED", "Card is " + foundCard.status());
            }

            if ("CREDIT".equals(foundCard.type())) {
                if (foundCard.availableLimit().compareTo(amount) < 0) {
                    BackofficePaymentResponse failed = new BackofficePaymentResponse(paymentId, "DECLINED", false, amount, BigDecimal.ZERO, request.reference(), updatedCustomer.customerId(), request.sourceId(), sourceDetail, "61", "Card declined", null);
                    store.saveBackofficePayment(failed);
                    throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Card declined", Map.of("response_code", "61", "payment_id", paymentId));
                }
                List<CustomerLookupResponse.CardDto> newCards = new ArrayList<>();
                for (CustomerLookupResponse.CardDto c : updatedCustomer.cards()) {
                    if (c.cardId().equals(foundCard.cardId())) {
                        newCards.add(new CustomerLookupResponse.CardDto(
                                c.cardId(), c.maskedNumber(), c.scheme(), c.type(), c.holderName(), c.expiry(), c.status(),
                                c.creditLimit(), c.availableLimit().subtract(amount), c.linkedAccountId()
                        ));
                    } else {
                        newCards.add(c);
                    }
                }
                updatedCustomer = new CustomerLookupResponse(updatedCustomer.customerId(), updatedCustomer.nationalId(), updatedCustomer.fullNameEn(), updatedCustomer.fullNameAr(), updatedCustomer.mobile(), updatedCustomer.status(), updatedCustomer.accounts(), newCards);
            } else {
                String linkedAccId = foundCard.linkedAccountId();
                CustomerLookupResponse.AccountDto linkedAcc = updatedCustomer.accounts().stream().filter(a -> a.accountId().equals(linkedAccId)).findFirst().orElse(null);
                if (linkedAcc == null || (!"ACTIVE".equals(linkedAcc.status()))) {
                    throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_NOT_ACTIVE", "Linked account is not active");
                }
                if (linkedAcc.availableBalance().compareTo(amount) < 0) {
                    BackofficePaymentResponse failed = new BackofficePaymentResponse(paymentId, "DECLINED", false, amount, BigDecimal.ZERO, request.reference(), updatedCustomer.customerId(), request.sourceId(), sourceDetail, "51", "Insufficient funds", null);
                    store.saveBackofficePayment(failed);
                    throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Insufficient funds in linked account", Map.of("response_code", "51", "payment_id", paymentId));
                }
                List<CustomerLookupResponse.AccountDto> newAccounts = new ArrayList<>();
                for (CustomerLookupResponse.AccountDto acc : updatedCustomer.accounts()) {
                    if (acc.accountId().equals(linkedAcc.accountId())) {
                        newAccounts.add(new CustomerLookupResponse.AccountDto(
                                acc.accountId(), acc.accountNumber(), acc.type(), acc.currency(),
                                acc.availableBalance().subtract(amount), acc.status()
                        ));
                    } else {
                        newAccounts.add(acc);
                    }
                }
                updatedCustomer = new CustomerLookupResponse(updatedCustomer.customerId(), updatedCustomer.nationalId(), updatedCustomer.fullNameEn(), updatedCustomer.fullNameAr(), updatedCustomer.mobile(), updatedCustomer.status(), newAccounts, updatedCustomer.cards());
            }

            finalStatus = capture ? "CAPTURED" : "AUTHORISED";
        }

        store.updateCustomer(updatedCustomer);

        BackofficePaymentResponse response = new BackofficePaymentResponse(
                paymentId,
                finalStatus,
                true,
                amount,
                capture || finalStatus.equals("POSTED") ? amount : BigDecimal.ZERO,
                request.reference(),
                updatedCustomer.customerId(),
                request.sourceId(),
                sourceDetail,
                responseCode,
                responseMessage,
                null
        );

        store.saveBackofficePayment(response);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            store.saveIdempotentBackofficePayment(idempotencyKey, response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BackofficePaymentResponse> getPayment(@PathVariable("id") String paymentId) {
        return store.getBackofficePayment(paymentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPayments(@RequestParam(value = "customer_id", required = false) String customerId) {
        List<BackofficePaymentResponse> items = store.listBackofficePayments(customerId);
        return ResponseEntity.ok(Map.of("total", items.size(), "items", items));
    }
    
    @PostMapping("/{id}/capture")
    public ResponseEntity<BackofficePaymentResponse> capturePayment(
            @PathVariable("id") String paymentId,
            @RequestBody(required = false) Map<String, Object> body) {
        
        BackofficePaymentResponse payment = store.getBackofficePayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
                
        if ("ACCOUNT".equals(payment.source().type())) {
            throw new MockBankException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Account payments cannot be captured");
        }
        
        if (!"AUTHORISED".equals(payment.status())) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATE", "Payment must be AUTHORISED");
        }
        
        BigDecimal captureAmount = payment.amount();
        if (body != null && body.containsKey("amount")) {
            captureAmount = new BigDecimal(body.get("amount").toString());
        }
        
        if (captureAmount.compareTo(payment.amount()) < 0) {
            BigDecimal diff = payment.amount().subtract(captureAmount);
            restoreFunds(payment, diff);
        }
        
        BackofficePaymentResponse captured = new BackofficePaymentResponse(
                payment.paymentId(), "CAPTURED", payment.approved(), payment.amount(), captureAmount,
                payment.reference(), payment.customerId(), payment.sourceId(), payment.source(),
                payment.responseCode(), payment.responseMessage(), payment.eppPlanId()
        );
        store.saveBackofficePayment(captured);
        return ResponseEntity.ok(captured);
    }
    
    @PostMapping("/{id}/void")
    public ResponseEntity<BackofficePaymentResponse> voidPayment(@PathVariable("id") String paymentId) {
        BackofficePaymentResponse payment = store.getBackofficePayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
                
        if ("ACCOUNT".equals(payment.source().type())) {
            throw new MockBankException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Account payments cannot be voided");
        }
        if (!"AUTHORISED".equals(payment.status())) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATE", "Payment must be AUTHORISED");
        }
        
        restoreFunds(payment, payment.amount());
        
        BackofficePaymentResponse voided = new BackofficePaymentResponse(
                payment.paymentId(), "VOIDED", payment.approved(), payment.amount(), BigDecimal.ZERO,
                payment.reference(), payment.customerId(), payment.sourceId(), payment.source(),
                payment.responseCode(), payment.responseMessage(), payment.eppPlanId()
        );
        store.saveBackofficePayment(voided);
        return ResponseEntity.ok(voided);
    }
    
    @PostMapping("/{id}/refund")
    public ResponseEntity<BackofficePaymentResponse> refundPayment(
            @PathVariable("id") String paymentId,
            @RequestBody(required = false) Map<String, Object> body) {
        
        BackofficePaymentResponse payment = store.getBackofficePayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
                
        if (!"CAPTURED".equals(payment.status()) && !"POSTED".equals(payment.status()) && !"PARTIALLY_REFUNDED".equals(payment.status())) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATE", "Payment must be captured/posted");
        }
        
        BigDecimal refundAmount = payment.capturedAmount();
        if (body != null && body.containsKey("amount")) {
            refundAmount = new BigDecimal(body.get("amount").toString());
        }
        
        restoreFunds(payment, refundAmount);
        
        BigDecimal remaining = payment.capturedAmount().subtract(refundAmount);
        String status = remaining.compareTo(BigDecimal.ZERO) == 0 ? "REFUNDED" : "PARTIALLY_REFUNDED";
        
        BackofficePaymentResponse refunded = new BackofficePaymentResponse(
                payment.paymentId(), status, payment.approved(), payment.amount(), remaining,
                payment.reference(), payment.customerId(), payment.sourceId(), payment.source(),
                payment.responseCode(), payment.responseMessage(), payment.eppPlanId()
        );
        store.saveBackofficePayment(refunded);
        return ResponseEntity.ok(refunded);
    }

    private void restoreFunds(BackofficePaymentResponse payment, BigDecimal amount) {
        CustomerLookupResponse customer = store.getCustomerById(payment.customerId())
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found"));
                
        List<CustomerLookupResponse.AccountDto> newAccounts = new ArrayList<>();
        List<CustomerLookupResponse.CardDto> newCards = new ArrayList<>();
        
        if ("ACCOUNT".equals(payment.source().type())) {
            for (CustomerLookupResponse.AccountDto acc : customer.accounts()) {
                if (acc.accountId().equals(payment.sourceId())) {
                    newAccounts.add(new CustomerLookupResponse.AccountDto(
                            acc.accountId(), acc.accountNumber(), acc.type(), acc.currency(),
                            acc.availableBalance().add(amount), acc.status()
                    ));
                } else {
                    newAccounts.add(acc);
                }
            }
            newCards.addAll(customer.cards());
        } else {
            CustomerLookupResponse.CardDto foundCard = customer.cards().stream().filter(c -> c.cardId().equals(payment.sourceId())).findFirst().orElse(null);
            if (foundCard != null && "CREDIT".equals(foundCard.type())) {
                for (CustomerLookupResponse.CardDto c : customer.cards()) {
                    if (c.cardId().equals(payment.sourceId())) {
                        newCards.add(new CustomerLookupResponse.CardDto(
                                c.cardId(), c.maskedNumber(), c.scheme(), c.type(), c.holderName(), c.expiry(), c.status(),
                                c.creditLimit(), c.availableLimit().add(amount), c.linkedAccountId()
                        ));
                    } else {
                        newCards.add(c);
                    }
                }
                newAccounts.addAll(customer.accounts());
            } else if (foundCard != null && "DEBIT".equals(foundCard.type())) {
                for (CustomerLookupResponse.AccountDto acc : customer.accounts()) {
                    if (acc.accountId().equals(foundCard.linkedAccountId())) {
                        newAccounts.add(new CustomerLookupResponse.AccountDto(
                                acc.accountId(), acc.accountNumber(), acc.type(), acc.currency(),
                                acc.availableBalance().add(amount), acc.status()
                        ));
                    } else {
                        newAccounts.add(acc);
                    }
                }
                newCards.addAll(customer.cards());
            }
        }
        
        store.updateCustomer(new CustomerLookupResponse(customer.customerId(), customer.nationalId(), customer.fullNameEn(), customer.fullNameAr(), customer.mobile(), customer.status(), newAccounts, newCards));
    }
}
