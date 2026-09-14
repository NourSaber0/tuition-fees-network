package com.tuitionnetwork.t24.client;

import com.tuitionnetwork.t24.dto.T24BillingDto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class T24CustomerBillingClientImpl implements T24CustomerBillingClient {

    private static final Logger log = LoggerFactory.getLogger(T24CustomerBillingClientImpl.class);

    private final T24SoapEnvelopeBuilder envelopeBuilder = new T24SoapEnvelopeBuilder();

    @Value("${t24.mock.endpoint:http://localhost:8080/soap/customerbilling}")
    private String endpoint;

    @Value("${t24.mock.username:CIB_USER}")
    private String username;

    @Value("${t24.mock.password:CIB_PASS}")
    private String password;

    @Value("${t24.mock.timeout-ms:2000}")
    private int timeoutMs;

    @Value("${t24.mock.fallback-to-local:true}")
    private boolean fallbackToLocal;

    @Override
    public RetrieveBillingResponse retrieveCustomerBilling(String nationalId, String accountNumber) {
        String soapXml = envelopeBuilder.buildRetrieveRequestXml(nationalId, accountNumber, username, password);
        log.info("Dispatching SOAP RetrieveCustomerBillingProcedure to endpoint: {}", endpoint);

        try {
            String responseXml = postSoap(endpoint, "RetrieveCustomerBillingProcedure", soapXml);
            return envelopeBuilder.parseRetrieveResponseXml(responseXml);
        } catch (Exception e) {
            log.warn("T24 SOAP call failed ({}). Fallback to local simulation: {}", e.getMessage(), fallbackToLocal);
            if (fallbackToLocal) {
                return simulateRetrieveResponse(nationalId, accountNumber);
            }
            return new RetrieveBillingResponse("ERROR", null, accountNumber, null, List.of(), BigDecimal.ZERO, "T24 service unavailable: " + e.getMessage());
        }
    }

    @Override
    public RequestBillingResponse requestCustomerBilling(RequestBillingRequest request) {
        String soapXml = envelopeBuilder.buildRequestBillingXml(request, username, password);
        log.info("Dispatching SOAP RequestCustomerBillingProcedure to endpoint: {}", endpoint);

        try {
            String responseXml = postSoap(endpoint, "RequestCustomerBillingProcedure", soapXml);
            return envelopeBuilder.parseRequestResponseXml(responseXml);
        } catch (Exception e) {
            log.warn("T24 SOAP call failed ({}). Fallback to local simulation: {}", e.getMessage(), fallbackToLocal);
            if (fallbackToLocal) {
                return simulateRequestResponse(request);
            }
            return new RequestBillingResponse("ERROR", null, "T24 service unavailable: " + e.getMessage());
        }
    }

    @Override
    public UpdateBillingResponse updateCustomerBilling(UpdateBillingRequest request) {
        String soapXml = envelopeBuilder.buildUpdateBillingXml(request, username, password);
        log.info("Dispatching SOAP UpdateCustomerBillingProcedure to endpoint: {}", endpoint);

        try {
            String responseXml = postSoap(endpoint, "UpdateCustomerBillingProcedure", soapXml);
            return envelopeBuilder.parseUpdateResponseXml(responseXml);
        } catch (Exception e) {
            log.warn("T24 SOAP call failed ({}). Fallback to local simulation: {}", e.getMessage(), fallbackToLocal);
            if (fallbackToLocal) {
                return simulateUpdateResponse(request);
            }
            return new UpdateBillingResponse("ERROR", request.billingId(), BigDecimal.ZERO, "T24 service unavailable: " + e.getMessage());
        }
    }

    private String postSoap(String urlStr, String soapAction, String xmlPayload) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction", "\"" + soapAction + "\"");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(xmlPayload.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            throw new RuntimeException("HTTP " + code + " from " + urlStr + " with empty body");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        if (code >= 400) {
            throw new RuntimeException("SOAP Fault HTTP " + code + ": " + sb);
        }

        return sb.toString();
    }

    private RetrieveBillingResponse simulateRetrieveResponse(String nationalId, String accountNumber) {
        String cleanId = nationalId != null ? nationalId.trim() : "29805150101023";
        String cleanAcc = accountNumber != null ? accountNumber.trim() : "100012345678";

        List<BillingItem> items = new ArrayList<>();
        items.add(new BillingItem(
                "T24-BILL-00101",
                "SCH-001",
                "Cairo International School",
                cleanId,
                "Yousef Adel",
                "Tuition",
                "Term 1 2026/27",
                new BigDecimal("18000.00"),
                BigDecimal.ZERO,
                new BigDecimal("18000.00"),
                LocalDate.of(2026, 10, 15),
                "Outstanding"
        ));

        items.add(new BillingItem(
                "T24-BILL-00102",
                "SCH-001",
                "Cairo International School",
                cleanId,
                "Yousef Adel",
                "Bus subscription",
                "Term 1 2026/27",
                new BigDecimal("4000.00"),
                BigDecimal.ZERO,
                new BigDecimal("4000.00"),
                LocalDate.of(2026, 10, 15),
                "Outstanding"
        ));

        BigDecimal total = new BigDecimal("22000.00");
        return new RetrieveBillingResponse(
                "SUCCESS",
                "CIF-" + (Math.abs(cleanId.hashCode()) % 900000 + 100000),
                cleanAcc,
                "Adel Mostafa",
                items,
                total,
                "Retrieved 2 customer billing records from T24 mock"
        );
    }

    private RequestBillingResponse simulateRequestResponse(RequestBillingRequest req) {
        String ref = "T24-BILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new RequestBillingResponse("CREATED", ref, "Successfully created billing item in T24 mock for student: " + req.studentNationalId());
    }

    private UpdateBillingResponse simulateUpdateResponse(UpdateBillingRequest req) {
        return new UpdateBillingResponse(
                "UPDATED",
                req.billingId(),
                req.newRemainingAmount() != null ? req.newRemainingAmount() : BigDecimal.ZERO,
                "Successfully updated T24 billing record balance"
        );
    }
}
