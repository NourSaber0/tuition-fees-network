# Project Context: Educational Tuition & Services Fees Collection Network (CLI Master Spec)

## 1. System Overview & Core Mission
A centralized, multi-tenant educational dues collection network in Egypt that allows parents/guardians to search, consolidate, and settle outstanding educational fees (tuition, bus, books, activities) across multiple schools and universities in a single portal.

**Key Value Propositions:**
* **Consolidated Guardian View:** A parent searches using their Parent National ID and retrieves all outstanding and historical fee lines across all associated children and educational institutions.
* **Flexible Settlement Rails:** Settle in full, in part, via direct bank account debit (CIB), credit card, or an Equal Payment Plan (EPP) over 3, 6, 12, or 18 months.
* **Institutional Reconciliation:** Daily settlement reporting, automated status transitions, and bulk dues ingestion via CSV upload or partner REST APIs.

## 2. Development Phases & Track Sequencing
The MVP is split into six phases across four weeks, divided into Backend, Frontend, and Mobile tracks. No parallel UI work starts before Phase 0 is signed off.

* **P0 (Setup & Design):** Lock the REST/JSON API contract, database schema, and mock environments.
* **P1 (Auth & Linking):** Register/login, tokens, and CIB customer verification.
* **P2 (Search & Display):** National ID search, categorized fee model, and consolidated views.
* **P3 (Payment & Receipts):** Full, partial, and EPP payments, plus transaction history.
* **P4 (Upload & Reports):** Institution CSV upload, daily summaries, and notifications.
* **P5 (Testing & Delivery):** Cross-track end-to-end integration.

## 3. Modular System Architecture
* **Frontend / Mobile Tracks:** Rely entirely on the backend REST/JSON API.
* **Adapter Layer:** Business services must never invoke external bank APIs directly.
* **Mock Bank Services:** Must be built first to simulate deterministic auth, verifications, and gateway responses so parallel UI development can proceed.

```plaintext
                      [ Channels ]
       ( Web Portal  |  Mobile App  |  Institution Portal )
                            │
                      [ API Gateway ]
          ( OAuth2/OIDC, Rate Limiting, Audit Logger )
                            │
┌───────────────────────────────────────────────────────────────┐
│                       Core Service Modules                    │
│                                                               │
│  [Identity] ────► [Search] ◄──── [Billing / Fee Engine]       │
│      │               │                      ▲                 │
│      ▼               │                      │                 │
│  [Payments] ─────────┴──────────────► [Institution Ingestion] │
│      │                                                        │
│      ├──────► [EPP Calculation Engine]                        │
│      ├──────► [Receipt & Audit Ledger]                        │
│      └──────► [Notifications / Alerts (SMS & Email)]          │
└───────────────────────────────┬───────────────────────────────┘
                                │
                      [ Bank Adapter Layer ]
          ┌─────────────────────┴─────────────────────┐
          ▼                                           ▼
  [ Mock Bank Services ]                    [ Real Bank Integration ]
  - Deterministic Auth & Verifications      - CIB Direct Debit API
  - Simulated Card Gateway & EPP            - Card Gateway / 3-D Secure
```

## 4. Core Entities & Data Model

### 4.1 Relationships
* **Guardian (Parent)** 1 — N **Student** (via verified guardian-child link table).
* **Institution** 1 — N **FeeLine** (Tuition, Bus, Books, Activities).
* **Student** 1 — N **FeeLine**.
* **Payment** 1 — N **PaymentAllocation** (mapping amounts to individual FeeLine records).
* **Payment** 1 — 1 **EPPSchedule** (Optional: if paid via credit card instalment plan).
* **Payment** 1 — 1 **Receipt**.

