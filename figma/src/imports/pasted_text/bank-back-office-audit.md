Audit the existing **Bank Back Office Portal** against **EPIC 7 — Payment & Collection Monitoring**.

IMPORTANT:
This is a **UI/UX design audit only**.

Do NOT redesign the existing portal from scratch.
Do NOT remove, rename, or restructure existing screens.
Do NOT change existing navigation or unrelated functionality.

Identify whether the current design already supports the following payment-related requirements. If something is missing, add only the minimum necessary UI/screens/states while preserving the existing design system and user flow.

### US-38 — View Payments

Check whether the bank portal provides an appropriate payment/transaction monitoring interface where authorized users can:

* View payments associated with a school
* See payment amount
* See payment date/time
* Identify the related fee
* Identify the related student/customer
* See payment status
* Open a payment/transaction for more details

The payment records should be presented in a professional, easy-to-scan table similar to an enterprise banking back-office system.

### US-39 — View Payment Status

Check whether payment statuses are clearly represented in the UI.

The design must support these statuses:

* Successful
* Pending
* Failed
* Refunded
* Reversed

Use clear status badges/indicators and make the statuses visually distinguishable while remaining consistent with the existing CIB-inspired design.

Check that the payment list and transaction details consistently display the current payment status.

### US-40 — View Partial Payment

Check whether the existing fee/payment interfaces visually support partial payments.

The design should allow the employee to understand:

* Original fee amount
* Amount already paid
* Remaining balance
* Partial-payment status
* Multiple payments against the same fee

Where appropriate, use a clear balance breakdown or progress indicator.

Do not imply that a partially paid fee is fully paid.

### US-41 — View Payment Details

Check whether the existing **Transaction Details** screen contains:

* Transaction ID
* School
* Student/customer
* Fee
* Amount
* Payment method
* Date/time
* Payment status
* Bank reference
* Settlement status

If these already exist, keep them.

If information is missing, add only the missing fields while preserving the current Transaction Details structure.

### US-42 — Search Customer by National ID

IMPORTANT: The revised project has **NO Parent Portal**.

The bank employee is the person interacting with the customer.

Check whether the Bank Back Office contains a bank-employee workflow that allows the employee to:

**Enter National ID → Search Customer → View Matching Customer → Continue to Fees**

The UI should include:

* National ID input
* Search action
* Loading/search state
* Customer found state
* No customer found state
* Invalid input/validation state
* Clear transition to the customer's eligible fees

The National ID should be treated as sensitive information.

Do not unnecessarily display the full National ID throughout the interface. Use masked/limited display where appropriate.

### US-43 — View Customer Fee Information

After a customer is found, check whether the design provides a customer fee view showing:

* Customer/student identification
* Outstanding fees
* Fee type
* Fee amount
* Amount already paid
* Remaining balance
* Fee due/status information
* Which fees are eligible for payment

The interface should make it immediately clear which fees can be settled.

### US-44 — Select Fees for Payment

Check whether the employee can select one or more eligible fees.

The UI should support:

* Fee selection controls
* Selected fee state
* Selected fee summary
* Selected amount/total
* Remaining balance
* Clear indication of which fees are selected
* Continue/Proceed to Payment action

The design should prevent confusion between eligible and ineligible fees.

### US-45 — Process Customer Payment

Check whether the design contains a clear payment-processing workflow:

**Customer Search → Customer Fees → Select Fees → Payment Review → Process Payment → Payment Result**

The payment review screen should clearly show:

* Customer
* Selected school/fee
* Selected fees
* Amount to pay
* Payment method
* Total amount
* Confirmation before processing

Also check for appropriate UI states:

* Processing/loading
* Successful payment
* Failed payment
* Payment error
* Retry where appropriate

A failed payment must be visually distinct from a successful payment.

### US-46 — Process Partial Payment

Check whether the payment workflow supports entering a partial payment amount.

The design should include:

* Payment amount input
* Remaining balance
* Validation
* Clear indication that the payment is partial
* Updated amount/remaining balance preview
* Error state if entered amount exceeds the remaining balance
* Continue/confirm action

The employee should be able to clearly understand:

**Fee Balance → Amount Being Paid → Remaining Balance**

### US-47 — Generate/View Payment Receipt

Check whether a successful payment leads to a receipt experience.

The design should include:

**Successful Payment → Receipt → View/Generate Receipt**

The receipt should visually contain, where appropriate:

* Transaction/reference ID
* Customer/student information
* School
* Fee information
* Paid amount
* Payment date/time
* Payment method
* Payment status

Also check that the receipt is only presented as a successful-payment receipt after a successful transaction.

### US-48 — Filter Network Payments

Check whether the existing **Transaction Monitoring** screen provides payment filters.

At minimum, check for:

* Transaction ID
* School
* Student/customer
* Date/date range
* Payment method
* Payment status

The filtered results should remain easy to scan, and each result should be clickable/openable into Transaction Details.

### US-49 — Process Refund

Check whether the existing Transaction Details/payment workflow provides an appropriate refund action for eligible transactions.

The UI should support:

* Refund action
* Clear indication that the transaction is refundable
* Refund confirmation
* Refund amount
* Refund reason where appropriate
* Confirmation/success state
* Refund status after completion
* Clear distinction between original payment and refund

Potentially destructive/irreversible actions such as refunds should use an appropriate confirmation step.

### PAYMENT WORKFLOW CHECK

Most importantly, verify that the revised Bank Back Office supports this complete employee workflow:

**Customer arrives at bank**

↓

**Bank Employee enters National ID**

↓

**Customer identified**

↓

**Authorized fee information displayed**

↓

**Employee selects fee(s)**

↓

**Payment amount confirmed**

↓

**Full or partial payment selected**

↓

**Payment processed**

↓

**Success / Failure result**

↓

**Receipt generated for successful payment**

Make sure this workflow exists within the **Bank Back Office Portal**.

Do NOT create or introduce a Parent Portal.

### DESIGN SYSTEM

Any missing UI should follow the existing CIB-inspired design:

* Corporate blue
* White and light-gray surfaces
* Professional banking/fintech aesthetic
* Clear typography
* Consistent cards, tables, forms, buttons, and status badges
* Accessible contrast
* Consistent spacing and hierarchy
* Production-ready enterprise banking UI

### FINAL AUDIT

For each US-38 through US-49, classify the current design as:

**✅ Fully satisfied**
**⚠️ Partially satisfied**
**❌ Missing**

Only evaluate requirements that can be represented through UI/UX.

Do not evaluate backend-only functionality such as actual payment authorization, database updates, refund processing, authorization enforcement, audit logging, or security implementation.

At the end, identify:

1. Which existing screens already satisfy the requirements
2. Which existing screens need minor UI additions
3. Which new UI screen/state is genuinely required
4. Any missing states in the customer payment workflow

Do not redesign unrelated parts of the portal.
