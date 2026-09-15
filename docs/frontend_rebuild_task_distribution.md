# Frontend Rebuild - Task Breakdown for Team Distribution

**Goal:** Rebuild both portals as one organized Next.js application with a single shared sign-in page, wired to the real backend, ready to split across the team. 
**Portals:** Bank Back-Office Portal (Figma source ported). School Portal (built from API contract + shared `@tuition/ui` design system).
**Backend status:** 100% Complete & Verified on `main` (Bank Back-Office, School Portal, Mock Banking, T24 Adapter, Payment Concurrency Guard, Reconciliation, and Audit Logs — all 492 backend tests pass).

## Recommended Architecture - One App, One Login, Role-Routed

**Design decision:** There is exactly one sign-in page (`/login`). The username/password typed in is what decides which portal the person lands in, not a separate login screen per portal. This is backed by how auth already works server-side, verified directly in code:

*   `AuthController.login()` (`src/main/java/com/tuitionnetwork/identity/web/AuthController.java`) exposes one POST `/auth/login` for everyone.
*   `IdentityUserDetailsService.loadUserByEmail()` (`src/main/java/com/tuitionnetwork/identity/security/IdentityUserDetailsService.java`) resolves that one email across Bank Employee, InstitutionAdmin, and Guardian tables and maps it to the right role (`ROLE_BACK_OFFICE`, or `ROLE_SCHOOL_ADMIN`/`ROLE_SCHOOL_FINANCE` + institutionId, etc.) - the backend already tells you which portal a login belongs to.
*   `MfaVerifyResponse.user.role` (returned once MFA passes) is serialized in kebab-case (`bank-admin`, `bank-operations`, `bank-finance`, `bank-reconciliation` or `school-admin`, `school-finance`) and is exactly the field the frontend router uses to route the user.

So the "session isolation" the School contract requires (a bank session must never authenticate against school endpoints) is a backend `@PreAuthorize` role check, not a frontend concern - it doesn't require two separate login UIs or two separate token stores. One shared token store is fine; the JWT's role claim is what the backend actually enforces on.

The frontend is structured as a single pnpm monorepo, one Next.js app (`apps/portal`), with explicit `/bank` and `/school` path segments (not Next.js route groups, to avoid URL collision on `/dashboard`):

```text
frontend/
├── pnpm-workspace.yaml
├── apps/
│   └── portal/                 # Next.js 16 (App Router)
│       └── src/app/
│           ├── login/page.tsx  # THE single shared sign-in page (+ MFA + reset)
│           ├── bank/           # real route segment /bank/*, gated by isBankRole()
│           │   ├── layout.tsx  # bank shell/sidebar, redirects out if role doesn't match
│           │   ├── dashboard/page.tsx  # [COMPLETED] BO-P2 dashboard
│           │   ├── schools/
│           │   ├── transactions/
│           │   ├── reconciliation/
│           │   ├── epp/
│           │   ├── reports/
│           │   ├── notifications/
│           │   ├── audit-logs/
│           │   ├── users/
│           │   └── settings/
│           └── school/         # real route segment /school/*, gated by isSchoolRole()
│               ├── layout.tsx  # school shell/sidebar, redirects out if role doesn't match
│               ├── dashboard/page.tsx  # SP-P2 dashboard (shell in place)
│               ├── students/
│               ├── fees/
│               ├── fee-uploads/
│               ├── payments/
│               ├── reconciliation/
│               ├── reports/
│               ├── notifications/
│               ├── users/
│               └── settings/
└── packages/
    ├── ui/                     # shared design system & components (PortalShell, Table, Modal, etc.)
    ├── api-client/             # fetch wrapper, unified AuthProvider/token store, shared TS types
    └── config/                 # shared tailwind/tsconfig/eslint
```

