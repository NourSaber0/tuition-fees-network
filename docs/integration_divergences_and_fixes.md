# Architecture Gap Analysis & Remediation Plan

This document outlines the architectural divergences between your Spring Boot implementation (`/demo`) and the requirements defined in your project specifications (`Development_Plan.csv`, `Mock Banking Services.md`, and `T24 Integration.csv`). It provides actionable steps to align your codebase with the specs.

---

## Part 1: REST Mock Bank Integration (`wit-mock-services`)

### 1. Embedded Mock Server vs. External Integration
* **The Divergence:** The project spec assumes the Mock Bank is a separate, external Python/Docker service. Your project actually includes a Java port of this server directly inside the codebase (`com.tuitionnetwork.mockbank`).
* **The Fix:** 
  1. Delete the `com.tuitionnetwork.mockbank.web`, `domain`, and `store` packages.
  2. Ensure your `MockBankBackOfficeClient` is configured to point to the `http://localhost:8000/api/v1` URL where the actual Python mock server runs.

### 2. Account Linking & MOI Validation (Phase 1)
* **The Divergence:** According to Phase 1, parent registration should validate against the MOI service, and account linking should query Mock Service 4 (`GET /api/v1/customers`). Currently, `IdentityResolverServiceImpl` bypasses this by securely hashing National IDs locally and never queries the mock bank for active accounts/cards.
* **The Fix:**
  1. In `IdentityResolverService`, inject `MockBankBackOfficeClient` (or create a new `MockBankIdentityClient`).
  2. Add a method to call `GET /api/v1/customers?national_id={NID}`.
  3. When a parent logs in/links an account, parse the JSON response from Service 4, filter for `"status": "ACTIVE"`, and save those `account_id` and `card_id` values to the parent's profile in your local database.

### 3. Easy Payment Plans (EPP) (Phase 3)
* **The Divergence:** Phase 3 dictates that EPP conversion should be handled by Mock Service 3 (`POST /api/v1/epp`). Your `EppPlanServiceImpl` implements the financial math (`EppPricing.calculate`) and scheduling completely locally.
* **The Fix:**
  1. Refactor `EppPlanServiceImpl.java`.
  2. For quoting, make an HTTP call to `GET /api/v1/epp/quotes?amount={amount}`.
  3. For converting, make an HTTP call to `POST /api/v1/epp` passing the `payment_id` and `tenor_months`.
  4. Save the returned `plan_id` and `schedule` to your local `eppScheduleRepository` instead of calculating it yourself.

---

## Part 2: T24 Core Banking SOAP Integration

### 4. CSV Uploads & T24 Billing Creation (Phase 4)
* **The Divergence:** `T24 Integration.csv` specifies that `RequestCustomerBillingProcedure` must be called to create dues in T24 when an institution uploads a CSV. Your `SchoolFeeUploadServiceImpl` parses the CSV and saves the fees to the local Postgres database (`FeeLineRepository`) but never notifies T24.
* **The Fix (Event-Driven):**
  1. Modify `SchoolFeeUploadServiceImpl.java`. Inside the loop where `feeLineRepository.save(feeLine)` succeeds, publish a Spring event: 
     `applicationEventPublisher.publishEvent(new FeeCreatedEvent(feeLine));`
  2. Create a new listener class `T24FeeEventListener` (similar to your excellent `T24PaymentEventListener`).
  3. In the listener, catch `FeeCreatedEvent` and call `T24CustomerBillingService.registerFeeInT24()` to asynchronously push the billing record to the SOAP mock.

### 5. Fee Search & Customer Verification (Phase 1 & 2)
* **The Divergence:** The spec requires `RetrieveCustomerBillingProcedure` to be used for fetching outstanding fees by National ID. Your `CustomerFeesController` and `BillingFeeQueryServiceImpl` bypass T24 entirely, querying your local database instead.
* **The Fix:**
  1. Update `CustomerFeesController` or the underlying `BillingFeeQueryService`.
  2. When a parent requests their fees, call `T24CustomerBillingService.retrieveCustomerDues(nationalId, accountNumber)`.
  3. Aggregate the data returned from the SOAP call with your local database records before returning the final JSON to the frontend.

---

## Summary of T24 Alignment
| T24 Operation | Spec Requirement | Current Status | Required Action |
| :--- | :--- | :--- | :--- |
| **Retrieve** | Phase 1 & 2 (Search) | ❌ Bypassed | Wire it into `CustomerFeesController`. |
| **Request** | Phase 4 (CSV Upload) | ❌ Bypassed | Add Spring Event in `SchoolFeeUploadServiceImpl`. |
| **Update** | Phase 3 (Payment) | ✅ Perfect | None. `T24PaymentEventListener` handles this correctly. |
