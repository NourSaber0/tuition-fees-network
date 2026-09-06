# Tuition & Services Fees Collection Network
## Comprehensive Architecture, Domain Model & Technical Reference Manual

---

### Table of Contents
1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
2. [Core Concepts & Architectural Terminology](#2-core-concepts--architectural-terminology)
3. [Architectural Decisions: Why It Was Done This Way](#3-architectural-decisions-why-it-was-done-this-way)
4. [File-by-File Technical Inventory (Deep Dive)](#4-file-by-file-technical-inventory-deep-dive)
   * 4.1 [Identity & Security Module (`identity`)](#41-identity--security-module-comtuitionnetworkidentity)
   * 4.2 [Ingestion & Roster Processing Module (`ingestion`)](#42-ingestion--roster-processing-module-comtuitionnetworkingestion)
   * 4.3 [Search & Dues Aggregation Module (`search`)](#43-search--dues-aggregation-module-comtuitionnetworksearch)
   * 4.4 [Billing & Fee Ledger Module (`billing`)](#44-billing--fee-ledger-module-comtuitionnetworkbilling)
   * 4.5 [Payment Settlement Engine Module (`payments`)](#45-payment-settlement-engine-module-comtuitionnetworkpayments)
   * 4.6 [Equal Payment Plans Engine (`epp`)](#46-equal-payment-plans-engine-comtuitionnetworkepp)
   * 4.7 [Digital Receipts Module (`receipts`)](#47-digital-receipts-module-comtuitionnetworkreceipts)
   * 4.8 [Notifications & Alerts Module (`notifications`)](#48-notifications--alerts-module-comtuitionnetworknotifications)
   * 4.9 [Audit & Compliance Module (`audit`)](#49-audit--compliance-module-comtuitionnetworkaudit)
   * 4.10 [Common Exceptions & Application Bootstrapping](#410-common-exceptions--application-bootstrapping)
5. [End-to-End System Flows from Multiple Perspectives](#5-end-to-end-system-flows-from-multiple-perspectives)
6. [Business Guardrails & Exception Handling](#6-business-guardrails--exception-handling)
7. [Testing Strategy & Verification Results](#7-testing-strategy--verification-results)

---

### 1. Executive Summary & Problem Statement

The **Tuition & Services Fees Collection Network** is a multi-tenant, institutional fee aggregation and payment settlement platform designed for Egypt's education and banking ecosystem.

#### Key Challenges Solved:
1. **Fragmented Invoicing:** Parents with children in different schools or universities receive isolated invoices across disparate banking and cash channels. This system aggregates all outstanding dues under a single parent National ID.
2. **Flexible Financing & EPP (Equal Payment Plans):** Large upfront tuition sums can be converted into 3-month promotional 0% plans or 6/12/18-month bank-backed installment schedules with automated interest and admin fee calculations.
3. **Institutional Bulk Ingestion:** Schools upload CSV rosters containing thousands of student dues. The system performs strict row-level validation, isolating failed rows into database error logs while idempotently provisioning valid fee lines.
4. **Data Privacy & Cryptographic Security:** Egyptian National IDs are protected using keyed HMAC-SHA256 for deterministic search and AES-256-GCM for storage at rest. Every search and upload operation is recorded in an immutable privacy audit trail.
5. **Fintech Paranoia & Concurrency Protection:** Strict idempotency tamper protection, overpayment blocking, debit card installment blocks, and Hibernate `@Version` optimistic locking protect against race conditions and financial spoofing.

---

### 2. Core Concepts & Architectural Terminology

To understand how Spring Boot, JPA, and Modular Monolith architectures operate, here is an explanation of the core building blocks:

```plaintext
┌─────────────────────────────────────────────────────────────────────────────┐
│                          HTTP CLIENT (Web / Mobile)                         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTP JSON Request + Auth Header
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ SECURITY & CONTROLLER LAYER (@RestController & @PreAuthorize)               │
│ - Validates HTTP authentication, headers, and RBAC roles.                   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Calls Service with DTO
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ SERVICE LAYER (@Service)                                                    │
│ - The Business Brain. Orchestrates transactions, calculations, and rules.   │
├──────────────────────────────┬───────────────────────────────┬──────────────┤
│                              │                               │              │
│ Persists State               │ Queries / Mutations           │ Integrations │
│                              │                               │              │
▼                              ▼                               ▼              ▼
┌──────────────────┐   ┌──────────────────┐   ┌────────────────────┐   ┌───────────────┐
│ DOMAIN (@Entity) │   │ REPOSITORY       │   │ SPI (Bank Port)    │   │ DOMAIN EVENT  │
│ - Database table │   │ - Spring Data    │   │ - Interface for    │   │ - Decoupled   │
│   state mapping  │   │   SQL execution  │   │   Bank Gateways    │   │   async bus   │
└──────────────────┘   └──────────────────┘   └────────────────────┘   └───────────────┘
```

#### What is a Domain / Entity (`@Entity`)?
* **Domain:** The core business sphere, containing real-world business models (e.g., invoices, payments, installments, parents, students).
* **`@Entity`:** A Java class mapped to a database table by Hibernate / JPA. Each object instance of an `@Entity` class represents a specific row in the database.
* *Example:* `FeeLine` represents a row in the `fee_line` table with columns like `total_amount`, `remaining_amount`, `due_date`, and `row_idempotency_key`.

#### What is a DTO (Data Transfer Object)?
* A simple data carrier (an immutable Java `record`) without database annotations or business logic.
* **Why DTOs are necessary:**
  1. **Security:** Exposing `@Entity` classes directly over REST APIs can leak internal database structure, version counters, or encrypted hashes.
  2. **API Contract Decoupling:** Database tables can change without breaking client applications.
  3. **Module Encapsulation:** Modules communicate using public DTOs rather than accessing other modules' database tables directly.

#### What is a Repository (`JpaRepository`)?
* A Spring Data interface that acts as an abstraction layer over SQL queries.
* Instead of writing `SELECT * FROM fee_line WHERE student_id = ?`, you declare `List<FeeLine> findByStudentId(UUID studentId);`. Spring Data generates the SQL implementation at runtime.

#### What is a Service (`@Service`)?
* The component containing **business logic**. It coordinates repositories, enforces business validation, computes financial math, and manages transaction boundaries.
* Controllers should never contain business logic, and repositories should never contain business logic—the Service orchestrates both.

#### What is a Controller (`@RestController`)?
* The web gateway that handles incoming HTTP requests (`GET`, `POST`).
* It extracts headers (e.g., `X-Guardian-National-Id`, `Idempotency-Key`), binds JSON request bodies into DTOs, calls the appropriate Service, and maps responses/exceptions into HTTP status codes (`200 OK`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `409 Conflict`, `422 Unprocessable Entity`).

#### What is `@Transactional`?
* Defines an atomic database transaction boundary.
* If a method marked `@Transactional` completes normally, all database changes are permanently committed (**ACID**). If an unhandled exception is thrown, all changes made during that method are **rolled back** automatically, preventing corrupt or half-saved data.

#### What is Optimistic Locking (`@Version`)?
* A concurrency control mechanism. On entities with `@Version private Long version;`, Hibernate increments the version number on every update.
* If two transactions read version `0` simultaneously and both try to update the row, the first transaction commits and sets the version to `1`. The second transaction fails with an `ObjectOptimisticLockingFailureException` because the database version is no longer `0`. This prevents overpayments and race conditions without locking the entire database table.

#### What is an SPI (Service Provider Interface / Port) and Infrastructure (Adapter)?
* **SPI (Port):** An interface defined by our business core (`BankGatewayAdapterInterface`) specifying what external capabilities are required, independent of any vendor.
* **Infrastructure (Adapter):** The concrete implementation (`MockBankAdapterImpl` or `RealCibBankAdapterImpl`). This prevents external vendor code from polluting core business logic.

#### What is a Domain Event (`PaymentCapturedEvent`) & Outbox Pattern?
* An event is an immutable record indicating that an action has already occurred in the system.
* **Outbox Pattern:** When a payment commits, Spring Modulith writes the event into the database `event_publication` table inside the same transaction. Asynchronous listeners (`@ApplicationModuleListener`) then pick up the event to generate EPP schedules, digital receipts, and SMS alerts without delaying the primary payment response.

---

### 3. Architectural Decisions: Why It Was Done This Way

#### Decision 1: Non-Transactional Bank Gateway Authorization
* **The Problem:** If you call an external bank card authorization API inside a `@Transactional` block, network latency (2–5 seconds) holds open the database connection. Under high load, database connection pools are exhausted, causing system-wide outages.
* **Our Architecture:** `PaymentSettlementService` calls `bankGatewayAdapter.chargeCard(...)` **outside** any database transaction. Only after the bank responds with `CAPTURED` does it call `PaymentTransactionExecutor` to open a fast (sub-millisecond) `@Transactional` database commit.

#### Decision 2: Keyed HMAC-SHA256 for Privacy-Compliant National ID Search
* **The Problem:** Plain SHA-256 hashes of 14-digit National IDs are vulnerable to rainbow table attacks because the input space is predictable. Plaintext storage violates Egyptian data privacy standards.
* **Our Architecture:** We compute `HMAC-SHA256(rawNationalId, secretKey)`. This provides deterministic database lookup capabilities while making rainbow table decryption impossible without the master secret key. Plaintext National IDs are **never** logged in `AUDIT_LOG` or log files.

#### Decision 3: Spreadsheet Row-Level Error Isolation
* **The Problem:** If a school uploads a CSV with 1,000 students and line 45 has a typo, rejecting the entire file frustrates school admins and delays billing.
* **Our Architecture:** `IngestionService` validates every row independently. Valid rows are provisioned into `FEE_LINE` records using a deterministic `row_idempotency_key = SHA256(Student+Inst+Type+Period)`. Invalid rows are isolated into the `UPLOAD_ERROR` table with exact line numbers and error explanations.

#### Decision 4: Role-Based Access Control (RBAC) & Multi-Tenant Separation
* **The Problem:** School admins should never see other schools' financial data or execute global citizen searches. Bank employees should never modify school fee rosters.
* **Our Architecture:** Distinct JPA entities (`BankEmployee`, `InstitutionAdmin`, `Guardian`) map to distinct Spring Security roles (`ROLE_BACK_OFFICE`, `ROLE_INSTITUTION_ADMIN`, `ROLE_GUARDIAN`). Endpoints and services enforce `@PreAuthorize` guards, ensuring complete tenant isolation.

---

### 4. File-by-File Technical Inventory (Deep Dive)

Below is an exhaustive, technical inventory of every file in the codebase, detailing its exact signature, properties, responsibilities, methods, and architectural connections.

---

#### 4.1 Identity & Security Module (`com.tuitionnetwork.identity`)

This module manages user personas, deterministic cryptographic hashing for Egyptian National IDs, Spring Security integration, and RBAC enforcement.

##### Domain Entities & Repositories:
1. **[`BankEmployee.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/domain/BankEmployee.java)** (`@Entity @Table(name = "bank_employee")`)
   * **Purpose:** Represents back-office bank personnel (tellers, operations, support staff).
   * **Properties:** `id` (UUID PK), `name` (String), `email` (String UK), `employeeId` (String UK), `passwordHash` (String), `department` (String, e.g., *"Operations"*, *"Branch Support"*).
   * **Role Mapping:** Mapped to Spring Security authority `ROLE_BACK_OFFICE`.
2. **[`BankEmployeeRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/repository/BankEmployeeRepository.java)** (`@Repository`)
   * **Methods:** `findByEmail(String email)`, `findByEmployeeId(String employeeId)`.
3. **[`Guardian.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/domain/Guardian.java)** (`@Entity @Table(name = "guardian")`)
   * **Purpose:** Represents parents and legal guardians responsible for paying student dues.
   * **Properties:** `id` (UUID PK), `nationalIdHash` (String UK, keyed HMAC-SHA256), `nationalIdEncrypted` (String, AES-256-GCM ciphertext), `name` (String), `email` (String UK), `phone` (String), `passwordHash` (String), `isCibAccountLinked` (boolean).
   * **Role Mapping:** Mapped to Spring Security authority `ROLE_GUARDIAN`.
4. **[`GuardianRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/repository/GuardianRepository.java)** (`@Repository`)
   * **Methods:** `findByNationalIdHash(String nationalIdHash)`, `findByEmail(String email)`.
5. **[`InstitutionAdmin.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/domain/InstitutionAdmin.java)** (`@Entity @Table(name = "institution_admin")`)
   * **Purpose:** Represents school and university finance administrators.
   * **Properties:** `id` (UUID PK), `institutionId` (UUID FK), `name` (String), `email` (String UK), `passwordHash` (String), `role` (String, e.g., *"Finance"*, *"SuperAdmin"*).
   * **Role Mapping:** Mapped to Spring Security authority `ROLE_INSTITUTION_ADMIN`.
6. **[`InstitutionAdminRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/repository/InstitutionAdminRepository.java)** (`@Repository`)
   * **Methods:** `findByEmail(String email)`, `findByInstitutionId(UUID institutionId)`.
7. **[`Student.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/domain/Student.java)** (`@Entity @Table(name = "student")`)
   * **Purpose:** Represents enrolled students linked to a parent and an educational institution.
   * **Properties:** `id` (UUID PK), `guardianId` (UUID FK), `institutionId` (UUID FK), `nationalIdHash` (String), `nationalIdEncrypted` (String), `fullName` (String), `dateOfBirth` (LocalDate).
8. **[`StudentRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/repository/StudentRepository.java)** (`@Repository`)
   * **Methods:** `findByGuardianId(UUID guardianId)`, `findByInstitutionId(UUID institutionId)`.
9. **[`Institution.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/domain/Institution.java)** (`@Entity @Table(name = "institution")`)
   * **Purpose:** Represents partner schools, academies, and universities.
   * **Properties:** `id` (UUID PK), `name` (String), `code` (String UK), `eppCommissionPolicy` (String, e.g., *"PAID_BY_INSTITUTION"*).
10. **[`InstitutionRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/repository/InstitutionRepository.java)** (`@Repository`)
    * **Methods:** `findByCode(String code)`.

##### Security & Service Components:
11. **[`SecurityConfig.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/security/SecurityConfig.java)** (`@Configuration @EnableWebSecurity @EnableMethodSecurity`)
    * **Purpose:** Defines the Spring Security 6 filter chain.
    * **Rules:** Requires authentication for `/api/v1/**` routes, disables CSRF for stateless REST execution, registers HTTP Basic authentication with custom `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` to guarantee unauthenticated requests receive `401 Unauthorized`.
12. **[`UserRole.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/security/UserRole.java)**
    * **Constants:** `ROLE_BACK_OFFICE`, `ROLE_INSTITUTION_ADMIN`, `ROLE_GUARDIAN`.
13. **[`SecurityUserPrincipal.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/security/SecurityUserPrincipal.java)** (`record implements UserDetails`)
    * **Fields:** `UUID userId`, `String username`, `String fullName`, `String role`.
14. **[`IdentityUserDetailsService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/security/IdentityUserDetailsService.java)** (`@Service implements UserDetailsService`)
    * **Logic:** Searches across `BankEmployeeRepository`, `InstitutionAdminRepository`, and `GuardianRepository` in sequence to construct an authenticated `SecurityUserPrincipal` with matching authorities.
15. **[`IdentityResolverService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/IdentityResolverService.java)** (Interface)
    * **Contract:** `String computeHmacSha256(String rawNationalId)`, `Optional<ResolvedGuardianDto> resolveGuardianByNationalId(String rawNationalId)`.
16. **[`IdentityResolverServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/IdentityResolverServiceImpl.java)** (`@Service`)
    * **Security:** Protected with `@PreAuthorize("hasRole('BACK_OFFICE')")`.
    * **Logic:** Computes HMAC-SHA256, inserts a search audit log record into `AUDIT_LOG` containing the bank employee's ID and the HMAC hash, loads guardian and student entities, and returns mapped `ResolvedGuardianDto` (or `Optional.empty()`, strictly with no mock fallback).
17. **[`ResolvedGuardianDto.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/dto/ResolvedGuardianDto.java)** & **[`ResolvedStudentDto.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/dto/ResolvedStudentDto.java)** (Public Records)
    * **Payloads:** Encapsulated carrier DTOs transferring resolved identity information across module boundaries.

---

#### 4.2 Ingestion & Roster Processing Module (`com.tuitionnetwork.ingestion`)

This module enables educational institutions to upload bulk CSV spreadsheets of student dues with row-level error isolation and deduplication.

1. **[`CsvIngestionController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/CsvIngestionController.java)** (`@RestController`)
   * **Path:** `POST /api/v1/institutions/{id}/dues/upload`
   * **Security:** `@PreAuthorize("hasRole('INSTITUTION_ADMIN')")`
   * **Logic:** Receives multipart CSV files and calls `IngestionService.processCsvUpload(institutionId, file)`.
2. **[`IngestionService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/service/IngestionService.java)** (`@Service`)
   * **5-Field Row Validation:**
     * `National_ID`: Regex `^\d{14}$`.
     * `Fee_Type`: One of `Tuition`, `Bus subscription`, `Books & materials`, `Activities`.
     * `Amount`: Decimal $> 0$.
     * `Currency`: Strictly `EGP`.
     * `Collection_Period`: Non-past academic period (e.g. `Term 2 · 2026`).
   * **Deduplication Key:** Computes `row_idempotency_key = SHA256(Student + Institution + FeeType + Period)`.
   * **Error Isolation:** Invalid rows are collected as `RowValidationError` and saved into the `upload_error` table.
   * **Batch & Audit Logging:** Persists `CsvUpload`, `IngestionBatch`, and an `AUDIT_LOG` record (`action = "UPLOAD_DUES_CSV"`).
   * **Dispatch:** Sends valid rows to `BillingFeeCommandService.createFeeLines(...)`.
3. **[`CsvUpload.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/domain/CsvUpload.java)** (`@Entity @Table(name = "csv_upload")`)
   * **Properties:** `id` (UUID PK), `institutionId` (UUID), `fileName` (String), `totalRows` (int), `failedRows` (int), `uploadedAt` (LocalDateTime), `uploadErrors` (`@OneToMany`).
4. **[`UploadError.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/domain/UploadError.java)** (`@Entity @Table(name = "upload_error")`)
   * **Properties:** `id` (UUID PK), `csvUpload` (`@ManyToOne`), `rowNumber` (int), `errorMessage` (String), `rawRowData` (String).
5. **[`IngestionBatch.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/domain/IngestionBatch.java)** (`@Entity @Table(name = "ingestion_batch")`)
   * **Properties:** `id` (UUID PK), `institutionId` (UUID), `fileName` (String), `fileHashSha256` (String), `totalRows` (int), `successfulRows` (int), `failedRows` (int), `createdAt` (LocalDateTime).
6. **[`IngestionReportResponse.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/dto/IngestionReportResponse.java)** & **[`RowValidationError.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/dto/RowValidationError.java)** (Records)
   * **Payloads:** Returned to the school admin displaying summary counts and row-by-row error details.

---

#### 4.3 Search & Dues Aggregation Module (`com.tuitionnetwork.search`)

This module orchestrates citizen dues discovery across multiple children and multiple educational institutions.

1. **[`DuesSearchController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/search/web/DuesSearchController.java)** (`@RestController`)
   * **Path:** `GET /api/v1/guardian/dues`
   * **Security:** `@PreAuthorize("hasRole('BACK_OFFICE')")`
   * **Headers / Params:** Accepts `X-Guardian-National-Id` header or `parentNationalId` query param.
2. **[`SearchService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/search/service/SearchService.java)** (`@Service`)
   * **Logic:** Coordinates identity resolution with `IdentityResolverService`. If not found, throws `ResponseStatusException(HttpStatus.NOT_FOUND)`. Calls `BillingFeeQueryService.findOpenFeesByStudentIds(...)`, groups fee lines by student, and calculates total outstanding balance.
3. **[`GuardianDuesResponse.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/search/dto/GuardianDuesResponse.java)**, **[`StudentDuesResponse.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/search/dto/StudentDuesResponse.java)**, and **[`FeeLineItemResponse.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/search/dto/FeeLineItemResponse.java)** (Records)
   * **Payloads:** Deliver the consolidated multi-school invoice tree to the client.

---

#### 4.4 Billing & Fee Ledger Module (`com.tuitionnetwork.billing`)

This module maintains the fee invoice state, remaining balances, and optimistic concurrency control.

1. **[`FeeLine.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/domain/FeeLine.java)** (`@Entity @Table(name = "fee_line")`)
   * **Properties:** `id` (UUID PK), `institutionId` (UUID), `studentId` (UUID), `feeType` (`@Enumerated FeeType`), `totalAmount` (BigDecimal), `paidAmount` (BigDecimal), `remainingAmount` (BigDecimal), `currency` (String), `collectionPeriod` (String), `status` (`@Enumerated FeeStatus`), `dueDate` (LocalDate), `rowIdempotencyKey` (String UK), `version` (`@Version Long` for optimistic locking).
2. **[`FeeStatus.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/domain/FeeStatus.java)** (Enum)
   * **Values:** `OUTSTANDING`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`.
3. **[`FeeType.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/domain/FeeType.java)** (Enum)
   * **Values:** `TUITION ("Tuition")`, `BUS_SUBSCRIPTION ("Bus subscription")`, `BOOKS_MATERIALS ("Books & materials")`, `ACTIVITIES ("Activities")`.
4. **[`FeeLineRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/repository/FeeLineRepository.java)** (`@Repository`)
   * **Methods:** `findByStudentIdInAndStatusNot(...)`, `findByInstitutionId(...)`, `findByInstitutionIdAndStudentId(...)`, `findByRowIdempotencyKey(...)`.
5. **[`BillingFeeCommandServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/service/BillingFeeCommandServiceImpl.java)** (`@Service`)
   * **Logic:** Deduplicates against `row_idempotency_key` and creates initial `FeeLine` entries (`status = OUTSTANDING`, `remaining_amount = total_amount`, `paid_amount = 0.00`).
6. **[`BillingFeeQueryServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/service/BillingFeeQueryServiceImpl.java)** (`@Service @Transactional(readOnly = true)`)
   * **Logic:** Implements public SPI queries for open dues across student IDs and institution boundaries.
7. **[`InstitutionDuesController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/billing/web/InstitutionDuesController.java)** (`@RestController`)
   * **Path:** `GET /api/v1/institutions/{id}/dues` & `GET /api/v1/institutions/{id}/students/{studentId}/dues`
   * **Security:** `@PreAuthorize("hasRole('INSTITUTION_ADMIN')")` (Blocks bank employees with `403 Forbidden`).
   * **Audit:** Inserts `AUDIT_LOG` record (`action = "VIEW_STUDENT_DUES"`).

---

#### 4.5 Payment Settlement Engine Module (`com.tuitionnetwork.payments`)

This module handles financial transactions, non-transactional bank authorization, atomic mutations, and fintech guardrails.

1. **[`PaymentController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/web/PaymentController.java)** (`@RestController`)
   * **Path:** `POST /api/v1/payments/settle`
   * **Exception Handlers:** Maps `PendingBusinessRuleException` $\rightarrow$ `422 Unprocessable Entity`, `ResponseStatusException` $\rightarrow$ matching status (e.g. `409 Conflict`), `ObjectOptimisticLockingFailureException` $\rightarrow$ `409 Conflict`.
2. **[`PaymentSettlementService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/service/PaymentSettlementService.java)** (`@Service`)
   * **Fintech Paranoia Guardrail Checks:**
     1. *Idempotency Tamper Protection:* Rejects modified payload amounts under an existing key with `409 Conflict`.
     2. *The Overpayment Block:* Forbids `amountToPay > remainingAmount` for any fee line.
     3. *EPP Debit Card Block:* Rejects Debit Card BINs (`5078`, `5888`, `6703`, `4000`) for EPP financing.
     4. *Post-Deadline Partial Payment Guard:* Forbids partial payments on overdue fees after deadline.
   * **External Authorization:** Calls `BankGatewayAdapterInterface.chargeCard(...)` **outside** DB transactions.
   * **Delegation:** Hands off to `PaymentTransactionExecutor` upon bank capture.
3. **[`PaymentTransactionExecutor.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/service/PaymentTransactionExecutor.java)** (`@Service`)
   * **Boundary:** `@Transactional`
   * **Mutations:** Deducts `FeeLine.remainingAmount`, sets `FeeLine.paidAmount`, checks `@Version` lock, saves `Payment`, saves `PaymentAllocation` records, logs `PaymentStateLog` (`PENDING` $\rightarrow$ `CAPTURED`), and publishes `PaymentCapturedEvent`.
4. **[`Payment.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/Payment.java)** (`@Entity @Table(name = "payment")`)
   * **Properties:** `id` (UUID PK), `guardianId` (UUID), `totalAmount` (BigDecimal), `paymentMethod` (`@Enumerated`), `status` (`@Enumerated`), `transactionReference` (String), `authCode` (String), `idempotencyKey` (String UK), `allocations` (`@OneToMany`), `createdAt` (LocalDateTime).
5. **[`PaymentAllocation.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/PaymentAllocation.java)** (`@Entity @Table(name = "payment_allocation")`)
   * **Properties:** `id` (UUID PK), `payment` (`@ManyToOne`), `feeLine` (`@ManyToOne`), `amountApplied` (BigDecimal).
6. **[`PaymentStateLog.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/PaymentStateLog.java)** (`@Entity @Table(name = "payment_state_log")`)
   * **Properties:** `id` (UUID PK), `paymentId` (UUID), `fromStatus` (String), `toStatus` (String), `reason` (String), `timestamp` (LocalDateTime).
7. **[`BankGatewayAdapterInterface.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/spi/BankGatewayAdapterInterface.java)** (SPI Port Interface)
   * **Methods:** `GatewayResponse chargeCard(BigDecimal amount, String idempotencyKey)`, `boolean verifyCibAccount(String accountNumber)`, `EppPlanResponse generateEppSchedule(BigDecimal principal, int tenorMonths)`.
8. **[`MockBankAdapterImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/infrastructure/MockBankAdapterImpl.java)** (`@Component`)
   * **Purpose:** Deterministic banking simulator returning synthetic auth codes (`AUTH-XXXX`), transaction references (`TXN-XXXX`), and amortization calculations for local development and integration testing.
9. **[`PaymentCapturedEvent.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/event/PaymentCapturedEvent.java)** (Domain Event Record)
   * **Fields:** `UUID paymentId`, `UUID guardianId`, `BigDecimal amount`, `PaymentMethod paymentMethod`, `Integer eppTenorMonths`, `String transactionReference`, `String authCode`, `LocalDateTime timestamp`.

---

#### 4.6 Equal Payment Plans Engine (`com.tuitionnetwork.epp`)

1. **[`EppScheduleGenerator.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/epp/EppScheduleGenerator.java)** (`@Component`)
   * **Listener:** `@ApplicationModuleListener on(PaymentCapturedEvent event)`
   * **Financing Math:**
     * **3 Months:** 0% promotional interest, 0 EGP admin fee.
     * **6, 12, or 18 Months:** 14% flat annual interest + 1% admin fee (capped at 500 EGP).
   * **Persistence:** Saves `EPPSchedule` into the `epp_schedule` table.
2. **[`EPPSchedule.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/EPPSchedule.java)** (`@Entity @Table(name = "epp_schedule")`)
   * **Properties:** `id` (UUID PK), `paymentId` (UUID FK), `tenorMonths` (int), `monthlyInstallment` (BigDecimal), `interestRate` (BigDecimal), `adminFee` (BigDecimal), `totalFinancedAmount` (BigDecimal), `status` (String).
3. **[`EppInstallment.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/EppInstallment.java)** (`@Entity @Table(name = "epp_installment")`)
   * **Purpose:** Read-only projection tracking bank monthly installment status (managed by core banking).

---

#### 4.7 Digital Receipts Module (`com.tuitionnetwork.receipts`)

1. **[`ReceiptGenerator.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/receipts/ReceiptGenerator.java)** (`@Component`)
   * **Listener:** `@ApplicationModuleListener on(PaymentCapturedEvent event)`
   * **Crypto Signature:** Computes `SHA-256(paymentId + amount + authCode + timestamp)`.
   * **Persistence:** Generates downloadable CDN URL (`https://cdn.tuitionnetwork.eg/receipts/...`) and saves [`Receipt`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/Receipt.java) entity.
2. **[`Receipt.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/domain/Receipt.java)** (`@Entity @Table(name = "receipt")`)
   * **Properties:** `id` (UUID PK), `payment` (`@OneToOne`), `cryptoSignature` (String), `fileUrl` (String), `issuedAt` (LocalDateTime).

---

#### 4.8 Notifications & Alerts Module (`com.tuitionnetwork.notifications`)

1. **[`PaymentNotificationService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/notifications/PaymentNotificationService.java)** (`@Component`)
   * **Listener:** `@ApplicationModuleListener on(PaymentCapturedEvent event)`
   * **Behavior:** Formats SMS confirmation template and simulates dispatch to the parent's mobile phone.
2. **[`Notification.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/notifications/domain/Notification.java)** (`@Entity @Table(name = "notification")`)
   * **Properties:** `id` (UUID PK), `recipientId` (UUID), `channel` (String), `message` (String), `sentAt` (LocalDateTime).

---

#### 4.9 Audit & Compliance Module (`com.tuitionnetwork.audit`)

1. **[`AuditLog.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/audit/domain/AuditLog.java)** (`@Entity @Table(name = "audit_log")`)
   * **Properties:** `auditId` (UUID PK), `actorId` (UUID), `actorType` (String: `"BANK_EMPLOYEE"`, `"INSTITUTION_ADMIN"`, `"Guardian"`), `action` (String: `"SEARCH_NATIONAL_ID"`, `"UPLOAD_DUES_CSV"`, `"VIEW_STUDENT_DUES"`), `targetResource` (String: HMAC-SHA256 Hash or File Batch), `timestamp` (LocalDateTime).
2. **[`AuditLogRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/audit/repository/AuditLogRepository.java)** (`@Repository`)
   * **Methods:** `findByActorId(UUID actorId)`, `findByAction(String action)`.

---

#### 4.10 Common Exceptions & Application Bootstrapping

1. **[`PendingBusinessRuleException.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/common/exceptions/PendingBusinessRuleException.java)** (`extends RuntimeException`)
   * **Purpose:** Thrown when a business guardrail is violated (e.g., overpayment attempt, debit card on EPP, post-deadline partial payment).
2. **[`DemoApplication.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/example/demo/DemoApplication.java)** (`@SpringBootApplication`)
   * **Purpose:** Application entry point scanning packages `com.example.demo` and `com.tuitionnetwork`.

---

### 5. End-to-End System Flows from Multiple Perspectives

#### Perspective 1: Back-Office Bank Employee (National ID Dues Lookup & Payment)
1. Parent arrives at a bank counter. Bank Employee (`ROLE_BACK_OFFICE`) enters the parent's 14-digit National ID into the portal.
2. `DuesSearchController` verifies `@PreAuthorize("hasRole('BACK_OFFICE')")`.
3. `IdentityResolverServiceImpl` computes keyed HMAC-SHA256, inserts an immutable `AUDIT_LOG` row (`actor_type = "BANK_EMPLOYEE"`, `action = "SEARCH_NATIONAL_ID"`), and fetches the parent's linked students.
4. `SearchService` calls `BillingFeeQueryService` to pull unpaid fees across all schools and presents a consolidated dashboard.
5. The teller selects the dues, chooses full payment or a 12-month EPP plan, and submits `POST /api/v1/payments/settle` with an `Idempotency-Key`.
6. `PaymentSettlementService` charges the card via `BankGatewayAdapterInterface`, deducts balances with `@Version` locking, saves the payment, and prints an official receipt.

#### Perspective 2: Institution Finance Admin (Spreadsheet Ingestion & Ledger View)
1. School Admin (`ROLE_INSTITUTION_ADMIN`) uploads a CSV roster via `POST /api/v1/institutions/{id}/dues/upload`.
2. `IngestionService` validates rows, isolates errors into `UPLOAD_ERROR`, computes `row_idempotency_key = SHA256(...)`, provisions `FeeLine` records, and logs the upload in `AUDIT_LOG`.
3. The admin queries `/api/v1/institutions/{id}/students/{studentId}/dues` to monitor real-time payment statuses (`OUTSTANDING`, `PARTIALLY_PAID`, `PAID`).

---

### 6. Business Guardrails & Exception Handling

```plaintext
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                               FINTECH PARANOIA GUARDRAIL MATRIX                                 │
├────────────────────────────────┬────────────────────────────────┬───────────────────────────────┤
│ Guardrail                      │ Condition Detected             │ Enforcement / HTTP Response   │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────┤
│ 1. Ledger RBAC Guard           │ Non-institution role attempts  │ 403 Forbidden                 │
│                                │ to query school student ledger │ (@PreAuthorize)               │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────┤
│ 2. Overpayment Guard           │ Requested payment amount >     │ 422 Unprocessable Entity /    │
│                                │ remaining fee line balance     │ 400 Bad Request               │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────┤
│ 3. EPP Debit Card Block        │ Debit Card BIN (e.g. 5078)     │ 422 Unprocessable Entity /    │
│                                │ used for EPP installment plan  │ 400 Bad Request               │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────┤
│ 4. Idempotency Tamper Guard    │ Re-using existing Idempotency  │ 409 Conflict                  │
│                                │ Key with altered total amount  │ (Payload mismatch rejection)  │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────┤
│ 5. Optimistic Lock Guard       │ Concurrent race condition on   │ 409 Conflict                  │
│                                │ the same FeeLine balance       │ (OptimisticLockException)     │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────┤
│ 6. Post-Deadline Partial Guard │ Partial payment on overdue fee │ 422 Unprocessable Entity      │
│                                │ past its due date              │ (PendingBusinessRuleException)│
└────────────────────────────────┴────────────────────────────────┴───────────────────────────────┘
```

---

### 7. Testing Strategy & Verification Results

The entire codebase is verified by 42 unit and integration tests covering security, privacy auditing, tenant boundary isolation, CSV ingestion resiliency, and payment concurrency:

```plaintext
[INFO] Results:
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
