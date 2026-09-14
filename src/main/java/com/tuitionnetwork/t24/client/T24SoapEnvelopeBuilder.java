package com.tuitionnetwork.t24.client;

import com.tuitionnetwork.t24.dto.T24BillingDto.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and parses standard SOAP 1.1 / 1.2 XML envelopes for T24 CustomerBilling service.
 */
public class T24SoapEnvelopeBuilder {

    public static final String T24_NS = "http://temenos.com/customerbilling";
    public static final String SOAP_ENV_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String SOAP12_ENV_NS = "http://www.w3.org/2003/05/soap-envelope";

    public String buildRetrieveRequestXml(String nationalId, String accountNumber, String username, String password) {
        String safeNationalId = nationalId != null ? escapeXml(nationalId) : "";
        String safeAccount = accountNumber != null ? escapeXml(accountNumber) : "";
        String safeUser = username != null ? escapeXml(username) : "CIB_USER";
        String safePass = password != null ? escapeXml(password) : "CIB_PASS";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
                   <soapenv:Header>
                      <t24:AuthHeader>
                         <t24:Username>%s</t24:Username>
                         <t24:Password>%s</t24:Password>
                         <t24:ChannelId>CIB_TUITION_NETWORK</t24:ChannelId>
                      </t24:AuthHeader>
                   </soapenv:Header>
                   <soapenv:Body>
                      <t24:RetrieveCustomerBillingProcedure>
                         <t24:NationalId>%s</t24:NationalId>
                         <t24:AccountNumber>%s</t24:AccountNumber>
                      </t24:RetrieveCustomerBillingProcedure>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(safeUser, safePass, safeNationalId, safeAccount).trim();
    }

