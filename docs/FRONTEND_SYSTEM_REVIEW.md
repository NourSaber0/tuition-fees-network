# Frontend Architecture & System Review: Tuition Network

This document provides an in-depth, file-specific breakdown of the Next.js frontend ecosystem. It details the exact packages, how the API client handles authentication state, and how the reusable components are organized within the monorepo structure. This serves as a definitive reference for technical reviews with the Head of Development.

## 1. System Overview & Monorepo Structure
The frontend is architected as a **Turborepo/pnpm workspace monorepo** located in the `/frontend` directory. This isolates routing, API fetching logic, and UI components into three strictly separated layers.

### Monorepo Packages:
- **`apps/portal/`**: The primary Next.js (App Router) application managing the UI views and page routing.
- **`packages/api-client/`**: Centralized HTTP client, JWT state management, and strictly typed Data Transfer Objects (DTOs).
- **`packages/ui/`**: The internal, reusable React component library enforcing UI consistency.

---

## 2. In-Depth File Breakdown & Interactions

### A. The Next.js Application (`frontend/apps/portal/`)
This is the application layer built using the Next.js App Router framework. It segregates logic based on the three system personas.

- **`src/app/login/`**:
  - **Function:** Handles the initial user authentication flow and MFA (Multi-Factor Authentication). 
  - **Interaction:** Upon success, it passes the resulting JWT and user object down to the `AuthProvider` to be stored in the session context.
- **`src/app/bank/transactions/page.tsx`**:
  - **Function & Complexity:** A robust, state-heavy UI for Bank Tellers to process payments. It utilizes the `useApiClient` hook to fetch Paginated transaction lists (`/transactions?page=0&pageSize=10`). 
  - **State Management:** It manages complex view states (`MainView` for toggling between list and detail views) and `StatusTab` filters ("SUCCESSFUL", "PENDING", "FAILED") without prop-drilling, relying heavily on local component state and URL search parameters for deep linking.
- **`src/app/school/`**:
  - **Function:** The Institution Dashboard. Contains the UI where School Admins (`ROLE_INSTITUTION_ADMIN`) upload CSV fee rosters and manage student profiles.
- **`src/app/layout.tsx`**:
  - **Function:** The root layout file. It wraps the entire Next.js application in global Context Providers, most notably the `AuthProvider`, ensuring all routes have access to the API client and session state.

### B. The API Communication Layer (`frontend/packages/api-client/`)
This package abstracts all network requests away from the UI, providing a strongly-typed SDK-like experience for the Next.js app.

- **`src/client.ts` (`createApiClient`)**:
  - **Function:** A sophisticated Axios/Fetch wrapper. It accepts a `baseUrl` (e.g., `http://localhost:8080/api/v1`) and automatically intercepts outgoing requests to append the `Authorization: Bearer <JWT>` header.
  - **Resiliency Handling:** If the backend returns a `401 Unauthorized` (indicating an expired token), the client intercepts the error, automatically attempts to call `/auth/refresh` to get a new token, and replays the original request seamlessly. If the refresh fails, it triggers a forced logout redirect.
- **`src/AuthProvider.tsx`**:
  - **Function:** A React Context Provider handling session persistence. It writes the JWT into the browser's `localStorage` (`tuition.auth.session`) so sessions persist across hard refreshes.
  - **Interaction:** It exposes critical methods like `login()`, `verifyMfa()`, and `logout()` via the `useAuth()` hook. The routing logic in `apps/portal` relies on `user.role` from this context to enforce route protection (redirecting unauthorized personas).
- **`src/types.ts`**:
  - **Function:** Defines exact TypeScript interfaces mimicking the Java backend DTOs (e.g., `TransactionDto`, `PageResponse`, `AuthUser`). This ensures strict compile-time safety and prevents runtime errors caused by mismatched JSON payloads.

### C. The Reusable Component Library (`frontend/packages/ui/`)
A shared component library ensuring visual consistency across both the Bank and School portals.

- **`src/PortalShell.tsx`**:
  - **Function:** The macro-layout component used by both portals. It standardizes the top navigation bar, sidebar menus, and responsive content area to give the application a unified "Suite" feel regardless of whether a school admin or bank teller is logged in.
- **`src/Table.tsx` & `Pagination.tsx`**:
  - **Function:** Reusable data-grid components. `Table.tsx` supports generic generic column definitions and row-rendering functions, heavily utilized in the Bank Transactions and School Student Ledgers views.
- **`src/Modal.tsx` & `Toast.tsx`**:
  - **Function:** Unobtrusive user feedback. `Modal.tsx` forces explicit confirmation for high-risk actions (e.g., executing a bank payment). `Toast.tsx` is hooked into the API client's error catching to display graceful transient errors (e.g., "Overpayment Attempted" from a `422 Unprocessable Entity`) directly to the user without breaking the UI flow.

---

## 3. Executive Summary of UI Workflows

If the Head of Development queries the frontend's resilience and architecture, emphasize these workflows:

1. **Idempotency Header Generation (`page.tsx`)**:
   - When executing an Over The Counter payment, the frontend locally generates a unique UUID `Idempotency-Key` and attaches it to the HTTP header. If the UI freezes and the teller clicks "Pay" twice, the identical key is transmitted, guaranteeing the backend's tamper protection intercepts and safely ignores the duplicate click.
2. **Strict Route Role Guards (`AuthProvider.tsx`)**:
   - Security isn't just hiding buttons. The frontend utilizes the decoded `user.role` from the `AuthProvider` to evaluate route accessibility dynamically. A `school-admin` manually typing `/bank/transactions` into the URL bar will be hard-redirected back to `/school/dashboard` instantly.
3. **Automated Token Refresh (`client.ts`)**:
   - Network requests are wrapped in an intelligent retry loop. If a JWT expires mid-session, the user is never interrupted. The client silently intercepts the `401`, exchanges the refresh token, and replays the failed request before the UI even knows an error occurred.
