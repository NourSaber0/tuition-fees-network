Audit the existing **Bank Back Office Portal** against **EPIC 9 — Reconciliation**.

This is a **UI/UX design audit only**.

Do NOT redesign the portal from scratch.
Do NOT remove, rename, or restructure existing screens, navigation, components, or functionality.

Use the existing:

* **B08 — Reconciliation Dashboard**
* **B09 — Reconciliation Exception**

as the primary screens for this epic.

Only add or modify UI elements that are necessary to satisfy the requirements below.

### US-55 — View Reconciliation Status

Check whether the portal clearly displays reconciliation status for collections.

The design should allow users to distinguish:

* Reconciled / Matched
* Unreconciled / Exception
* Pending, where applicable

Check whether the reconciliation status is clearly visible in relevant transaction/collection records.

Do not create a separate screen if the existing reconciliation dashboard or collection details can provide this information.

### US-56 — Reconcile Transactions

Check whether **B08 — Reconciliation Dashboard** visually represents the comparison between:

**Payment Record ↔ Bank Transaction ↔ School Fee**

The reconciliation interface should allow the bank employee to understand the comparison between:

* Transaction/payment amount
* Bank amount
* School fee/collection amount
* Matching status

The existing reconciliation table should clearly distinguish matched and unmatched records.

Check whether there is a clear workflow from identifying transactions to reviewing reconciliation results.

### US-57 — Identify Exceptions

Check whether unmatched transactions are clearly identified as reconciliation exceptions.

The UI should show, where available:

* Exception status
* Transaction/reference
* Difference/discrepancy
* Relevant amounts
* Reason/type of discrepancy where available

Exceptions must be visually distinguishable from successfully reconciled transactions.

### US-58 — View Exception Queue

Check whether **B08 — Reconciliation Dashboard** provides an identifiable **Exception Queue** or equivalent section.

The queue should allow a bank employee to see:

* Unresolved exceptions
* Exception/reference ID
* Transaction ID
* School
* Exception type/reason
* Difference amount where applicable
* Current exception status
* Assigned user where applicable
* Priority/date where appropriate

The employee should be able to select/open an exception to investigate it.

Resolved exceptions should be clearly distinguished from unresolved exceptions and should not appear as active unresolved items.

### US-59 — View Exception Details

Check whether **B09 — Reconciliation Exception** provides a complete comparison view.

The screen should allow the employee to compare:

**Payment Record**

* Transaction ID
* Amount
* Payment status
* Payment information

**Bank Transaction**

* Bank reference
* Bank amount
* Bank transaction status
* Settlement information where available

**School Fee**

* School
* Fee reference
* Fee amount
* Collection/payment information

Most importantly, make the **differences between the records visually obvious**.

For example:

| Record  |     Amount | Status     |
| ------- | ---------: | ---------- |
| Payment | 10,000 EGP | Successful |
| Bank    | 10,000 EGP | Settled    |
| School  |  9,000 EGP | Recorded   |

Clearly highlight the discrepancy without changing the existing information architecture.

### US-60 — Assign Reconciliation Exception

Check whether B09 supports assigning an exception for investigation.

The UI should provide:

* Assigned-to field/dropdown
* Available bank employee/appropriate assignee
* Current assignee
* Assignment status
* Clear save/confirm action
* Success feedback after assignment

If the existing design already has an investigation workflow, extend it rather than creating a new screen.

### US-61 — Resolve Reconciliation Exception

Check whether B09 provides a clear resolution workflow.

The employee should be able to:

* Enter/select resolution reason
* Record resolution/outcome
* Add investigation notes
* Add supporting reference where appropriate
* Review the resolution before completing it
* Mark the exception as Resolved
* See the updated resolution status

The UI should clearly distinguish:

**Unresolved → Assigned → Investigating → Resolved**

where these states are supported by the existing requirements.

### REQUIRED RECONCILIATION WORKFLOW

Most importantly, verify that the design communicates this complete workflow:

**Match**

↓

**Exception Queue**

↓

**Review Exception**

↓

**Assign / Investigate**

↓

**Record Resolution**

↓

**Resolve**

↓

**Resolved Record**

The reconciliation module must feel like an **operational workflow**, not simply a reporting dashboard.

### DESIGN REQUIREMENTS

Maintain the existing CIB-inspired visual identity:

* Corporate blue
* White and light-gray surfaces
* Professional banking/fintech aesthetic
* Clear tables and data hierarchy
* Clear status badges
* Consistent buttons and form controls
* Accessible contrast
* Consistent spacing and typography
* Production-ready enterprise banking UI

Do NOT evaluate backend-only functionality such as actual transaction matching, database storage, assignment persistence, or audit-log implementation.

Only evaluate what can be represented through the UI/UX.

### FINAL AUDIT

Classify each requirement as:

**✅ Fully satisfied**
**⚠️ Partially satisfied**
**❌ Missing**

At the end, identify:

1. Which requirements are already covered by B08/B09
2. Which UI elements are missing
3. Whether the exception queue is clearly represented
4. Whether the three records — Payment, Bank, School Fee — can be compared
5. Whether assignment and investigation states are represented
6. Whether the resolution workflow is represented
7. Any minimal modifications needed

Do NOT create unnecessary new screens.

Prefer extending **B08 — Reconciliation Dashboard** and **B09 — Reconciliation Exception**.
