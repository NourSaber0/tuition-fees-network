# Education Payment System - Architecture Flowchart
## Comprehensive Flow & Architecture Diagram

Below is the complete architectural flowchart for the **Tuition & Services Fees Collection Network**, showing all access channels, RBAC security filters, core modules, synchronous and asynchronous message boundaries, and external banking adapters.

```mermaid
flowchart TD
    %% Channels Layer
    subgraph Channels ["Channels & Client Portals"]
        direction LR
        BOP["Back-Office Portal<br/>(Bank Teller / Support Staff)<br/><b>Role: ROLE_BACK_OFFICE</b>"]
        IP["Institution Portal<br/>(School Finance Admin)<br/><b>Role: ROLE_INSTITUTION_ADMIN</b>"]
        MP["Guardian Portal & Mobile App<br/>(Parents / Students)<br/><b>Role: ROLE_GUARDIAN</b>"]
    end

    %% Security & API Gateway Layer
    subgraph SecurityGateway ["Security & Web Layer (Spring Security 6)"]
        direction TB
        FW["SecurityFilterChain (/api/v1/** Authenticated)"]
        RBAC{"Method-Level RBAC<br/>(@EnableMethodSecurity / @PreAuthorize)"}
        FW --> RBAC
    end

    Channels -->|HTTP JSON Requests + JWT / Session| FW

    %% Core Services Layer (Modular Monolith)
    subgraph CoreServices ["Core Business Modules (Spring Boot & Spring Modulith)"]
        direction TB
        
        %% Identity Module
        subgraph ModIdentity ["1. Identity Module"]
            ID_AUTH["IdentityUserDetailsService<br/>(BankEmployee, InstitutionAdmin, Guardian)"]
            ID_RES["IdentityResolverServiceImpl<br/>(@PreAuthorize hasRole BACK_OFFICE)"]
            HMAC_ENG["HMAC-SHA256 Privacy Engine<br/>(Deterministic National ID Hashing)"]
            ID_RES --> HMAC_ENG
        end

        %% Audit Module
        subgraph ModAudit ["2. Audit & Compliance Module"]
            AUDIT_REPO[("AUDIT_LOG Table<br/>- actor_id, actor_type<br/>- action, target_resource (HMAC)<br/>- timestamp")]
        end

        %% Ingestion Module
        subgraph ModIngestion ["3. Ingestion Module"]
            ING_SVC["IngestionService<br/>- 5-Field Contract Validation<br/>- Computes row_idempotency_key"]
            UP_ERR[("UPLOAD_ERROR & CSV_UPLOAD Tables<br/>(Row-level Error Isolation)")]
            ING_SVC --> UP_ERR
        end

        %% Search & Aggregation Module
        subgraph ModSearch ["4. Search & Aggregation Module"]
            SCH_SVC["SearchService<br/>(@PreAuthorize hasRole BACK_OFFICE)"]
            DUES_RESP["Aggregated Guardian Dues Response<br/>(Multi-Child, Multi-School Ledgers)"]
            SCH_SVC --> DUES_RESP
        end

        %% Billing Module
        subgraph ModBilling ["5. Billing Module"]
            BILL_CMD["BillingFeeCommandService<br/>(Idempotent Fee Creation)"]
            BILL_QRY["BillingFeeQueryService<br/>(Institution & Student Dues SPI)"]
            FEE_DB[("FEE_LINE Table<br/>- total, paid, remaining<br/>- status: OUTSTANDING/PAID<br/>- @Version Optimistic Lock<br/>- row_idempotency_key UK")]
            BILL_CMD --> FEE_DB
            BILL_QRY --> FEE_DB
        end

        %% Payments Module (The Financial Core)
        subgraph ModPayments ["6. Payment Settlement Engine"]
            PAY_SVC["PaymentSettlementService<br/><b>Fintech Paranoia Guardrails:</b><br/>1. Idempotency & Tamper Check (409 Conflict)<br/>2. Overpayment Block (paid <= remaining)<br/>3. EPP Debit Card Block (Credit Card only)<br/>4. Post-Deadline Partial Payment Guard"]
            PAY_EXEC["PaymentTransactionExecutor<br/>(@Transactional Boundary)<br/>- Deducts FeeLine balance<br/>- Checks @Version lock<br/>- Saves Payment & PaymentAllocation"]
            PAY_DB[("PAYMENT, ALLOCATION & STATE_LOG Tables")]
            PAY_SVC --> PAY_EXEC
            PAY_EXEC --> PAY_DB
        end

        %% Outbox Event Broker
        MQ[("Spring Modulith Event Outbox<br/>(PaymentCapturedEvent)")]
        
        %% Asynchronous Fulfillment Modules
        subgraph ModAsync ["7. Asynchronous Fulfillment Listeners"]
            EPP["EPP Schedule Generator<br/>(@ApplicationModuleListener)<br/>- 0% 3-Month Promo<br/>- 14% Flat Interest (6/12/18M)<br/>- 1% Admin Fee (Cap 500 EGP)"]
            REC["Receipt Generator<br/>(@ApplicationModuleListener)<br/>- SHA-256 Cryptographic Sig<br/>- Digital PDF Receipt URL"]
            NOT["Payment Notification Service<br/>(@ApplicationModuleListener)<br/>- SMS Dispatch Simulation"]
        end
    end

    %% RBAC to Core Module Routing
    RBAC -->|hasRole BACK_OFFICE| SCH_SVC
    RBAC -->|hasRole INSTITUTION_ADMIN| ING_SVC
    RBAC -->|hasRole INSTITUTION_ADMIN| BILL_QRY
    RBAC -->|hasRole GUARDIAN or BACK_OFFICE| PAY_SVC

    %% Module Cross-Talk
    SCH_SVC -->|1. Resolve Guardian Profile| ID_RES
    ID_RES -->|2. Log Privacy Search| AUDIT_REPO
    SCH_SVC -->|3. Query Open Fees Across Schools| BILL_QRY
    ING_SVC -->|Bulk Provision Valid Fees| BILL_CMD
    ING_SVC -->|Log CSV Ingestion Audit| AUDIT_REPO
    BILL_QRY -->|Log Ledger Access Audit| AUDIT_REPO

    %% Payment Execution Cross-Talk
    PAY_EXEC -->|Atomically Mutate Balances| FEE_DB
    PAY_EXEC -->|Publish Domain Event| MQ
    MQ -.->|Async Event| EPP
    MQ -.->|Async Event| REC
    MQ -.->|Async Event| NOT

    %% External Banking Integration Layer
    subgraph BankIntegrations ["External Banking Infrastructure"]
        direction LR
        BAL(("BankGatewayAdapterInterface<br/>(Non-Transactional Port)"))
        MOCK_BANK["MockBankAdapterImpl<br/>(Deterministic Auth, Capture, EPP Plan)"]
        REAL_BANK["Core Banking Integration<br/>(CIB Direct Debit API, Card Payment Gateway)"]
        BAL --> MOCK_BANK
        BAL --> REAL_BANK
    end

    PAY_SVC -->|Non-Transactional Authorization (chargeCard)| BAL

    %% Styling
    classDef gateway fill:#f9f2f4,stroke:#d3b8c0,stroke-width:2px;
    classDef security fill:#fff2cc,stroke:#d6b656,stroke-width:2px;
    classDef core fill:#eef3f8,stroke:#b2cce5,stroke-width:2px;
    classDef queue fill:#e2f0d9,stroke:#548235,stroke-width:2px;
    classDef adapter fill:#fcf8e3,stroke:#faebcc,stroke-width:2px;
    classDef db fill:#f5f5f5,stroke:#666666,stroke-width:2px;
    
    class FW,RBAC security;
    class ModIdentity,ModAudit,ModIngestion,ModSearch,ModBilling,ModPayments,ModAsync core;
    class MQ queue;
    class BAL adapter;
    class AUDIT_REPO,UP_ERR,FEE_DB,PAY_DB db;
```

