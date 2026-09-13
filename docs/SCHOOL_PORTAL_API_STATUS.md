# School Portal API Contract — Implementation & Milestone Tracking

**Project:** CIB Tuition & Services Fees Collection Network — School Portal  
**Contract Reference:** [`docs/School-Portal-API-Contract.md`](file:///Users/nourahmed/Downloads/demo/docs/School-Portal-API-Contract.md)  
**Companion Status Document:** [`docs/BANK_BACK_OFFICE_API_STATUS.md`](file:///Users/nourahmed/Downloads/demo/docs/BANK_BACK_OFFICE_API_STATUS.md)  
**Status Date:** 2026-09-11  
**Backend Framework:** Spring Boot 4.1.1 / Java 25 / Spring Security 6 / Spring Data JPA  
**Tenant Scoping:** Strict Multi-Tenant School Scoping (`institutionId` enforced on all operations)  

---

## Executive Summary

The School Portal contract defines approximately **64 client-callable endpoints** and **2 automated system engines** across **11 phases** to back the front-end portal screens (`Login.tsx`, `Dashboard.tsx`, `Students.tsx`, `Fees.tsx`, `Upload.tsx`, `Payments.tsx`, `Reconciliation.tsx`, `Reports.tsx`, `Notifications.tsx`, `Users.tsx`, and `Settings.tsx`).

Currently, **Milestone 1 (Phases 1 & 10)**, **Milestone 2 (Phases 2 & 11)**, **Milestone 3 (Phase 3)**, **Milestone 4 (Phase 4)**, **Milestone 5 (Phase 5)**, and **Milestone 6 (Phase 6)** are **100% complete, verified, and passing 424 automated unit and integration tests** (407 prior tests + 17 payment view, receipts & E2E tests, 0 failures, 0 errors).

| Metric | Count | Percentage |
|---|:---:|:---:|
| **Total Contract Endpoints & Jobs** | 66 (64 endpoints + 2 engines) | 100% |
| **Milestones 1, 2, 3, 4, 5 & 6 Implemented & Verified** (Phases 1, 2, 3, 4, 5, 6, 10, 11) | 55 (53 endpoints + 2 engines) | 83.3% |
| **Pending Implementation** (Milestones 7–9 / Phases 7–9) | 11 endpoints | 16.7% |
| **Automated Test Suite Health** | 424 / 424 Tests Green | 100% Pass Rate |

---

## Milestone Roadmap & Progress Matrix

```
[=============================================================>] 83.3% Overall Progress
  - Milestone 1: Security, Identity & School Users (Phases 1 & 10)     --> [100% DONE] ✅
  - Milestone 2: Dashboard & Settings Profile (Phases 2 & 11)          --> [100% DONE] ✅
  - Milestone 3: Student Roster & Guardians (Phase 3)                  --> [100% DONE] ✅
  - Milestone 4: Fee Structure & Penalty Automation (Phase 4)          --> [100% DONE] ✅
  - Milestone 5: Fee Upload & Ingestion Pipeline (Phase 5)             --> [100% DONE] ✅
  - Milestone 6: Payments View & Receipts (Phase 6)                    --> [100% DONE] ✅
  - Milestone 7: Reconciliation & Settlement Visibility (Phase 7)      --> [PENDING]   ⏳
  - Milestone 8: Reports & Asynchronous Generation Pipeline (Phase 8)  --> [PENDING]   ⏳
  - Milestone 9: In-App & Email Notifications (Phase 9)                --> [PENDING]   ⏳
```

---

## Detailed Milestone & Phase Status

### Milestone 1: Security, Identity & School User Management (Phases 1 & 10)
**Status:** ✅ **100% COMPLETE** (17 / 17 Endpoints & Features Implemented and Tested)  
**Test Suite:** [`SchoolAuthIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/identity/SchoolAuthIntegrationTest.java) (8 tests) & [`SchoolUserManagementIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/identity/SchoolUserManagementIntegrationTest.java) (9 tests)

#### Phase 1 — Authentication, Session & Permissions
**Source:** `Login.tsx`, S01–S03 | **Roles:** `school-admin`, `school-finance` | **Status:** 10 / 10 DONE

| Endpoint | Method | Contract Purpose | Status | Implementation Details |
|---|---|---|:---:|---|
| `/auth/login` | `POST` | School user credentials check; triggers SMS MFA challenge | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Authenticates `InstitutionAdmin`, validates school active status (`account_deactivated`), user lock status (`account_locked`), increments failed attempts (lockout at 5), and returns 60s MFA challenge. |
| `/auth/mfa/verify` | `POST` | Exchanges 6-digit OTP for JWT access + refresh tokens | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Validates OTP code, issues 4-part JWT (`userId:email:role:institutionId`), returns `schoolId` (e.g. `SCH-001`), `schoolName`, and server-owned permissions. |
| `/auth/mfa/resend` | `POST` | Resends OTP SMS challenge with 60s cooldown | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Throttles resends and issues updated challenge. |
| `/auth/mfa/trust-device` | `POST` | Sets trusted-device flag per US-02 ("Remember this device") | ✅ **DONE** | [`AuthController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/AuthController.java) via [`TrustDeviceRequest.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/dto/auth/TrustDeviceRequest.java). Returns 200 message. |
| `/auth/forgot-password` | `POST` | Sends password reset email link (anti-enumeration) | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Returns generic 200 message; generates 15-minute password reset token. |
| `/auth/reset-password` | `POST` | Validates token and updates password | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Validates password complexity policy and updates hashed credentials. |
| `/auth/refresh` | `POST` | Rotates refresh token & issues new access token | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Re-validates active session and returns new access/refresh tokens with school context. |
| `/auth/logout` | `POST` | Revokes refresh token & session; logs audit trail | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Invalidates token and logs `LOGOUT` in `AuditLog`. |
| `/auth/me` | `GET` | Rehydrates user profile, role, school metadata, permissions | ✅ **DONE** | [`BankAuthServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/BankAuthServiceImpl.java). Resolves school user from JWT principal or database, returning `schoolId`, `schoolName`, and permissions. |
| `/roles` & `/roles/{role}/permissions` | `GET` | Returns server-authoritative role-permission matrix | ✅ **DONE** | [`RoleController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/RoleController.java). Serves 10 permissions for `School Admin` and 8 for `School Finance`. |

#### Phase 10 — School Users
**Source:** `Users.tsx`, S10–S13, US-04 to US-06 | **Roles:** `school-admin` only | **Status:** 7 / 7 DONE

| Endpoint | Method | Contract Purpose | Status | Implementation Details |
|---|---|---|:---:|---|
| `/users` | `GET` | Paginated search and listing of same-school team members | ✅ **DONE** | [`UserManagementController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/UserManagementController.java) via [`SchoolUserManagementService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/service/SchoolUserManagementService.java). Filter by `search`, `role`, `status`. Strict tenant isolation. |
| `/users` | `POST` | Provision new school team member (`School Admin` \| `School Finance`) | ✅ **DONE** | [`UserManagementController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/UserManagementController.java). Rejects duplicate emails (`409 email_exists`), validates roles (`400 invalid_role`), logs audit trail. |
| `/users/{id}` | `GET` | Detail view of a single school user | ✅ **DONE** | [`UserManagementController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/UserManagementController.java). Prevents cross-school access (`403 Forbidden` / `404 Not Found`). |
| `/users/{id}` | `PATCH` | Edit name, email, or role of school user | ✅ **DONE** | [`UserManagementController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/UserManagementController.java). Validates email uniqueness and normalized school roles. |
| `/users/{id}/deactivate` | `POST` | Deactivate school user (US-06) | ✅ **DONE** | [`UserManagementController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/UserManagementController.java). Sets status to `Inactive`, retains historical activity, logs audit trail. |
| `/users/{id}/activate` | `POST` | Re-activate school user | ✅ **DONE** | [`UserManagementController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/UserManagementController.java). Sets status to `Active`, logs audit trail. |
| `/roles` | `GET` | List school roles and permission matrices | ✅ **DONE** | [`RoleController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/identity/web/RoleController.java). Returns permissions list for `School Admin` and `School Finance`. |

---

### Milestone 2: Dashboard & Settings Profile (Phases 2 & 11)
**Status:** ✅ **100% COMPLETE** (7 / 7 Endpoints & Features Implemented and Tested)  
**Test Suite:** [`SchoolDashboardIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/dashboard/SchoolDashboardIntegrationTest.java) (7 tests) & [`SchoolSettingsIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/settings/SchoolSettingsIntegrationTest.java) (9 tests)

#### Phase 2 — Dashboard
**Source:** `Dashboard.tsx`, S04–S06 | **Roles:** `school-admin`, `school-finance` (read-only) | **Status:** 4 / 4 DONE

| Endpoint | Method | Contract Purpose | Status | Implementation Details |
|---|---|---|:---:|---|
| `/dashboard/summary` | `GET` | Pre-aggregated KPIs (`totalCollectedEGP`, `outstandingEGP`, `overdueEGP`, `feeUploadStatus`) | ✅ **DONE** | [`DashboardController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/dashboard/web/DashboardController.java) via [`SchoolDashboardService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/dashboard/service/SchoolDashboardService.java). Computes real-time tenant-isolated sums over `FeeLine` and `CsvUpload`. Returns ISO-8601 `asOf` timestamp. |
| `/dashboard/recent-payments` | `GET` | Recent settled payment transactions scoped to the school | ✅ **DONE** | [`DashboardController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/dashboard/web/DashboardController.java) (aliased to `/recent-transactions`). Resolves payments and student names from [`PaymentAllocationRepository.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/repository/PaymentAllocationRepository.java). |
| `/dashboard/quick-links` | `GET` | Fast action link targets (`students`, `fees`, `fee-upload`, `payments`, `reports`, `notifications`) with live badge counts | ✅ **DONE** | [`DashboardController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/dashboard/web/DashboardController.java). Computes live badge counts for student roster, active fees, and unread notifications. |
| `/dashboard/collections/weekly` | `GET` | 7-day Monday–Sunday collections curve with daily totals | ✅ **DONE** | [`DashboardController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/dashboard/web/DashboardController.java). Aggregates captured payments for the school over the target Monday–Sunday calendar week. |

#### Phase 11 — Settings
**Source:** `Settings.tsx` | **Roles:** Read (`school-admin`, `school-finance`), Write (`school-admin` only) | **Status:** 3 / 3 DONE

| Endpoint | Method | Contract Purpose | Status | Implementation Details |
|---|---|---|:---:|---|
| `/settings/profile` | `GET` | Read-only school profile metadata (name, code, address, principal, tax ID, bank accounts) | ✅ **DONE** | [`SettingsController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/settings/web/SettingsController.java) via [`SchoolSettingsService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/settings/service/SchoolSettingsService.java). Returns read-only school record from `InstitutionRepository` scoped to `principal.institutionId()`. |
| `/settings/notifications` | `GET` / `PUT` | School-level notification delivery preferences (`inApp`, `email` toggles) | ✅ **DONE** | [`SettingsController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/settings/web/SettingsController.java). Read by `school-admin` & `school-finance`; update restricted to `school-admin` (403 for `school-finance`). Toggles stored on `Institution` domain and audited. |
| `/settings/change-password` | `POST` | Self-service password rotation while logged in | ✅ **DONE** | [`SettingsController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/settings/web/SettingsController.java). Validates current password, enforces 5-rule complexity policy, prevents password reuse, updates hash, and logs audit trail. |

---

### Milestone 3: Student Roster & Guardians Management (Phase 3)
**Status:** ✅ **100% COMPLETE** (10 / 10 Endpoints Implemented and Tested)  
**Test Suite:** [`SchoolStudentIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/students/SchoolStudentIntegrationTest.java) (15 tests)

#### Phase 3 — Student Management
**Source:** `Students.tsx`, S07–S09, US-16 to US-25 | **Roles:** Read (`school-admin`, `school-finance`), Write (`school-admin` only)

| Endpoint | Method | Contract Purpose | Status | Target Component / Notes |
|---|---|---|:---:|---|
| `/students` | `GET` | Paginated search & list with grade, section, status, and fee status filters | ✅ **DONE** | [`SchoolStudentController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/students/web/SchoolStudentController.java) via [`SchoolStudentServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/students/service/SchoolStudentServiceImpl.java). Filters: `search`, `grade`, `page`, `pageSize`. Dynamically aggregates `totalFeesEGP`, `paidEGP`, and `outstandingEGP`. Omits National ID. |
| `/students/deactivated` | `GET` | List deactivated/withdrawn students with date-range filter | ✅ **DONE** | [`SchoolStudentController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/students/web/SchoolStudentController.java). Filters: `search`, `deactivatedFrom`, `deactivatedTo`, `page`, `pageSize`. |
| `/students/{id}` | `GET` | Student profile detail with masked National ID, guardians, and fee summary | ✅ **DONE** | Returns complete student record with `nationalIdMasked` (`299*******4567`), parent contact, and fee roll-up totals. Blocks cross-school access (`403 Forbidden`). |
| `/students` | `POST` | Enroll new student with studentRef uniqueness check | ✅ **DONE** | School-admin only. Validates `studentRef` uniqueness within school (`409 Conflict`), validates 14-digit National ID, hashes with HMAC-SHA256, links parent, and writes audit trail. |
| `/students/{id}` | `PATCH` | Update student demographic or academic info | ✅ **DONE** | School-admin only. Updates name, grade, section, contact info, and verifies studentRef collisions. |
| `/students/{id}/deactivate` | `POST` | Deactivate student with withdrawal reason | ✅ **DONE** | School-admin only. Sets `status = Inactive`, records `deactivatedDate`, rejects already inactive (`409 Conflict`), writes audit trail. |
| `/students/{id}/reactivate` | `POST` | Reactivate inactive student to active status | ✅ **DONE** | School-admin only. Clears deactivation metadata, sets `status = Active`, writes audit trail. |
| `/students/{id}/fees` | `GET` | Detailed fee lines for the student (active & historical) | ✅ **DONE** | Returns all tuition, bus, books, and activity fee lines for student with due dates, balances, and totals. |
| `/students/{id}/payments` | `GET` | Payment history and allocated dues for the student | ✅ **DONE** | Returns payment transactions and allocations matching student's dues (`dateFrom`, `dateTo` filters). |
| `/students/search` | `GET` | Fast typed student search picker for Add-Fee screen | ✅ **DONE** | Returns active matching students (`q=`); strictly excludes deactivated students. |
| `/students/{id}/guardians` | `GET` | List linked guardians with relationships and primary flags | ✅ **DONE** | Retrieves linked guardians and parent contacts for student. |
| `/students/{id}/guardians` | `POST` | Link or create guardian for student | ✅ **DONE** | School-admin only. Provisions or links guardian with contact details and writes audit trail. |
| `/students/{id}/guardians/{guardianId}` | `DELETE` | Unlink guardian from student | ✅ **DONE** | School-admin only. Unlinks guardian association from student and writes audit trail. |
| `/students/{id}/statement` | `GET` | Official account statement with running balance | ✅ **DONE** | Chronological ledger of fee assessments (debits) and payment allocations (credits) with computed running balance. |

---

### Milestone 4: Fee Structure & Penalty Automation (Phase 4)
**Status:** ✅ **100% COMPLETE** (7 / 7 Endpoints + 2 Scheduled Engines Implemented and Tested)  
**Test Suite:** [`SchoolFeeIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/fees/SchoolFeeIntegrationTest.java) (20 tests)

#### Phase 4 — Fee Management
**Source:** `Fees.tsx`, US-20 to US-27 | **Roles:** Read (`school-admin`, `school-finance`), Write (`school-admin`, `school-finance`), Cancel/Penalty (`school-admin` only)

| Endpoint | Method | Contract Purpose | Status | Target Component / Notes |
|---|---|---|:---:|---|
| `/fees` | `GET` | Paginated fee line query (`search`, `status`, `category`, `grade`, `dueDateFrom`, `dueDateTo`, `studentId`) | ✅ **DONE** | [`SchoolFeeController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/web/SchoolFeeController.java) via [`SchoolFeeServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/service/SchoolFeeServiceImpl.java). Real-time dynamic status computation (`Paid`, `Overdue`, `Partial`, `Active`) and embedded penalty roll-ups. Strict multi-tenant isolation. |
| `/fees` | `POST` | Create individual fee assessment | ✅ **DONE** | [`SchoolFeeController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/web/SchoolFeeController.java). Requires mandatory `dueDate` (`400 due_date_required`), validates student active status (`409 student_inactive`), validates category (`400 invalid_category`), logs audit trail. |
| `/fees/{id}` | `GET` | Detailed fee line view with payments & penalty snapshot | ✅ **DONE** | Returns fee line, student info, `FeeOverdueInfoDto`, `FeePenaltySnapshotDto`, and linked `paymentHistory` allocations. Blocks cross-school access (`403 Forbidden`). |
| `/fees/{id}` | `PATCH` | Update fee line amount, due date, or term | ✅ **DONE** | Enforces `newAmountEGP >= paidEGP` (`400 amount_below_paid`), locks if locked in active EPP schedule (`409 not_eligible_for_edit`), recalculates remaining balance and audit logs. |
| `/fees/{id}/cancel` | `POST` | Cancel uncollected fee line with reason | ✅ **DONE** | School-admin only. Rejects if actively locked in EPP (`422 Unprocessable Entity`) or if partial payments exist (`409 Conflict`), sets status to `CANCELLED`, logs audit trail. |
| `/fees/{id}/apply-penalty` | `POST` | Manually apply 5% late penalty to overdue Tuition fee | ✅ **DONE** | School-admin only. Enforces Tuition category only, overdue condition, non-zero balance, prevents duplicate penalties (`409 penalty_already_applied`), logs audit trail. |
| `/fees/{id}/penalty-info` | `GET` | Real-time deadline and priority snapshot | ✅ **DONE** | [`SchoolFeeController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/web/SchoolFeeController.java). Returns real-time deadline status, daysToDue, and priority from `DeadlinePolicy`. |
| `/fees/stats` | `GET` | Aggregated fee statistics for school | ✅ **DONE** | Computes real-time tenant totals: `totalInvoicedEGP`, `totalCollectedEGP`, `totalOutstandingEGP`, `totalOverdueEGP`, `overdueCount`, and distinct `totalStudentsWithOverdue`. |
| `/fee-categories` | `GET` | Fee category listing and allocation priority order | ✅ **DONE** | [`FeeCategoryController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/web/FeeCategoryController.java). Serves Tuition (1), Books (2), Activity (3), and Bus (4) priority hierarchy. |
| **Engine A** | Scheduled (`00:05` Cairo) | Automated Late Penalty Evaluation Engine | ✅ **DONE** | [`FeeAutomatedRulesEngine.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/service/FeeAutomatedRulesEngine.java). Evaluates overdue Tuition fees with grace ended (`daysOverdue > 7`), applies flat 5% penalty idempotently, persists `penaltyAmountEGP` and `penaltyAppliedAt`, and writes audit log. |
| **Engine B** | Scheduled (`08:00` Cairo) | 7-Day Due Date Approaching Reminder Engine | ✅ **DONE** | [`FeeAutomatedRulesEngine.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/fees/service/FeeAutomatedRulesEngine.java). Dispatches unread guardian reminders exactly 7 calendar days before `dueDate` for outstanding fee lines with idempotency guard. |

---

### Milestone 5: Fee Upload & Ingestion Pipeline (Phase 5)
**Status:** ✅ **100% COMPLETE** (8 / 8 Endpoints & Features Implemented and Tested)  
**Test Suite:** [`SchoolFeeUploadIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/ingestion/SchoolFeeUploadIntegrationTest.java) (13 tests)  
**Target:** Dual-format CSV/Excel upload, Row-level error isolation, Resubmission idempotency, Error export

#### Phase 5 — Fee Upload
**Source:** `Upload.tsx`, US-27 to US-32, Epic 6 | **Roles:** `school-admin`, `school-finance`

| Endpoint | Method | Contract Purpose | Status | Target Component / Notes |
|---|---|---|:---:|---|
| `/fee-uploads/template` | `GET` | Download sample CSV template with standard columns | ✅ **DONE** | [`SchoolFeeUploadController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/SchoolFeeUploadController.java). Streams approved CSV template with standard 6 columns (`studentRef, feeName, category, amountEGP, term, dueDate`) and sample rows. (Aliases: `/fee-upload/template`). |
| `/fee-uploads` | `POST` | Upload fee sheet (supports CSV and Excel XLSX) | ✅ **DONE** | [`SchoolFeeUploadServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/service/SchoolFeeUploadServiceImpl.java). Supports both Standard SIS 6-column and Legacy 5-column formats, enforces SHA-256 duplicate rejection (`409 duplicate_upload`, US-37), validates file type (`400 unsupported_file_type`), isolates bad rows, creates `FeeLine` records for valid rows, and returns 202 Accepted with `uploadId`. |
| `/fee-uploads/{uploadId}` | `GET` | Query upload job status and breakdown | ✅ **DONE** | [`SchoolFeeUploadController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/SchoolFeeUploadController.java). Returns `totalRows`, `validRows`, `invalidRows`, `acceptedRows`, `rejectedRows`, `uploadedAt`, `fileName`, and calculated status (`Completed`, `Completed with Errors`, `Failed`). (Aliases: `/fee-upload/{jobId}`). |
| `/fee-uploads/{uploadId}/rows` | `GET` | Inspect individual row results with optional status filter | ✅ **DONE** | [`SchoolFeeUploadController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/SchoolFeeUploadController.java). Returns all row lines with rowNumber, studentRef, feeName, category, amountEGP, dueDate, status (`Accepted` \| `Rejected`), and errorReason. Supports `?status=Accepted` and `?status=Rejected`. |
| `/fee-uploads/{uploadId}/errors` | `GET` | Inspect rejected rows only for error drawer | ✅ **DONE** | [`SchoolFeeUploadController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/SchoolFeeUploadController.java). Scoped query for the error-details drawer (S18). |
| `/fee-uploads/{uploadId}/errors/export` | `GET` | Export isolated row validation errors as CSV | ✅ **DONE** | [`SchoolFeeUploadController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/SchoolFeeUploadController.java). Streams downloadable CSV file with failed row numbers, attributes, and specific failure reasons. |
| `/fee-uploads/{uploadId}/resubmit` | `POST` | Resubmit corrected rows | ✅ **DONE** | [`SchoolFeeUploadServiceImpl.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/service/SchoolFeeUploadServiceImpl.java). Re-validates corrected rows, creates fee lines for newly valid rows, and guarantees previously accepted rows are never duplicated (US-36). |
| `/fee-uploads` | `GET` | History of upload batches for the school | ✅ **DONE** | [`SchoolFeeUploadController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/ingestion/web/SchoolFeeUploadController.java). Chronological list of historical uploads for the school. (Aliases: `/fee-upload/history`). |

---

### Milestone 6: Payments View & Receipts (Phase 6)
**Status:** ✅ **100% COMPLETE** (4 / 4 Endpoints Implemented and Tested)  
**Test Suite:** [`SchoolPaymentIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/payments/SchoolPaymentIntegrationTest.java) (16 tests) & [`SchoolPortalMasterE2EIntegrationTest.java`](file:///Users/nourahmed/Downloads/demo/src/test/java/com/tuitionnetwork/e2e/SchoolPortalMasterE2EIntegrationTest.java) (Stage 8)

#### Phase 6 — Payments (view-only)
**Source:** `Payments.tsx`, S22–S23, US-33 to US-36 | **Roles:** `school-admin`, `school-finance` (read-only) | **Status:** 4 / 4 DONE

| Endpoint | Method | Contract Purpose | Status | Implementation Details |
|---|---|---|:---:|---|
| `/payments` | `GET` | Paginated payment transactions list for the school | ✅ **DONE** | [`BackOfficePaymentController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/web/BackOfficePaymentController.java) via [`SchoolPaymentService.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/service/SchoolPaymentService.java). Scoped strictly to school's dues; filters by `search`, `dateFrom`, `dateTo`, `status`, `feeCategory`, `studentId`, `method`. Returns Phase 6.1 contract shape with `data`, `total`, `page`, `pageSize`, `totalPages`. |
| `/payments/{id}` | `GET` | Payment detail view with allocated dues breakdown | ✅ **DONE** | [`BackOfficePaymentController.java`](file:///Users/nourahmed/Downloads/demo/src/main/java/com/tuitionnetwork/payments/web/BackOfficePaymentController.java). Accepts UUID or transaction reference string (`TX-2026...`). Enforces tenant isolation (blocks cross-school access with `403 Forbidden`). Returns Phase 6.2 structure with `allocation` array (`feeId`, `feeName`, `feeCategory`, `dueDate`, `originalAmountEGP`, `previouslyPaidEGP`, `outstandingEGP`, `allocatedEGP`, `remainingAfterEGP`, `feeStatus`, `priority`, `isOverdue`). |
| `/payments/{id}/receipt` | `GET` | Download official crypto-signed payment receipt (PDF or JSON) | ✅ **DONE** | Dual format support: returns JSON metadata (`ReceiptDetailDto` with `cryptoSignature`, `receiptReference`, etc.) or downloadable official `%PDF-1.4` binary receipt when `?format=pdf` or `Accept: application/pdf` is supplied. Enforces school tenant access. |
| `/payments/export` | `GET` | Export filtered payments as CSV (`GET /payments/export?format=csv`) | ✅ **DONE** | Streams downloadable UTF-8 CSV with BOM for current filtered view with transaction ID, student details, fee name, amount, method, status, and remaining fee balance. |

---

### Milestone 7: Reconciliation & Settlement Visibility (Phase 7)
**Status:** ⏳ **PENDING** (0 / 2 Endpoints)  
**Target:** Daily settlement cycles, 2% CIB fee transparency, Net payout visibility

#### Phase 7 — Reconciliation (view-only)
**Source:** `Reconciliation.tsx`, US-37 to US-40 | **Roles:** `school-admin`, `school-finance` (read-only)

| Endpoint | Method | Contract Purpose | Status | Target Component / Notes |
|---|---|---|:---:|---|
| `/reconciliation/summary` | `GET` | Settlement status overview (gross collected, 2% fee, net settled, pending payout) | ⏳ **PENDING** | Aggregates settlement totals for the school. |
| `/reconciliation/settlements` | `GET` | Paginated settlement cycles and bank transfer references | ⏳ **PENDING** | Lists historical daily/weekly settlement runs with bank transfer batch numbers. |

---

### Milestone 8: Reports & Asynchronous Generation Pipeline (Phase 8)
**Status:** ⏳ **PENDING** (0 / 5 Endpoints)  
**Target:** Standard report templates, Async generation jobs (`202 Accepted`), Export downloads

#### Phase 8 — Reports
**Source:** `Reports.tsx`, US-41 to US-44 | **Roles:** `school-admin`, `school-finance`

| Endpoint | Method | Contract Purpose | Status | Target Component / Notes |
|---|---|---|:---:|---|
| `/reports/templates` | `GET` | List available school report templates (Aging, Collection, Class, Reconciliation) | ⏳ **PENDING** | Standard report types and parameter schemas. |
| `/reports/generate` | `POST` | Trigger asynchronous report generation (`202 Accepted` + `jobId`) | ⏳ **PENDING** | Enqueues background report task, returns job identifier. |
| `/reports/jobs/{jobId}` | `GET` | Poll report generation status | ⏳ **PENDING** | Returns job status (`IN_PROGRESS`, `COMPLETED`, `FAILED`). |
| `/reports/jobs/{jobId}/download` | `GET` | Download generated report (PDF, Excel, CSV) | ⏳ **PENDING** | Streams generated binary report file. |
| `/reports/history` | `GET` | List past generated reports for school | ⏳ **PENDING** | Paginated list of historical report jobs. |

---

### Milestone 9: In-App & Email Notifications (Phase 9)
**Status:** ⏳ **PENDING** (0 / 6 Endpoints)  
**Target:** Notification feeds, Read markers, Bell counter, Channel delivery settings

#### Phase 9 — Notifications
**Source:** `Notifications.tsx`, US-45 to US-48 | **Roles:** `school-admin`, `school-finance`

| Endpoint | Method | Contract Purpose | Status | Target Component / Notes |
|---|---|---|:---:|---|
| `/notifications` | `GET` | Paginated list of in-app notifications for school user | ⏳ **PENDING** | Filter by `read`, `category`, `priority`. Scoped to user/school. |
| `/notifications/unread-count` | `GET` | Unread notifications count for header badge | ⏳ **PENDING** | Lightweight query for live bell badge counter. |
| `/notifications/{id}/read` | `PATCH` | Mark specific notification as read | ⏳ **PENDING** | Sets `isRead = true` and records timestamp. |
| `/notifications/read-all` | `POST` | Mark all notifications as read | ⏳ **PENDING** | Bulk update for current user notifications. |
| `/notifications/preferences` | `GET` | User notification preferences | ⏳ **PENDING** | User-specific channel preferences. |
| `/notifications/preferences` | `PUT` | Update user notification preferences | ⏳ **PENDING** | Saves updated channel toggles. |

---

## Out-of-Scope Guardrails (Bank-Only Operations)

To maintain absolute security and architectural integrity, the following operations are **strictly reserved for the Bank Back-Office** and must never be exposed or callable by school users:

| Feature / Namespace | Enforced Policy |
|---|---|
| `POST /payments` (Execution) | Direct card processing, debit debiting, and refund execution are strictly bank-governed. School portal is **read-only** for payments. |
| `/institutions/*` (Governance) | School registration approval/rejection, KYC verification, integration credentials, and status toggles are bank-employee-only actions. School portal only reads its own profile. |
| `/epp/*` (Plan Management) | EPP product definitions, risk assessment, citizen eligibility checks, and tenure interest matrices are bank-exclusive. |
| `/reconciliation/runs` & `/exceptions/*` | Triggering match runs, resolving exception line items, and approving manual adjustments are bank-only. |
| `/audit-logs/*` | Platform-wide master audit logs are bank admin view only; schools only receive local action logs within their own screens. |

---

## Verification & Test Health Log

| Date | Event | Tests Passed | Regressions | Status |
|---|---|:---:|:---:|:---:|
| **2026-09-07** | Bank Back-Office API Completion (Phases 1–12) | 311 / 311 | 0 | ✅ Clean |
| **2026-09-11** | OpenApiConfig, Seeder, Recon Scheduler Enhancements | 319 / 319 | 0 | ✅ Clean |
| **2026-09-11** | **Milestone 1:** School Auth & School User Management (Phases 1 & 10) | **336 / 336** | **0** | ✅ **Clean** |
| **2026-09-11** | **Milestone 2:** School Dashboard & Settings Profile (Phases 2 & 11) | **352 / 352** | **0** | ✅ **Clean** |
| **2026-09-12** | **Milestone 3:** Student Roster & Guardians (Phase 3) | **364 / 364** | **0** | ✅ **Clean** |
| **2026-09-12** | **Milestone 4:** Fee Structure & Penalty Automation (Phase 4) | **387 / 387** | **0** | ✅ **Clean** |
| **2026-09-13** | **Milestone 5:** Fee Upload & Ingestion Pipeline (Phase 5) | **400 / 400** | **0** | ✅ **Clean** |
| **2026-09-13** | **Master E2E:** School Portal Master End-to-End Test Suite (Stages 1–7) | **407 / 407** | **0** | ✅ **Clean** |
| **2026-09-13** | **Milestone 6:** Payments View, Receipts & Master E2E Stage 8 (Phase 6) | **424 / 424** | **0** | ✅ **Clean** |
