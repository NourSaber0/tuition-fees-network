Audit the existing **Bank Back Office Portal** against the Bank-related requirements of **EPIC 10 — Reports**.

IMPORTANT:
This audit is ONLY for the **Bank Back Office Portal**.

Do NOT add or design school-user report functionality from US-62 or US-63.

Do NOT redesign the existing portal.
Do NOT remove, rename, or restructure existing screens, navigation, components, or user flows.

Use the existing **B12 — Reports** screen as the primary screen for these requirements.

### US-64 — View Network Reports

Check whether B12 — Reports allows an authorized bank employee to view **network-level reports across participating schools**.

The Reports interface should support reports containing:

* Network-wide collections
* Network-wide payments
* Information across participating schools
* Aggregated financial information
* Relevant report date/range
* School information where applicable

The interface should make it clear that the bank employee is viewing **network-level data**, rather than information belonging to only one school.

### US-65 — View Daily Collections

Check whether the Reports screen provides a way to view **daily collection summaries**.

The design should support:

* Selecting a collection date
* Daily collection total
* Collections across participating schools
* Payment status information
* Collection breakdown by school where appropriate
* Clear indication of the selected date

The report should be easy to scan and suitable for monitoring daily bank collections.

### US-66 — View EPP Reports

Check whether B12 — Reports provides an **EPP report**.

The report should allow bank employees to view:

* EPP plans
* Customer/payment reference where appropriate
* School
* Principal
* Tenor
* Total payable
* Monthly installment
* EPP status
* Paid installments
* Outstanding installments

The report should support the existing EPP tenors:

* 3 months
* 6 months
* 12 months
* 18 months

Do not create a separate EPP reporting system. Integrate this into the existing Reports experience.

### US-67 — View Reconciliation Reports

Check whether B12 — Reports provides a **Reconciliation Report**.

The report should allow bank employees to identify:

* Matched transactions
* Unmatched transactions
* Exceptions
* Reconciliation status
* Relevant transaction/reference information
* Difference/discrepancy information where applicable

The design should make matched and unmatched records visually distinguishable.

The Reconciliation Report should complement the existing **B08 — Reconciliation Dashboard** and **B09 — Reconciliation Exception** screens rather than replacing them.

### US-68 — Filter Reports

Check whether B12 — Reports provides appropriate report filters.

The design should support, where applicable:

* Date/date range
* School
* Fee type
* Payment status
* Report type

Check that the selected filters are clearly visible and that the user can:

* Apply filters
* Clear/reset filters
* Understand which filters are currently active
* See results based on the selected criteria

The Reports interface should remain clean and easy to use even when multiple filters are applied.

### REPORT TYPES

Check whether the existing Reports screen can provide access to the bank-level reports required by the project, including:

* Network Collections
* Collections by School
* Payments
* Failed Transactions
* EPP Reports
* Reconciliation Reports
* Outstanding Balances
* Daily Collections

If these report types already exist, keep them.

If some are missing, add them to the existing B12 Reports interface rather than creating unnecessary new pages.

### REPORT INTERACTION

Check whether the report interface provides appropriate UI states for:

* Loading report data
* No results
* Results available
* Invalid filter combination
* Clearing filters
* Changing date range
* Switching between report types

Do not invent unnecessary functionality.

### IMPORTANT SCOPE RULE

US-62 and US-63 are **School Portal requirements**.

Do NOT add school-specific report permissions or school report screens to the Bank Back Office unless they are already part of the existing bank portal requirements.

For the Bank Back Office, focus on **network-level reporting and bank employee access**.

### DESIGN RULES

Maintain the existing CIB-inspired visual identity:

* Corporate blue
* White and light-gray surfaces
* Professional banking/fintech aesthetic
* Clean data tables
* Clear charts/KPIs where already appropriate
* Consistent filters
* Consistent buttons
* Clear status indicators
* Accessible contrast
* Consistent spacing and typography

Do NOT redesign the portal.

### FINAL AUDIT

For each requirement **US-64, US-65, US-66, US-67, and US-68**, classify it as:

**✅ Fully satisfied**
**⚠️ Partially satisfied**
**❌ Missing**

Then identify:

1. Which requirements are already covered by B12 — Reports
2. Which report types are missing
3. Which filters are missing
4. Which UI states are missing
5. Which minimal changes should be made to B12

Only make changes that are necessary to satisfy the requirements.
