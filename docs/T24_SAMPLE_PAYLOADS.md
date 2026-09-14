# T24 CustomerBilling SOAP / REST Adapter Specifications & Sample Payloads

**Reference:** `docs/T24 Integration.csv` & `docs/Development_Plan.csv` (Task `P0-05`, `P1-03`, `P2-01`, `P3-02`, `P4-01`, `P5-03`)  
**Source:** Postman workspace `MOCKFees`  
**Base Namespace:** `http://temenos.com/customerbilling`  
**Endpoint:** `/soap/customerbilling` (SOAP 1.1 / 1.2) & `/api/v1/t24/billing/**` (REST Adapter)

---

## 1. Overview & Architectural Mapping

The Tuition Network Backend acts as an enterprise facade and adapter that bridges modern JSON/REST clients (School Portal, Back-Office Portal, Guardian App) with Temenos T24 Core Banking CustomerBilling SOAP services.

| Procedure | SOAP Operation | REST Adapter Endpoint | Application Trigger |
|---|---|---|---|
| **Base** | `customerbilling` | `GET /api/v1/t24/billing/wsdl` | Root SOAP envelope, auth headers, and WSDL descriptor |
| **Read** | `RetrieveCustomerBillingProcedure` | `GET /api/v1/t24/billing/retrieve` | National ID Dues Search (`P1-03`, `P2-01`) |
| **Create** | `RequestCustomerBillingProcedure` | `POST /api/v1/t24/billing/request` | Institution CSV Fee Upload (`P4-01`) |
| **Update** | `UpdateCustomerBillingProcedure` | `POST /api/v1/t24/billing/update` | Payment Settlement (`P3-02`) |

---

## 2. Operation 1: RetrieveCustomerBillingProcedure

### SOAP Request XML
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
   <soapenv:Header>
      <t24:AuthHeader>
         <t24:Username>CIB_USER</t24:Username>
         <t24:Password>CIB_PASS</t24:Password>
         <t24:ChannelId>CIB_TUITION_NETWORK</t24:ChannelId>
      </t24:AuthHeader>
   </soapenv:Header>
   <soapenv:Body>
      <t24:RetrieveCustomerBillingProcedure>
         <t24:NationalId>29805150101023</t24:NationalId>
         <t24:AccountNumber>100012345678</t24:AccountNumber>
      </t24:RetrieveCustomerBillingProcedure>
   </soapenv:Body>
</soapenv:Envelope>
```

### SOAP Response XML (Success)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
   <soapenv:Body>
      <t24:RetrieveCustomerBillingResponse>
         <t24:Status>SUCCESS</t24:Status>
         <t24:CustomerNumber>CIF-109283</t24:CustomerNumber>
         <t24:AccountNumber>100012345678</t24:AccountNumber>
         <t24:CustomerName>Adel Mostafa</t24:CustomerName>
         <t24:BillingItem>
            <t24:BillingId>T24-BILL-00101</t24:BillingId>
            <t24:InstitutionCode>SCH-001</t24:InstitutionCode>
            <t24:InstitutionName>Cairo International School</t24:InstitutionName>
            <t24:StudentNationalId>31005120104921</t24:StudentNationalId>
            <t24:StudentName>Yousef Adel</t24:StudentName>
            <t24:FeeType>Tuition</t24:FeeType>
            <t24:AcademicPeriod>Term 1 2026/27</t24:AcademicPeriod>
            <t24:OriginalAmount>18000.00</t24:OriginalAmount>
            <t24:PaidAmount>0.00</t24:PaidAmount>
            <t24:RemainingAmount>18000.00</t24:RemainingAmount>
            <t24:DueDate>2026-10-15</t24:DueDate>
            <t24:Status>Outstanding</t24:Status>
         </t24:BillingItem>
         <t24:BillingItem>
            <t24:BillingId>T24-BILL-00102</t24:BillingId>
            <t24:InstitutionCode>SCH-001</t24:InstitutionCode>
            <t24:InstitutionName>Cairo International School</t24:InstitutionName>
            <t24:StudentNationalId>31005120104921</t24:StudentNationalId>
            <t24:StudentName>Yousef Adel</t24:StudentName>
            <t24:FeeType>Bus subscription</t24:FeeType>
            <t24:AcademicPeriod>Term 1 2026/27</t24:AcademicPeriod>
            <t24:OriginalAmount>4000.00</t24:OriginalAmount>
            <t24:PaidAmount>0.00</t24:PaidAmount>
            <t24:RemainingAmount>4000.00</t24:RemainingAmount>
            <t24:DueDate>2026-10-15</t24:DueDate>
            <t24:Status>Outstanding</t24:Status>
         </t24:BillingItem>
         <t24:TotalOutstanding>22000.00</t24:TotalOutstanding>
         <t24:Message>Retrieved 2 customer billing records from T24 mock</t24:Message>
      </t24:RetrieveCustomerBillingResponse>
   </soapenv:Body>
</soapenv:Envelope>
```

