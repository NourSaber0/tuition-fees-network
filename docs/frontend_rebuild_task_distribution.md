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

### BO-P5 - Reconciliation ✅ DONE
**Screen:** `Reconciliation.tsx` -> `app/bank/reconciliation/`
**Endpoints:** `/reconciliation/summary`, `/reconciliation/runs`, `/reconciliation/exceptions`, `/reconciliation/exceptions/{id}` (PATCH, `/assign`), `/reconciliation/assignees`, `/reconciliation/export` | **Size:** M
- [x] Summary counts (KPI cards), runs list ("Reconciliation Summary" tab) with an Investigate cross-link into the matching exception.
- [x] Exception Queue tab: show/hide resolved toggle, priority/status badges, per-card amount breakdown.
- [x] Exception resolution view: real backend-computed 3-way record comparison, investigation workflow steps, and SLA - not client-derived.
- [x] Save Resolution enforces the backend's `resolution_action_required` validation (blocked client-side with the same message before it ever reaches the API).
- [x] CSV export via `/reconciliation/export`.

### BO-P6 - EPP Plans
**Screen:** `EPP.tsx` -> `app/bank/epp/`
**Endpoints:** `/epp/plans`, `/plans/{id}/schedule`, `/quote`, `/cards/validate`, `/plans` (create) | **Size:** M

### BO-P7 - Reports
**Screen:** `Reports.tsx` -> `app/bank/reports/`
**Endpoints:** `/reports/catalogue`, `/reports/generate`, `/reports/jobs/{id}[/download]`, `/reports/history` | **Size:** M
- [ ] Bank Back-Office reports generate synchronously (`200 OK`) with immediate table preview and export.

### BO-P8 - Notifications
**Screens:** `Notifications.tsx` + header bell in `PortalShell.tsx`
**Endpoints:** `/notifications`, `/notifications/unread-count`, `/notifications/{id}/read`, `/notifications/read-all`, `/notifications/{id}` (DELETE) | **Size:** S

### BO-P9 - Audit Logs
**Screen:** `AuditLogs.tsx` -> `app/bank/audit-logs/`
**Endpoints:** `/audit-logs` (list/detail/stats/export) | **Size:** S
- [ ] Backend module is 100% merged and verified (`AuditLogController.java`).

### BO-P10 - Users & Roles
**Screen:** `Users.tsx` -> `app/bank/users/`
**Endpoints:** `/users` (list/create/patch/activate/deactivate), `/roles` | **Size:** M

### BO-P11 - Settings
**Screen:** `Settings.tsx` -> `app/bank/settings/`
**Endpoints:** `/settings/fee-types` (list/create/patch), `/settings/payment-statuses`, `/settings/epp` (get/put), `/settings/notifications` (get/put), `/settings/institutions` (get/put) | **Size:** M

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

### SP-P3 - Student Management
**Screen:** `apps/portal/src/app/school/students/`
**Endpoints:** `/students` (list/get/create/patch), `/students/deactivated`, `/{id}/deactivate|reactivate`, `/{id}/fees`, `/{id}/payments`, `/students/search` | **Size:** L
- [ ] Active roster (search + grade filter), student detail (masked national ID), fees + payment history sub-views.
- [ ] Deactivated-students list with a date-range filter.
- [ ] Add/Edit student form (school-admin only), Deactivate/Reactivate with confirmation modal.
- [ ] Typed search picker that only ever returns active students.

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
| **BO-P5** | Bank | Reconciliation | M | ✅ Complete | Runs, exceptions, summary counts, resolution workflow, CSV export |
| **BO-P6** | Bank | EPP Plans | M | Ready | Plan schedule, card validation, quote |
| **BO-P7** | Bank | Reports | M | Ready | Sync `200 OK` report generation & download |
| **BO-P8** | Bank | Notifications | S | Ready | Unread badge, notification list & actions |
| **BO-P9** | Bank | Audit Logs | S | Ready | Backend `AuditLogController` merged and ready |
| **BO-P10** | Bank | Users & Roles | M | Ready | Bank employee CRUD and permissions |
| **BO-P11** | Bank | Settings | M | Ready | Fee types, payment statuses, system settings |
| **BO-P12** | Bank | Deadline / Priority / Penalty | S | Ready | UI priority badges in place; integrate in transaction lists |
| **SP-P2** | School | Dashboard | S | ⏳ In Progress | Shell & routing ready; dashboard widgets next |
| **SP-P3** | School | Student Management | L | Ready | Active & deactivated roster, guardian linking |
| **SP-P4** | School | Fee Management | L | Ready | Categorized fees, penalty snapshot, create/edit |
| **SP-P5** | School | Fee Upload | M | Ready | CSV drag-drop, validation error export, resubmit |
| **SP-P6** | School | Payments (view) | S | Ready | Read-only payment ledger & allocation breakdown |
| **SP-P7** | School | Reconciliation (view) | S | Ready | Read-only reconciliation summary & transactions |
| **SP-P8** | School | Reports | M | Ready | Async `202 ACCEPTED` job polling & download |
| **SP-P9** | School | Notifications | S | Ready | School in-app feed, reminders status |
| **SP-P10** | School | School Users | M | Ready | School Admin & School Finance CRUD |
| **SP-P11** | School | Settings | S | Ready | School profile, notifications, change password |

*22 tickets total. Foundation, Auth, and Bank Dashboard are complete. The remaining 19 screen phases are fully unblocked for parallel development.*