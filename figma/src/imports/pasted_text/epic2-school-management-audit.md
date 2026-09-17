Audit the existing **Bank Back Office Portal** against **EPIC 2 — School Management** and check whether the current UI/UX design satisfies the following requirements:

### US-06 — Register School

Check whether the design provides an authorized bank employee with a way to:

* Create/register a new school
* Enter all required school information
* See clear required-field and validation feedback
* Enter or display a school registration number
* See the initial registration status after submission
* Receive clear success/error feedback after registration

If any of these UI elements or states are missing, add only the necessary components while preserving the existing portal structure and user flow.

### US-07 — View Schools

Check whether the existing School Management screen allows bank employees to:

* View all registered schools
* See school name and relevant identifying information
* See the current school status
* Search for a school
* Filter schools where appropriate
* Clearly distinguish different statuses such as Pending, Approved, Active, Rejected, and Deactivated

Ensure the school list is easy to scan and appropriate for a professional banking back-office portal.

### US-08 — Review School Registration

Check whether a bank employee can:

* Open a school's registration/application
* View all submitted registration information
* Review the information before making a decision
* Clearly see the current registration status
* Access Approve and Reject actions

The review screen should have a clear information hierarchy and make the approval decision easy to understand.

### US-09 — Approve School

Check whether the design supports the approval workflow:

* Approve action
* Confirmation state/dialog where appropriate
* Clear success feedback
* Updated school status after approval
* Clear indication that the school is now approved/active

### US-10 — Reject School

Check whether the design supports:

* Reject action
* Required rejection reason
* Rejection reason input
* Validation if the reason is empty
* Confirmation/review before rejection where appropriate
* Clear rejection success feedback
* Updated school status showing Rejected
* Ability for authorized employees to continue viewing the rejected registration

### US-11 — Manage School Status

Check whether the School Management and School Details interfaces support:

* Viewing the current school status
* Activate action
* Deactivate action
* Appropriate confirmation for status-changing actions
* Clear success feedback
* Clear updated status after the action
* Different visual states for Active and Deactivated schools

Make potentially destructive actions such as Deactivate visually clear without introducing unnecessary redesign.

### US-12 — View School Details

Check whether the existing School Details screen clearly displays:

* School registration information
* School status
* Relevant school identifiers
* Other important school information already defined in the portal

Ensure the information is organized clearly and can be scanned easily by a bank employee.

### US-13 — View School Integration Status

Check whether School Details provides a clearly visible integration-status section.

The design should allow employees to understand whether the school's integration is:

* Connected/Active
* Pending
* Failed/Error
* Disconnected, if applicable

Use clear status indicators while maintaining the existing CIB-inspired visual language.

### US-14 — View School Fee Submission Activity

Check whether the School Details experience includes a clear way to view the school's fee submission activity.

Verify that the design supports:

* Previous fee submissions
* Submission date/time where appropriate
* Submission status
* Relevant submission information
* Clear association between each submission and the selected school

If the existing School Details page already contains Fee Submissions, improve only the missing UI elements or states.

### US-15 — View School Settlement Information

Check whether the School Details experience provides access to school settlement information.

Verify that the design supports:

* Settlement records
* Settlement status
* Relevant settlement information
* Clear association between settlement records and the selected school
* Easy navigation between settlement information and related transaction/reconciliation information

### DESIGN AUDIT RULES

Do NOT redesign the portal from scratch.

Do NOT change:

* Existing navigation structure
* Existing pages
* Existing user flows
* Existing functionality
* Existing information architecture
* Existing component structure unless a missing requirement requires a new component

Only identify and add the UI/UX elements necessary to satisfy EPIC 2.

Maintain the existing CIB-inspired banking visual identity:

* Corporate blue
* White and light-gray surfaces
* Professional banking/fintech aesthetic
* Clear status indicators
* Consistent buttons, forms, tables, cards, spacing, and typography
* Accessible contrast
* Production-ready enterprise UI

### FINAL AUDIT

For each requirement, determine:

**✅ Fully satisfied** — The existing design already covers the requirement.

**⚠️ Partially satisfied** — Some UI exists but important elements/states are missing.

**❌ Missing** — The required UI/UX does not exist.

Do not evaluate backend-only requirements such as database storage, audit-log implementation, authorization enforcement, or whether records are actually updated. Only evaluate what can be represented and verified through the UI/UX design.

At the end, provide:

1. **Missing UI/UX elements**
2. **Existing screens that already satisfy requirements**
3. **Specific screens/components that need modification**
4. **New screens/components that are genuinely necessary**

Only make changes where a requirement is actually missing.
