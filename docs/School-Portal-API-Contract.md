# School Portal — REST API Contract
**CIB Tuition & Services Fees Collection Network**
Companion to Bank-Back-Office-API-Contract.md (P0-02) — same backend, same conventions, different client and role set.  
**Status: Aligned & Approved for Implementation (v1.1)** — Harmonized with Master Spec, Onboarding Guide & Bank Back-Office API

This contract lists all endpoints invoked by the School Institution Portal client. Core bank operations (institution approvals, citizen payment processing, bank EPP creation, 3-way reconciliation resolution, network audit logs, bank user provisioning, and global system settings) are intentionally out-of-scope for schools. Where an endpoint represents shared domain data (e.g. payment allocations or reconciliation run summaries), only the school-scoped view is exposed; the underlying authoritative financial aggregate remains governed by the Bank contract.

## Global Conventions
(Inherited from the Bank contract and Developer Onboarding Guide — kept strictly uniform)

| Item | Rule |
| :--- | :--- |
| **Base URL** | `/api/v1` |
| **Auth** | `Authorization: Bearer <accessToken>` on every call except Phase 1 login/reset |
| **Money** | integer or decimal EGP in `*EGP` fields (e.g. `amountEGP`, `paidEGP`, `remainingEGP`) — matches the Bank contract convention |
| **Timestamps** | ISO-8601 UTC in payloads; display timezone is Africa/Cairo (`EET`, UTC+2) |
| **Pagination** | `?page=1&pageSize=25 -> { data:[...], page, pageSize, total, totalPages }` |
| **List filters** | `?search=` plus resource-specific params; `?sort=field:asc` |
| **Error shape** | `{ "error": { "code": "snake_case", "message": "...", "details": {} } }` |
| **Audit** | Every mutating endpoint writes an immutable audit entry (`actorUserId`, `actorRole`, `action`, `entityType`, `entityId`, `before`, `after`, `ip`, `requestId`). School users cannot read the bank's global audit log, but all school mutations write to it for data privacy compliance. |
| **School Scoping**| Every response is implicitly filtered to the authenticated user's `schoolId` / `institutionId`, resolved server-side from the JWT token claims — never from a client-supplied param. Manipulating an ID in the URL must not leak another school's records (zero cross-institution data bleed). |

## School Portal Roles & Spring Security Mapping
* `school-admin` (Spring Security authority: `ROLE_SCHOOL_ADMIN`): Full administration of school users, students, fee lines, uploads, and school settings.
* `school-finance` (Spring Security authority: `ROLE_SCHOOL_FINANCE`): Operational view and management of student rosters, fees, CSV uploads, payment allocations, reconciliation summaries, and reporting.
* Mapped server-side in `IdentityUserDetailsService` from the `InstitutionAdmin` entity via the `institution_admin.role` column.

**Deck/module map:** identity -> P1 · dashboard -> P2 · students -> P3 · fees -> P4 · upload -> P5 · payments (view) -> P6 · reconciliation (view) -> P7 · reports -> P8 · notifications -> P9 · users -> P10 · settings -> P11

---

## PHASE 1 — Authentication, Session & Permissions
Source: Login.tsx, MFA.tsx, ForgotPassword.tsx, ResetPassword.tsx, App.tsx. Same credentials -> MFA -> session -> role flow as the Bank contract, on a separate token/session namespace so a bank session can never authenticate against School Portal endpoints and vice versa.

### 1.1 POST /auth/login
Validates username/email + password; does not issue a session.

**Request:**
```json
{ "username": "d.fouad@cis.edu.eg", "password": "Finance@2026", "rememberMe": false }
```

**Response 200** (MFA required per US-02, "when MFA is enabled or required" — not every login necessarily challenges):
```json
{
  "mfaRequired": true,
  "mfaToken": "mfa_9b2e...",
  "otpChannel": "sms",
  "otpDestinationHint": "**** 7710",
  "expiresInSeconds": 60,
  "resendAvailableInSeconds": 60
}
```
If MFA is not required for this account/policy, return the full session payload from 1.2 directly instead of `mfaRequired`.
**Errors:** 400 `missing_credentials` · 401 `invalid_credentials` · 403 `account_locked` · 403 `account_deactivated` (deactivated school user, US-06) · 429 `rate_limited`