    public String buildRequestBillingXml(RequestBillingRequest req, String username, String password) {
        String safeUser = username != null ? escapeXml(username) : "CIB_USER";
        String safePass = password != null ? escapeXml(password) : "CIB_PASS";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
                   <soapenv:Header>
                      <t24:AuthHeader>
                         <t24:Username>%s</t24:Username>
                         <t24:Password>%s</t24:Password>
                         <t24:ChannelId>CIB_TUITION_NETWORK</t24:ChannelId>
                      </t24:AuthHeader>
                   </soapenv:Header>
                   <soapenv:Body>
                      <t24:RequestCustomerBillingProcedure>
                         <t24:InstitutionCode>%s</t24:InstitutionCode>
                         <t24:StudentNationalId>%s</t24:StudentNationalId>
                         <t24:StudentName>%s</t24:StudentName>
                         <t24:FeeType>%s</t24:FeeType>
                         <t24:Amount>%s</t24:Amount>
                         <t24:Currency>%s</t24:Currency>
                         <t24:AcademicPeriod>%s</t24:AcademicPeriod>
                         <t24:DueDate>%s</t24:DueDate>
                      </t24:RequestCustomerBillingProcedure>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                safeUser, safePass,
                escapeXml(req.institutionCode()),
                escapeXml(req.studentNationalId()),
                escapeXml(req.studentName()),
                escapeXml(req.feeType()),
                req.amount() != null ? req.amount().toPlainString() : "0.00",
                req.currency() != null ? escapeXml(req.currency()) : "EGP",
                escapeXml(req.academicPeriod()),
                req.dueDate() != null ? req.dueDate().toString() : ""
        ).trim();
    }

    public String buildUpdateBillingXml(UpdateBillingRequest req, String username, String password) {
        String safeUser = username != null ? escapeXml(username) : "CIB_USER";
        String safePass = password != null ? escapeXml(password) : "CIB_PASS";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
                   <soapenv:Header>
                      <t24:AuthHeader>
                         <t24:Username>%s</t24:Username>
                         <t24:Password>%s</t24:Password>
                         <t24:ChannelId>CIB_TUITION_NETWORK</t24:ChannelId>
                      </t24:AuthHeader>
                   </soapenv:Header>
                   <soapenv:Body>
                      <t24:UpdateCustomerBillingProcedure>
                         <t24:BillingId>%s</t24:BillingId>
                         <t24:AmountPaid>%s</t24:AmountPaid>
                         <t24:PaymentMethod>%s</t24:PaymentMethod>
                         <t24:TransactionReference>%s</t24:TransactionReference>
                         <t24:NewRemainingAmount>%s</t24:NewRemainingAmount>
                      </t24:UpdateCustomerBillingProcedure>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                safeUser, safePass,
                escapeXml(req.billingId()),
                req.amountPaid() != null ? req.amountPaid().toPlainString() : "0.00",
                escapeXml(req.paymentMethod()),
                escapeXml(req.transactionReference()),
                req.newRemainingAmount() != null ? req.newRemainingAmount().toPlainString() : "0.00"
        ).trim();
    }

    public RetrieveBillingResponse parseRetrieveResponseXml(String xml) {
        try {
            Document doc = parseXmlDocument(xml);
            String fault = extractSoapFault(doc);
            if (fault != null) {
                return new RetrieveBillingResponse("ERROR", null, null, null, List.of(), BigDecimal.ZERO, fault);
            }
            String status = getElementText(doc, "Status", "SUCCESS");
            String custNo = getElementText(doc, "CustomerNumber", "");
            String accNo = getElementText(doc, "AccountNumber", "");
            String custName = getElementText(doc, "CustomerName", "");
            String message = getElementText(doc, "Message", "Billing retrieved successfully");

            List<BillingItem> items = new ArrayList<>();
            BigDecimal totalOutstanding = BigDecimal.ZERO;

            NodeList itemNodes = doc.getElementsByTagName("BillingItem");
            if (itemNodes.getLength() == 0) {
                itemNodes = doc.getElementsByTagName("t24:BillingItem");
            }

            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element el = (Element) itemNodes.item(i);
                String bId = getChildText(el, "BillingId");
                String instCode = getChildText(el, "InstitutionCode");
                String instName = getChildText(el, "InstitutionName");
                String stuId = getChildText(el, "StudentNationalId");
                String stuName = getChildText(el, "StudentName");
                String fType = getChildText(el, "FeeType");
                String period = getChildText(el, "AcademicPeriod");
                BigDecimal orig = parseDecimal(getChildText(el, "OriginalAmount"));
                BigDecimal paid = parseDecimal(getChildText(el, "PaidAmount"));
                BigDecimal rem = parseDecimal(getChildText(el, "RemainingAmount"));
                LocalDate dueDate = parseDate(getChildText(el, "DueDate"));
                String bStatus = getChildText(el, "Status");

                items.add(new BillingItem(bId, instCode, instName, stuId, stuName, fType, period, orig, paid, rem, dueDate, bStatus));
                if (rem != null) {
                    totalOutstanding = totalOutstanding.add(rem);
                }
            }

            return new RetrieveBillingResponse(status, custNo, accNo, custName, items, totalOutstanding, message);
        } catch (Exception e) {
            return new RetrieveBillingResponse("ERROR", null, null, null, List.of(), BigDecimal.ZERO, "Failed to parse SOAP response: " + e.getMessage());
        }
    }

    public RequestBillingResponse parseRequestResponseXml(String xml) {
        try {
            Document doc = parseXmlDocument(xml);
            String fault = extractSoapFault(doc);
            if (fault != null) {
                return new RequestBillingResponse("ERROR", null, fault);
            }
            String status = getElementText(doc, "Status", "CREATED");
            String billingId = getElementText(doc, "BillingId", "");
            String message = getElementText(doc, "Message", "Customer billing record created in T24");
            return new RequestBillingResponse(status, billingId, message);
        } catch (Exception e) {
            return new RequestBillingResponse("ERROR", null, "Failed to parse SOAP response: " + e.getMessage());
        }
    }

    public UpdateBillingResponse parseUpdateResponseXml(String xml) {
        try {
            Document doc = parseXmlDocument(xml);
            String fault = extractSoapFault(doc);
            if (fault != null) {
                return new UpdateBillingResponse("ERROR", null, BigDecimal.ZERO, fault);
            }
            String status = getElementText(doc, "Status", "UPDATED");
            String billingId = getElementText(doc, "BillingId", "");
            BigDecimal rem = parseDecimal(getElementText(doc, "NewRemainingAmount", "0.00"));
            String message = getElementText(doc, "Message", "Customer billing record updated in T24");
            return new UpdateBillingResponse(status, billingId, rem, message);
        } catch (Exception e) {
            return new UpdateBillingResponse("ERROR", null, BigDecimal.ZERO, "Failed to parse SOAP response: " + e.getMessage());
        }
    }

    private Document parseXmlDocument(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Detects a SOAP 1.1/1.2 Fault element in the response body (spec: T24_SAMPLE_PAYLOADS.md
     * &sect;5) and returns a human-readable message if present, or null for a fault-free response.
     * Matched by namespace URI + local name rather than prefix, since a real T24 endpoint may use
     * any prefix (soapenv:, soap:, SOAP-ENV:, ...) for the same Fault element.
     */
    private String extractSoapFault(Document doc) {
        Element fault = firstElementByTagNameNS(doc, SOAP_ENV_NS, "Fault");
        if (fault == null) {
            fault = firstElementByTagNameNS(doc, SOAP12_ENV_NS, "Fault");
        }
        if (fault == null) {
            return null;
        }

        String faultString = getChildText(fault, "faultstring");
        String faultCode = getChildText(fault, "faultcode");
        String errorCode = getChildText(fault, "ErrorCode");

        String message = !faultString.isEmpty() ? faultString
                : (!faultCode.isEmpty() ? faultCode : "T24 returned a SOAP fault");
        return errorCode.isEmpty() ? message : message + " (" + errorCode + ")";
    }

    private Element firstElementByTagNameNS(Document doc, String namespaceUri, String localName) {
        NodeList nl = doc.getElementsByTagNameNS(namespaceUri, localName);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private String getElementText(Document doc, String tagName, String defaultVal) {
        NodeList nl = doc.getElementsByTagName(tagName);
        if (nl.getLength() == 0) {
            nl = doc.getElementsByTagName("t24:" + tagName);
        }
        if (nl.getLength() > 0 && nl.item(0).getTextContent() != null) {
            return nl.item(0).getTextContent().trim();
        }
        return defaultVal;
    }

    private String getChildText(Element parent, String childName) {
        NodeList nl = parent.getElementsByTagName(childName);
        if (nl.getLength() == 0) {
            nl = parent.getElementsByTagName("t24:" + childName);
        }
        if (nl.getLength() > 0 && nl.item(0).getTextContent() != null) {
            return nl.item(0).getTextContent().trim();
        }
        return "";
    }

    private BigDecimal parseDecimal(String val) {
        if (val == null || val.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(val.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