After `/mfa/verify` returns, the login page reads `user.role` using helper functions in `@tuition/api-client`:
*   `isBankRole(user.role)` (`bank-admin`, `bank-operations`, `bank-finance`, `bank-reconciliation`) -> `router.replace('/bank/dashboard')`.
*   `isSchoolRole(user.role)` (`school-admin`, `school-finance`) -> `router.replace('/school/dashboard')`.
*   Other / Guardian -> `router.replace('/')`.

Each portal's `layout.tsx` also re-checks the role on every load (not just at login) and bounces anyone with the wrong role back to `/login` so a bank employee can never view `/school/*` routes by typing a URL, and vice versa.

**Why explicit `/bank` and `/school` paths instead of route groups `(bank)` / `(school)`:** In Next.js App Router, route group folders are omitted from the browser URL path. Having both define `/dashboard/page.tsx` causes a hard URL collision. Explicit `/bank/*` and `/school/*` path segments prevent collisions, allow deep-linking, and align with the sidebar navigation.

### ✅ Settled Architectural Conventions

1.  **Error shape normalization:** Solved. `packages/api-client/src/client.ts` implements an adaptive `parseErrorBody(res)` function that automatically normalizes both nested error objects (`{"error": { "code", "message", "details" }}`) and flat error objects (`{"error": "<CODE>", "message": ...}`) into typed `ApiError` instances.
2.  **Reports: sync vs async:** Solved & differentiated by role.
    *   **Bank Back-Office:** `ReportsController.java` (`POST /reports/generate`) returns `200 OK` synchronously with inline preview and byte download.
    *   **School Portal:** `ReportsController.java` (`POST /reports/generate`) returns `202 ACCEPTED` with a `ReportJobResponse` (`jobId`, `status: "processing"` / `"queued"`), allowing client polling via `GET /reports/jobs/{jobId}` and download via `GET /reports/jobs/{jobId}/download`.

---

## PHASE 0 - Monorepo & Shared Foundation [COMPLETED]
**Owner:** Shared Foundation | **Status:** ✅ Complete on `main`

- [x] `pnpm-workspace.yaml` + root `package.json`; scaffold the single `apps/portal` app with `create-next-app` (TypeScript, App Router, Tailwind v4, `src/` dir).
- [x] `packages/config` - shared `tsconfig.base.json`, ESLint config, Tailwind preset (carrying CIB navy `#003087`/orange `#F7941D` palette from Figma `index.css`).
- [x] `packages/api-client`:
  - `createApiClient(baseUrl)` -> get/post/patch/put/delete, JSON parsing, adaptive error handling -> typed `ApiError`.
  - **Auth:** unified `AuthProvider` (React context) - holds `accessToken`/`refreshToken`/`user`; persists to `localStorage`; injects `Authorization: Bearer`; automatic 401 refresh handling; trust-device, forgot-password, reset-password methods.
  - **Shared types:** `PageResponse<T>`, `ApiError`, `AuthUser`, role helpers (`isBankRole`, `isSchoolRole`).
- [x] `packages/ui` - Icons, `PortalShell`, Badge, Modal, Table, Toast, Button, Pagination, EmptyState, LoadingSpinner, priority helpers (`dueDateLabel`, `PRIORITY_BADGE_CLASSES`).
- [x] Root `.env.example` and `portal/.env.local`: `NEXT_PUBLIC_API_BASE_URL`.
- [x] CI / Build validation: `pnpm -r typecheck`, `pnpm -r lint`, and `next build` all clean.

---