### 1.2 POST /auth/mfa/verify
```json
{ "mfaToken": "mfa_9b2e...", "code": "482913" }
```

**Response 200:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "rt_7c...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "USR-SCH-014",
    "name": "Dina Fouad",
    "email": "d.fouad@cis.edu.eg",
    "role": "school-finance",
    "schoolId": "SCH-001",
    "schoolName": "Cairo International School",
    "permissions": ["dashboard","students","fee-management","fee-upload",
                      "payments","reconciliation","reports","notifications"],
    "mustChangePassword": false,
    "lastLoginAt": "2026-09-06T08:12:00Z"
  }
}
```
`role` is `school-admin | school-finance`. `permissions` is the server-authoritative screen allow-list (US-03: "permission checks are applied consistently across the frontend and backend").
**Errors:** 400 `invalid_code_format` · 401 `invalid_code` · 410 `code_expired` · 401 `mfa_token_invalid` · 429 `too_many_attempts`

### 1.3 POST /auth/mfa/resend
```json
{ "mfaToken": "mfa_9b2e..." }
```
-> `{ "expiresInSeconds": 60, "resendAvailableInSeconds": 60 }`
**Errors:** 401 `mfa_token_invalid` · 429 `resend_throttled`

### 1.4 POST /auth/mfa/trust-device
```json
{ "mfaToken": "mfa_9b2e..." }
```
-> sets a trusted-device cookie/flag per US-02 ("Remember this device"). Returns 200.

### 1.5 POST /auth/forgot-password
```json
{ "email": "d.fouad@cis.edu.eg" }
```
-> `{ "message": "If the account exists, a reset link has been sent." }` (no enumeration).
**Errors:** 400 `invalid_email` · 429 `rate_limited`

### 1.6 POST /auth/reset-password
```json
{ "token": "prt_5f...", "newPassword": "NewFinance@2027" }
```
Same policy as the Bank contract (≥8 chars, upper, lower, digit, special). 200 `{ "message": "Password updated. Please sign in." }`
**Errors:** 400 `weak_password` (with unmet: [...]) · 400 `password_reused` · 401 `reset_token_invalid` · 410 `reset_token_expired`

### 1.7 POST /auth/refresh
```json
{ "refreshToken": "rt_7c..." }
```
-> same shape as 1.2.
**Errors:** 401 `refresh_token_invalid` / `refresh_token_expired`.

### 1.8 POST /auth/logout
Header auth, body `{ "refreshToken": "rt_7c..." }` -> 204.

### 1.9 GET /auth/me
Rehydrates role/school on app load. Same shape as user in 1.2. 401 `unauthenticated` -> redirect to Login.

### 1.10 Role -> permission matrix (server-owned)
| Screen | school-admin | school-finance |
| :--- | :--- | :--- |
| dashboard | Y | Y |
| students | Y | Y |
| students (add/edit/deactivate) | Y | — |
| fee-management (view) | Y | Y |
| fee-management (add/edit fee) | Y | Y |
| fee-upload | Y | Y |
| payments (view) | Y | Y |
| reconciliation (view) | Y | Y |
| reports | Y | Y |
| notifications | Y | Y |
| users | Y | — |
| settings | Y | — |

A request behind a disallowed screen -> 403 `forbidden` (UI renders "Access Restricted", S30).

---

## PHASE 2 — Dashboard
Source: Dashboard.tsx. Read-only, both roles. School-scoped only (US-16/US-17/US-18).

### 2.1 GET /dashboard/summary
```json
{
  "asOf": "2026-09-07T06:00:00Z",
  "kpis": {
    "totalCollectedEGP": { "value": 4286000, "trendPct": 5.4 },
    "outstandingEGP": { "value": 1120000, "trendPct": -2.1 },
    "overdueEGP": { "value": 312000, "overdueFeeCount": 47, "trendPct": 8.9 },
    "feeUploadStatus": { "lastUploadStatus": "Completed with Errors",
                          "lastUploadAt": "2026-09-05T10:00:00Z",
                          "pendingResubmission": true }
  }
}
```

### 2.2 GET /dashboard/recent-payments?limit=6
```json
{ "data": [
  { "id": "TX-20260906-0041", "studentId": "STU-0231", "studentName": "Yousef Adel",
    "feeId": "FEE-0231-01", "feeName": "Tuition - Term 1 2026/27",
    "amountEGP": 5000, "date": "2026-09-06T13:40:00Z", "status": "Successful", "isPartial": true }
] }
```

### 2.3 GET /dashboard/quick-links
Returns the six quick-action targets shown on S04 (Students, Fees, Upload Fees, Payments, Reports, Notifications) — static, but exposed so counts/badges (e.g. unread notifications, pending upload) can ride along.

---

## PHASE 3 — Student Management
Source: Students.tsx, S05-S11. Epic 4 + the deactivation/historical-records addendum (US-19 to US-25).

### 3.1 GET /students
Active students only. Query: `search` (name or studentRef), `grade`, `page`, `pageSize`.
```json
{ "data": [
  { "id": "STU-0231", "studentRef": "2026-0231", "name": "Yousef Adel", "grade": "Grade 10",
    "section": "B", "totalFeesEGP": 32000, "paidEGP": 20000, "outstandingEGP": 12000,
    "status": "Active" }
], "page": 1, "pageSize": 25, "total": 812, "totalPages": 33 }
```
National ID is never returned in this list view (US-20: "search does not expose sensitive National ID values unnecessarily").

### 3.2 GET /students/deactivated
Query: `search`, `deactivatedFrom`, `deactivatedTo`, `page`, `pageSize` (S11's explicit date-range filter).
```json
{ "data": [
  { "id": "STU-0198", "name": "Nour Kamal", "grade": "Grade 8",
    "deactivatedDate": "2026-07-14", "status": "Inactive" }
] }
```

### 3.3 GET /students/{id}
```json
{
  "id": "STU-0231", "studentRef": "2026-0231", "name": "Yousef Adel", "grade": "Grade 10",
  "section": "B", "status": "Active", "nationalIdMasked": "299*******4567",
  "parentName": "Adel Mostafa", "parentPhone": "+20 10 1234 5678",
  "parentEmail": "a.mostafa@example.com",
  "totals": { "totalFeesEGP": 32000, "totalPaidEGP": 20000, "totalOutstandingEGP": 12000 },
  "deactivatedDate": null
}
```
403 `cross_school_access` if the student does not belong to the caller's school (US-19/US-75).

### 3.4 POST /students
school-admin only.
```json
{ "studentRef": "2026-0512", "name": "Laila Samir", "grade": "Grade 3", "section": "A",
  "nationalId": "30105011234567", "parentName": "Samir Fathy",
  "parentPhone": "+20 10 9988 7766", "parentEmail": "s.fathy@example.com" }