### REST Equivalent (`GET /api/v1/t24/billing/retrieve?nationalId=29805150101023`)
```json
{
  "status": "SUCCESS",
  "customerNumber": "CIF-109283",
  "accountNumber": "100012345678",
  "customerName": "Adel Mostafa",
  "items": [
    {
      "billingId": "T24-BILL-00101",
      "institutionCode": "SCH-001",
      "institutionName": "Cairo International School",
      "studentNationalId": "31005120104921",
      "studentName": "Yousef Adel",
      "feeType": "Tuition",
      "academicPeriod": "Term 1 2026/27",
      "originalAmount": 18000.00,
      "paidAmount": 0.00,
      "remainingAmount": 18000.00,
      "dueDate": "2026-10-15",
      "status": "Outstanding"
    }
  ],
  "totalOutstanding": 22000.00,
  "message": "Retrieved 2 customer billing records from T24 mock"
}
```

---

## 3. Operation 2: RequestCustomerBillingProcedure

### SOAP Request XML
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
   <soapenv:Header>
      <t24:AuthHeader>
         <t24:Username>CIB_USER</t24:Username>
         <t24:Password>CIB_PASS</t24:Password>
         <t24:ChannelId>CIB_TUITION_NETWORK</t24:ChannelId>
      </t24:AuthHeader>
   </soapenv:Header>
   <soapenv:Body>
      <t24:RequestCustomerBillingProcedure>
         <t24:InstitutionCode>SCH-001</t24:InstitutionCode>
         <t24:StudentNationalId>31005120104921</t24:StudentNationalId>
         <t24:StudentName>Yousef Adel</t24:StudentName>
         <t24:FeeType>Tuition</t24:FeeType>
         <t24:Amount>18000.00</t24:Amount>
         <t24:Currency>EGP</t24:Currency>
         <t24:AcademicPeriod>Term 1 2026/27</t24:AcademicPeriod>
         <t24:DueDate>2026-10-15</t24:DueDate>
      </t24:RequestCustomerBillingProcedure>
   </soapenv:Body>
</soapenv:Envelope>
```

### SOAP Response XML (Success)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
   <soapenv:Body>
      <t24:RequestCustomerBillingResponse>
         <t24:Status>CREATED</t24:Status>
         <t24:BillingId>T24-BILL-8A7B9C10</t24:BillingId>
         <t24:Message>Successfully created billing item in T24 mock for student: 31005120104921</t24:Message>
      </t24:RequestCustomerBillingResponse>
   </soapenv:Body>
</soapenv:Envelope>
```

### REST Equivalent (`POST /api/v1/t24/billing/request`)
```json
// Request Body
{
  "institutionCode": "SCH-001",
  "studentNationalId": "31005120104921",
  "studentName": "Yousef Adel",
  "feeType": "Tuition",
  "amount": 18000.00,
  "currency": "EGP",
  "academicPeriod": "Term 1 2026/27",
  "dueDate": "2026-10-15"
}

// Response 201 Created
{
  "status": "CREATED",
  "billingId": "T24-BILL-8A7B9C10",
  "message": "Successfully created billing item in T24 mock for student: 31005120104921"
}
```

---

## 4. Operation 3: UpdateCustomerBillingProcedure

### SOAP Request XML
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
   <soapenv:Header>
      <t24:AuthHeader>
         <t24:Username>CIB_USER</t24:Username>
         <t24:Password>CIB_PASS</t24:Password>
         <t24:ChannelId>CIB_TUITION_NETWORK</t24:ChannelId>
      </t24:AuthHeader>
   </soapenv:Header>
   <soapenv:Body>
      <t24:UpdateCustomerBillingProcedure>
         <t24:BillingId>T24-BILL-00101</t24:BillingId>
         <t24:AmountPaid>18000.00</t24:AmountPaid>
         <t24:PaymentMethod>CREDIT_CARD</t24:PaymentMethod>
         <t24:TransactionReference>TXN-20260914-0981</t24:TransactionReference>
         <t24:NewRemainingAmount>0.00</t24:NewRemainingAmount>
      </t24:UpdateCustomerBillingProcedure>
   </soapenv:Body>
</soapenv:Envelope>
```

### SOAP Response XML (Success)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:t24="http://temenos.com/customerbilling">
   <soapenv:Body>
      <t24:UpdateCustomerBillingResponse>
         <t24:Status>UPDATED</t24:Status>
         <t24:BillingId>T24-BILL-00101</t24:BillingId>
         <t24:NewRemainingAmount>0.00</t24:NewRemainingAmount>
         <t24:Message>Successfully updated T24 billing record balance</t24:Message>
      </t24:UpdateCustomerBillingResponse>
   </soapenv:Body>
</soapenv:Envelope>
```

### REST Equivalent (`POST /api/v1/t24/billing/update`)
```json
// Request Body
{
  "billingId": "T24-BILL-00101",
  "amountPaid": 18000.00,
  "paymentMethod": "CREDIT_CARD",
  "transactionReference": "TXN-20260914-0981",
  "newRemainingAmount": 0.00
}

// Response 200 OK
{
  "status": "UPDATED",
  "billingId": "T24-BILL-00101",
  "newRemainingAmount": 0.00,
  "message": "Successfully updated T24 billing record balance"
}
```

---

## 5. SOAP Fault XML (Error Example)

```xml
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
```