## AUTH - Shared Sign-In & Role Routing [COMPLETED]
**Owner:** Shared Auth | **Status:** ✅ Complete on `main`
**Screens:** `apps/portal/src/app/login/page.tsx` (the only login screen in the entire app)
**Endpoints:** `/auth/login`, `/auth/mfa/verify`, `/auth/mfa/resend`, `/auth/mfa/trust-device`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/refresh`, `/auth/logout`, `/auth/me`

- [x] Port the credentials -> MFA OTP -> reset flow from the Figma `Login.tsx` into `app/login/page.tsx`.
- [x] Wire the single `AuthProvider` from `packages/api-client`; on app load, hydrate session and validate.
- [x] After `/mfa/verify` succeeds, branch purely on `user.role`: `isBankRole(user.role)` -> `router.replace('/bank/dashboard')`; `isSchoolRole(user.role)` -> `router.replace('/school/dashboard')`. No portal picker UI.
- [x] `bank/layout.tsx` and `school/layout.tsx` each re-check `user.role` on load and redirect to `/login` if it doesn't match their portal.
- [x] "Remember this device" checkbox wired to `/auth/mfa/trust-device`.
- [x] Drive each portal's sidebar nav allow-list from `user.permissions`, not a hardcoded map.
- [x] CORS enabled on backend in `SecurityConfig.java` for `http://localhost:*`.

---

## PART A - Bank Back-Office Portal
**Source:** Figma export migrated into `apps/portal/src/app/bank/`. Backend is 100% complete and verified on `main`.