```
201 with the created student. Errors: 400 `validation_failed`, 409 `duplicate_student_ref`.

### 3.5 PATCH /students/{id}
school-admin only. Partial update of the same fields. Errors: 400 `validation_failed`, 403 `cross_school_access`.

### 3.6 POST /students/{id}/deactivate
school-admin only, confirmation-gated on the client (S10 modal).
```json
{ "reason": "Withdrawn" }
```
-> 200 `{ "status": "Inactive", "deactivatedDate": "2026-09-07" }`. Historical fees/payments remain queryable via 3.8/3.9. 409 `already_inactive`.

### 3.7 POST /students/{id}/reactivate
school-admin only. 200 `{ "status": "Active", "deactivatedDate": null }`.

### 3.8 GET /students/{id}/fees
All fees for the student, active or historical.
```json
{ "data": [
  { "feeId": "FEE-0231-01", "name": "Tuition - Term 1 2026/27", "category": "Tuition",
    "dueDate": "2026-09-15", "originalAmountEGP": 18000, "paidEGP": 5000,
    "remainingEGP": 13000, "status": "Partial" }
], "totals": { "totalFeesEGP": 32000, "totalPaidEGP": 20000, "totalOutstandingEGP": 12000 } }
```

### 3.9 GET /students/{id}/payments
Query: `dateFrom`, `dateTo` (required-capable filter for the historical-records section, S11). Shape matches Phase 6.1 rows, scoped to this student, including payments made before deactivation.

### 3.10 GET /students/search?activeOnly=true&q=
Typed search used by the Add-Fee student picker (S14). Returns only active students in the caller's school; deactivated students are never returned regardless of the query.

---

## PHASE 4 — Fee Management
Source: FeeManagement.tsx, S12-S15, Epic 5, and the fee-rules addendum (due dates, Tuition penalty, priority allocation, one-week reminder). This phase carries the core business logic in the School Portal.

*   **Category enum:** `Tuition` | `Books` | `Activity` | `Bus` (matching Master Spec §4.2 `FeeType`). Late penalties are modeled directly as an embedded attribute of Tuition fee lines (`penaltyAmountEGP`, `penaltyAppliedAt`, and dynamic snapshot) rather than creating separate orphan invoice records.
*   **Status enum:** `Active` | `Paid` | `Partial` | `Outstanding` | `Overdue` | `Draft`. `Overdue` = due date passed and `remainingEGP > 0`; status is computed server-side on every read, never trusted from a stale client cache.

### 4.1 GET /fees
Query: `search` (fee name/id), `category`, `studentId`, `dueDateFrom`, `dueDateTo`, `status`, `page`, `pageSize`.
```json
{ "data": [
  { "id": "FEE-0231-01", "studentId": "STU-0231", "studentName": "Yousef Adel",
    "name": "Tuition - Term 1 2026/27", "category": "Tuition", "dueDate": "2026-09-15",
    "originalAmountEGP": 18000, "paidEGP": 5000, "remainingEGP": 13000, "status": "Partial",
    "penaltyApplied": false, "totalDueEGP": 13000 }
] }
```

### 4.2 GET /fees/{id}
Full detail: fee fields + `paymentHistory`: `[ { paymentId, dateEGP, amountEGP, status } ]` + `overdue`: `{ isOverdue, daysOverdue }` + embedded penalty snapshot.
```json
{
  "id": "FEE-0231-05", "studentId": "STU-0231", "name": "Tuition - Term 1 2026/27",
  "category": "Tuition", "term": "Term 1 2026/27", "dueDate": "2026-09-01",
  "originalAmountEGP": 18000, "paidEGP": 0, "remainingEGP": 18000, "status": "Overdue",
  "overdue": { "isOverdue": true, "daysOverdue": 8 },
  "penalty": {
    "applied": true,
    "penaltyAmountEGP": 900,
    "penaltyAppliedAt": "2026-09-09T08:00:00Z",
    "graceEnded": true,
    "totalDueEGP": 18900
  },
  "paymentHistory": []
}
```

### 4.3 POST /fees
school-admin, school-finance. `dueDate` is required — reject the save otherwise (400).
```json
{ "studentId": "STU-0231", "name": "Books - Term 1 2026/27", "category": "Books",
  "amountEGP": 2400, "term": "Term 1 2026/27", "dueDate": "2026-10-10" }