### 4.2 Entity Schemas
```typescript
// --- Identity & Guardian ---
interface Guardian {
  id: string; // UUID
  nationalIdHmac: string; // Keyed HMAC for indexed queries (never plaintext)
  nationalIdEncrypted: string; // AES-256-GCM encrypted string
  fullName: string;
  phone: string;
  email: string;
  cibAccountLinked?: string;
  createdAt: Date;
}

interface Student {
  id: string; // UUID
  nationalIdHmac: string;
  nationalIdEncrypted: string;
  fullName: string;
  guardianId: string; // FK to Guardian
  dateOfBirth: Date;
}

// --- Institutions & Dues ---
interface Institution {
  id: string;
  name: string; // e.g., "Nile International School"
  code: string;
  bankAccountRef: string;
  isActive: boolean;
}

enum FeeType {
  TUITION = "Tuition",
  BUS = "Bus subscription",
  BOOKS = "Books & materials",
  ACTIVITIES = "Activities"
}

enum FeeStatus {
  OUTSTANDING = "Outstanding",
  PARTIALLY_PAID = "Partially Paid",
  PAID = "Paid",
  CANCELLED = "Cancelled"
}

interface FeeLine {
  id: string;
  institutionId: string;
  studentId: string;
  feeType: FeeType;
  academicPeriod: string; // e.g., "Term 2 · 2026"
  totalAmount: number; // Currency: EGP
  paidAmount: number; // Currency: EGP
  remainingAmount: number; // Computed: totalAmount - paidAmount
  currency: "EGP";
  status: FeeStatus;
  dueDate: Date;
  version: number; // Optimistic locking version
}

// --- Payments & EPP ---
enum PaymentMethod {
  CIB_ACCOUNT = "CIB_ACCOUNT",
  CREDIT_CARD = "CREDIT_CARD",
  EPP_INSTALMENTS = "EPP_INSTALMENTS"
}

enum PaymentStatus {
  PENDING = "PENDING",
  AUTHORIZED = "AUTHORIZED",
  CAPTURED = "CAPTURED",
  FAILED = "FAILED",
  REFUNDED = "REFUNDED"
}

interface Payment {
  id: string;
  idempotencyKey: string;
  guardianId: string;
  amount: number;
  currency: "EGP";
  paymentMethod: PaymentMethod;
  status: PaymentStatus;
  authCode?: string;
  transactionReference: string;
  createdAt: Date;
}

interface PaymentAllocation {
  id: string;
  paymentId: string;
  feeLineId: string;
  allocatedAmount: number;
}

interface EPPSchedule {
  id: string;
  paymentId: string;
  tenorMonths: 3 | 6 | 12 | 18;
  principalAmount: number;
  annualInterestRate: number; // e.g., 0.14 for 14%
  interestAmount: number;
  adminFee: number;
  totalPayable: number;
  monthlyInstalment: number;
  schedule: Array<{
    month: number;
    dueDate: Date;
    amount: number;
  }>;
}
```

## 5. Business Logic & Calculation Engines

### 5.1 Parent National ID Search & Aggregation
**Search Endpoint:** `GET /api/v1/dues/search?parentNationalId={ID}`

**Resolution Steps:**
1. Compute `HMAC_SHA256(parentNationalId, SECRET_KEY)`.
2. Verify authenticated user identity matches or has guardian authorization.
3. Query all Student records where `guardianId == resolvedGuardian.id`.
4. Query all FeeLine entries where `studentId IN (students)` and `status != 'PAID'`.
5. Return aggregated response grouped by Student → Fee Type → Total Outstanding.

### 5.2 Equal Payment Plan (EPP) Formulas
Egyptian standard flat rate card instalment pricing:
*   $\text{Interest} = \text{Principal} \times \text{Annual Rate} \times \left(\frac{\text{Tenor in Months}}{12}\right)$
*   $\text{Admin Fee} = \min(\text{Principal} \times \text{Admin Rate}, \text{Cap})$
*   $\text{Total Payable} = \text{Principal} + \text{Interest} + \text{Admin Fee}$
*   $\text{Monthly Instalment} = \frac{\text{Total Payable}}{\text{Tenor}}$