### BO-P2 - Dashboard [COMPLETED]
**Screen:** `apps/portal/src/app/bank/dashboard/page.tsx` + `types.ts`
**Endpoints:** `/dashboard/summary`, `/dashboard/collections/weekly`, `/dashboard/institution-status`, `/dashboard/recent-transactions`, `/dashboard/deadline-summary` | **Size:** S | **Status:** ✅ Complete on `main`
- [x] 9 real KPI cards (Active Institutions, Total Students, Today's Transactions, Collection, Successful/Failed/Pending Payments, Reconciliation, Active EPP).
- [x] Dynamic SVG weekly collections chart with day hover tooltips and nice ceil scale.
- [x] Institution status breakdown bar with school/university counts.
- [x] Recent transactions table with method, timestamp, and status badges.
- [x] Payment deadline priority queue cards with days-to-due labels and penalty indicators.

### BO-P3 - Institution Management
**Screen:** `Schools.tsx` -> `app/bank/schools/`
**Endpoints:** `/institutions` (list/register/get), `/{id}/students`, `/{id}/application`, `/{id}/integration`, `/{id}/approve|reject|activate|deactivate`, `/{id}/fee-submissions` [...] | **Size:** L
- [ ] List + register + detail header.
- [ ] Students/Application/Integration/Fee-Submissions tabs - 4 real queries.
- [ ] Approve/Reject/Activate/Deactivate actions with confirmation modals.
- [ ] Settlement tab wired to institution settlement endpoints.

### BO-P4 - Transactions & Payment Workflow
**Screen:** `Transactions.tsx` -> `app/bank/transactions/`
**Endpoints:** `/transactions` (list/tab-counts/detail/export), `/customers/fees`, `/payments` (create/retry/receipt) | **Size:** L
- [ ] Table + filters + export.
- [ ] Multi-step payment workflow (search NID -> select fees -> amount -> review -> pay -> receipt), `Idempotency-Key` generated once per attempt.
- [ ] Remove the `simulateFail` demo toggle.

### BO-P5 - Reconciliation
**Screen:** `Reconciliation.tsx` -> `app/bank/reconciliation/`
**Endpoints:** `/reconciliation/summary`, `/reconciliation/runs`, `/reconciliation/exceptions`, `/reconciliation/exceptions/{id}` (PATCH, `/assign`) | **Size:** M
- [ ] Summary counts, runs list, and exception management. Backend module is 100% merged and verified.

### BO-P6 - EPP Plans ✅ DONE
**Screen:** `EPP.tsx` -> `app/bank/epp/`
**Endpoints:** `/epp/summary`, `/epp/plans`, `/epp/plans/{id}`, `/epp/plans/{id}/schedule`, `/epp/quote`, `/epp/cards/validate` | **Size:** M
- [x] List with KPI summary, search/status/tenor filters, real pagination.
- [x] Create wizard adapted to the real backend contract: an EPP plan converts an EXISTING successful CIB credit-card payment (`sourcePaymentId`), it isn't a brand-new charge - so step 1 searches/selects an eligible payment instead of Figma's blank card+student form.
- [x] Card validation via `/epp/cards/validate` (real BIN classifier, not Figma's demo BINs) and live pricing via `/epp/quote` (real backend rate: 0% for 3-month tenor, 14% flat p.a. otherwise, 1% admin fee capped at EGP 500).
- [x] Detail view: real pricing breakdown, progress bars, and installment schedule (recorded installments where they exist, projected otherwise).
- [x] Cancel Plan action via `PATCH /epp/plans/{id}`.
- [x] Real business-rule errors (principal range, one plan per payment, max 2 plans/student, card/payment eligibility) mapped to readable messages instead of raw backend codes.

### BO-P7 - Reports ✅ DONE
**Screen:** `Reports.tsx` -> `app/bank/reports/`
**Endpoints:** `/reports/catalogue`, `/reports/generate`, `/reports/jobs/{id}[/download]`, `/reports/history`, `/institutions` | **Size:** M
- [x] Bank Back-Office reports generate synchronously (`200 OK`) with immediate table preview and export.
- [x] Fully catalogue-driven: category tabs, per-report formats, context filters, and `available`/`unavailableReason` all come from `GET /reports/catalogue` - not Figma's hardcoded 12-report list (2 of Figma's "Deadlines" reports don't exist server-side and were dropped; nothing else was invented).
- [x] Real filter value domains, not Figma's guesses: `FeeType` display names (Tuition / Bus subscription / Books & materials / Activities), `PaymentStatus` labels (Successful / Pending / Failed / Refunded), `PaymentMethod` labels (CIB Account / Credit Card / EPP). Reconciliation status is a free-text input since real run statuses aren't a fixed enum.
- [x] Recent Reports history panel (`GET /reports/history`) with per-entry download.
- [x] Backend currently only accepts `format=CSV` for real generation even though the catalogue advertises PDF/XLSX too - selecting a non-CSV format surfaces the backend's real rejection via a friendly mapped message instead of a raw error code.

### BO-P8 - Notifications ✅ DONE
**Screens:** `Notifications.tsx` + header bell in `PortalShell.tsx`
**Endpoints:** `/notifications`, `/notifications/unread-count`, `/notifications/{id}/read`, `/notifications/read-all`, `/notifications/{id}` (DELETE) | **Size:** S
- [x] Feed + type filter tabs driven by the real `NotifType` enum (`FAILED_PAYMENT`, `RECON_EXCEPTION`, `INSTITUTION_ISSUE`, `NEW_INSTITUTION`, `SYSTEM_ALERT` - only 5 values; Figma's invented "Deadline Warnings" 6th type doesn't exist server-side and was correctly dropped rather than faked).
- [x] Mark read / mark all read / dismiss, all against the real endpoints.
- [x] Action CTA navigates to `/bank/{action.screen}` using the real screen strings `BackOfficeNotificationPublisher` publishes (`transactions`, `reconciliation`, `schools`) - a generic template, not a hardcoded per-screen map, so it stays correct as new notification-producing screens are added.
- [x] Header bell badge already wired from BO-P2/dashboard work.
- Note: `GET /notifications/stream` (SSE) exists server-side for live push but wasn't wired up in this pass - the feed is poll/refetch on mutation, consistent with the rest of the app.

### BO-P9 - Audit Logs ✅ DONE
**Screen:** `AuditLogs.tsx` -> `app/bank/audit-logs/`
**Endpoints:** `/audit-logs` (list/stats/roles/export) | **Size:** S
- [x] Backend module is 100% merged and verified (`AuditLogController.java`).
- [x] Filterable, expandable audit table (search/role/severity/date range), role dropdown driven by the real `GET /audit-logs/roles` rather than Figma's guessed role names.
- [x] Severity stat bar matches the backend's real `stats()` semantics: computed from the date range only, unaffected by role/search/severity filters - same as Figma's own (unfiltered) severity counts, just backed by real data now.
- [x] CSV export via `GET /audit-logs/export`.
- [x] Verified live against real audit entries generated by earlier BO-P3/BO-P5/BO-P6/BO-P7/BO-P8 testing sessions, including the SHA-256-hashed NID recorded on `SEARCH_NATIONAL_ID` entries.

### BO-P10 - Users & Roles ✅ DONE
**Screen:** `Users.tsx` -> `app/bank/users/`
**Endpoints:** `/users` (list/create/patch/activate/deactivate/reset-password), `/users/summary`, `/roles` | **Size:** M
- [x] Bank employee CRUD (add/edit/deactivate/reactivate), gated by `user.role === "bank-admin"` matching the backend's own permission model.
- [x] Role Permissions panel driven by the real `GET /roles` (`BankRole` enum's actual route-segment permissions per role) rather than Figma's fictional text permission descriptions - verified live that switching to "Reconciliation" correctly shows only Dashboard/Reconciliation/Notifications checked.
- [x] Reset Password action (`POST /users/{id}/reset-password`) - a real backend capability Figma's screen didn't include.
- [x] Role-count summary cards from the real `GET /users/summary`.

### BO-P11 - Settings ✅ DONE
**Screen:** `Settings.tsx` -> `app/bank/settings/`
**Endpoints:** `/settings/fee-types` (list/create/patch), `/settings/payment-statuses`, `/settings/epp` (get/put), `/settings/notifications` (get/put), `/settings/institutions` (get/put) | **Size:** M
- [x] Fee Types: list + add + inline quick-toggle (taxable/active) + full edit form, all real DB-backed CRUD (Figma's table was fully static).
- [x] Payment Statuses: all 6 real statuses (`PaymentStatusInfo.ALL`) - Figma only modeled 4, missing Refunded and Reversed.
- [x] EPP Configuration: tenors, amount limits, per-tenor interest rates, a real separate admin-fee rate + cap (Figma only had one flat admin fee rate, no cap), require-approval, max plans/student - all persisted via `PUT /settings/epp` with the backend's real validation (min<max, ≥1 tenor enabled, 0-100% rates, etc.) surfaced inline.
- [x] Notification Settings: event triggers + delivery channels, including a real editable "In-app" toggle (Figma hardcoded it always-on and disabled).
- [x] School Configuration: toggles, upload limits, and allowed formats - verified live that unchecking all formats surfaces the backend's real "At least one upload format must be allowed" validation.
- Note: `EppSettingsDto` is fully read/write and persisted, but per its own javadoc it isn't yet wired into `EppPricing`'s actual quote calculation (still hardcoded flat 14% p.a. / 0% for 3-month tenor) - a real backend gap to flag for BO-P6 follow-up, not something this ticket could or should paper over.

### BO-P12 - Payment Deadline, Priority & Late Penalty
**Screens:** `Transactions.tsx` deadline columns + `@tuition/ui/src/priority.ts` | **Size:** S
- [ ] Backend returns `dueDate`, `priority`, `penaltyEGP`, `totalDueEGP`. Use presentational helpers from `@tuition/ui` (`dueDateLabel`, `PRIORITY_BADGE_CLASSES`).

---

## PART B - School Portal (build from scratch)
**Source:** Build directly from `docs/School-Portal-API-Contract.md`, under `apps/portal/src/app/school/`. Reuses `@tuition/ui` and `@tuition/api-client`.

### SP-P2 - Dashboard
**Screen:** `apps/portal/src/app/school/dashboard/page.tsx`
**Endpoints:** `/dashboard/summary`, `/dashboard/recent-payments`, `/dashboard/quick-links` | **Size:** S | **Status:** ⏳ Shell in place, ready for dashboard body
- [ ] 4 KPI cards (collected, outstanding, overdue + count, upload status).
- [ ] Recent payments table (6 rows).
- [ ] 6 quick-action tiles (Students, Fees, Upload, Payments, Reports, Notifications).

### SP-P3 - Student Management [COMPLETED]
**Screen:** `apps/portal/src/app/school/students/`
**Endpoints:** `/students` (list/get/create/patch), `/students/deactivated`, `/{id}/deactivate|reactivate`, `/{id}/fees`, `/{id}/payments`, `/students/search` | **Size:** L | **Status:** ✅ Complete
- [x] Active roster (search + grade filter), student detail (masked national ID), fees + payment history sub-views.
- [x] Deactivated-students list with a date-range filter.
- [x] Add/Edit student form (school-admin only), Deactivate/Reactivate with confirmation modal.
- [x] Typed search picker that only ever returns active students.

### SP-P4 - Fee Management
**Screen:** `apps/portal/src/app/school/fee-management/`
**Endpoints:** `/fees` (list/get/create/patch), `/fee-categories`, `/{id}/penalty-info` | **Size:** L
- [ ] Fee list with category/status/due-date filters; status badges computed server-side, never cached client-side.
- [ ] Fee detail: original/paid/remaining, payment history, and for Tuition the penalty snapshot.
- [ ] Add/Edit fee form; `dueDate` required client-side; edit blocks `newAmount < paid`.
- [ ] No UI needed for automated engines (penalty + reminder)—they're backend cron jobs; just render output.

### SP-P5 - Fee Upload
**Screen:** `apps/portal/src/app/school/fee-upload/`
**Endpoints:** `/fee-uploads/template`, `/fee-uploads` (POST/list/get), `/{uploadId}/rows|errors[/export]`, `/{uploadId}/resubmit` | **Size:** M
- [ ] Template download, drag-drop upload (accept both formats).
- [ ] Upload status view, row-level error table, export-rejected-rows button.
- [ ] Resubmit flow (upload a corrected file against the same uploadId).
- [ ] Upload history list.

### SP-P6 - Payments (view-only)
**Screen:** `apps/portal/src/app/school/payments/`
**Endpoints:** `/payments` (list/detail/export) | **Size:** S
- [ ] Read-only payment table + filters; detail view renders allocation breakdown.
- [ ] No "process payment" button anywhere in this app.

### SP-P7 - Reconciliation (view-only)
**Screen:** `apps/portal/src/app/school/reconciliation/`
**Endpoints:** `/reconciliation/summary`, `/transactions` | **Size:** S
- [ ] Summary counts + a filtered, read-only transaction list. No assign/resolve UI.

### SP-P8 - Reports
**Screen:** `apps/portal/src/app/school/reports/`
**Endpoints:** `/reports/catalogue`, `/reports/generate` (async `202 ACCEPTED`), `/reports/jobs/{id}[/download]`, `/reports/history` | **Size:** M
- [ ] 4 report types (Collections, Payment History, Outstanding Fees, Partial Payments), no school-selector. Async job status polling and download.

### SP-P9 - Notifications
**Screen:** `apps/portal/src/app/school/notifications/`
**Endpoints:** `/notifications`, `/notifications/unread-count`, `/notifications/{id}/read`, `/notifications/read-all`, `/notifications/{id}` (DELETE), `/notifications/reminders` | **Size:** S
- [ ] Feed with type filter + unread badge.
- [ ] Dedicated "Reminders" view showing Scheduled/Sent/Failed.

### SP-P10 - School Users
**Screen:** `apps/portal/src/app/school/users/`
**Endpoints:** `/users` (list/create/get/patch/activate/deactivate), `/roles` | **Size:** M
- [ ] Same CRUD pattern as BO-P10, scoped to two roles (School Admin / School Finance).

### SP-P11 - Settings
**Screen:** `apps/portal/src/app/school/settings/`
**Endpoints:** `/settings/profile` (read-only), `/settings/notifications` (get/put), `/settings/change-password` | **Size:** S
- [ ] Read-only school profile card.
- [ ] Notification delivery toggles (in-app/email).
- [ ] Self-service change-password form.

---

## Suggested Sequencing & Current Status

1.  **Phase 0 (Monorepo & Foundation):** ✅ **COMPLETE** on `main`.
2.  **AUTH (Shared Sign-In & Role Routing):** ✅ **COMPLETE** on `main`.
3.  **Bank Dashboard (BO-P2):** ✅ **COMPLETE** on `main`.
4.  **Fan-out Execution (Current Stage):**
    *   **School Track Priority 1:** Implement **SP-P2 (School Dashboard)** to match the bank dashboard's fidelity.
    *   **Parallel Tracks:** Assign independent phase modules across the team:
        *   *Bank Portal:* **BO-P3 (Institutions)**, **BO-P4 (Transactions & Payments)**, **BO-P5 (Reconciliation)**, **BO-P6 (EPP)**.
        *   *School Portal:* **SP-P3 (Student Management)**, **SP-P4 (Fee Management)**, **SP-P5 (Fee Upload)**.
    *   All backend endpoints across both tracks are 100% merged to `main` with 492 passing tests — zero blocking backend dependencies remain.

---

## One-line Ticket Summary

| ID | Portal | Phase | Size | Status | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **P0** | shared | Monorepo & foundation | M | ✅ Complete | Next.js 16, `@tuition/ui`, `@tuition/api-client`, config |
| **AUTH** | shared | Sign-In & Role Routing | M | ✅ Complete | Shared `/login`, MFA OTP, role-based portal routing, CORS |
| **BO-P2** | Bank | Dashboard | S | ✅ Complete | 9 KPIs, SVG trend chart, breakdown, recent transactions |
| **BO-P3** | Bank | Institution Management | L | Ready | List, register, tabs (Students/Application/Integration) |
| **BO-P4** | Bank | Transactions & Payments | L | Ready | Multi-step payment, receipt, idempotency |
| **BO-P5** | Bank | Reconciliation | M | Ready | Runs, exceptions, summary counts |
| **BO-P6** | Bank | EPP Plans | M | ✅ Complete | Plan schedule, card validation, quote, create wizard, cancel |
| **BO-P7** | Bank | Reports | M | ✅ Complete | Catalogue-driven, sync `200 OK` generation, preview, history, download |
| **BO-P8** | Bank | Notifications | S | ✅ Complete | Unread badge, notification feed, mark read, dismiss |
| **BO-P9** | Bank | Audit Logs | S | ✅ Complete | Filterable/expandable table, real roles, stats, CSV export |
| **BO-P10** | Bank | Users & Roles | M | ✅ Complete | Bank employee CRUD, real role permissions, reset password |
| **BO-P11** | Bank | Settings | M | ✅ Complete | Fee types, payment statuses, EPP/notification/school config |
| **BO-P12** | Bank | Deadline / Priority / Penalty | S | Ready | UI priority badges in place; integrate in transaction lists |
| **SP-P2** | School | Dashboard | S | ⏳ In Progress | Shell & routing ready; dashboard widgets next |
| **SP-P3** | School | Student Management | L | ✅ Complete | Active & deactivated roster, guardian linking |
| **SP-P4** | School | Fee Management | L | Ready | Categorized fees, penalty snapshot, create/edit |
| **SP-P5** | School | Fee Upload | M | Ready | CSV drag-drop, validation error export, resubmit |
| **SP-P6** | School | Payments (view) | S | Ready | Read-only payment ledger & allocation breakdown |
| **SP-P7** | School | Reconciliation (view) | S | Ready | Read-only reconciliation summary & transactions |
| **SP-P8** | School | Reports | M | Ready | Async `202 ACCEPTED` job polling & download |
| **SP-P9** | School | Notifications | S | Ready | School in-app feed, reminders status |
| **SP-P10** | School | School Users | M | Ready | School Admin & School Finance CRUD |
| **SP-P11** | School | Settings | S | Ready | School profile, notifications, change password |

*22 tickets total. Foundation, Auth, and Bank Dashboard are complete. The remaining 19 screen phases are fully unblocked for parallel development.*