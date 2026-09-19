# Codebase vs. Backend Integration Spec: Divergences & Fixes

This document compares your current Spring Boot codebase (`/Users/nourahmed/Downloads/demo`) directly against the requirements outlined in the `backend_integration_spec.md` document. It highlights exactly where your code diverges from the spec and provides the step-by-step technical fixes required to realign them.

---

## 1. Global Setup: Embedded Mock Server
**Spec Requirement:** The backend acts as a bridge, making HTTP requests to an external Mock Bank server (`http://localhost:8000`).
**Codebase Reality:** The project embeds the mock bank directly into the Java application (`com.tuitionnetwork.mockbank`).

### 🛠️ How to Fix:
1. **Delete Embedded Code:** Completely delete the `com.tuitionnetwork.mockbank` package (including `MockCardPaymentController`, `MockCustomerController`, `MockBankStore`, etc.).
2. **Update Configuration:** Ensure your `application.yml` or `application.properties` points your `MockBankBackOfficeClient` to the external Python/Docker server:
   ```yaml
   mockbank.api.url: http://localhost:8000/api/v1
   mockbank.api.key: wit-intern-2026
   ```

---

## 2. Phase 1: Authentication & Account Linking
**Spec Requirement:** When a parent links a bank account, call Mock Service 4 (`GET /api/v1/customers?national_id={NID}`) to verify they are a customer and fetch their active accounts/cards.
**Codebase Reality:** `IdentityResolverServiceImpl` bypasses this. It hashes the National ID locally using HMAC-SHA256 and queries a local `GuardianRepository` without ever checking the Mock Bank for CIB accounts.

### 🛠️ How to Fix:
1. **Create an HTTP Client:** Create a new `MockBankCustomerClient.java` (similar to your existing `MockBankBackOfficeClient`) that uses `RestTemplate` to call `GET /api/v1/customers?national_id={NID}`.
2. **Refactor IdentityResolverService:** In `IdentityResolverServiceImpl.resolveGuardianByNationalId()`:
   * Call the new `MockBankCustomerClient`.
   * If the response is `404 CUSTOMER_NOT_FOUND`, throw a `ResponseStatusException(HttpStatus.NOT_FOUND)`.
   * If `200 OK`, parse the `accounts` and `cards` arrays.
   * Filter for items where `"status" == "ACTIVE"`.
   * Save the resulting `account_id` and `card_id` values to the parent's `Guardian` record in your local database so they can be used later for Phase 3 payments.

---

## 3. Phase 2 & 4: Fee Search & Upload
**Spec Requirement:** The Mock Bank does not store fees. The backend must parse uploaded CSVs and store them locally. Parent searches query this local DB.
**Codebase Reality:** **(MATCH ✅)** Your project handles this correctly! `SchoolFeeUploadServiceImpl` parses the CSV and saves to `FeeLineRepository`, and `CustomerFeesController` queries it perfectly. 
*No fixes required here for the REST Mock integration.*

---

## 4. Phase 3: Scenario A & B (Payment Processing)
**Spec Requirement:** Use Mock Service 5 (`/backoffice/payments`) for saved accounts, and Mock Service 2 (`/payments/cards`) for new cards.
**Codebase Reality:** **(MATCH ✅)** Your `MockBankBackOfficeClient` beautifully implements this using `RestTemplate` and properly passes the `X-API-Key` and `Idempotency-Key` headers.
*No fixes required here.*

---

## 5. Phase 3: Scenario C (Easy Payment Plans - EPP)
**Spec Requirement:** Call Mock Service 3 (`GET /api/v1/epp/quotes`) to show installment options, and (`POST /api/v1/epp`) to convert a captured payment into a plan.
**Codebase Reality:** `EppPlanServiceImpl` completely ignores the Mock Bank. It performs all the EPP math, interest rate logic, and scheduling internally using a local `EppPricing.calculate()` method.

### 🛠️ How to Fix:
1. **Delete Local Math:** Remove the `EppPricing` class and any local interest-rate calculation logic.
2. **Create an HTTP Client:** Add a new `MockBankEppClient.java` with two methods:
   * `getQuotes(BigDecimal amount)` -> Makes a GET request to `/api/v1/epp/quotes?amount={amount}`.
   * `createPlan(String paymentId, int tenorMonths)` -> Makes a POST request to `/api/v1/epp` with the required JSON body.
3. **Refactor EppPlanServiceImpl:** 
   * In the method that generates quotes for the frontend, delegate entirely to `MockBankEppClient.getQuotes()`.
   * In the method that converts a payment (`createEppPlan(...)`), call `MockBankEppClient.createPlan()`. 
   * Extract the returned `plan_id` and `schedule` from the Mock Bank's JSON response and save those references to your local `eppScheduleRepository` (rather than generating the schedule locally).