```
201 with `{ id, paidEGP: 0, remainingEGP: amountEGP, status: "Active" }`. Rejects deactivated students (409 `student_inactive`). Errors: 400 `due_date_required`, 400 `validation_failed`, 400 `invalid_category` (must be one of Tuition, Books, Activity, Bus).

### 4.4 PATCH /fees/{id}
Edit; enforces `newAmountEGP >= paidEGP` (US-27). school-admin, school-finance where the fee is still eligible for correction.
Errors: 400 `amount_below_paid`, 400 `due_date_required`, 409 `not_eligible_for_edit` (e.g. fee line locked in active settlement).

### 4.5 GET /fee-categories
```json
[
  { "code": "Tuition", "displayName": "Tuition", "priority": 1 },
  { "code": "Books", "displayName": "Books & materials", "priority": 2 },
  { "code": "Activity", "displayName": "Activities", "priority": 3 },
  { "code": "Bus", "displayName": "Bus subscription", "priority": 4 }
]
```
`priority` is the allocation-priority order used by payment allocation (overdue-first, then Tuition -> Books -> Activity -> Bus, then earliest due date) and read here so the UI renders it consistently.

### 4.6 GET /fees/{id}/penalty-info
For a Tuition fee: returns real-time deadline status from `FeeDeadlineSnapshot`:
```json
{
  "dueDate": "2026-09-01",
  "priority": "HIGH",
  "daysToDue": -8,
  "outstandingEGP": 18000,
  "penaltyAmountEGP": 900,
  "penaltyAppliedAt": "2026-09-09T08:00:00Z",
  "graceEnded": true,
  "totalDueEGP": 18900
}
```

### 4.7 Automated rules engines
(System background jobs — evaluated against `Africa/Cairo` timezone clock at midnight)

| Job | Trigger | Rule |
| :--- | :--- | :--- |
| **Tuition penalty engine** | Daily batch (`00:05` Cairo) | 5% flat penalty, Tuition only, fires exactly once when 7 full calendar days overdue and `remainingEGP > 0`. Updates `penaltyAmountEGP` and `penaltyAppliedAt` idempotently on the fee line; never fires on non-Tuition categories or fully-paid fees. |
| **One-week reminder engine** | Daily batch (`08:00` Cairo) | Sends a guardian payment reminder exactly 7 calendar days before `dueDate`, only if the fee is still outstanding (`remainingEGP > 0`), at most once per fee. Writes a Notification (type `reminder`). |

Both engines are School-Portal-scoped only — they evaluate against educational institution fee lines without altering bank back-office ledger configurations.

---

## PHASE 5 — Fee Upload
Source: FeeUpload.tsx, S16-S21, Epic 6. Flow: Upload -> Validate -> Accept valid rows -> Error report -> Correct -> Resubmit. A single bad row never rejects the whole file.

### 5.1 GET /fee-uploads/template?format=csv
Downloads the approved CSV template. The backend supports **Dual-Format CSV Ingestion** with automatic header detection:
* **Standard School SIS Format (Recommended 6 fields):** `studentRef, feeName, category, amountEGP, term, dueDate`
* **Legacy Master Spec Format (5 fields):** `nationalId, feeType, amount, currency, collectionPeriod`

### 5.2 POST /fee-uploads
`multipart/form-data` (file). school-admin, school-finance. Validates file header and processes rows with line-by-line error isolation (malformed rows do not abort valid rows). Response 202:
```json
{ "uploadId": "UPL-SCH001-0007", "status": "Processing" }
```
Errors: 400 `unsupported_file_type`, 409 `duplicate_upload` (same file/content hash previously submitted, US-37).

### 5.3 GET /fee-uploads/{uploadId}
```json
{ "uploadId": "UPL-SCH001-0007", "status": "Completed with Errors",
  "totalRows": 240, "validRows": 236, "invalidRows": 4, "acceptedRows": 236,
  "rejectedRows": 4, "uploadedAt": "2026-09-05T10:00:00Z", "fileName": "term1_fees.xlsx" }
