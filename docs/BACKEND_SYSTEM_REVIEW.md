# Backend Architecture & System Review: Tuition Network

This document provides a highly detailed, file-by-file breakdown of the Java Spring Boot backend (`src/main/java/com/tuitionnetwork/`). It details the exact classes, their responsibilities, and how they interact to enforce financial integrity. This serves as a definitive reference for technical reviews with the Head of Development.

## 1. System Overview & Monolith Structure
The backend is a **Modular Spring Boot Monolith** designed around Domain-Driven Design (DDD) principles. It handles ingestion of fee rosters, consolidation of student dues, and secure payment processing. 

### Core Modules:
- **`identity/`**: Authentication, MFA, and Role-Based Access Control (RBAC).
- **`ingestion/`**: Manages CSV uploads, row-by-row parsing, and error isolation.
- **`search/`**: Handles cross-institution dues aggregation.
- **`billing/`**: The core ledger for student `FeeLine` records.
- **`payments/`**: The payment settlement engine and fintech guardrails.
- **`audit/`**: Immutable data privacy and action logging.
- **`mockbank/`**: The embedded in-process banking simulator (replaces the old Python server).

---

## 2. In-Depth File Breakdown & Interactions

### A. The Identity & Security Module (`identity/`)
- **`src/main/java/com/tuitionnetwork/identity/security/SecurityConfig.java`**: 
  - **Function:** The global security filter chain. It protects all `/api/v1/**` endpoints. If a request lacks a valid JWT `Authorization` header, it immediately returns `401 Unauthorized`. It activates `@PreAuthorize` method-level security throughout the application.
- **`src/main/java/com/tuitionnetwork/identity/security/IdentityUserDetailsService.java`**:
  - **Function:** The multi-domain user details loader. Depending on the user's origin, it dynamically assigns critical Spring Security roles: `ROLE_BACK_OFFICE` (Bank Employees), `ROLE_INSTITUTION_ADMIN` (School Finance), or `ROLE_GUARDIAN` (Parents).

### B. The Ingestion Pipeline (`ingestion/`)
- **`src/main/java/com/tuitionnetwork/ingestion/web/CsvIngestionController.java`**:
  - **Function:** Exposes the `POST /api/v1/institutions/{id}/dues/upload` endpoint. Strictly protected by `@PreAuthorize("hasRole('INSTITUTION_ADMIN')")`. It takes a multipart CSV file uploaded by a school admin.
- **`src/main/java/com/tuitionnetwork/ingestion/service/IngestionService.java`**:
  - **Function & Interactions:** This is the validation engine. It iterates over the CSV row by row. 
  - **Error Isolation:** If a row is invalid (e.g., negative amount or missing student ID), it instantiates an `UploadError` entity (from `ingestion/domain/UploadError.java`) recording the exact line number and reason. This prevents the entire file from failing due to one typo.
  - **Idempotency Generation:** For valid rows, it computes a deterministic SHA-256 hash `row_idempotency_key = SHA256(Student + Institution + FeeType + Period)`. This key is passed to the `BillingFeeCommandService` to guarantee that re-uploading the same CSV will safely ignore duplicate rows.

### C. Search & Privacy Auditing (`search/` & `audit/`)
- **`src/main/java/com/tuitionnetwork/identity/service/IdentityResolverServiceImpl.java`**:
  - **Function & Privacy Logic:** When a Bank Teller searches for a parent (Guardian) by National ID, this service intercepts the raw 14-digit string and computes an **HMAC-SHA256 hash**.
  - **Audit Logging:** It instantiates an `AuditLog` entity (from `audit/domain/AuditLog.java`) recording the Bank Employee's UUID, the action `"SEARCH_NATIONAL_ID"`, and the HMAC hash. **The plaintext National ID is never stored in logs.**
  - **Database Query:** It queries `GuardianRepository.findByNationalIdHash(hmac)`.
- **`src/main/java/com/tuitionnetwork/search/service/SearchService.java`**:
  - **Function:** Once the Guardian is resolved, this service aggregates all unpaid `FeeLine` records across all schools for the Guardian's children by calling `BillingFeeQueryService.findOpenFeesByStudentIds()`. It ensures the bank teller sees a fully consolidated invoice tree.

### D. The Payment Settlement Engine (`payments/`)
This is the most critical financial logic, heavily scrutinized during code reviews.

- **`src/main/java/com/tuitionnetwork/payments/service/PaymentSettlementService.java`**:
  - **Function (The Guardrail Engine):** 
    1. **Idempotency Check:** Checks `paymentRepository.findByIdempotencyKey(key)`. If found, it validates that the payload hasn't been altered (Tamper Protection). If altered, it throws a `409 Conflict`. If valid, returns the cached transaction without re-charging.
    2. **Overpayment Block:** Iterates through the requested `FeeLine` items and asserts that `amountToPay <= remainingAmount`. 
    3. **EPP Debit Block:** Explicitly forbids Debit Card BINs from initiating an Equal Payment Plan.
    4. **Network Isolation:** Calls the `BankGatewayAdapterInterface.chargeCard` **outside** the database transaction. This prevents slow bank API networks from tying up local database connections.
- **`src/main/java/com/tuitionnetwork/payments/service/PaymentTransactionExecutor.java`**:
  - **Function (Atomic Execution):** Once the bank approves the charge, this service opens a `@Transactional` boundary. It deducts the balances on the `FeeLine` entities. It utilizes the `@Version` annotation on `FeeLine` (Optimistic Locking) to detect if another thread modified the fee concurrently, throwing an `ObjectOptimisticLockingFailureException` to prevent race conditions. Finally, it saves the `Payment` and `PaymentAllocation` records.

### E. The Mock Bank Simulator (`mockbank/`)
- **`src/main/java/com/tuitionnetwork/mockbank/web/MockCustomerController.java` & `MockCardPaymentController.java`**:
  - **Function:** These controllers completely replace the legacy external Python mock server. They expose standard banking endpoints (`/api/v1/customers` and `/api/v1/payments/cards`) directly on `localhost:8080`.
- **`src/main/java/com/tuitionnetwork/payments/infrastructure/MockBankAdapterImpl.java`**:
  - **Function:** Implements the `BankGatewayAdapterInterface` used by the `PaymentSettlementService`. Rather than making actual HTTP network calls, it instantly simulates a successful banking charge by generating a fake `AUTH-UUID` (e.g., `AUTH-A1B2C3D4`) and returning a `PaymentStatus.CAPTURED` enum. This guarantees blazing fast, deterministic integration tests without moving real money.

---

## 3. Executive Summary of System Guardrails

If the Head of Development queries the system's resilience, emphasize these 4 pillars:

1. **Idempotency Tamper Protection (`PaymentSettlementService.java`)**: Duplicate requests don't just return early; they are cryptographically checked against the original payload to prevent users from altering amounts on retries.
2. **Zero Tenant Bleed (`SecurityIntegrationTest.java`)**: School Admins (`ROLE_INSTITUTION_ADMIN`) can only ever upload and view `FeeLines` matching their specific `institutionId` session claim.
3. **Optimistic Locking (`FeeLine.java`)**: The JPA `@Version` annotation prevents race conditions if a parent pays via mobile app at the exact millisecond a bank teller processes a branch payment for the same invoice.
4. **Non-Transactional External Calls (`PaymentSettlementService.java`)**: By moving `chargeCard()` outside the `@Transactional` wrapper, we eliminate the risk of external bank network latency exhausting the Hikari database connection pool.