---

### Key Architectural Flows Illustrated Above

1. **Back-Office Citizen Dues Lookup:**
   * Bank Employee (`ROLE_BACK_OFFICE`) provides a 14-digit National ID.
   * `IdentityResolverServiceImpl` computes keyed **HMAC-SHA256** and records an immutable entry in `AUDIT_LOG`.
   * `SearchService` calls `BillingFeeQueryService` to consolidate unpaid fees across all linked schools into a single response.

2. **Institutional Ingestion & Error Isolation:**
   * School Admin (`ROLE_INSTITUTION_ADMIN`) uploads a 5-field CSV.
   * `IngestionService` validates rows, computes `row_idempotency_key = SHA256(Student+Inst+Type+Period)`, isolates invalid rows into `UPLOAD_ERROR`, provisions valid rows via `BillingFeeCommandService`, and logs the upload in `AUDIT_LOG`.

3. **Payment Settlement & Fintech Paranoia Guardrails:**
   * Enforces 4 financial guardrails:
     1. **Idempotency Tamper Protection:** Rejects modified payload amounts under an existing key (`409 Conflict`).
     2. **Overpayment Block:** Strictly forbids `paid_amount > remaining_amount`.
     3. **Debit Card EPP Block:** Strictly restricts EPP installment plans to Credit Cards.
     4. **Post-Deadline Partial Payment Guard:** Forbids partial payments on overdue fees after deadline.
   * Authorizes card outside DB transactions via `BankGatewayAdapterInterface`.
   * Commits updates atomically in `PaymentTransactionExecutor` with Hibernate `@Version` optimistic locking.

4. **Asynchronous Outbox Fulfillment:**
   * `PaymentCapturedEvent` is published into Spring Modulith's outbox.
   * Event listeners asynchronously book the EPP schedule, issue a SHA-256 cryptographically signed receipt PDF URL, and simulate SMS dispatch to the parent's mobile phone.