```

### 5.4 GET /fee-uploads/{uploadId}/rows?status=
Row-level detail: rowNumber, studentRef, feeName, category, amountEGP, dueDate, status (Valid|Invalid|Accepted|Rejected), errorReason.

### 5.5 GET /fee-uploads/{uploadId}/errors
Rejected rows only, same shape, for the error-details drawer (S18).

### 5.6 GET /fee-uploads/{uploadId}/errors/export?format=csv
File stream of the rejected rows + reasons (S19).

### 5.7 POST /fee-uploads/{uploadId}/resubmit
`multipart/form-data` (corrected file, typically just the previously-rejected rows). Re-validates; accepted rows become real fees; still-invalid rows return updated error info. Never creates duplicate fees for rows already accepted in a prior pass (US-36).

### 5.8 GET /fee-uploads
Upload history list (S21): uploadId, uploadedAt, fileName, status, totalRows, acceptedRows, rejectedRows.

---

## PHASE 6 — Payments (view-only)
Source: Payments.tsx, S22-S23, Epic 7. School users never process a payment — the bank employee does that from the Bank Back-Office (`POST /payments` lives in that contract, not this one). This phase is read-only.

### 6.1 GET /payments
Query: `search` (payment id/student/fee), `dateFrom`, `dateTo`, `status`, `feeCategory`, `studentId`, `page`, `pageSize`.
```json
{ "data": [
  { "id": "TX-20260906-0041", "studentId": "STU-0231", "studentName": "Yousef Adel",
    "feeId": "FEE-0231-01", "feeName": "Tuition - Term 1 2026/27", "feeDueDate": "2026-09-15",
    "feePriority": 1, "amountEGP": 5000, "method": "Card", "date": "2026-09-06",
    "time": "13:40", "status": "Successful", "reconciliation": "Reconciled",
    "isPartial": true, "originalFeeAmountEGP": 18000, "remainingAfterEGP": 13000 }
] }
```
`status` mirrors the Bank contract's transaction statuses verbatim (Successful, Pending, Failed, Refunded, Reversed) — the School Portal never redefines them (fee-rules addendum, constraint #7).

### 6.2 GET /payments/{id}
Full detail including, when the payment was split across multiple fees, the allocation breakdown produced at processing time:
```json
{
  "id": "TX-20260906-0055", "studentId": "STU-0231", "amountEGP": 11000,
  "method": "Bank Transfer", "date": "2026-09-06", "status": "Successful",
  "reconciliation": "Pending",
  "allocation": [
    { "feeId": "FEE-0231-01", "feeName": "Tuition", "feeCategory": "Tuition",
      "dueDate": "2026-09-01", "originalAmountEGP": 10000, "previouslyPaidEGP": 0,
      "outstandingEGP": 10000, "allocatedEGP": 10000, "remainingAfterEGP": 0,
      "feeStatus": "Paid", "priority": 1, "isOverdue": true },
    { "feeId": "FEE-0231-02", "feeName": "Books", "feeCategory": "Books",
      "dueDate": "2026-09-10", "originalAmountEGP": 2000, "previouslyPaidEGP": 0,
      "outstandingEGP": 2000, "allocatedEGP": 1000, "remainingAfterEGP": 1000,
      "feeStatus": "Partial", "priority": 2, "isOverdue": false }
  ]
}
```
This is populated by the Bank Back-Office's allocation logic (overdue-first, then Tuition -> Books -> Activity -> Bus, then earliest due date) and simply displayed here — the School Portal does not run or trigger the allocation itself.

### 6.3 GET /payments/export?format=csv
Filtered export for the current view.

---

## PHASE 7 — Reconciliation (view-only)
Source: Reconciliation.tsx, S24, Epic 9. School users see status only — no assign/investigate/resolve, which are Bank Back-Office actions.

### 7.1 GET /reconciliation/summary
`{ "totalReconciled": 618, "totalUnreconciled": 4, "totalPending": 2 }` — school-scoped totals.

### 7.2 GET /reconciliation/transactions
Query: `dateFrom`, `dateTo`, `status` (Reconciled|Pending|Unreconciled), `studentId`, `paymentId`, `page`, `pageSize`.
```json
{ "data": [
  { "paymentId": "TX-20260906-0041", "studentId": "STU-0231", "feeId": "FEE-0231-01",
    "amountEGP": 5000, "date": "2026-09-06", "reconciliationStatus": "Reconciled" }
] }
```
No assign, resolve, or investigate actions exist on this resource for the School Portal role set — a POST to any exception-management path returns 403 `forbidden` for `school-admin`/`school-finance` even if attempted.

---

## PHASE 8 — Reports
Source: Reports.tsx, S25-S27, Epic 10. School-scoped only; no school-selector filter exists (the portal is already restricted to the logged-in user's school).

### 8.1 GET /reports/catalogue
```json
[
  { "id": "school-collections", "title": "Collection Report", "category": "Collections", "formats": ["PDF","CSV"] },
  { "id": "school-payments", "title": "Payment History", "category": "Payments", "formats": ["PDF","CSV"] },
  { "id": "school-outstanding-fees", "title": "Outstanding Fees", "category": "Fees", "formats": ["PDF","CSV"] },
  { "id": "school-partial-payments", "title": "Partial Payments", "category": "Fees", "formats": ["PDF","CSV"] }
]
```

### 8.2 POST /reports/generate
```json
{ "reportId": "school-outstanding-fees", "dateFrom": "2026-08-01", "dateTo": "2026-08-31",
  "format": "CSV", "filters": { "feeCategory": "Tuition", "paymentStatus": null } }