**Standard Tenors:**
*   **3 Months (Promotional):** 0% interest, 0 EGP admin fee.
*   **6, 12, 18 Months:** Configurable flat annual rate (e.g., 14%) with 1% admin fee (capped at 500 EGP).

### 5.3 Five-Field CSV Ingestion Contract
Each row in the institutional upload file requires:
1.  **National_ID:** 14-digit Egyptian National ID of the student.
2.  **Fee_Type:** One of [Tuition, Bus subscription, Books & materials, Activities].
3.  **Amount:** Positive integer or float > 0.
4.  **Currency:** Must be EGP.
5.  **Collection_Period:** Valid academic term (e.g., Term 2 · 2026).

## 6. REST API Specifications

```http
### 1. Search Consolidated Dues by Parent National ID
GET /api/v1/guardian/dues
Authorization: Bearer <JWT>
X-Guardian-National-Id: <14-Digit-ID>

Response 200 OK:
{
  "guardianName": "Ahmed Saber",
  "totalOutstandingEGP": 37300.00,
  "students": [
    {
      "studentId": "std_101",
      "studentName": "Sara Ahmed",
      "institutionName": "Nile International School",
      "dues": [
        {
          "feeLineId": "fee_001",
          "feeType": "Tuition",
          "period": "Term 2 · 2026",
          "totalAmount": 18000.00,
          "remainingAmount": 18000.00,
          "status": "Outstanding"
        }
      ]
    }
  ]
}

### 2. Initiate Payment (Full / Partial / EPP)
POST /api/v1/payments/settle
Idempotency-Key: <UUID>
Content-Type: application/json

{
  "paymentMethod": "EPP_INSTALMENTS",
  "selectedDues": [
    { "feeLineId": "fee_001", "amountToPay": 18000.00 },
    { "feeLineId": "fee_003", "amountToPay": 6000.00 }
  ],
  "totalAmount": 24000.00,
  "eppSelection": {
    "tenorMonths": 12
  }
}
```

## 7. Resiliency & Failure Modes
The CLI must build resilient infrastructure rather than "happy path" APIs. Implement the following strictly:
*   **Charging Twice (Idempotency):** Every create-payment call must use an `Idempotency-Key`. If submitted twice, return the original payment state; do not trigger a second charge.
*   **Gateway Down:** If the payment gateway fails, fail loudly with a retryable error. Never fail silently. Queue the retry and accurately notify the user.
*   **Concurrency (Two Payers, One Fee):** Implement optimistic locking (version field) on the `FeeLine`. Prevent the second payer from overpaying if both settle the exact same balance simultaneously.
*   **Bad CSV Uploads:** Use idempotent ingestion. Reject individual malformed rows (e.g., amount ≤ 0, unknown fee type) and report them back; do not reject the entire valid file.
*   **Traffic Spikes:** Design the database and API to handle national traffic spikes when fees fall due in the same week.

## 8. Pending Business Rules (Strict Guardrails)
There are undefined business rules in the requirements document. The AI CLI is strictly forbidden from inventing business logic for these states.

**Directive:** If execution enters one of the following undefined states, the code MUST throw a `PendingBusinessRuleException` with a descriptive error message indicating the rule requires business clarification.

**Monitored States for `PendingBusinessRuleException`:**
*   **EPP Interest Allocation:** When calculating EPP schedules, if the system must allocate the cost of the interest/admin fee (Family, School, or Bank).
*   **Post-Deadline Partial Payments:** When processing a partial payment for a fee line where the collection period deadline has already passed.
*   **Cardless EPP Initiation:** If a user without a credit card attempts to initiate an instalment plan.
*   **Cross-Institution Data Bleed:** If a query risks exposing School A's outstanding balances to School B's administrative portal.
*   **Mid-Year EPP Cancellation:** If an institution attempts to cancel or unwind a fee line that is actively locked in a 12-month or 18-month EPP schedule due to a student withdrawing.
