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

    @Override
    public RetrieveBillingResponse retrieveCustomerBilling(String nationalId, String accountNumber) {
        String soapXml = envelopeBuilder.buildRetrieveRequestXml(nationalId, accountNumber, username, password);
        log.info("Dispatching SOAP RetrieveCustomerBillingProcedure to endpoint: {}", endpoint);

        try {
            String responseXml = postSoap(endpoint, "RetrieveCustomerBillingProcedure", soapXml);
            return envelopeBuilder.parseRetrieveResponseXml(responseXml);
        } catch (Exception e) {
            log.warn("T24 SOAP call failed ({}).", e.getMessage());
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
            log.warn("T24 SOAP call failed ({}).", e.getMessage());
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
            log.warn("T24 SOAP call failed ({}).", e.getMessage());
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
}
