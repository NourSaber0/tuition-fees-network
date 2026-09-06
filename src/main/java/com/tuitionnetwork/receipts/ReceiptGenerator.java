package com.tuitionnetwork.receipts;

import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class ReceiptGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReceiptGenerator.class);

    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;

    public ReceiptGenerator(ReceiptRepository receiptRepository, PaymentRepository paymentRepository) {
        this.receiptRepository = receiptRepository;
        this.paymentRepository = paymentRepository;
    }

    @ApplicationModuleListener
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        log.info("Generating digital receipt for payment: {}", event.paymentId());

        if (receiptRepository.findByPaymentId(event.paymentId()).isPresent()) {
            log.info("Receipt already generated for payment: {}", event.paymentId());
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findById(event.paymentId());
        if (paymentOpt.isEmpty()) {
            log.warn("Payment {} not found during receipt generation", event.paymentId());
            return;
        }
        Payment payment = paymentOpt.get();

        String fileUrl = "https://cdn.tuitionnetwork.eg/receipts/receipt-" + event.paymentId() + ".pdf";
        String cryptoSignature = generateCryptoSignature(event);

        Receipt receipt = new Receipt(payment, cryptoSignature, fileUrl);
        receipt.setIssuedAt(LocalDateTime.now());
        receiptRepository.save(receipt);

        log.info("Successfully issued receipt for payment {} [URL: {}, Signature: {}]",
                event.paymentId(), fileUrl, cryptoSignature.substring(0, Math.min(16, cryptoSignature.length())) + "...");
    }

    private String generateCryptoSignature(PaymentCapturedEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String rawData = event.paymentId() + "|" + event.totalAmount() + "|" +
                    event.transactionReference() + "|" + event.idempotencyKey();
            byte[] hash = digest.digest(rawData.getBytes(StandardCharsets.UTF_8));
            return "SIG-SHA256-" + HexFormat.of().formatHex(hash).toUpperCase();
        } catch (Exception e) {
            return "SIG-" + event.paymentId();
        }
    }
}