```
Response 202 `{ "jobId": "RPT-SCH-0007", "status": "processing" }`. Errors: 400 `date_from_after_date_to`, 400 `unsupported_format_for_report`.

### 8.3 GET /reports/jobs/{jobId}
Same shape as the Bank contract's 7.3 (status, downloadUrl, preview), scoped to this school's data only.

### 8.4 GET /reports/jobs/{jobId}/download
File stream.

### 8.5 GET /reports/history?reportId=&page=
Prior runs for this school.

---

## PHASE 9 — Notifications
Source: Notifications.tsx, S28-S29, Epic 11 + the one-week reminder addendum. Both roles; there is no create endpoint from this client — all notifications are server-generated.

### 9.1 GET /notifications
Query: `type` (payment|upload|reminder|penalty), `read`, `page`, `pageSize`.
```json
{ "data": [
  { "id": "NTF-0091", "type": "reminder", "title": "Payment reminder sent",
    "description": "Reminder: Yousef Adel's Tuition fee of 18,000 EGP is due in 1 week on 2026-09-15.",
    "studentName": "Yousef Adel", "feeType": "Tuition", "feeAmountEGP": 18000,
    "dueDate": "2026-09-15", "daysUntilDue": 7, "notificationStatus": "Sent",
    "date": "2026-09-08", "time": "08:00", "read": false, "relatedId": "FEE-0231-01" }
], "unreadCount": 3 }
```

### 9.2 GET /notifications/unread-count
`{ "count": 3 }`

### 9.3 POST /notifications/{id}/read
200

### 9.4 POST /notifications/read-all
200

### 9.5 DELETE /notifications/{id}
204 (dismiss)

### 9.6 GET /notifications/reminders
Parent-reminder records specifically, with Scheduled | Sent | Failed status — the dedicated view called out on S28's Notifications Center spec. Same row shape as 9.1 filtered to `type=reminder`.

---

## PHASE 10 — School Users
Source: Users.tsx, S10-S13, Epic 1 (school-side stories US-04 to US-06). school-admin only, both for the screen and every mutation.

### 10.1 GET /users
Query: `search`, `role` (School Admin|School Finance), `status`, `page`, `pageSize`.
```json
{ "data": [
  { "id": "USR-SCH-014", "name": "Dina Fouad", "email": "d.fouad@cis.edu.eg",
    "role": "School Finance", "status": "Active", "lastLogin": "2026-09-06T08:12:00Z" }
] }
```

### 10.2 POST /users
```json
{ "name": "Mostafa Sherif", "email": "m.sherif@cis.edu.eg", "role": "School Finance" }
```
201. Errors: 409 `email_exists`, 400 `invalid_email`, 400 `invalid_role` (only School Admin/School Finance accepted).

### 10.3 GET /users/{id}
Detail.

### 10.4 PATCH /users/{id}
`{ name?, email?, role? }`. Same 409/400 as 10.2.

### 10.5 POST /users/{id}/deactivate
Confirmation-gated on the client (S13). 200 `{ "status": "Inactive" }`. Historical activity retained.

### 10.6 POST /users/{id}/activate
200 `{ "status": "Active" }`.

### 10.7 GET /roles
`[ { "role": "School Admin", "permissions": [...] }, { "role": "School Finance", "permissions": [...] } ]` — mirrors the matrix in 1.10.

---

## PHASE 11 — Settings
Source: Settings.tsx. Not itemized screen-by-screen in the screen-list document, so this phase is inferred from the code/role model rather than a signed-off spec — flag for confirmation before build. school-admin only.

### 11.1 GET /settings/profile
Read-only school profile (name, address, contact) — editing institution-level details is a Bank Back-Office action (Phase 3 of the Bank contract), not exposed here.

### 11.2 GET / PUT /settings/notifications
School-level toggle for which notification types generate in-app/email entries (does not control the penalty/reminder business rules themselves, only delivery).
```json
{ "channels": { "inApp": true, "email": true } }
```

### 11.3 POST /settings/change-password
Self-service password change while logged in (distinct from the forgot-password flow in Phase 1).
```json
{ "currentPassword": "...", "newPassword": "..." }
```

---

## Out of scope
*(do not build under this contract — see the Bank contract instead)*
*   `POST /payments`, `POST /payments/{id}/refund`, `POST /payments/{id}/retry`, `/payments/allocate-preview` — payment processing is bank-employee-only.
*   `/institutions/*` — school registration, approval/rejection, activation, integration status management — Bank Back-Office only. The School Portal only ever reads its own already-approved profile (11.1).
*   `/epp/*` — EPP creation, eligibility checks, schedules — Bank Back-Office only; no EPP screen exists in the School Portal.
*   `/reconciliation/exceptions/*` (assign/resolve), `/reconciliation/runs` (trigger a match run) — Bank Back-Office only.
*   `/audit-logs/*` — Bank Portal, admin role only, per the epics doc explicitly.
*   `/users` under the bank namespace, `/settings/fee-types`, `/settings/epp`, `/settings/institutions` — Bank Back-Office configuration, not school-facing.
*   Any Parent-Portal endpoint — does not exist; parents are only ever reached via the one-week reminder notification generated by Phase 4.7's system job.

---

## Consolidated count
Auth 10 · Dashboard 3 · Students 10 · Fee Management 7 (+2 system jobs) · Fee Upload 8 · Payments 3 · Reconciliation 2 · Reports 5 · Notifications 6 · Users 7 · Settings 3 -> ~64 client-callable endpoints, plus the 2 internal engines in Phase 4.7.

---

## Final Design Resolutions & Implementation Decisions

1. **MFA Requirement Policy**:
   * Aligned with the Bank contract and US-02. Step-up MFA challenge (`{ "mfaRequired": true }`) is triggered when logging in from an unrecognized device or when MFA enforcement is active for the account. If a valid `trusted-device` cookie/token exists, the system issues the full session token directly upon password verification.

2. **Phase 11 Settings Scope**:
   * Confirmed lean scope: `GET /settings/profile` (read-only school metadata), `GET / PUT /settings/notifications` (in-app vs. email delivery toggles), and `POST /settings/change-password` (self-service password rotation). All institution profile mutations (principal, bank accounts, integration status) remain bank-governed under Bank Back-Office Phase 3.

3. **Dashboard KPI vs. Fees Query**:
   * Dashboard renders from the pre-aggregated `GET /dashboard/summary` KPI cache (`kpis.overdueEGP`). Clicking the KPI navigates to `GET /fees?status=Overdue` which computes server-side overdue status dynamically against the current date.

4. **Async Reporting Pipeline**:
   * Retains the unified asynchronous model (`POST /reports/generate` returning `202 Accepted` with `jobId` -> `GET /reports/jobs/{jobId}/download`). This maintains exact architectural symmetry with the Bank Back-Office reporting pipeline and handles large institutional student cohorts without HTTP gateway timeouts.

5. **Timezone Clock for Rules Engines**:
   * Confirmed. Both the 5% late penalty engine and the 7-day parent reminder scheduler execute against the **`Africa/Cairo` (EET, UTC+2)** midnight clock. This guarantees 100% synchronization between frontend "Overdue" badges and backend automated jobs without date drift.

6. **Late Penalty Data Architecture**:
   * Standardized to the embedded model (`penaltyAmountEGP` and `penaltyAppliedAt` on `FeeLine`, mapped via `FeeDeadlineSnapshot`). Rather than creating orphan child penalty fee lines, penalties are natively integrated into the tuition fee record and settled as part of the total due balance.

7. **Dual-Format CSV Ingestion**:
   * The file ingestion engine supports both the 6-field School SIS format (`studentRef, feeName, category, amountEGP, term, dueDate`) and the 5-field Master Spec legacy format (`nationalId, feeType, amount, currency, collectionPeriod`) with automatic header detection and line-by-line error isolation.

