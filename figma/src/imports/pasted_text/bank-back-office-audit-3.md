I want you to perform a COMPLETE REQUIREMENTS AUDIT of this Bank Back Office prototype and then IMPLEMENT anything that is missing.

IMPORTANT:
Do NOT redesign the existing product unnecessarily.
Keep the current visual design, layout, typography, colors, components, navigation style, and overall UX wherever they already work.
Only add, modify, or improve screens, flows, states, permissions, and interactions that are required by the user stories below.

Your task has 3 phases:

PHASE 1 — AUDIT
First inspect the ENTIRE existing Bank Back Office prototype:
- all pages
- all navigation items
- all dashboards
- all tables
- all forms
- all modals
- all detail pages
- all filters
- all buttons/actions
- all authentication flows
- all user/role flows
- all payment flows
- all EPP flows
- all reconciliation flows
- all reporting flows
- all notifications
- all audit/security areas

Compare the current implementation against the requirements below.

Classify every requirement as:
1. DONE — clearly implemented
2. PARTIALLY DONE — some of the requirement exists but something is missing
3. MISSING — no implementation exists
4. BACKEND ONLY — cannot be proven through UI and should not be invented as a visual feature

PHASE 2 — IMPLEMENT
After auditing, implement all requirements that are MISSING or PARTIALLY DONE and can reasonably be represented in the Bank Back Office UI.

Do not merely create decorative screens.
Make the flows connected and usable.

Use realistic sample data.

==================================================
BANK BACK OFFICE REQUIREMENTS
==================================================

1. AUTHENTICATION & ACCESS

Bank Login:
- Login using username/email and password
- Invalid credentials must show an error state
- Successful authentication enters the Bank Back Office
- The employee's role is identified after authentication
- Access is controlled according to the employee's role
- Secure authenticated session should be represented appropriately

Bank roles:
- BANK_ADMIN
- BANK_OPERATIONS
- BANK_FINANCE

Role-based access:
- Each bank user has an assigned role
- Different roles have different permissions
- Unauthorized users cannot access restricted screens
- Unauthorized users cannot perform restricted actions
- Navigation/actions should reflect the user's permissions
- BANK_ADMIN should have administrative/user-management capabilities
- BANK_OPERATIONS should have operational capabilities
- BANK_FINANCE should have financial/reporting/reconciliation capabilities where appropriate

Do NOT create three completely different login pages.
Use one Bank Back Office login and show role-specific access after authentication.

Password Recovery:
- Forgot password
- Account verification
- New password creation
- Password requirements
- Success/error states
- Password is never displayed

Additional Bank Authentication / MFA:
- Additional authentication step when required
- OTP/verification code input
- Invalid/expired code states
- Resend code
- Remember device where appropriate
- Failed MFA must not allow access
- Successful MFA continues to Bank Back Office

==================================================
2. BANK USER MANAGEMENT
==================================================

Create/verify a Bank Users area.

Add Bank User:
- Only BANK_ADMIN can perform this
- Required user information
- Validation
- Assign BANK_ADMIN / BANK_OPERATIONS / BANK_FINANCE
- Prevent duplicate email/username
- Account status
- Successful creation
- User appears in Bank Users list

Edit Bank User:
- BANK_ADMIN only
- Select existing user
- View existing information
- Edit permitted information
- Change role where authorized
- Validation
- Prevent duplicate email/username
- Save changes
- Updated permissions reflect new role
- Historical activity is retained

Deactivate Bank User:
- BANK_ADMIN only
- Show current status
- Confirmation modal
- Change status to inactive/deactivated
- Deactivated user cannot access the Bank Back Office
- Historical activity remains
- Audit activity remains
- Support reactivation

==================================================
3. SCHOOL MANAGEMENT — BANK PORTAL
==================================================

The Bank Back Office manages participating schools.

School list:
- View all registered schools
- School name
- Identifying information
- Current status
- Search
- Filters
- Latest records

Register School:
- Authorized bank employee can register a school
- Required school information
- Validation
- Registration number
- Initial status
- Save registration
- Success/error states

Review School Registration:
- Open a school registration
- View submitted information
- Review status
- Approve
- Reject

Reject School:
- Rejection reason is REQUIRED
- Status becomes Rejected
- Reason is stored/displayed
- Confirmation/success state

Approve School:
- School becomes approved/active
- School becomes eligible for the network
- Success state

Manage School Status:
- View current status
- Activate eligible school
- Deactivate active school
- Confirmation before status change
- Show consequences/status clearly

School Details:
- Registration information
- Current status
- Relevant identifiers

School Integration Status:
- Show current integration/connection status

School Fee Submission Activity:
- Show previous submissions
- Submission status
- Submission information
- Associated school

School Settlement Information:
- Settlement records
- Settlement status
- Relevant settlement information

==================================================
4. PAYMENT & COLLECTION MONITORING
==================================================

Bank employee should be able to monitor/process customer payments.

Customer search:
- Search customer
- National ID search where appropriate
- Do not expose sensitive National ID unnecessarily

Outstanding fees:
- Fee type
- Amount
- Remaining balance
- Due date/status
- Select eligible fee(s)

Payment processing:
- Full payment
- Partial payment
- Payment amount input
- Validation against outstanding balance
- Prevent overpayment

Payment methods:
- CIB debit card
- CIB credit card
- Clearly distinguish the two

Payment states:
- Successful
- Failed
- Processing where appropriate
- Error states
- Failed payments must never appear successful

Successful payment:
- Transaction/reference ID
- Updated fee balance
- Payment status
- Receipt generation/viewing

Payment monitoring:
- Payment history
- Search/filter
- Date
- School
- Payment status
- Relevant transaction/reference information

Refund:
- Eligible transactions can be refunded
- Link refund to original transaction
- Refund confirmation
- Refund status
- Updated balance/status
- Prevent duplicate refund
- Show audit-related information
