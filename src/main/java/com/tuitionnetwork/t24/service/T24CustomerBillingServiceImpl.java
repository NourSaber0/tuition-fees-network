package com.tuitionnetwork.t24.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.t24.client.T24CustomerBillingClient;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.RetrieveBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class T24CustomerBillingServiceImpl implements T24CustomerBillingService {

    private final T24CustomerBillingClient client;
    private final IdentityResolverService identityResolverService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public T24CustomerBillingServiceImpl(
            T24CustomerBillingClient client,
            @Autowired(required = false) IdentityResolverService identityResolverService,
            @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.client = client;
        this.identityResolverService = identityResolverService;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public RetrieveBillingResponse retrieveCustomerDues(String nationalId, String accountNumber, UUID actorId) {
        logAudit(actorId, "T24_RETRIEVE_CUSTOMER_BILLING",
                "Retrieved billing from T24 for " + sanitizeNationalId(nationalId));
        return client.retrieveCustomerBilling(nationalId, accountNumber);
    }

    @Override
    public RequestBillingResponse registerFeeInT24(RequestBillingRequest request, UUID actorId) {
        logAudit(actorId, "T24_REQUEST_CUSTOMER_BILLING",
                "Registered new fee in T24 for institution: " + request.institutionCode());
        return client.requestCustomerBilling(request);
    }

    @Override
    public UpdateBillingResponse syncPaymentSettlement(UpdateBillingRequest request, UUID actorId) {
        logAudit(actorId, "T24_UPDATE_CUSTOMER_BILLING",
                "Updated T24 billing balance for " + request.billingId() + " (Paid: " + request.amountPaid() + ")");
        return client.updateCustomerBilling(request);
    }

    @Override
    public String getWsdlDescription() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                                  xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                                  xmlns:tns="http://temenos.com/customerbilling"
                                  targetNamespace="http://temenos.com/customerbilling">
                   <wsdl:types>
                      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="http://temenos.com/customerbilling">
                         <xs:element name="RetrieveCustomerBillingProcedure">
                            <xs:complexType>
                               <xs:sequence>
                                  <xs:element name="NationalId" type="xs:string" minOccurs="0"/>
                                  <xs:element name="AccountNumber" type="xs:string" minOccurs="0"/>
                               </xs:sequence>
                            </xs:complexType>
                         </xs:element>
                         <xs:element name="RequestCustomerBillingProcedure">
                            <xs:complexType>
                               <xs:sequence>
                                  <xs:element name="InstitutionCode" type="xs:string"/>
                                  <xs:element name="StudentNationalId" type="xs:string"/>
                                  <xs:element name="StudentName" type="xs:string"/>
                                  <xs:element name="FeeType" type="xs:string"/>
                                  <xs:element name="Amount" type="xs:decimal"/>
                                  <xs:element name="Currency" type="xs:string"/>
                                  <xs:element name="AcademicPeriod" type="xs:string"/>
                                  <xs:element name="DueDate" type="xs:date"/>
                               </xs:sequence>
                            </xs:complexType>
                         </xs:element>
                         <xs:element name="UpdateCustomerBillingProcedure">
                            <xs:complexType>
                               <xs:sequence>
                                  <xs:element name="BillingId" type="xs:string"/>
                                  <xs:element name="AmountPaid" type="xs:decimal"/>
                                  <xs:element name="PaymentMethod" type="xs:string"/>
                                  <xs:element name="TransactionReference" type="xs:string"/>
                                  <xs:element name="NewRemainingAmount" type="xs:decimal"/>
                               </xs:sequence>
                            </xs:complexType>
                         </xs:element>
                      </xs:schema>
                   </wsdl:types>
                   <wsdl:portType name="CustomerBillingPortType">
                      <wsdl:operation name="RetrieveCustomerBillingProcedure"/>
                      <wsdl:operation name="RequestCustomerBillingProcedure"/>
                      <wsdl:operation name="UpdateCustomerBillingProcedure"/>
                   </wsdl:portType>
                   <wsdl:service name="CustomerBillingService">
                      <wsdl:port name="CustomerBillingPort" binding="tns:CustomerBillingBinding">
                         <soap:address location="http://localhost:8080/soap/customerbilling"/>
                      </wsdl:port>
                   </wsdl:service>
                </wsdl:definitions>
                """.trim();
    }

    private void logAudit(UUID actorId, String action, String details) {
        if (auditLogRepository != null) {
            try {
                auditLogRepository.save(new AuditLog(actorId, "T24_ADAPTER", action, details));
            } catch (Exception ignored) {}
        }
    }

    private String sanitizeNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) return "ANONYMOUS";
        if (identityResolverService != null) {
            return "HMAC:" + identityResolverService.computeHmacSha256(nationalId.trim());
        }
        return "ID:***" + (nationalId.length() >= 4 ? nationalId.substring(nationalId.length() - 4) : "");
    }
}
