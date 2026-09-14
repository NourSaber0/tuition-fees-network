## Authentication & Access-Control Ethics/Requirements Audit

Audit the **existing Bank Back Office Portal** against the following requirements:

* **US-02 — Bank Login**
* **US-03 — Role-Based Access**
* **US-04 — Password Recovery**
* **US-05 — Secure Bank Authentication**

IMPORTANT:
Do NOT redesign the existing portal from scratch.
Do NOT remove, rename, restructure, or replace existing screens, features, components, navigation, or user flows.

The purpose of this task is to **audit the existing design and identify missing UI/UX states required to satisfy these user stories and acceptance criteria**.

Maintain the existing CIB visual identity and design system:

* CIB-inspired corporate blue palette
* White and light-gray surfaces
* Professional banking/fintech aesthetic
* Clear typography
* Accessible contrast
* Consistent buttons, forms, cards, alerts, and spacing
* Production-ready enterprise banking interface

### 1. US-02 — Bank Login

Verify whether the current login experience visually supports:

* Username/email input
* Password input
* Login action
* Credential validation
* Successful login state
* Invalid credential/error state
* Role identification after authentication
* Role-based access after login
* Secure-session concept where it can be represented through the UI

Check whether the design includes appropriate states such as:

**Login → Valid Credentials → Authentication → Bank Back Office**

and

**Login → Invalid Credentials → Error Message → Remain on Login**

If missing, add only the minimum necessary UI states.

Check whether the login page has appropriate:

* Field validation
* Required-field messages
* Invalid credential feedback
* Loading/authentication state
* Disabled/loading login button
* Security-conscious error messaging
* Clear MFA/OTP transition if MFA is required

Do NOT expose sensitive information through error messages.

---

### 2. US-03 — Role-Based Access

Audit whether the current portal visually demonstrates role-based access.

Check for:

* User role identification
* Role information in the user profile/account area
* Different permissions based on role
* Restricted navigation items where appropriate
* Disabled or hidden unauthorized actions
* Unauthorized-access state
* Access-denied page/message
* School users being limited to their own school's data
* Bank users receiving access appropriate to their responsibilities
* Permission-aware actions such as Approve, Reject, Activate, Deactivate, Refund, Resolve, Manage Users, and Settings

Verify that restricted screens have an appropriate UI state such as:

**User attempts restricted function → Access Denied → Explanation → Return/Back**

If the design currently assumes every bank employee has access to everything, identify this as a gap.

If missing, add only the necessary role/permission UI.

IMPORTANT:
Clearly distinguish between what Figma can visually represent and what must be implemented in the backend.

The following cannot be proven by visual design alone:

* Unauthorized API requests being rejected
* Backend permission enforcement
* Consistent frontend/backend permission checks
* Actual session authorization
* Actual data isolation/security

Mark these as **Backend Verification Required**, rather than pretending the Figma design satisfies them.

---

### 3. US-04 — Password Recovery

Audit whether the existing authentication flow includes a complete password recovery journey.

Check for:

**Forgot Password → Account Verification → Reset Password → Password Requirements → Success → Login**

The design should support:

* Forgot Password entry point
* Account/email/username input
* Account verification step
* Verification/error state
* Password reset form
* New password field
* Confirm password field
* Password requirements
* Password strength/requirement feedback
* Invalid/weak password state
* Password mismatch state
* Successful password reset confirmation
* Return to login
* Ability to log in with the new password

Check that the UI never displays or reveals the user's existing password.

If any of these states are missing, add only the missing states without changing the existing authentication structure.

---

### 4. US-05 — Secure Bank Authentication / MFA

Audit whether the existing Bank Login flow visually supports an additional authentication step for designated bank employees.

Check for:

**Username + Password → Additional Authentication → Verification → Bank Back Office**

The design should support:

* MFA/OTP screen if MFA is part of the existing requirements
* OTP input
* Clear authentication instructions
* Verification/loading state
* Invalid OTP state
* Expired OTP state
* Retry/resend mechanism where appropriate
* Authentication failure state
* Successful verification state
* Protected-access transition
* Avoidance of displaying sensitive credentials

The design should make it clear that the employee **cannot access protected Bank Back Office functions until the additional authentication step is successfully completed**.

Also check whether the design provides an appropriate place/state for an authentication event to be audited, such as an audit-log entry after successful or failed authentication.

Do NOT invent unnecessary authentication methods if the existing product requirements specify OTP/MFA.

---

## AUDIT CLASSIFICATION

For EVERY acceptance criterion, classify it as exactly one of:

### ✅ Fully represented

The existing UI clearly supports the requirement.

### ⚠️ Partially represented

The design supports part of the requirement but is missing an important state, screen, or interaction.

### ❌ Missing

The required UI/UX element does not exist.

### 🔒 Backend/Security verification required

The requirement cannot be proven through Figma alone and must be implemented/tested in the backend.

---

## IMPORTANT SECURITY CHECK

Review the authentication UX for potential security problems.

Flag if the design:

* Reveals whether an account exists unnecessarily
* Displays passwords
* Exposes OTPs
* Uses overly detailed authentication error messages
* Allows access before authentication is completed
* Gives unauthorized users access to restricted actions
* Shows sensitive information to users without permission

Recommend only the minimum UI changes necessary to address these issues.

---

## FINAL OUTPUT

After auditing the existing design, provide a table with:

| Requirement | Status | Existing Screen/Component | Missing Element | Figma Change Needed? | Backend Verification? |
| ----------- | ------ | ------------------------- | --------------- | -------------------- | --------------------- |

Then provide:

### Missing UI/UX

List only the authentication/access-control elements that are genuinely missing.

### Backend/Security Requirements

List acceptance criteria that Figma cannot verify and that must be implemented/tested technically.

### Required Figma Changes

If anything is missing, make only those minimal changes.

DO NOT redesign the portal.

The final result must remain:
**Same portal + same structure + same functionality + same user flow + CIB visual identity + complete authentication/access-control UX coverage.**
