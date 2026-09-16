# Tuition Network E2E Test Strategy Blueprint

## 1. The 7 Personas to Mock
When writing `MockMvc` tests, use the `@WithMockUser` annotation to simulate the exact roles defined in the system.

**Bank Back Office Portal:**
1. `@WithMockUser(roles = "BACK_OFFICE", authorities = "bank-admin")`
2. `@WithMockUser(roles = "BACK_OFFICE", authorities = "bank-operations")`
3. `@WithMockUser(roles = "BACK_OFFICE", authorities = "bank-finance")`
4. `@WithMockUser(roles = "BACK_OFFICE", authorities = "bank-reconciliation")`

**School Portal (Must enforce `institutionId` isolation):**
5. `@WithMockUser(roles = "INSTITUTION_ADMIN", authorities = "school-admin")`
6. `@WithMockUser(roles = "INSTITUTION_ADMIN", authorities = "school-finance")`

**Guardian Portal:**
7. `@WithMockUser(roles = "GUARDIAN")`

## 2. Cross-Role Test Scenarios to Implement

For every test class, you must implement both Positive (200 OK / 201 Created) and Negative (403 Forbidden / 401 Unauthorized / 409 Conflict) tests.

### Scenario A: The School Ingestion Workflow
*   **Actor:** `school-admin` or `school-finance`
*   **Test 1 (Positive):** Verify a School Admin can POST a valid CSV file to `/api/v1/institutions/{id}/dues/upload`. Expect `202 Accepted`.
*   **Test 2 (Negative):** Verify a Bank Employee (`bank-operations`) attempting to upload a CSV to the school's endpoint receives `403 Forbidden`.
*   **Test 3 (Negative):** Verify a Guardian attempting to access the school's dashboard receives `403 Forbidden`.

### Scenario B: The Cross-Institution Tenant Bleed Check
*   **Actor:** `school-admin` from `Institution A` (e.g., `SCH-001`)
*   **Test 1 (Negative):** Verify the admin from `SCH-001` attempting to GET students or fees from `SCH-002` receives `403 Forbidden` or `404 Not Found`.

### Scenario C: The Bank Teller Payment Workflow
*   **Actor:** `bank-operations` or `bank-admin`
*   **Test 1 (Positive):** Verify `bank-operations` can GET `/api/v1/guardian/dues` using a National ID and receive the consolidated invoice tree.
*   **Test 2 (Positive):** Verify `bank-operations` can POST to `/api/v1/payments/settle` with a valid `Idempotency-Key` and receive a `201 Created`.
*   **Test 3 (Negative):** Verify a `school-admin` attempting to POST to `/api/v1/payments/settle` receives `403 Forbidden` (Schools cannot execute payments).

### Scenario D: Fintech Guardrail Enforcement
*   **Actor:** `bank-operations`
*   **Test 1 (Negative):** *Overpayment Block.* Submit a payment where `amountToPay` > `remainingAmount`. Expect `422 Unprocessable Entity` or `400 Bad Request`.
*   **Test 2 (Negative):** *Idempotency Tampering.* Submit a payment with `Idempotency-Key = "key-1"`. Then, resubmit `"key-1"` but change the total amount. Expect `409 Conflict`.