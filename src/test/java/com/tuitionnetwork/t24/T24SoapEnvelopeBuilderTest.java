package com.tuitionnetwork.t24;

import com.tuitionnetwork.t24.client.T24SoapEnvelopeBuilder;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.RetrieveBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class T24SoapEnvelopeBuilderTest {

    private final T24SoapEnvelopeBuilder builder = new T24SoapEnvelopeBuilder();

    @Test
    @DisplayName("Build and parse RetrieveCustomerBillingProcedure SOAP envelopes")
    void testRetrieveCustomerBillingSoap() {
        String reqXml = builder.buildRetrieveRequestXml("29805150101023", "100012345678", "CIB_USER", "CIB_PASS");
        assertNotNull(reqXml);
        assertTrue(reqXml.contains("<t24:RetrieveCustomerBillingProcedure>"));
        assertTrue(reqXml.contains("<t24:NationalId>29805150101023</t24:NationalId>"));
        assertTrue(reqXml.contains("<t24:AccountNumber>100012345678</t24:AccountNumber>"));

        String respXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
                   <soapenv:Body>
                      <t24:RetrieveCustomerBillingResponse>
                         <t24:Status>SUCCESS</t24:Status>
                         <t24:CustomerNumber>CIF-10023</t24:CustomerNumber>
                         <t24:AccountNumber>100012345678</t24:AccountNumber>
                         <t24:CustomerName>Mona Samir</t24:CustomerName>
                         <t24:BillingItem>
                            <t24:BillingId>T24-B1</t24:BillingId>
                            <t24:InstitutionCode>SCH-001</t24:InstitutionCode>
                            <t24:InstitutionName>Cairo School</t24:InstitutionName>
                            <t24:StudentNationalId>31001</t24:StudentNationalId>
                            <t24:StudentName>Ali</t24:StudentName>
                            <t24:FeeType>Tuition</t24:FeeType>
                            <t24:AcademicPeriod>Term 1</t24:AcademicPeriod>
                            <t24:OriginalAmount>15000.00</t24:OriginalAmount>
                            <t24:PaidAmount>0.00</t24:PaidAmount>
                            <t24:RemainingAmount>15000.00</t24:RemainingAmount>
                            <t24:DueDate>2026-10-15</t24:DueDate>
                            <t24:Status>Outstanding</t24:Status>
                         </t24:BillingItem>
                         <t24:TotalOutstanding>15000.00</t24:TotalOutstanding>
                         <t24:Message>Success</t24:Message>
                      </t24:RetrieveCustomerBillingResponse>
                   </soapenv:Body>
                </soapenv:Envelope>
                """;

        RetrieveBillingResponse resp = builder.parseRetrieveResponseXml(respXml);
        assertEquals("SUCCESS", resp.status());
        assertEquals("CIF-10023", resp.customerNumber());
        assertEquals("Mona Samir", resp.customerName());
        assertEquals(1, resp.items().size());
        assertEquals(new BigDecimal("15000.00"), resp.items().get(0).remainingAmount());
        assertEquals(new BigDecimal("15000.00"), resp.totalOutstanding());
    }

    @Test
    @DisplayName("Build and parse RequestCustomerBillingProcedure SOAP envelopes")
    void testRequestCustomerBillingSoap() {
        RequestBillingRequest req = new RequestBillingRequest(
                "SCH-001", "31001", "Yousef", "Tuition",
                new BigDecimal("18000.00"), "EGP", "Term 1", LocalDate.of(2026, 10, 15)
        );

        String xml = builder.buildRequestBillingXml(req, "CIB_USER", "CIB_PASS");
        assertTrue(xml.contains("<t24:RequestCustomerBillingProcedure>"));
        assertTrue(xml.contains("<t24:Amount>18000.00</t24:Amount>"));
        assertTrue(xml.contains("<t24:DueDate>2026-10-15</t24:DueDate>"));

        String respXml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Body>
                      <RequestCustomerBillingResponse>
                         <Status>CREATED</Status>
                         <BillingId>T24-BILL-9988</BillingId>
                         <Message>Record created</Message>
                      </RequestCustomerBillingResponse>
                   </soap:Body>
                </soap:Envelope>
                """;

        RequestBillingResponse resp = builder.parseRequestResponseXml(respXml);
        assertEquals("CREATED", resp.status());
        assertEquals("T24-BILL-9988", resp.billingId());
    }

    @Test
    @DisplayName("Build and parse UpdateCustomerBillingProcedure SOAP envelopes")
    void testUpdateCustomerBillingSoap() {
        UpdateBillingRequest req = new UpdateBillingRequest(
                "T24-BILL-9988", new BigDecimal("5000.00"), "CREDIT_CARD",
                "TXN-1234", new BigDecimal("13000.00")
        );

        String xml = builder.buildUpdateBillingXml(req, "CIB_USER", "CIB_PASS");
        assertTrue(xml.contains("<t24:UpdateCustomerBillingProcedure>"));
        assertTrue(xml.contains("<t24:BillingId>T24-BILL-9988</t24:BillingId>"));
        assertTrue(xml.contains("<t24:AmountPaid>5000.00</t24:AmountPaid>"));

        String respXml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Body>
                      <UpdateCustomerBillingResponse>
                         <Status>UPDATED</Status>
                         <BillingId>T24-BILL-9988</BillingId>
                         <NewRemainingAmount>13000.00</NewRemainingAmount>
                         <Message>Record updated</Message>
                      </UpdateCustomerBillingResponse>
                   </soap:Body>
                </soap:Envelope>
                """;

        UpdateBillingResponse resp = builder.parseUpdateResponseXml(respXml);
        assertEquals("UPDATED", resp.status());
        assertEquals("T24-BILL-9988", resp.billingId());
        assertEquals(new BigDecimal("13000.00"), resp.newRemainingAmount());
    }

    /**
     * Exact fault payload documented in docs/T24_SAMPLE_PAYLOADS.md &sect;5. Before the fault-detection
     * fix, all three parsers would miss this entirely (no &lt;Status&gt; element to find) and silently
     * default to a fabricated success response instead of surfacing the fault.
     */
    private static final String DOCUMENTED_FAULT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
               <soapenv:Body>
                  <soapenv:Fault>
                     <faultcode>soapenv:Client</faultcode>
                     <faultstring>Customer Billing Record Not Found for ID: T24-BILL-INVALID</faultstring>
                     <detail>
                        <t24:ErrorCode xmlns:t24="http://temenos.com/customerbilling">ERR_BILLING_NOT_FOUND</t24:ErrorCode>
                     </detail>
                  </soapenv:Fault>
               </soapenv:Body>
            </soapenv:Envelope>
            """;

    @Test
    @DisplayName("parseRetrieveResponseXml: documented SOAP fault is surfaced as ERROR, not a fabricated SUCCESS")
    void testRetrieveResponse_detectsDocumentedSoapFault() {
        RetrieveBillingResponse resp = builder.parseRetrieveResponseXml(DOCUMENTED_FAULT_XML);

        assertEquals("ERROR", resp.status());
        assertTrue(resp.items().isEmpty());
        assertEquals(BigDecimal.ZERO, resp.totalOutstanding());
        assertTrue(resp.message().contains("Customer Billing Record Not Found"));
        assertTrue(resp.message().contains("ERR_BILLING_NOT_FOUND"));
    }

    @Test
    @DisplayName("parseRequestResponseXml: documented SOAP fault is surfaced as ERROR, not a fabricated CREATED")
    void testRequestResponse_detectsDocumentedSoapFault() {
        RequestBillingResponse resp = builder.parseRequestResponseXml(DOCUMENTED_FAULT_XML);

        assertEquals("ERROR", resp.status());
        assertNull(resp.billingId());
        assertTrue(resp.message().contains("Customer Billing Record Not Found"));
    }

    @Test
    @DisplayName("parseUpdateResponseXml: documented SOAP fault is surfaced as ERROR, not a fabricated UPDATED")
    void testUpdateResponse_detectsDocumentedSoapFault() {
        UpdateBillingResponse resp = builder.parseUpdateResponseXml(DOCUMENTED_FAULT_XML);

        assertEquals("ERROR", resp.status());
        assertNull(resp.billingId());
        assertEquals(BigDecimal.ZERO, resp.newRemainingAmount());
        assertTrue(resp.message().contains("Customer Billing Record Not Found"));
    }

    @Test
    @DisplayName("extractSoapFault matches Fault element regardless of the envelope prefix used (soap: vs soapenv:)")
    void testFaultDetection_isPrefixAgnostic() {
        String differentPrefixFault = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Body>
                      <soap:Fault>
                         <faultcode>soap:Server</faultcode>
                         <faultstring>T24 core banking timeout</faultstring>
                      </soap:Fault>
                   </soap:Body>
                </soap:Envelope>
                """;

        UpdateBillingResponse resp = builder.parseUpdateResponseXml(differentPrefixFault);
        assertEquals("ERROR", resp.status());
        assertTrue(resp.message().contains("T24 core banking timeout"));
    }
}
