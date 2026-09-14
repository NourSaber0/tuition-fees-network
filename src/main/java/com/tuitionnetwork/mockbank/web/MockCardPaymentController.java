package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.domain.CardLib;
import com.tuitionnetwork.mockbank.dto.CardActionRequests;
import com.tuitionnetwork.mockbank.dto.CardPaymentRequest;
import com.tuitionnetwork.mockbank.dto.CardPaymentResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/cards")
public class MockCardPaymentController {

    private final MockBankStore store;

    public MockCardPaymentController(MockBankStore store) {
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<CardPaymentResponse> processCardPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CardPaymentRequest request) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CardPaymentResponse> cached = store.getIdempotentPayment(idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(cached.get());
            }
        }

        if (request == null || request.card() == null || request.card().number() == null) {
            throw new MockBankException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER", "Card details and number are required");
        }

        String cardNumber = CardLib.cleanCardNumber(request.card().number());
        if (!CardLib.isValidLuhn(cardNumber)) {
            throw new MockBankException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER", "Invalid card number (failed Luhn check)");
        }

        String scheme = CardLib.detectScheme(cardNumber);
        if (scheme == null) {
            throw new MockBankException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SCHEME", "Card scheme not supported (Visa, Mastercard, Meeza, Amex only)");
        }

        int expMonth = request.card().expiryMonth() != null ? request.card().expiryMonth() : 12;
        int expYear = request.card().expiryYear() != null ? request.card().expiryYear() : 2030;

        // Check expired card
        if (CardLib.isExpired(expMonth, expYear) || "4000000000000069".equals(cardNumber)) {
            throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_EXPIRED", "Card has expired", Map.of("response_code", "54"));
        }

        BigDecimal amount = (request.amount() != null && request.amount().value() != null)
                ? request.amount().value()
                : BigDecimal.ZERO;

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MockBankException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Payment amount must be greater than zero",
                    Map.of("fields", Map.of("amount.value", "must be positive")));
        }

        // Test card specific decline triggers
        if ("4000000000000002".equals(cardNumber)) {
            throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Insufficient funds", Map.of("response_code", "51"));
        }
        if ("4000000000009995".equals(cardNumber)) {
            throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Do not honour", Map.of("response_code", "05"));
        }
        if ("4000000000000101".equals(cardNumber)) {
            throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Incorrect CVV", Map.of("response_code", "82"));
        }
        if ("4000000000000119".equals(cardNumber)) {
            throw new MockBankException(HttpStatus.BAD_GATEWAY, "ISSUER_UNAVAILABLE", "Issuer unavailable — safe to retry", Map.of("response_code", "91"));
        }
        if ("4000000000000077".equals(cardNumber) && amount.compareTo(new BigDecimal("10000")) > 0) {
            throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Transaction limit exceeded (max 10,000 EGP)", Map.of("response_code", "51"));
        }

        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String cardType = CardLib.detectCardType(cardNumber);
        String maskedNumber = CardLib.maskCardNumber(cardNumber);

        // 3-D Secure card test case
        if ("4000000000003220".equals(cardNumber)) {
            CardPaymentResponse response3ds = new CardPaymentResponse(
                    paymentId,
                    "PENDING_3DS",
                    false,
                    amount,
                    BigDecimal.ZERO,
                    new CardPaymentResponse.CardInfo(maskedNumber, scheme, cardType),
                    null,
                    null,
                    "00",
                    "3-D Secure authentication required"
            );
            store.savePayment(response3ds);
            return ResponseEntity.ok(response3ds);
        }

        boolean capture = request.capture() == null || Boolean.TRUE.equals(request.capture());
        String status = capture ? "CAPTURED" : "AUTHORISED";
        BigDecimal capturedAmount = capture ? amount : BigDecimal.ZERO;
        String authCode = String.format("%06d", (int)(Math.random() * 900000) + 100000);
        String rrn = String.format("%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000000000L);

        CardPaymentResponse response = new CardPaymentResponse(
                paymentId,
                status,
                true,
                amount,
                capturedAmount,
                new CardPaymentResponse.CardInfo(maskedNumber, scheme, cardType),
                authCode,
                rrn,
                "00",
                "Approved"
        );

        store.savePayment(response);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            store.saveIdempotentPayment(idempotencyKey, response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/3ds")
    public ResponseEntity<CardPaymentResponse> confirm3ds(
            @PathVariable("id") String paymentId,
            @RequestBody(required = false) CardActionRequests.Card3dsRequest request) {

        CardPaymentResponse existing = store.getPayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

        if (!"PENDING_3DS".equals(existing.status())) {
            throw new MockBankException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Payment is not in PENDING_3DS state");
        }

        if (request == null || !"123456".equals(request.otp())) {
            throw new MockBankException(HttpStatus.PAYMENT_REQUIRED, "CARD_DECLINED", "Invalid 3DS OTP challenge", Map.of("response_code", "05"));
        }

        String authCode = String.format("%06d", (int)(Math.random() * 900000) + 100000);
        String rrn = String.format("%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000000000L);

        CardPaymentResponse confirmed = new CardPaymentResponse(
                existing.paymentId(),
                "CAPTURED",
                true,
                existing.amount(),
                existing.amount(),
                existing.card(),
                authCode,
                rrn,
                "00",
                "Approved"
        );

        store.savePayment(confirmed);
        return ResponseEntity.ok(confirmed);
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<CardPaymentResponse> capturePayment(
            @PathVariable("id") String paymentId,
            @RequestBody(required = false) CardActionRequests.CardCaptureRequest request) {

        CardPaymentResponse existing = store.getPayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

        if (!"AUTHORISED".equals(existing.status())) {
            throw new MockBankException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Cannot capture payment with status: " + existing.status());
        }

        BigDecimal captureAmt = (request != null && request.amount() != null) ? request.amount() : existing.amount();

        CardPaymentResponse captured = new CardPaymentResponse(
                existing.paymentId(),
                "CAPTURED",
                true,
                existing.amount(),
                captureAmt,
                existing.card(),
                existing.authCode(),
                existing.rrn(),
                existing.responseCode(),
                "Captured"
        );

        store.savePayment(captured);
        return ResponseEntity.ok(captured);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<CardPaymentResponse> refundPayment(
            @PathVariable("id") String paymentId,
            @RequestBody(required = false) CardActionRequests.CardRefundRequest request) {

        CardPaymentResponse existing = store.getPayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

        if (!"CAPTURED".equals(existing.status())) {
            throw new MockBankException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Cannot refund payment that is not captured");
        }

        BigDecimal refundAmt = (request != null && request.amount() != null) ? request.amount() : existing.capturedAmount();
        BigDecimal newCaptured = existing.capturedAmount().subtract(refundAmt);

        CardPaymentResponse refunded = new CardPaymentResponse(
                existing.paymentId(),
                newCaptured.compareTo(BigDecimal.ZERO) == 0 ? "REFUNDED" : "PARTIALLY_REFUNDED",
                true,
                existing.amount(),
                newCaptured,
                existing.card(),
                existing.authCode(),
                existing.rrn(),
                existing.responseCode(),
                "Refunded " + refundAmt + " EGP"
        );

        store.savePayment(refunded);
        return ResponseEntity.ok(refunded);
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<CardPaymentResponse> voidPayment(@PathVariable("id") String paymentId) {
        CardPaymentResponse existing = store.getPayment(paymentId)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

        if (!"AUTHORISED".equals(existing.status())) {
            throw new MockBankException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Only AUTHORISED payments can be voided");
        }

        CardPaymentResponse voided = new CardPaymentResponse(
                existing.paymentId(),
                "VOIDED",
                false,
                existing.amount(),
                BigDecimal.ZERO,
                existing.card(),
                existing.authCode(),
                existing.rrn(),
                existing.responseCode(),
                "Voided"
        );

        store.savePayment(voided);
        return ResponseEntity.ok(voided);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardPaymentResponse> getPayment(@PathVariable("id") String paymentId) {
        return store.getPayment(paymentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));
    }

    @GetMapping
    public ResponseEntity<List<CardPaymentResponse>> listPayments(@RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(store.listPayments(status));
    }
}
