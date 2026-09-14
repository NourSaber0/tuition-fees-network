# Frontend Rebuild — Team Task Distribution & Implementation Status

**Project:** CIB Tuition & Services Fees Collection Network — Unified Frontend Rebuild  
**Reference Document:** [`docs/frontend_rebuild_task_distribution.md`](file:///Users/nourahmed/Downloads/demo/docs/frontend_rebuild_task_distribution.md)  
**Status Date:** 2026-09-14  
**App Framework:** Next.js 16 (App Router) / React 19 / TypeScript 5 / Tailwind CSS v4 / pnpm Monorepo  
**Target Backend:** Spring Boot API at `http://localhost:8080/api/v1` (100% complete, 492 tests passing)  

---

## 1. Executive Summary

The frontend rebuild unifies both the **Bank Back-Office Portal** and the **School Portal** into a single Next.js monorepo application (`apps/portal`) powered by a single shared `/login` entrypoint and role-based portal routing.

```text
[ Shared Login (/login) ] 
       │
       ├── isBankRole()   ──► /bank/dashboard   ──► Bank Back-Office Modules (BO-P2 .. BO-P12)
       └── isSchoolRole() ──► /school/dashboard ──► School Portal Modules (SP-P2 .. SP-P11)
```

### Overall Progress Tracker

| Category | Total Tickets | Completed | In Progress | Ready for Assignment | Progress % |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Shared Foundation & Auth** | 2 | 2 | 0 | 0 | 100% |
| **Bank Back-Office Portal** | 11 | 1 | 0 | 10 | 9.1% |
| **School Portal** | 10 | 0 | 1 | 9 | 10.0% |
| **Total** | **22** | **3** | **1** | **18** | **18.2%** |

> **Milestone Note:** Phase 0 (monorepo & foundation), AUTH (shared sign-in & role routing), and BO-P2 (full Bank Back-Office dashboard) are **100% complete and merged into `main`**. All developers can now branch off `main` and build their assigned screens concurrently.

---

## 2. Master Task Distribution Matrix

### Track 0: Shared Foundation & Authentication (Complete)

| Ticket ID | Module / Screen | Route / File Path | Target Endpoints | Size | Status | Assigned To |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| **P0** | Monorepo & Shared Design System | [`frontend/`](file:///Users/nourahmed/Downloads/demo/frontend)<br>[`packages/ui`](file:///Users/nourahmed/Downloads/demo/frontend/packages/ui)<br>[`packages/api-client`](file:///Users/nourahmed/Downloads/demo/frontend/packages/api-client)<br>[`packages/config`](file:///Users/nourahmed/Downloads/demo/frontend/packages/config) | N/A (Core infrastructure) | M | ✅ **DONE** | Shared |
| **AUTH** | Shared Sign-In & Role Routing | [`apps/portal/src/app/login/page.tsx`](file:///Users/nourahmed/Downloads/demo/frontend/apps/portal/src/app/login/page.tsx)<br>[`packages/ui/src/PortalShell.tsx`](file:///Users/nourahmed/Downloads/demo/frontend/packages/ui/src/PortalShell.tsx) | `/auth/login`<br>`/auth/mfa/verify`<br>`/auth/mfa/resend`<br>`/auth/mfa/trust-device`<br>`/auth/forgot-password`<br>`/auth/reset-password`<br>`/auth/me` | M | ✅ **DONE** | Shared |

---

### Track A: Bank Back-Office Portal (`/bank/*`)

Gated by `isBankRole(user.role)`: `bank-admin`, `bank-operations`, `bank-finance`, `bank-reconciliation`.  
Layout: [`apps/portal/src/app/bank/layout.tsx`](file:///Users/nourahmed/Downloads/demo/frontend/apps/portal/src/app/bank/layout.tsx) with dynamic permission filtering.

| Ticket ID | Screen / Feature | Route / File Target | Backend Endpoints | Size | Status | Notes |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| **BO-P2** | **Bank Dashboard** | [`apps/portal/src/app/bank/dashboard/page.tsx`](file:///Users/nourahmed/Downloads/demo/frontend/apps/portal/src/app/bank/dashboard/page.tsx) | `/dashboard/summary`<br>`/dashboard/collections/weekly`<br>`/dashboard/institution-status`<br>`/dashboard/recent-transactions`<br>`/dashboard/deadline-summary` | S | ✅ **DONE** | 9 KPI cards, SVG weekly chart, status breakdown, recent transactions, deadline queue |
| **BO-P3** | **Institution Management** | `apps/portal/src/app/bank/schools/page.tsx` | `/institutions`<br>`/institutions/{id}/students`<br>`/institutions/{id}/application`<br>`/institutions/{id}/integration`<br>`/institutions/{id}/approve\|reject\|activate\|deactivate`<br>`/institutions/{id}/fee-submissions` | L | 📋 **Ready** | List + register + tabs (Students/Application/Integration/Fees/Settlement) |
| **BO-P4** | **Transactions & Payments** | `apps/portal/src/app/bank/transactions/page.tsx` | `/transactions`<br>`/transactions/{id}`<br>`/transactions/export`<br>`/customers/fees`<br>`/payments` | L | 📋 **Ready** | Filterable table, receipt view, multi-step payment workflow with idempotency key |
| **BO-P5** | **Reconciliation** | `apps/portal/src/app/bank/reconciliation/page.tsx` | `/reconciliation/summary`<br>`/reconciliation/runs`<br>`/reconciliation/exceptions`<br>`/reconciliation/exceptions/{id}` | M | 📋 **Ready** | Backend module is 100% merged; status cards, runs history, exception resolver |
| **BO-P6** | **EPP Plans** | `apps/portal/src/app/bank/epp/page.tsx` | `/epp/plans`<br>`/epp/plans/{id}/schedule`<br>`/epp/quote`<br>`/epp/cards/validate` | M | 📋 **Ready** | EPP plan calculator, installment schedule table, credit card validation |
| **BO-P7** | **Reports** | `apps/portal/src/app/bank/reports/page.tsx` | `/reports/catalogue`<br>`/reports/generate`<br>`/reports/history`<br>`/reports/jobs/{id}/download` | M | 📋 **Ready** | Synchronous generation (`200 OK`) with immediate table preview and export |
| **BO-P8** | **Notifications** | `apps/portal/src/app/bank/notifications/page.tsx` | `/notifications`<br>`/notifications/unread-count`<br>`/notifications/{id}/read`<br>`/notifications/read-all` | S | 📋 **Ready** | In-app notification feed, header bell counter, mark as read, delete |
| **BO-P9** | **Audit Logs** | `apps/portal/src/app/bank/audit-logs/page.tsx` | `/audit-logs`<br>`/audit-logs/{id}`<br>`/audit-logs/stats`<br>`/audit-logs/export` | S | 📋 **Ready** | Backend `AuditLogController` is merged; audit table with SHA-256 masked NID view |
| **BO-P10** | **Users & Roles** | `apps/portal/src/app/bank/users/page.tsx` | `/users`<br>`/users/{id}`<br>`/users/{id}/activate\|deactivate`<br>`/roles` | M | 📋 **Ready** | Bank employee CRUD, role-permissions inspector, activation toggle |
| **BO-P11** | **Settings** | `apps/portal/src/app/bank/settings/page.tsx` | `/settings/fee-types`<br>`/settings/payment-statuses`<br>`/settings/epp`<br>`/settings/notifications`<br>`/settings/institutions` | M | 📋 **Ready** | System parameter tabs, PUT audited changes |
| **BO-P12** | **Deadline & Priority Badges** | Integrated across Transaction & Dashboard tables | `/dashboard/deadline-summary`<br>`/transactions` | S | 📋 **Ready** | Helpers in `@tuition/ui/src/priority.ts` (`dueDateLabel`, `PRIORITY_BADGE_CLASSES`) |

---

### Track B: School Portal (`/school/*`)

Gated by `isSchoolRole(user.role)`: `school-admin`, `school-finance`.  
Layout: [`apps/portal/src/app/school/layout.tsx`](file:///Users/nourahmed/Downloads/demo/frontend/apps/portal/src/app/school/layout.tsx).  
Specification: [`docs/School-Portal-API-Contract.md`](file:///Users/nourahmed/Downloads/demo/docs/School-Portal-API-Contract.md).

| Ticket ID | Screen / Feature | Route / File Target | Backend Endpoints | Size | Status | Notes |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| **SP-P2** | **School Dashboard** | [`apps/portal/src/app/school/dashboard/page.tsx`](file:///Users/nourahmed/Downloads/demo/frontend/apps/portal/src/app/school/dashboard/page.tsx) | `/dashboard/summary`<br>`/dashboard/recent-payments`<br>`/dashboard/quick-links` | S | ⏳ **In Progress** | Layout shell ready; 4 KPI cards, recent payments table, quick links tiles to build |
| **SP-P3** | **Student Management** | `apps/portal/src/app/school/students/page.tsx` | `/students`<br>`/students/{id}`<br>`/students/deactivated`<br>`/students/{id}/fees`<br>`/students/{id}/payments`<br>`/students/search` | L | 📋 **Ready** | Active roster, deactivated archive, student detail drawer, guardian link/unlink |
| **SP-P4** | **Fee Management** | `apps/portal/src/app/school/fee-management/page.tsx` | `/fees`<br>`/fees/{id}`<br>`/fee-categories`<br>`/fees/{id}/penalty-info` | L | 📋 **Ready** | Categorized fees list, overdue/due status badges, fee creation modal |
| **SP-P5** | **Fee Upload** | `apps/portal/src/app/school/fee-upload/page.tsx` | `/fee-uploads/template`<br>`/fee-uploads`<br>`/fee-uploads/{id}/rows`<br>`/fee-uploads/{id}/errors`<br>`/fee-uploads/{id}/resubmit` | M | 📋 **Ready** | Drag-and-drop CSV uploader, row validation error table, error CSV export, resubmit flow |
| **SP-P6** | **Payments (View-Only)** | `apps/portal/src/app/school/payments/page.tsx` | `/payments`<br>`/payments/{id}`<br>`/payments/export` | S | 📋 **Ready** | Read-only payments ledger, allocation breakdown drawer, CSV export |
| **SP-P7** | **Reconciliation (View-Only)** | `apps/portal/src/app/school/reconciliation/page.tsx` | `/reconciliation/summary`<br>`/reconciliation/transactions` | S | 📋 **Ready** | Read-only daily settlement summary & school transaction audit |
| **SP-P8** | **Reports** | `apps/portal/src/app/school/reports/page.tsx` | `/reports/catalogue`<br>`/reports/generate`<br>`/reports/jobs/{id}`<br>`/reports/jobs/{id}/download`<br>`/reports/history` | M | 📋 **Ready** | Async `202 ACCEPTED` report pipeline; polling job status until `READY`, then download |
| **SP-P9** | **Notifications** | `apps/portal/src/app/school/notifications/page.tsx` | `/notifications`<br>`/notifications/unread-count`<br>`/notifications/{id}/read`<br>`/notifications/reminders` | S | 📋 **Ready** | In-app notifications feed and automated reminder status tracker (scheduled/sent/failed) |
| **SP-P10** | **School Users** | `apps/portal/src/app/school/users/page.tsx` | `/users`<br>`/users/{id}`<br>`/users/{id}/activate\|deactivate`<br>`/roles` | M | 📋 **Ready** | Manage School Admin and School Finance users, status toggle, permissions check |
| **SP-P11** | **Settings** | `apps/portal/src/app/school/settings/page.tsx` | `/settings/profile`<br>`/settings/notifications`<br>`/settings/change-password` | S | 📋 **Ready** | Read-only school profile card, notification delivery toggles, self-service password change |

---

## 3. Developer Implementation Cheatsheet

When starting on a ticket, follow this standard pattern:

### Step 1: Create the Page File
Place the page under the appropriate folder, e.g. `apps/portal/src/app/school/students/page.tsx`.

### Step 2: Use Client Hooks & UI Components
```tsx
"use client";

import { useEffect, useState } from "react";
import { useApiClient, useAuth } from "@tuition/api-client";
import { 
  Table, 
  Button, 
  Badge, 
  Modal, 
  LoadingSpinner, 
  EmptyState 
} from "@tuition/ui";

export default function StudentsPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiClient.get("/students?page=1&size=25")
      .then(setData)
      .catch((err) => console.error(err))
      .finally(() => setLoading(false));
  }, [apiClient]);

  if (loading) return <LoadingSpinner />;
  // Render UI...
}
```

### Step 3: Verify Your Work
Before opening a pull request, run the verification suite:
```bash
cd frontend
npx pnpm -r typecheck   # Must pass with 0 errors
npx pnpm -r lint        # Must pass with 0 warnings/errors
npx pnpm -r build       # Next.js production build check
```

---

## 4. Local Development Environment & Pre-Seeded Logins

Start the backend and frontend locally:

```bash
# Terminal 1: Backend
./mvnw spring-boot:run

# Terminal 2: Frontend
cd frontend
npx pnpm dev
```

### Pre-Seeded Accounts for Testing

| Role | Username / Email | Password | Target URL |
| :--- | :--- | :--- | :--- |
| **Bank Admin** | `mohamed.ali@cibeg.com` | `Password123!` | `/bank/dashboard` |
| **School Admin (Cairo Int. School)** | `amr.hassan@cis.edu.eg` | `Password123!` | `/school/dashboard` |
| **School Finance (Cairo Int. School)** | `dina.fouad@cis.edu.eg` | `Finance@2026` | `/school/dashboard` |

*(MFA verification code in local dev is pre-printed in the Spring Boot server log, or can be queried via the debug endpoints).*
