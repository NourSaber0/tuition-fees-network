# System Workflows & End-to-End Walkthroughs: Tuition Network

This document provides a highly detailed, step-by-step walkthrough of the core user journeys in the Tuition Network. It maps exactly how data flows from the frontend UI components, through the API requests, down into the specific Java backend services, and how the data is mutated in the database.

---

## Workflow 1: The School Fee Ingestion Pipeline
**Goal:** A School Administrator uploads a spreadsheet of student fees. The system parses it, isolates errors, and makes the valid fees available for collection.

### 1. Frontend Interaction
- **UI Location:** `frontend/apps/portal/src/app/school/dashboard`
- **Action:** The School Admin (`ROLE_INSTITUTION_ADMIN`) selects a `.csv` file and clicks "Upload Roster".
- **Data Required:** A `MultipartFile` (CSV) containing columns: `National ID`, `Student Name`, `Fee Type`, `Amount (EGP)`, and `Due Date`.

### 2. API Communication
- **Endpoint:** `POST /api/v1/institutions/{institutionId}/dues/upload`
- **Client:** `frontend/packages/api-client/src/client.ts` attaches the `Authorization: Bearer <JWT>` header containing the admin's session token.

### 3. Backend Execution
- **Controller:** `CsvIngestionController.java`
- **Validation Service:** `IngestionService.processCsvUpload()`
  - Iterates over the CSV row by row. 
  - **Error Isolation:** If Row 5 has a negative amount, it catches the error and saves an `UploadError` entity to the database, but *continues processing the rest of the file*.
  - **Idempotency Key:** For valid rows, it computes a deterministic hash: `SHA256(InstitutionID + StudentRef + FeeType + Term)`.
- **Database Mutator:** `BillingFeeCommandServiceImpl.java`
  - Attempts to insert the valid rows as `FeeLine` entities into the database.
  - If a `FeeLine` with the same idempotency key already exists, it is safely ignored (preventing duplicate billing if the admin uploads the same file twice).
- **Result:** The `FeeLine` entities are saved with `status = "ACTIVE"`.

---

## Workflow 2: The Bank Teller Dues Lookup
**Goal:** A parent walks into a bank branch. The Bank Teller searches for the parent's National ID to see a consolidated list of all fees owed across all their children's schools.

### 1. Frontend Interaction
- **UI Location:** `frontend/apps/portal/src/app/bank/transactions/page.tsx`
- **Action:** The Bank Teller (`ROLE_BACK_OFFICE`) types the 14-digit Egyptian National ID into the search bar and hits Enter.
- **Data Required:** `parentNationalId` (e.g., `29805150101023`).

### 2. API Communication
- **Endpoint:** `GET /api/v1/guardian/dues?parentNationalId=29805150101023`

### 3. Backend Execution
- **Controller:** `DuesSearchController.java`
- **Privacy & Identity Service:** `IdentityResolverServiceImpl.resolveGuardianByNationalId()`
  - Takes the plaintext National ID and computes an `HMAC-SHA256` hash.
  - Instantiates an `AuditLog` entity (Action: `"SEARCH_NATIONAL_ID"`) with the teller's UUID and the HMAC hash (ensuring strict data privacy).
  - Queries `GuardianRepository.findByNationalIdHash(hmac)`.
- **Aggregation Service:** `SearchService.java`
  - Retrieves all children associated with the Guardian.
  - Calls `BillingFeeQueryService.findOpenFeesByStudentIds()` to select all `FeeLine` records across *multiple* isolated institution tenants where `status IN ('ACTIVE', 'PARTIALLY_PAID')`.
- **Result:** Returns a consolidated JSON tree grouping the fees by student, which the frontend renders using the `Table.tsx` component.

---

## Workflow 3: Payment Settlement & Fintech Guardrails
**Goal:** The Bank Teller processes a payment for selected fees. The system deducts the balance, generates a receipt, and updates the school's ledger instantly.

### 1. Frontend Interaction
- **UI Location:** `frontend/apps/portal/src/app/bank/transactions/page.tsx`
- **Action:** The teller selects specific fee checkboxes, chooses a payment source (Account Debit or Credit Card), and clicks "Execute Payment". The UI generates a UUID (`Idempotency-Key`).
- **Data Required:** 
  - `Header: Idempotency-Key` (e.g., `req-a1b2-c3d4`)
  - Payload: `{ nationalId: "...", feeIds: ["fee-1", "fee-2"], amountEGP: 15000.0, method: "DEBIT_ACCOUNT", sourceId: "acc_mona_current" }`

### 2. API Communication
- **Endpoint:** `POST /api/v1/payments` (or `/api/v1/payments/settle`)

### 3. Backend Execution - The Guardrails (`PaymentSettlementService.java`)
- **Tamper Protection:** Checks if the `Idempotency-Key` exists in the database. If it does, and the payload amount matches, it returns the cached success response. If the amount differs, it throws `409 Conflict`.
- **Overpayment Guard:** Iterates the requested `feeIds` and asserts that the `amountEGP` does not exceed the sum of their `remainingAmount`.
- **Bank Gateway:** Calls `MockBankAdapterImpl.chargeCard(amountEGP, idempotencyKey)` **outside** the database transaction. It receives a fake `AUTH-UUID` representing successful bank authorization.

### 4. Backend Execution - Atomic Ledger Update (`PaymentTransactionExecutor.java`)
- Opens a `@Transactional` database session.
- Fetches the `FeeLine` entities.
- **Optimistic Locking:** JPA checks the `@Version` column. If a parent paid on the mobile app simultaneously, the version number would have incremented, and this transaction rolls back with an `ObjectOptimisticLockingFailureException`.
- **Balance Deduction:** Subtracts `amountEGP` from the `remainingAmount` of the `FeeLine`. If `remainingAmount == 0`, `status` becomes `"PAID"`. Otherwise, `"PARTIALLY_PAID"`.
- **Persistence:** Saves a master `Payment` entity and junction `PaymentAllocation` entities to link the payment to the specific fee lines.
- **Event Publishing:** Publishes a `PaymentCapturedEvent` to the internal Spring application event bus.

### 5. Asynchronous Fulfillment (Event Listeners)
- **`ReceiptGenerator.java`:** Listens for the `PaymentCapturedEvent`, generates a SHA-256 digital signature, and prepares a PDF receipt reference.
- **`PaymentNotificationService.java`:** Simulates dispatching an SMS confirmation to the parent's registered mobile number.
- **`EppScheduleGenerator.java`:** (If the payment method was an Equal Payment Plan), calculates the 6/12/18 month interest structure and saves the `EPPSchedule`.

### 6. Verification in the School Portal
- **Frontend Interaction:** The School Admin refreshes `frontend/apps/portal/src/app/school/dashboard`.
- **Backend Flow:** `InstitutionDuesController.java` returns the updated `FeeLine` records. Because the `PaymentTransactionExecutor` atomically updated the `FeeLine.remainingAmount` and `FeeLine.status`, the School Admin instantly sees the invoice marked as "PAID" with the exact timestamp and transaction reference, completing the financial loop.
