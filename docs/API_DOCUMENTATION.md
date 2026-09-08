# CIB Tuition & Services Fees Collection Network — Comprehensive API Documentation

**Project:** CIB Tuition & Services Fees Collection Network  
**API Specification Reference:** [`Bank-Back-Office-API-Contract.md`](file:///Users/nourahmed/downloads/demo/docs/Bank-Back-Office-API-Contract.md) & [`1_MASTER_SPEC.md`](file:///Users/nourahmed/downloads/demo/docs/1_MASTER_SPEC.md)  
**Backend Framework:** Spring Boot 4.1.1 / Java 25 / Spring Security / Spring Data JPA / Hibernate  
**Base URL:** `/api/v1` (with root path aliases provided for Back-Office compatibility)  
**Total Documented Endpoints:** 96 endpoints across 12 implementation phases and core portals.

---

## Table of Contents
1. [Security, Auth & Fintech Guardrails Architecture](#1-security-auth--fintech-guardrails-architecture)
2. [Phase 1: Authentication, Session & Access Control](#2-phase-1-authentication-session--access-control)
3. [Phase 2: Dashboard Analytics & Operational Intelligence](#3-phase-2-dashboard-analytics--operational-intelligence)
4. [Phase 3: Institution Management, Student Rosters & Ingestion](#4-phase-3-institution-management-student-rosters--ingestion)
5. [Phase 4: Transactions & Payment Workflow](#5-phase-4-transactions--payment-workflow)
6. [Phase 5: Reconciliation, Settlement & Payout Runs](#6-phase-5-reconciliation-settlement--payout-runs)
7. [Phase 6: Easy Payment Plans (EPP)](#7-phase-6-easy-payment-plans-epp)
8. [Phase 7: Reports Engine & Asynchronous Exports](#8-phase-7-reports-engine--asynchronous-exports)
9. [Phase 8: Notifications Engine & Real-Time SSE Stream](#9-phase-8-notifications-engine--real-time-sse-stream)
10. [Phase 9: Audit Logs & Regulatory Compliance](#10-phase-9-audit-logs--regulatory-compliance)
11. [Phase 10: Bank Users & Role-Based Access Control (RBAC)](#11-phase-10-bank-users--role-based-access-control-rbac)
12. [Phase 11: System Settings & Configurable Parameters](#12-phase-11-system-settings--configurable-parameters)
13. [Phase 12: Payment Deadlines, Priority Queues & Late Penalties](#13-phase-12-payment-deadlines-priority-queues--late-penalties)
14. [Core Citizen & Institution Portal Endpoints](#14-core-citizen--institution-portal-endpoints)
15. [Summary of Excluded Endpoints](#15-summary-of-excluded-endpoints)

---

## 1. Security, Auth & Fintech Guardrails Architecture

All mutating and sensitive read endpoints enforce banking-grade guardrails and zero-trust policies:

1. **Zero-Refund Policy:** Exclusion of refund and reversal endpoints (`/transactions/{id}/refund`, `/transactions/{id}/reverse`) across all back-office and customer portals. Any reversal must be handled through offline interbank disputes.
2. **School Ledger Isolation (Fintech Paranoia Guardrail #1):** Bank employees receive `403 Forbidden` if attempting to query or modify internal institution ledgers (`/institutions/{id}/dues`). School admins are isolated to their own institution ID.
3. **Overpayment Block (Fintech Paranoia Guardrail #2):** Payments validate `amountToPay <= remainingAmount` on every fee line. Overpaying by even 0.01 EGP throws `400 Bad Request` or `422 Unprocessable Entity`.
4. **EPP Debit Card BIN Block (Fintech Paranoia Guardrail #3):** Debit card BIN prefixes (e.g. `5078`, `400000`, `604900`) are blocked from installment financing. Only verified credit cards are permitted.
5. **Idempotency Payload Tamper Protection (Fintech Paranoia Guardrail #4):** Payments and retry operations require an `Idempotency-Key` header. Replaying the key with identical payload returns the cached result; replaying with altered amount/payload raises `409 Conflict`.
6. **Audit Trail Completeness:** All state-mutating operations record an immutable entry in `AuditLogRepository` (`INFO`, `WARNING`, or `CRITICAL`).

---

## 2. Phase 1: Authentication, Session & Access Control

### 2.1 `POST /auth/login` (Alias: `/api/v1/auth/login`)
- **Roles:** Anonymous / Public.
- **What Does It Do?** Validates bank employee username/email and bcrypt-hashed password against the `bank_users` table. Checks account lock status and brute-force attempt limits. Upon success, generates a transient 60-second MFA session token, produces a 6-digit OTP challenge, simulates SMS delivery, and returns masked phone number details.
- **The Need For It:** Two-Factor Authentication (2FA/MFA) is mandated by Central Bank of Egypt (CBE) cybersecurity regulations for financial back-office portals. Single-factor username/password login is prohibited.
- **What Info/Data It Needs:**
  - **Headers:** `Content-Type: application/json`
  - **Body (JSON):**
    ```json
    {
      "username": "ahmed.ops",
      "password": "Password123!"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Increments failed login attempts counter on `bank_users` if invalid credentials.
  - Locks user account (`account_status = LOCKED`) if consecutive failures exceed 5.
  - Creates an in-memory or persisted `MfaSession` with token ID, target user ID, hashed OTP, and expiration timestamp (+60s).
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "mfaToken": "mfa_sess_89ab3c...",
      "phoneMasked": "+20 10 **** 7890",
      "expiresInSeconds": 60
    }
    ```
  - `401 Unauthorized`: Invalid credentials.
  - `403 Forbidden`: Account is inactive or locked.
  - `429 Too Many Requests`: Rate limit exceeded.

---

### 2.2 `POST /auth/mfa/verify` (Alias: `/api/v1/auth/mfa/verify`)
- **Roles:** Anonymous (possessing valid `mfaToken`).
- **What Does It Do?** Verifies the 6-digit one-time password (OTP) against the pending MFA session. Upon verification, generates a signed JWT Access Token (15m validity) and Refresh Token (7d validity), registers an active session, and returns user permissions.
- **The Need For It:** Completes the two-step verification workflow, establishing the cryptographically signed JWT identity context for all subsequent bank operations.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "mfaToken": "mfa_sess_89ab3c...",
      "code": "123456"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Alters MFA session attempt counter (rejects after 3 invalid attempts).
  - Invalidates the used MFA session token.
  - Creates a new `RefreshToken` record in database with expiry timestamp.
  - Logs `USER_LOGIN` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsIn...",
      "refreshToken": "ref_98a7c2...",
      "user": {
        "id": "c1f7b889-...",
        "name": "Ahmed Hassan",
        "email": "ahmed.hassan@cibeg.com",
        "role": "bank-operations",
        "department": "Operations",
        "status": "Active"
      },
      "permissions": ["view:dashboard", "manage:institutions", "view:transactions"]
    }
    ```
  - `400 Bad Request`: Missing token or code.
  - `410 Gone`: MFA session expired.
  - `429 Too Many Requests`: Exceeded maximum 3 OTP attempts.

---

### 2.3 `POST /auth/mfa/resend` (Alias: `/api/v1/auth/mfa/resend`)
- **Roles:** Anonymous (possessing active `mfaToken`).
- **What Does It Do?** Generates a fresh 6-digit OTP for the active MFA session, sends simulated SMS, resets the verification window, and enforces a 60-second cooldown period via standard HTTP headers.
- **The Need For It:** Handles telecommunication SMS delivery failures or delayed SMS delivery to bank operators.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "mfaToken": "mfa_sess_89ab3c..."
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Updates pending MFA challenge code and resets the 60-second expiration clock.
  - Sets `lastResentAt` timestamp.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "mfaToken": "mfa_sess_89ab3c...",
      "expiresInSeconds": 60,
      "message": "OTP resent successfully"
    }
    ```
  - `410 Gone`: Initial MFA session has already expired.
  - `429 Too Many Requests`: Resend attempted before 60s cooldown expires (includes `Retry-After: 45` header).

---

### 2.4 `POST /auth/forgot-password` (Alias: `/api/v1/auth/forgot-password`)
- **Roles:** Public.
- **What Does It Do?** Initiates password recovery. If the email corresponds to an active bank employee, generates a single-use 15-minute cryptographically secure reset token and dispatches an email link.
- **The Need For It:** Enables self-service password recovery for bank personnel while strictly avoiding user enumeration vulnerabilities.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "email": "sarah.finance@cibeg.com"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Creates a `PasswordResetToken` entity (`token`, `userId`, `expiresAt`, `used = false`).
  - Logs `PASSWORD_RESET_REQUESTED` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: Generic anti-enumeration response:
    ```json
    {
      "message": "If that email is registered, a password reset link has been sent."
    }
    ```

---

### 2.5 `POST /auth/reset-password` (Alias: `/api/v1/auth/reset-password`)
- **Roles:** Public (with valid reset token).
- **What Does It Do?** Validates reset token authenticity and expiration. Enforces 5-part password complexity rules (minimum 8 characters, at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special symbol). Verifies against historical password reuse, hashes the new password with BCrypt, and marks the token used.
- **The Need For It:** Finalizes self-service credential restoration with enterprise password policy enforcement.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "token": "pwd_rst_90df21a...",
      "newPassword": "NewSecurePassword2026!"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Updates `bank_users.password_hash`.
  - Sets `password_reset_tokens.used = true`.
  - Clears account lockout counters.
  - Logs `PASSWORD_RESET_COMPLETED` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: `{ "message": "Password successfully reset. You may now log in." }`
  - `400 Bad Request`: Password fails complexity rules or token is invalid/used.
  - `410 Gone`: Reset token expired (>15 minutes).

---

### 2.6 `POST /auth/refresh` (Alias: `/api/v1/auth/refresh`)
- **Roles:** Authenticated (Possessing active Refresh Token).
- **What Does It Do?** Validates active refresh token, checks that user account is still active and unlocked, performs refresh token rotation (deletes old token, issues new token), and returns a fresh JWT access token.
- **The Need For It:** Provides seamless user sessions without requiring re-login every 15 minutes, while maintaining short-lived access tokens to limit exposure if intercepted.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "refreshToken": "ref_98a7c2..."
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Deletes or invalidates prior refresh token.
  - Persists new refresh token.
- **Return Data & Status Codes:**
  - `200 OK`: Returns new JWT access token, new refresh token, and user details.
  - `401 Unauthorized`: Invalid, expired, or revoked refresh token.
  - `403 Forbidden`: User account has been deactivated or locked since token was issued.

---

### 2.7 `POST /auth/logout` (Alias: `/api/v1/auth/logout`)
- **Roles:** Authenticated (`ROLE_BACK_OFFICE`).
- **What Does It Do?** Revokes the caller's active refresh token, terminates the server session, and clears authentication contexts.
- **The Need For It:** Essential security requirement to immediately invalidate sessions upon operator logoff, preventing session hijacking on shared bank workstations.
- **What Info/Data It Needs:**
  - **Headers:** `Authorization: Bearer <jwt>`
  - **Body (JSON, optional):** `{ "refreshToken": "ref_98a7c2..." }`
- **What Info/Data It Creates / Alters:**
  - Deletes active refresh token from database.
  - Logs `USER_LOGOUT` audit event.
- **Return Data & Status Codes:**
  - `204 No Content`: Successful logout.

---

### 2.8 `GET /auth/me` (Alias: `/api/v1/auth/me`)
- **Roles:** Authenticated (`ROLE_BACK_OFFICE`).
- **What Does It Do?** Resolves the current caller's identity from the JWT Bearer token and returns their profile details, department, role, and permission matrix.
- **The Need For It:** Drives portal initialization in `Portal.tsx` to render user profile widgets, navigation options, and client-side route authorization gates.
- **What Info/Data It Needs:**
  - **Headers:** `Authorization: Bearer <jwt>`
- **What Info/Data It Creates / Alters:** None (Read-only).
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "id": "c1f7b889-...",
      "username": "ahmed.ops",
      "name": "Ahmed Hassan",
      "email": "ahmed.hassan@cibeg.com",
      "role": "bank-operations",
      "department": "Operations",
      "status": "Active",
      "permissions": ["view:dashboard", "manage:institutions"]
    }
    ```
  - `401 Unauthorized`: Missing or invalid Bearer token.

---

### 2.9 `GET /roles` & `GET /roles/{role}/permissions` (Alias: `/api/v1/roles`)
- **Roles:** Authenticated (`ROLE_BACK_OFFICE`).
- **What Does It Do?** Returns the server-authoritative Role-Permission matrix defining all 4 bank back-office roles (`bank-admin`, `bank-operations`, `bank-finance`, `bank-reconciliation`) or permissions for a specific role.
- **The Need For It:** Provides a single source of truth for role entitlements across both front-end navigation rendering and back-office administrative user provisioning.
- **What Info/Data It Needs:**
  - **Path Variable (for single role):** `role` (e.g., `bank-operations`).
- **What Info/Data It Creates / Alters:** None (Read-only).
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    [
      {
        "role": "bank-admin",
        "description": "Full system administrator",
        "permissions": ["view:dashboard", "manage:institutions", "manage:users", "manage:settings", "view:audit-logs", "export:reports"]
      }
    ]
    ```

---

## 3. Phase 2: Dashboard Analytics & Operational Intelligence

### 3.1 `GET /api/v1/dashboard/summary`
- **Roles:** `ROLE_BACK_OFFICE` (All 4 bank roles).
- **What Does It Do?** Aggregates real-time top-level KPI metrics across the platform: count of active schools/universities, total enrolled students, today's transaction volume, total gross collections in EGP, and active EPP plans.
- **The Need For It:** Primary executive overview screen on `Dashboard.tsx` for bank operations and management.
- **What Info/Data It Needs:**
  - **Headers:** `Authorization: Bearer <jwt>`
- **What Info/Data It Creates / Alters:** None (Read-only aggregation).
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "activeInstitutions": 24,
      "totalStudents": 14500,
      "todayTransactions": 342,
      "todayCollectionsEGP": 1850400.00,
      "activeEppPlans": 78
    }
    ```

---

### 3.2 `GET /api/v1/dashboard/collections/weekly`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Computes daily collection totals (Monday through Sunday) and transaction volume counts for the specified calendar week. Defaults to current week if unspecified.
- **The Need For It:** Renders the 7-day revenue trend bar/area chart in `Dashboard.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `weekOf` (Optional, ISO `YYYY-MM-DD`).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "weekStartDate": "2026-09-01",
      "weekEndDate": "2026-09-07",
      "days": [
        { "day": "Monday", "date": "2026-09-01", "amountEGP": 450000.00, "count": 85 },
        { "day": "Tuesday", "date": "2026-09-02", "amountEGP": 620000.00, "count": 112 }
      ],
      "totalWeekEGP": 3840000.00
    }
    ```

---

### 3.3 `GET /api/v1/dashboard/institution-status`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Aggregates institutional onboarding status breakdown across categories: `Integrated`, `Pending Review`, `Suspended`, and `Rejected`.
- **The Need For It:** Displays the institutional onboarding donut chart in `Dashboard.tsx` to monitor partner pipeline health.
- **What Info/Data It Needs:** None (Authorization header only).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "integrated": 18,
      "pendingReview": 4,
      "suspended": 2,
      "rejected": 1
    }
    ```

---

### 3.4 `GET /api/v1/dashboard/recent-transactions`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Fetches the latest $N$ transactions (default: 6) ordered by timestamp descending, with student name, institution, amount, payment method, and execution status.
- **The Need For It:** Populates the real-time transaction activity ticker widget on the main dashboard screen.
- **What Info/Data It Needs:**
  - **Query Parameters:** `limit` (Optional, default `6`).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "transactions": [
        {
          "id": "a910bf20-...",
          "studentName": "Omar Farouk",
          "institutionName": "Cairo English School",
          "amountEGP": 25000.00,
          "method": "CREDIT_CARD",
          "status": "SUCCESSFUL",
          "createdAt": "2026-09-08T11:24:00Z"
        }
      ]
    }
    ```

---

### 3.5 `GET /api/v1/dashboard/deadline-summary`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Aggregates tuition fee payment deadline statistics across all schools: counts for overdue dues, dues expiring today, dues expiring this week, fees in grace period, total late penalties applied, and the top priority overdue fee lines sorted by urgency.
- **The Need For It:** Provides Phase 12 operational monitoring for collections desks to identify delinquency risks and high-priority unpaid balances.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "overdueCount": 124,
      "dueTodayCount": 45,
      "dueThisWeekCount": 210,
      "urgentCount": 35,
      "totalPenaltiesAppliedEGP": 142500.00,
      "priorityQueue": [
        {
          "feeLineId": "439b1a0e-...",
          "studentName": "Kareem Tarek",
          "institutionName": "Al-Amal School",
          "dueDate": "2026-08-30",
          "daysOverdue": 9,
          "outstandingEGP": 12000.00,
          "penaltyEGP": 600.00,
          "totalDueEGP": 12600.00,
          "priority": "CRITICAL"
        }
      ]
    }
    ```

---

## 4. Phase 3: Institution Management, Student Rosters & Ingestion

### 4.1 `GET /api/v1/institutions`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Returns a paginated list of educational institutions (schools and universities) with filtering by keyword search, institution type (`NATIONAL_SCHOOL`, `INTERNATIONAL_SCHOOL`, `PRIVATE_UNIVERSITY`, `PUBLIC_UNIVERSITY`), registration status, and account status.
- **The Need For It:** Powers the search, filtering, and table grid of registered institutions in `Schools.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:**
    - `search` (String, optional)
    - `type` (Enum: `InstitutionType`, optional)
    - `regStatus` (Enum: `RegistrationStatus`, optional)
    - `accountStatus` (Enum: `AccountStatus`, optional)
    - `page` (Integer, default 0)
    - `pageSize` / `size` (Integer, default 25)
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: Paginated `PageResponse<InstitutionSummaryDto>`.

---

### 4.2 `POST /api/v1/institutions`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Initiates the onboarding of a new school or university into the CIB network (US-06). Validates commercial registration number, tax ID, principal contact information, bank settlement account (IBAN), and document URLs.
- **The Need For It:** Allows bank operators to register applicant educational entities.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "name": "Nile International Academy",
      "type": "INTERNATIONAL_SCHOOL",
      "commercialRegistrationNo": "CR-892182",
      "taxNumber": "TR-449102",
      "contactEmail": "finance@nileacademy.edu.eg",
      "contactPhone": "+201002233445",
      "settlementIban": "EG380010000000001234567890123",
      "address": "New Cairo, District 5, Cairo",
      "moeCertificateUrl": "https://docs.cib.eg/cert_8921.pdf"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Creates new `Institution` entity with `registration_status = PENDING_REVIEW` and `account_status = SUSPENDED`.
  - Logs `REGISTER_INSTITUTION` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `201 Created`: Full `InstitutionDetailDto`.
  - `400 Bad Request`: Validation failure (invalid IBAN, duplicate tax number).

---

### 4.3 `GET /api/v1/institutions/{id}`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Fetches the complete profile and configuration of a specific institution by UUID.
- **The Need For It:** Displays the institution profile header and settings in `Schools.tsx`.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `InstitutionDetailDto`.
  - `404 Not Found`: Institution ID does not exist.

---

### 4.4 `GET /api/v1/institutions/{id}/students`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Returns the roster of enrolled students at the institution with rolled-up financial metrics: total dues billed, total paid to date, and remaining outstanding balance.
- **The Need For It:** Back-office visibility into student rosters and fee status (US-12).
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `List<InstitutionStudentDto>`.

---

### 4.5 `GET /api/v1/institutions/{id}/application`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Retrieves registration review data, legal entity documents, Ministry of Education (MOE) approvals, and tax clearance certificates submitted during onboarding (US-08).
- **The Need For It:** KYC and compliance review before approving a school.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `InstitutionApplicationDto`.

---

### 4.6 `GET /api/v1/institutions/{id}/integration`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Returns the technical integration configuration: channel protocol (`API`, `SFTP`, `MANUAL_CSV`), sync frequency, last sync timestamp, and integration health status (US-13).
- **The Need For It:** Allows bank engineers to audit data synchronization between school ERPs and CIB.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `InstitutionIntegrationDto`.

---

### 4.7 `GET /api/v1/institutions/{id}/settlements`
- **Roles:** `bank-admin`, `bank-operations`, `bank-finance`.
- **What Does It Do?** Aggregates daily settlement cycles, gross collections, the contractual 2% CIB processing fee deduction, net payout amount, and payout execution reference (US-15).
- **The Need For It:** Enables finance teams to reconcile bank commission deductions and verify funds wired to school accounts.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `InstitutionSettlementsResponse` containing settlement cycles, total gross, CIB 2% fee, net payouts, and linked Phase 5 reconciliation runs.

---

### 4.8 `POST /api/v1/institutions/{id}/approve`
- **Roles:** `bank-admin`.
- **What Does It Do?** Approves an institution application after KYC verification (US-09).
- **The Need For It:** Four-eyes governance workflow before enabling fee collection.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:**
  - Transitions `registration_status` from `PENDING_REVIEW` to `APPROVED`.
  - Logs `APPROVE_INSTITUTION` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `InstitutionDetailDto`.
  - `400 Bad Request`: Cannot approve an already approved or rejected institution.

---

### 4.9 `POST /api/v1/institutions/{id}/reject`
- **Roles:** `bank-admin`.
- **What Does It Do?** Rejects an institution registration application, recording a mandatory rejection reason and compliance notes (US-10).
- **The Need For It:** Formally declines non-compliant entities and creates an audit record.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Body (JSON):** `{ "reason": "Expired Ministry of Education operating permit" }`
- **What Info/Data It Creates / Alters:**
  - Transitions `registration_status` to `REJECTED`.
  - Logs `REJECT_INSTITUTION` audit event (`severity = WARNING`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `InstitutionDetailDto`.

---

### 4.10 `POST /api/v1/institutions/{id}/activate` & `POST /api/v1/institutions/{id}/deactivate`
- **Roles:** `bank-admin`.
- **What Does It Do?** Activates or suspends an approved institution's dues collection channels (US-11). Deactivating an institution immediately blocks citizens from making payments towards its fee lines.
- **The Need For It:** Emergency kill-switch for compliance violations, legal freezes, or billing irregularities.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:**
  - Updates `account_status` to `ACTIVE` or `SUSPENDED`.
  - Logs `ACTIVATE_INSTITUTION` or `DEACTIVATE_INSTITUTION` audit event (`severity = WARNING`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `InstitutionDetailDto`.

---

### 4.11 `GET /api/v1/institutions/{id}/fee-submissions` & `.../{submissionId}`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Lists upload history and details of fee ingestion files (CSV/batch) submitted for an institution, including total rows, successful fee lines created, and isolated row validation errors (US-14).
- **The Need For It:** Ingestion auditing and troubleshooting for failed student rows.
- **What Info/Data It Needs:**
  - **Path Parameters:** `id` (UUID), `submissionId` (UUID, for detail).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `List<FeeSubmissionSummaryDto>` or `FeeSubmissionDetailDto`.

---

### 4.12 `POST /api/v1/institutions/{id}/dues/upload`
- **Roles:** `ROLE_INSTITUTION_ADMIN` (Scoped to own institution ID).
- **What Does It Do?** Ingests student dues via multipart CSV file. Implements error isolation: invalid rows generate `UploadError` entries without failing the entire batch. Valid rows create idempotent `FeeLine` and `Student` records.
- **The Need For It:** Primary mechanism for educational institutions to bulk upload semester/annual tuition fee schedules.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Form Data:** `file` (Multipart CSV, max configured size).
- **What Info/Data It Creates / Alters:**
  - Creates `FeeSubmission` record.
  - Creates/updates `Student` entities.
  - Inserts new `FeeLine` entities (`status = UNPAID`).
  - Logs `CSV_INGESTION_PROCESSED` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: `IngestionReportResponse` (processed rows, created count, error count).
  - `403 Forbidden`: Attempted upload by bank staff or unauthorized institution admin.

---

## 5. Phase 4: Transactions & Payment Workflow

### 5.1 `GET /api/v1/transactions`
- **Roles:** `bank-admin`, `bank-operations`, `bank-finance`.
- **What Does It Do?** Paginated query across all transactions with filtering by status (`SUCCESSFUL`, `PENDING`, `FAILED`), free-text search, institution, institution type, payment method (`DEBIT_CARD`, `CREDIT_CARD`, `EPP`), date range, and Phase 12 deadline priority (`HIGH`, `MEDIUM`, `LOW`, `CRITICAL`).
- **The Need For It:** Main audit and transaction monitoring view in `Transactions.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `status`, `search`, `institution`, `institutionType`, `method`, `dateFrom`, `dateTo`, `priority`, `dueBucket`, `page`, `pageSize`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `PageResponse<TransactionDto>`.

---

### 5.2 `GET /api/v1/transactions/tab-counts`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Returns live transaction counts grouped by status tab: `All`, `Successful`, `Pending`, and `Failed`.
- **The Need For It:** Drives the badge counters on the status tabs in `Transactions.tsx`.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `{ "all": 1420, "successful": 1380, "pending": 12, "failed": 28 }`

---

### 5.3 `GET /api/v1/transactions/{id}` (Alias: `GET /api/v1/payments/{id}`)
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Returns full transaction details including payer details, student name, fee lines covered, payment gateway authorization codes, and the 5-stage lifecycle timeline (`INITIATED`, `AUTH_REQUESTED`, `CAPTURED`, `FEE_SPLIT_APPLIED`, `SETTLED`).
- **The Need For It:** Detailed transaction inspection drawer and dispute review in `Transactions.tsx`.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `TransactionDetailDto`.
  - `404 Not Found`: Transaction ID not found.

---

### 5.4 `GET /api/v1/transactions/export`
- **Roles:** `bank-admin`, `bank-finance`.
- **What Does It Do?** Generates and streams an RFC-4180 compliant CSV export of filtered transactions with UTF-8 encoding.
- **The Need For It:** Regulatory reporting and external financial auditing.
- **What Info/Data It Needs:** Same filter parameters as `GET /transactions`.
- **What Info/Data It Creates / Alters:** Logs `EXPORT_TRANSACTIONS_CSV` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: Streams `text/csv; charset=UTF-8` with `Content-Disposition: attachment; filename="transactions-export.csv"`.

---

### 5.5 `GET /api/v1/customers/fees`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Looks up all active and overdue fee lines for a citizen by their 14-digit Egyptian National ID (US-42/43). Returns privacy-masked National ID (`299*******4567`), student name, institution, outstanding balance, due date, priority, and late penalty breakdown.
- **The Need For It:** Over-the-counter (OTC) bank branch teller fee lookup when parents pay dues in person at CIB branches.
- **What Info/Data It Needs:**
  - **Query Parameter:** `nationalId` (14-digit Egyptian National ID string).
- **What Info/Data It Creates / Alters:**
  - Logs `CUSTOMER_FEES_LOOKUP` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: `CustomerFeesResponse`.
  - `400 Bad Request`: National ID is not 14 digits or contains invalid characters.

---

### 5.6 `POST /api/v1/payments` (and Core `POST /api/v1/payments/settle`)
- **Roles:** `ROLE_BACK_OFFICE` / `ROLE_GUARDIAN`.
- **What Does It Do?** Processes payment against one or more fee lines with full enforcement of the 4 Fintech Paranoia Guardrails:
  1. Validates `Idempotency-Key` header (replaying identical request returns saved transaction; replaying with altered amount returns `409 Conflict`).
  2. Enforces overpayment protection (`amountToPay <= remainingAmount`).
  3. Rejects debit cards if payment method is EPP.
  4. Slices payments between late penalty balances and principal amounts, updating `paid_amount` and `fee_status`.
- **The Need For It:** Core revenue collection engine supporting card payments, bank branch cashier settlements, and EPP loan conversions (US-45/46).
- **What Info/Data It Needs:**
  - **Headers:** `Idempotency-Key: <uuid-string>` (Mandatory)
  - **Body (JSON):**
    ```json
    {
      "feeLineIds": ["439b1a0e-..."],
      "amount": 15750.00,
      "method": "CREDIT_CARD",
      "cardNumber": "4532111122223333",
      "cardholderName": "Tarek Mohamed",
      "nationalId": "29501011234567",
      "eppTenor": null
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Inserts `Transaction` record (`status = SUCCESSFUL`).
  - Creates `PaymentReceipt` with crypto signature.
  - Updates `FeeLine.paid_amount` and marks status `PAID` or `PARTIAL`.
  - Clears late penalty balance if included in amount.
  - Logs `PROCESS_PAYMENT` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `201 Created`: `BackOfficePaymentResponse` with transaction ID, receipt reference, paid breakdown, and remaining dues.
  - `400 Bad Request`: Amount exceeds outstanding dues (Overpayment Guardrail).
  - `409 Conflict`: Idempotency key reused with mismatched payload.
  - `422 Unprocessable Entity`: EPP requested with debit card or card decline.

---

### 5.7 `POST /api/v1/payments/{id}/retry`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Re-attempts payment authorization for a failed transaction. Requires a fresh `Idempotency-Key` header.
- **The Need For It:** Allows bank operators to assist customers whose payments failed due to transient gateway network timeouts without creating duplicate charges.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID of failed transaction).
  - **Headers:** `Idempotency-Key: <new-uuid>`
- **What Info/Data It Creates / Alters:**
  - Creates a new retry attempt log and updates transaction status upon success.
  - Logs `RETRY_PAYMENT` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: Updated `TransactionDetailDto`.
  - `400 Bad Request`: Transaction is already successful or cancelled.

---

### 5.8 `GET /api/v1/payments/{id}/receipt`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Returns cryptographic receipt metadata, transaction confirmation number, SHA-256 digital signature, and PDF receipt download link (US-47).
- **The Need For It:** Official payment proof for parents, tax authorities, and school registrars.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `ReceiptDetailDto`.
  - `404 Not Found`: Transaction or receipt not found.

---

## 6. Phase 5: Reconciliation, Settlement & Payout Runs

### 6.1 `GET /api/v1/reconciliation/summary`
- **Roles:** `bank-reconciliation`, `bank-admin`, `bank-finance`.
- **What Does It Do?** Aggregates 3-way reconciliation totals across payment gateway, internal ledger, and CBE core banking system: `totalTransactions`, `matched`, `pending`, and `exceptions`.
- **The Need For It:** Top-level status banner on `Reconciliation.tsx`.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `ReconciliationSummaryDto`.

---

### 6.2 `GET /api/v1/reconciliation/runs` & `.../runs/{id}`
- **Roles:** `bank-reconciliation`, `bank-admin`, `bank-finance`.
- **What Does It Do?** Lists historical automated (6h / nightly) and manual reconciliation execution batches with status (`COMPLETED`, `RUNNING`, `FLAGGED`), matched counts, discrepancy amounts, and detailed matched transaction lists.
- **The Need For It:** Daily audit log of reconciliation batches and settlements.
- **What Info/Data It Needs:**
  - **Query Parameters:** `date`, `institution`, `status`, `page`, `pageSize`.
  - **Path Parameter (detail):** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `PageResponse<ReconciliationRunDto>` or `ReconciliationRunDetailDto`.

---

### 6.3 `POST /api/v1/reconciliation/runs`
- **Roles:** `bank-reconciliation`, `bank-admin`.
- **What Does It Do?** Triggers an immediate ad-hoc 3-way reconciliation batch run for a target date or institution.
- **The Need For It:** Allows operations to re-run reconciliation after resolving network outages or clearing delayed batch bank files.
- **What Info/Data It Needs:**
  - **Body (JSON, optional):**
    ```json
    {
      "date": "2026-09-07",
      "institutionId": "7d12f49a-..."
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Creates `ReconciliationRun` record (`status = PROCESSING`).
  - Spawns comparison job and generates discrepancy records.
  - Logs `TRIGGER_RECON_RUN` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `202 Accepted`: Initial `ReconciliationRunDto`.

---

### 6.4 `GET /api/v1/reconciliation/exceptions` & `.../exceptions/{id}`
- **Roles:** `bank-reconciliation`, `bank-admin`.
- **What Does It Do?** Retrieves paginated list and detailed 3-way comparison views of reconciliation discrepancies (amount mismatches, gateway orphaned charges, missing internal ledger lines) with priority and SLA metrics.
- **The Need For It:** Case management queue for reconciliation officers.
- **What Info/Data It Needs:**
  - **Query Parameters:** `status` (`OPEN`, `UNDER_INVESTIGATION`, `RESOLVED`), `priority`, `assignedTo`, `includeResolved`, `page`, `pageSize`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `PageResponse<ReconciliationExceptionDto>` or `ReconciliationExceptionDetailDto`.

---

### 6.5 `PATCH /api/v1/reconciliation/exceptions/{id}`
- **Roles:** `bank-reconciliation`, `bank-admin`.
- **What Does It Do?** Resolves an open reconciliation exception (US-59/60). Requires an action code (`FORCE_MATCH`, `ADJUST_LEDGER`, `WRITE_OFF`) and resolution notes.
- **The Need For It:** Regulated resolution workflow ensuring every financial discrepancy has an auditable justification.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Body (JSON):**
    ```json
    {
      "action": "FORCE_MATCH",
      "notes": "Verified against CBE RTGS settlement confirmation batch #4401."
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Updates `ReconciliationException.status` to `RESOLVED`, sets `resolved_at` and `resolved_by`.
  - Logs `RESOLVE_RECON_EXCEPTION` audit event (`severity = WARNING`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `ReconciliationExceptionDto`.
  - `400 Bad Request`: Missing mandatory resolution notes.

---

### 6.6 `POST /api/v1/reconciliation/exceptions/{id}/assign` & `GET /api/v1/reconciliation/assignees`
- **Roles:** `bank-reconciliation`, `bank-admin`.
- **What Does It Do?** Assigns an exception case to an investigator and lists eligible reconciliation officers.
- **The Need For It:** Workload management across the bank's back-office audit team.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Body (JSON):** `{ "assignee": "mona.recon" }`
- **What Info/Data It Creates / Alters:**
  - Updates `assigned_to` and transitions status to `UNDER_INVESTIGATION`.
  - Logs `ASSIGN_RECON_EXCEPTION` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: Updated `ReconciliationExceptionDto` or `List<String>` assignees.

---

### 6.7 `GET /api/v1/reconciliation/export`
- **Roles:** `bank-reconciliation`, `bank-finance`.
- **What Does It Do?** Streams a CSV report containing reconciliation run summaries and unresolved exceptions.
- **The Need For It:** Daily offline submission to CBE compliance inspectors.
- **What Info/Data It Needs:** `format=csv`.
- **What Info/Data It Creates / Alters:** Logs `EXPORT_RECONCILIATION_REPORT` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: CSV attachment.

---

## 7. Phase 6: Easy Payment Plans (EPP)

### 7.1 `GET /api/v1/epp/plans` & `.../plans/{id}`
- **Roles:** `bank-admin`, `bank-operations`, `bank-finance`.
- **What Does It Do?** Lists and inspects consumer installment financing plans converted from tuition fee payments. Details tenor (3, 6, 12, 18 months), interest rate, monthly installment, total paid, and next due date.
- **The Need For It:** EPP loan portfolio monitoring in `EPP.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `search`, `status` (`Active`, `Completed`, `Defaulted`, `Cancelled`), `tenor`, `page`, `pageSize`.
  - **Path Parameter (detail):** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `EppPlanListResponse` or `EppPlanDetailDto`.

---

### 7.2 `GET /api/v1/epp/summary`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Returns active, completed, defaulted plan counts and total outstanding installment debt in EGP.
- **The Need For It:** Top KPI widget on the EPP dashboard.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `EppSummaryResponse`.

---

### 7.3 `GET /api/v1/epp/plans/{id}/schedule`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Generates the detailed amortization schedule for an EPP plan: installment index (1 to $N$), due date, principal portion, interest portion, installment fee, and payment status (`PAID`, `DUE`, `UPCOMING`).
- **The Need For It:** Customer service inquiries and debt collection tracking.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `List<EppScheduleInstallmentDto>`.

---

### 7.4 `POST /api/v1/epp/quote`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Calculates installment pricing preview based on loan principal and selected tenor (3, 6, 12, 18 months). Computes interest rate (e.g. 10% for 3m, 12% for 6m), administrative fee, monthly installment amount, and total repayment amount.
- **The Need For It:** Real-time loan simulator displayed to parents and bank tellers prior to plan booking.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "principalAmount": 30000.00,
      "tenorMonths": 6
    }
    ```
- **What Info/Data It Creates / Alters:** None (Pure calculation).
- **Return Data & Status Codes:**
  - `200 OK`: `EppQuoteResponse`.

---

### 7.5 `POST /api/v1/epp/cards/validate`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Enforces Fintech Paranoia Guardrail #3: classifies card BIN prefixes. Validates that the card is an eligible CIB or domestic credit card, and strictly rejects debit card BINs (`5078`, `400000`, `604900`).
- **The Need For It:** Regulatory and credit risk protection—installment plans cannot be funded by debit accounts.
- **What Info/Data It Needs:**
  - **Body (JSON):** `{ "cardNumber": "4532111122223333" }`
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "eligible": true,
      "result": "valid-credit",
      "cardBrand": "VISA",
      "cardType": "CREDIT"
    }
    ```
  - `200 OK` (Debit card rejected):
    ```json
    {
      "eligible": false,
      "result": "rejected-debit",
      "message": "Debit cards are not eligible for installment financing plans."
    }
    ```

---

### 7.6 `POST /api/v1/epp/plans`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Creates and books a standalone or converted EPP plan from a tuition fee transaction. Enforces credit card BIN eligibility, maximum student active plans limit, and principal bounds.
- **The Need For It:** Allows bank back-office staff to book installment plans on behalf of eligible cardholders.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "transactionId": "a910bf20-...",
      "principalAmount": 45000.00,
      "tenor": 12,
      "cardNumber": "4532111122223333",
      "firstPaymentDate": "2026-10-01"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Inserts `EppPlan` record (`status = Active`).
  - Generates $N$ `EppInstallment` schedule entities.
  - Logs `CREATE_EPP_PLAN` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `201 Created`: `EppPlanDetailDto`.
  - `400 Bad Request`: Debit card detected or principal exceeds limits.

---

### 7.7 `PATCH /api/v1/epp/plans/{id}`
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Updates the administrative status of an EPP plan (`Active`, `Completed`, `Defaulted`, `Cancelled`) with mandatory reason notes.
- **The Need For It:** Loan delinquency handling and cancellation workflows.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Body (JSON):** `{ "status": "Defaulted", "reason": "3 consecutive missed installments" }`
- **What Info/Data It Creates / Alters:**
  - Updates `EppPlan.status`.
  - Logs `UPDATE_EPP_PLAN_STATUS` audit event (`severity = WARNING`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `EppPlanDetailDto`.

---

## 8. Phase 7: Reports Engine & Asynchronous Exports

### 8.1 `GET /api/v1/reports/catalogue`
- **Roles:** `bank-admin`, `bank-finance`.
- **What Does It Do?** Returns the catalogue of 10 standard financial and operational reports with report IDs, titles, descriptions, supported export formats (`CSV`, `XLSX`, `PDF`), required filter parameters, and last generation timestamps.
- **The Need For It:** Populates the report selection screen in `Reports.tsx`.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `List<ReportCatalogueEntry>`.

---

### 8.2 `POST /api/v1/reports/generate`
- **Roles:** `bank-admin`, `bank-finance`.
- **What Does It Do?** Creates and executes a report generation job with date range validation (`dateFrom <= dateTo`) and filter constraints. Returns job metadata and inline sample preview rows.
- **The Need For It:** On-demand generation of reconciliations, daily settlement sheets, tax summaries, and delinquency logs.
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "reportId": "daily-collections-summary",
      "format": "CSV",
      "dateFrom": "2026-09-01",
      "dateTo": "2026-09-07",
      "institutionId": null
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Creates `ReportJob` record with UUID.
  - Updates report execution history.
  - Logs `GENERATE_REPORT` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `200 OK`: `ReportJobResponse` containing job status (`READY`), filename, download URL, preview column headers, and data rows.
  - `400 Bad Request`: Invalid date range or unsupported format.

---

### 8.3 `GET /api/v1/reports/jobs/{jobId}` & `.../download`
- **Roles:** `bank-admin`, `bank-finance`.
- **What Does It Do?** Checks generation job progress and streams the compiled report file (XLSX, CSV, PDF) as an attachment.
- **The Need For It:** Allows client polling for heavy export jobs and secure binary file delivery.
- **What Info/Data It Needs:**
  - **Path Parameter:** `jobId` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `ReportJobResponse` or binary file stream.

---

### 8.4 `GET /api/v1/reports/history`
- **Roles:** `bank-admin`, `bank-finance`.
- **What Does It Do?** Returns a paginated log of all previously executed report jobs with initiator, date range, format, and download availability.
- **The Need For It:** Renders the "Past Reports" tab in `Reports.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `reportId`, `page`, `pageSize`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `PageResponse<ReportHistoryEntry>`.

---

## 9. Phase 8: Notifications Engine & Real-Time SSE Stream

### 9.1 `GET /api/v1/notifications`
- **Roles:** `ROLE_BACK_OFFICE` (All 4 roles).
- **What Does It Do?** Returns a paginated list of back-office notifications with filtering by event type (`TRANSACTION`, `SETTLEMENT`, `RECON_EXCEPTION`, `SYSTEM`, `ONBOARDING`) and unread status.
- **The Need For It:** Feeds the notification feed panel in `Notifications.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `type`, `unread` (boolean), `page`, `pageSize`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `NotificationListResponse`.

---

### 9.2 `GET /api/v1/notifications/unread-count`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Returns integer count of unread notifications for the active user.
- **The Need For It:** Drives the notification bell badge counter in the top navigation bar.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `{ "count": 5 }`.

---

### 9.3 `POST /api/v1/notifications/{id}/read` & `POST /api/v1/notifications/read-all`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Marks a specific notification or all unread notifications as read.
- **The Need For It:** User inbox management.
- **What Info/Data It Needs:**
  - **Path Parameter (for single):** `id` (UUID).
- **What Info/Data It Creates / Alters:**
  - Sets `is_read = true` and `read_at = CURRENT_TIMESTAMP`.
  - Logs `NOTIFICATION_MARKED_READ` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: `{ "id": id, "read": true }` or `{ "updated": 8 }`.

---

### 9.4 `DELETE /api/v1/notifications/{id}`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Dismisses/deletes a notification from the user's feed.
- **The Need For It:** Removing acknowledged or obsolete operational alerts.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:**
  - Deletes or archives the notification entity.
  - Logs `NOTIFICATION_DISMISSED` audit event.
- **Return Data & Status Codes:**
  - `204 No Content`.

---

### 9.5 `GET /api/v1/notifications/stream`
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Opens a persistent Server-Sent Events (SSE) HTTP connection (`text/event-stream`). Emits real-time notifications as events occur in the system (e.g. high-value transaction, reconciliation exception, institution approval).
- **The Need For It:** Provides real-time reactive updates to back-office operators without requiring browser polling loops.
- **What Info/Data It Needs:**
  - **Headers:** `Accept: text/event-stream`
- **What Info/Data It Creates / Alters:**
  - Registers an active `SseEmitter` in `NotificationsService`.
- **Return Data & Status Codes:**
  - `200 OK`: Infinite SSE stream with `event: notification` payloads.

---

## 10. Phase 9: Audit Logs & Regulatory Compliance

### 10.1 `GET /api/v1/audit-logs` & `.../audit-logs/{id}`
- **Roles:** `bank-admin` only.
- **What Does It Do?** Performs multi-field search and retrieval across the append-only audit trail. Supports filtering by text search, actor role, severity level (`INFO`, `WARNING`, `CRITICAL`), and date range.
- **The Need For It:** Central Bank of Egypt compliance mandate requiring forensic oversight of all user actions and system configuration changes.
- **What Info/Data It Needs:**
  - **Query Parameters:** `search`, `role` (or `actorType`), `severity`, `dateFrom`, `dateTo`, `page`, `pageSize`.
  - **Path Parameter (detail):** `id` (UUID).
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `PageResponse<AuditLogDto>` or `AuditLogDto`.

---

### 10.2 `GET /api/v1/audit-logs/stats`
- **Roles:** `bank-admin`.
- **What Does It Do?** Returns audit record aggregation counts categorized by severity (`critical`, `warning`, `info`) and total volume over an optional date range.
- **The Need For It:** Renders the severity health cards on `AuditLogs.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `dateFrom`, `dateTo`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `{ "total": 142, "critical": 8, "warning": 24, "info": 110 }`.

---

### 10.3 `GET /api/v1/audit-logs/roles`
- **Roles:** `bank-admin`.
- **What Does It Do?** Returns distinct list of all actor roles currently recorded in the audit repository.
- **The Need For It:** Dynamically populates the role filter dropdown on the audit log screen.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `["BANK_ADMIN", "BANK_OPERATIONS", "BANK_FINANCE", "BANK_RECONCILIATION", "SYSTEM"]`.

---

### 10.4 `GET /api/v1/audit-logs/export`
- **Roles:** `bank-admin`.
- **What Does It Do?** Exports filtered audit records as a signed, timestamped UTF-8 CSV file.
- **The Need For It:** External submission to CBE examiners and regulatory audit archives.
- **What Info/Data It Needs:** Same query filters as `GET /audit-logs`.
- **What Info/Data It Creates / Alters:** Logs `EXPORT_AUDIT_LOGS` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: CSV attachment.

---

## 11. Phase 10: Bank Users & Role-Based Access Control (RBAC)

### 11.1 `GET /api/v1/users` (Alias: `/users`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Paginated list of bank back-office employees with filtering by name/email search, role (`bank-admin`, `bank-operations`, `bank-finance`, `bank-reconciliation`), and status (`Active`, `Inactive`, `Locked`).
- **The Need For It:** User administration grid in `Users.tsx`.
- **What Info/Data It Needs:**
  - **Query Parameters:** `search`, `role`, `status`, `page`, `pageSize`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `PageResponse<BankUserSummaryDto>`.

---

### 11.2 `GET /api/v1/users/summary` (Alias: `/users/summary`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Returns active user count grouped by bank role: `Bank Admin`, `Operations`, `Finance`, and `Reconciliation`.
- **The Need For It:** Displays the active staff summary KPI cards in `Users.tsx`.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "Bank Admin": 3,
      "Operations": 8,
      "Finance": 4,
      "Reconciliation": 5
    }
    ```

---

### 11.3 `POST /api/v1/users` (Alias: `/users`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Provisions a new bank employee account. Validates corporate email format, verifies email and username uniqueness (returning `409 Conflict` on duplicate), auto-generates username if blank, sets default department fallback, and triggers an initial onboarding email.
- **The Need For It:** User onboarding workflow (US-64).
- **What Info/Data It Needs:**
  - **Body (JSON):**
    ```json
    {
      "name": "Nour Ahmed",
      "username": "nour.ops",
      "email": "nour.ahmed@cibeg.com",
      "role": "bank-operations",
      "department": "Operations"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Inserts `BankUser` record (`status = Active`, temporary credentials generated).
  - Logs `CREATE_BANK_USER` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `201 Created`: `BankUserSummaryDto`.
  - `400 Bad Request`: Invalid email format.
  - `409 Conflict`: Email or username already exists.

---

### 11.4 `GET /api/v1/users/{id}` & `PATCH /api/v1/users/{id}` (Alias: `/users/{id}`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Retrieves employee details and updates user attributes (name, email, role, department). Checks email uniqueness on changes.
- **The Need For It:** User modification drawer in `Users.tsx`.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Body (JSON, for PATCH):** `{ "role": "bank-finance", "department": "Finance & Treasury" }`
- **What Info/Data It Creates / Alters:**
  - Updates `BankUser` fields.
  - Logs `UPDATE_BANK_USER` audit event (`severity = INFO`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `BankUserSummaryDto`.
  - `404 Not Found`: User not found.

---

### 11.5 `POST /api/v1/users/{id}/deactivate` & `.../{id}/activate` (Alias: `/users/...`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Deactivates or reactivates an employee account. **Immediate Access Loss:** Deactivation immediately revokes active refresh tokens and terminates the employee's ability to authenticate or call protected APIs.
- **The Need For It:** Immediate offboarding or temporary suspension of employees leaving the bank or under internal review.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:**
  - Updates `BankUser.status` to `Inactive` or `Active`.
  - Revokes all active refresh tokens for the user.
  - Logs `DEACTIVATE_BANK_USER` (`severity = WARNING`) or `ACTIVATE_BANK_USER` (`severity = INFO`).
- **Return Data & Status Codes:**
  - `200 OK`: `{ "status": "Inactive" }` or `{ "status": "Active" }`.

---

### 11.6 `POST /api/v1/users/{id}/reset-password` (Alias: `/users/{id}/reset-password`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Triggers an administrative password reset email for an employee (AUD-012). Generates a 15-minute reset token without exposing it in the HTTP response.
- **The Need For It:** Helpdesk assisted password recovery for locked out bank users.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
- **What Info/Data It Creates / Alters:**
  - Inserts `PasswordResetToken` record.
  - Logs `ADMIN_TRIGGERED_PASSWORD_RESET` audit event (`severity = WARNING`).
- **Return Data & Status Codes:**
  - `200 OK`: `{ "message": "Password reset email sent" }`.

---

## 12. Phase 11: System Settings & Configurable Parameters

### 12.1 `GET /api/v1/settings/fee-types` & `POST /api/v1/settings/fee-types` (Alias: `/settings/...`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Lists configurable fee categories (`TUITION`, `BUS`, `UNIFORM`, `ACTIVITIES`, `BOOKS`, `EXAM`) and allows adding new categories. Validates code uniqueness (returns `409 Conflict` on duplicate code).
- **The Need For It:** Supports dynamic educational billing structures across schools.
- **What Info/Data It Needs (POST):**
  - **Body (JSON):**
    ```json
    {
      "name": "Technology & Lab Fee",
      "code": "TECH_LAB",
      "taxable": false,
      "active": true
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Inserts `FeeTypeSetting` record.
  - Logs `CREATE_FEE_TYPE` audit event (`severity = CRITICAL`).
- **Return Data & Status Codes:**
  - `200 OK` / `201 Created`: `FeeTypeSettingDto`.
  - `409 Conflict`: Fee type code already exists.

---

### 12.2 `PATCH /api/v1/settings/fee-types/{id}` (Alias: `/settings/fee-types/{id}`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Updates fee type name, taxability flag, or active status.
- **The Need For It:** Disabling obsolete fee types without deleting historical billing lines.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID).
  - **Body (JSON):** `{ "active": false }`
- **What Info/Data It Creates / Alters:**
  - Updates `FeeTypeSetting`.
  - Logs `UPDATE_FEE_TYPE` audit event (`severity = CRITICAL`).
- **Return Data & Status Codes:**
  - `200 OK`: Updated `FeeTypeSettingDto`.

---

### 12.3 `GET /api/v1/settings/payment-statuses` (Alias: `/settings/payment-statuses`)
- **Roles:** `ROLE_BACK_OFFICE`.
- **What Does It Do?** Returns read-only reference metadata for all 6 payment statuses: `PENDING`, `AUTHORIZED`, `SETTLED`, `FAILED`, `CANCELLED`, and `REFUNDED` (informational only), detailing description and terminal status flags.
- **The Need For It:** Reference dictionary for front-end badge styling and state machine documentation.
- **What Info/Data It Needs:** None.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `List<PaymentStatusInfo>`.

---

### 12.4 `GET /api/v1/settings/epp` & `PUT /api/v1/settings/epp` (Alias: `/settings/epp`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Manages bank-wide EPP financing parameters: enabled tenors (`3`, `6`, `12`, `18` months), interest rate per tenor (default: 10% for 3m, 12% for 6m, etc.), administrative processing fee percentage, minimum plan principal (e.g. 5,000 EGP), and maximum plan principal (e.g. 200,000 EGP).
- **The Need For It:** Allows bank finance managers to adjust credit terms to reflect changing CBE monetary policy and corridor interest rates.
- **What Info/Data It Needs (PUT):**
  - **Body (JSON):** Full `EppSettingsDto`.
- **What Info/Data It Creates / Alters:**
  - Overwrites system EPP configuration.
  - **Audit Requirement:** Writes audit entry with **`severity = "CRITICAL"`** and action `UPDATE_EPP_SETTINGS`.
- **Return Data & Status Codes:**
  - `200 OK`: Updated `EppSettingsDto`.

---

### 12.5 `GET /api/v1/settings/notifications` & `PUT /api/v1/settings/notifications` (Alias: `/settings/notifications`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Configures platform alert triggers and notification channels (Email, SMS, SSE in-app push) for critical events: high-value transactions, reconciliation exceptions, overdue deadline warnings, and onboarding applications.
- **The Need For It:** Controls alert routing and operational noise levels.
- **What Info/Data It Needs (PUT):**
  - **Body (JSON):** Full `NotificationSettingsDto`.
- **What Info/Data It Creates / Alters:**
  - Overwrites notification configuration.
  - **Audit Requirement:** Writes audit entry with **`severity = "CRITICAL"`** and action `UPDATE_NOTIFICATION_SETTINGS`.
- **Return Data & Status Codes:**
  - `200 OK`: Updated `NotificationSettingsDto`.

---

### 12.6 `GET /api/v1/settings/institutions` & `PUT /api/v1/settings/institutions` (Alias: `/settings/institutions`)
- **Roles:** `bank-admin`.
- **What Does It Do?** Manages global institutional onboarding controls: dual-authorization requirement toggle (`requireDualApproval`), mandatory Ministry of Education certificate toggle (`requireMOECertificate`), maximum student upload batch size, and allowed ingestion file types (`CSV`, `XLSX`).
- **The Need For It:** Compliance policy tuning for school onboarding.
- **What Info/Data It Needs (PUT):**
  - **Body (JSON):** Full `InstitutionSettingsDto`.
- **What Info/Data It Creates / Alters:**
  - Overwrites institution settings.
  - **Audit Requirement:** Writes audit entry with **`severity = "CRITICAL"`** and action `UPDATE_INSTITUTION_SETTINGS`.
- **Return Data & Status Codes:**
  - `200 OK`: Updated `InstitutionSettingsDto`.

---

## 13. Phase 12: Payment Deadlines, Priority Queues & Late Penalties

### 13.1 `PATCH /api/v1/fees/{id}/due-date` (Alias: `/fees/{id}/due-date`)
- **Roles:** `bank-admin`, `bank-operations`.
- **What Does It Do?** Overrides the scheduled payment due date of a specific fee line. Automatically recalculates the days until due, dynamic priority bucket (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), grace period expiration, and removes or updates pending late penalty flags accordingly.
- **The Need For It:** Allows bank operations to grant authorized extensions to distressed families or handle administrative school schedule changes.
- **What Info/Data It Needs:**
  - **Path Parameter:** `id` (UUID of the fee line).
  - **Body (JSON):**
    ```json
    {
      "dueDate": "2026-10-15",
      "reason": "Family financial relief extension approved by school board"
    }
    ```
- **What Info/Data It Creates / Alters:**
  - Updates `FeeLine.due_date`.
  - Recalculates `FeeLine.priority` and recalculates deadline snapshot.
  - Logs `FEE_DUE_DATE_CHANGED` audit event with actor ID (`severity = WARNING`).
- **Return Data & Status Codes:**
  - `200 OK`: `FeeDeadlineDto` containing updated due date, priority, days to due, outstanding EGP, penalty EGP, and total due.
  - `404 Not Found`: Fee line ID does not exist.

---

### 13.2 `POST /api/v1/internal/fees/apply-penalties` (Alias: `/internal/fees/apply-penalties`)
- **Roles:** Back-office service / Scheduled batch runner.
- **What Does It Do?** Nightly batch job that evaluates all overdue fee lines past grace period. Implements Master Spec §15 penalty rules:
  1. **Remainder-Only 5% Calculation:** Computes the 5% penalty strictly on the *unpaid remaining amount* (`remainingAmount = totalAmount - paidAmount`), never on the original total fee.
  2. **Idempotency ("No Second 5%"):** Checks `penalty_applied_at`; skips fee lines that have already been penalized.
  3. **Overpayment Allocation Preservation:** Separates `penalty_amount` from principal dues, ensuring principal payments only reduce `remainingAmount` to preserve the overpayment guardrail.
- **The Need For It:** Automated execution of contractual late penalties on overdue tuition accounts.
- **What Info/Data It Needs:** None (Triggered by scheduler or internal webhook).
- **What Info/Data It Creates / Alters:**
  - Updates each qualified `FeeLine`: sets `penalty_amount = remainingAmount * 0.05`, `penalty_applied_at = CURRENT_TIMESTAMP`, `priority = CRITICAL`.
  - Logs `BATCH_PENALTIES_APPLIED` audit event (`severity = INFO`) recording total evaluated, penalized count, and total EGP penalty volume.
- **Return Data & Status Codes:**
  - `200 OK`:
    ```json
    {
      "evaluatedCount": 540,
      "penalizedCount": 38,
      "totalPenaltyAmountEGP": 42750.00,
      "executionTimestamp": "2026-09-08T00:00:00Z"
    }
    ```

---

## 14. Core Citizen & Institution Portal Endpoints

### 14.1 `GET /api/v1/guardian/dues`
- **Roles:** `ROLE_GUARDIAN`, `ROLE_BACK_OFFICE`.
- **What Does It Do?** Public / Parent mobile portal endpoint to query open tuition and service dues by parent Egyptian National ID.
- **The Need For It:** Enables parents to view their children's pending school fees in the citizen mobile app or web portal.
- **What Info/Data It Needs:**
  - **Header or Query Parameter:** `X-Guardian-National-Id` or `parentNationalId`.
- **What Info/Data It Creates / Alters:** None.
- **Return Data & Status Codes:**
  - `200 OK`: `GuardianDuesResponse` with student profiles and open fee lines.
  - `400 Bad Request`: Missing national ID.

---

### 14.2 `POST /api/v1/institutions/{id}/dues/{feeLineId}/cancel`
- **Roles:** `ROLE_INSTITUTION_ADMIN` (Scoped to own institution).
- **What Does It Do?** Cancels an individual fee line at the school. **Mid-Year EPP Lock Protection:** If the fee line is already locked into an active EPP installment financing plan with the bank, cancellation is rejected with `422 Unprocessable Entity` to prevent ledger desynchronization.
- **The Need For It:** Allows schools to cancel mistaken or waived tuition charges while protecting active bank financing contracts.
- **What Info/Data It Needs:**
  - **Path Parameters:** `id` (Institution UUID), `feeLineId` (Fee line UUID).
- **What Info/Data It Creates / Alters:**
  - Updates `FeeLine.status = CANCELLED`.
  - Logs `CANCEL_FEE_LINE` audit event.
- **Return Data & Status Codes:**
  - `200 OK`: `{ "status": "CANCELLED", "message": "Fee line ... successfully cancelled." }`.
  - `422 Unprocessable Entity`: Cannot cancel fee line bound to an active EPP plan.
  - `403 Forbidden`: Institution access violation.

---

## 15. Summary of Excluded Endpoints

Per explicit business policy and banking architecture specifications, the following endpoints are intentionally **excluded** from the application:

| Endpoint | Method | Status | Rationale & Policy |
| :--- | :--- | :---: | :--- |
| `/transactions/{id}/refund` | `POST` | **EXCLUDED** | **Zero Refund Policy:** Reversals and refunds are strictly disallowed across all portals and APIs to prevent chargeback fraud and ledger desynchronization. |
| `/transactions/{id}/reverse` | `POST` | **EXCLUDED** | **Zero Refund Policy:** Payment reversals must be processed through manual, offline banking dispute resolution and interbank settlement claims. |

---

*This document serves as the authoritative API technical specification for all 12 phases of the CIB Tuition & Services Fees Collection Network.*
