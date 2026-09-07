Bank Back-Office Portal - REST API Contract

Project: CIB Tuition & Services Fees Collection Network Client: Bank Back-Office Portal (React / Vite / Tailwind - Figma Make)
Document: P0-02 API contract, derived screen-by-screen from the front-end + intern-session brief Status: Draft for sign-off - the
contract that unblocks all three tracks (Backend / Frontend / Mobile)



Global Conventions

  Item                                                                       Rule


  Base URL                                                                   /api/v1


  Auth                                                                       Authorization: Bearer <accessToken> on every call except
                                                                             Phase 1 login / reset


  Money                                                                      integer EGP in *EGP fields (e.g. amountEGP). Add *Minor
                                                                             (piastres) later if required


  Timestamps                                                                 ISO-8601 UTC in payloads; display timezone is Africa/Cairo
                                                                             (EET)


  Pagination                                                                 ?page=1&pageSize=25 -> { data:[...], page, pageSize,
                                                                             total, totalPages }


  List filters                                                               ?search= plus resource-specific params; ?sort=field:asc


  Error shape                                                                { "error": { "code": "snake_case", "message": "...",
                                                                             "details": {} } }


  Audit                                                                      every mutating endpoint writes a Phase 9 audit entry:
                                                                             actorUserId, actorRole, action, entityType,
                                                                             entityId, before, after, ip, requestId


  Adapter                                                                    national-ID verification, card authorisation and EPP schedule creation
                                                                             sit behind the mock adapter layer; the contract is identical when the
                                                                             real bank systems are wired


Deck module map: identity -> P1 / P10 billing -> P3 / P4 search -> P4 payments -> P4 epp -> P6 receipts -> P4 notifications ->
P8 reporting -> P7 institution-integration -> P3
Back-office roles: bank-admin, bank-operations, bank-finance, bank-reconciliation




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 1 of 17
PHASE 1 - Authentication, Session & Permissions

Source: Login.tsx, Portal.tsx. Sign-in flow: credentials -> MFA (6-digit OTP, 60s expiry, resend) -> authenticated with a role. Plus
forgot -> email link -> reset (password policy) -> success.


1.1 POST /auth/login
Step 1. Validates username / email + password. Does not issue a session - triggers the OTP challenge.
Request:

  { "username": "admin", "password": "CIB@2026", "rememberMe": false }

username accepts a username or an email (you@cibeg.com).

Response 200 - credentials OK, MFA challenge issued:

  {
   "mfaRequired": true,
   "mfaToken": "mfa_5f3c...",
   "otpChannel": "sms",
   "otpDestinationHint": "**** 4821",
   "expiresInSeconds": 60,
   "resendAvailableInSeconds": 60
  }

Errors:

  Status                                           code                                                 When


  400                                              missing_credentials                                  username or password empty


  401                                              invalid_credentials                                  bad username / password (generic
                                                                                                        message)


  403                                              account_locked                                       too many attempts / disabled by admin


  429                                              rate_limited                                         brute-force throttle; include Retry-After



1.2 POST /auth/mfa/verify
Step 2. Exchanges the OTP for a real session. On success the UI calls onAuthenticated(role).
Request:

  { "mfaToken": "mfa_5f3c...", "code": "123456" }

Response 200:

  {
   "accessToken": "eyJ...",
   "refreshToken": "rt_9a...",
   "tokenType": "Bearer",
   "expiresIn": 900,
   "user": {
     "id": "USR-001",
     "name": "Mohamed Ali",
     "initials": "MA",
     "email": "mohamed.ali@cibeg.com",
     "role": "bank-admin",
     "permissions": ["dashboard","schools","transactions","reconciliation",
                      "epp","reports","notifications","audit-logs","users","settings"],
     "mustChangePassword": false,
     "lastLoginAt": "2026-09-06T14:28:00Z"
   }
  }

role is one of bank-admin | bank-operations | bank-finance | bank-reconciliation. permissions is the server-authoritative
screen allow-list (the front-end currently hardcodes this in rolePermissions - it must move server-side).


                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 2 of 17
Errors:

  Status                                            code                                                 When


  400                                               invalid_code_format                                  not 6 digits


  401                                               invalid_code                                         wrong OTP

  410                                               code_expired                                         60s elapsed -> UI shows "expired, request a
                                                                                                         new code"


  401                                               mfa_token_invalid                                    mfaToken unknown / consumed


  429                                               too_many_attempts                                    lock the challenge



1.3 POST /auth/mfa/resend
UI: "Resend OTP" after the countdown reaches 0.
Request: { "mfaToken": "mfa_5f3c..." } Response 200: { "expiresInSeconds": 60, "resendAvailableInSeconds": 60 }
Errors: 401 mfa_token_invalid, 429 resend_throttled (+ Retry-After).


1.4 POST /auth/forgot-password
UI: enter email, always show success ("check your email").
Request: { "email": "you@cibeg.com" } Response 200: { "message": "If the account exists, a reset link has been
sent." } (identical for unknown emails - no user enumeration). Errors: 400 invalid_email, 429 rate_limited.


1.5 POST /auth/reset-password
UI: new-password form reached from the emailed link. Enforces the policy shown in PASS_REQS.
Request:

  { "token": "prt_1b2c...", "newPassword": "NewP@ssw0rd" }

Password policy (from UI): at least 8 chars, at least 1 uppercase, at least 1 lowercase, at least 1 digit, at least 1 special character. The
UI currently allows submit at "3 of 5 satisfied" - recommend the server require all 5 (open question 2).
Response 200: { "message": "Password updated. Please sign in." }
Errors:

  Status                                            code                                                 When


  400                                               weak_password                                        fails policy; return unmet:
                                                                                                         ["uppercase","special"]


  400                                               password_reused                                      matches one of the last N passwords


  401                                               reset_token_invalid                                  unknown token


  410                                               reset_token_expired                                  link expired



1.6 POST /auth/refresh
Silent renewal before expiresIn lapses.
Request: { "refreshToken": "rt_9a..." } Response 200: same shape as 1.2 (new accessToken, rotated refreshToken). Errors:
401 refresh_token_invalid / refresh_token_expired -> force re-login.


1.7 POST /auth/logout
UI: sidebar sign-out button.
Request: header auth; body { "refreshToken": "rt_9a..." } (to revoke it). Response 204.



                           CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 3 of 17
1.8 GET /auth/me
Called on app load / refresh to rehydrate the role and gate navigation.
Response 200: the user object from 1.2. Errors: 401 unauthenticated -> redirect to Login.


1.9 Role -> permission matrix (server must own this)

  Screen                               admin                        operations                        finance                 reconciliation


  dashboard                              Y                               Y                               Y                          Y


  schools                                Y                               Y                               -                          -


  transactions                           Y                               Y                               Y                          -


  reconciliation                         Y                               Y                               Y                          Y


  epp                                    Y                               Y                               Y                          -


  reports                                Y                                -                              Y                          -

  notifications                          Y                               Y                               Y                          Y


  audit-logs                             Y                                -                              -                          -


  users                                  Y                                -                              -                          -


  settings                               Y                                -                              -                          -


Any request to an endpoint backing a disallowed screen -> 403 forbidden (UI renders its "Access Restricted" state).




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 4 of 17
PHASE 2 - Dashboard

Source: Dashboard.tsx. Read-only, all four roles.


2.1 GET /dashboard/summary

  {
   "asOf": "2026-08-31T14:32:00Z",
   "kpis": {
     "activeInstitutions":    { "value": 61, "schools": 35, "universities": 26, "trendPct": 6.3 },
     "totalStudents":         { "value": 42816, "trendPct": 4.1 },
     "todayTransactions":     { "value": 1284, "trendPct": 11.2 },
     "todayCollectionEGP":    { "value": 2418600, "prevDayEGP": 2100000, "trendPct": 15.2 },
     "successfulPayments":    { "value": 1201, "ratePct": 93.5, "trendPct": 0.8 },
     "failedPayments":        { "value": 48, "ratePct": 3.7, "trendPct": -2.1 },
     "pendingPayments":       { "value": 35, "trendPct": -8.4 },
     "pendingReconciliation": { "value": 3 },
     "activeEppPlans":        { "value": 312, "outstandingEGP": 7200000, "trendPct": 9.5 }
   }
  }



2.2 GET /dashboard/collections/weekly?weekOf=2026-08-25

  {
   "currency": "EGP", "from": "2026-08-25", "to": "2026-08-31",
   "series": [ { "date": "2026-08-25", "label": "Mon", "amountEGP": 1840000 } ],
   "weekTotalEGP": 13228600, "dailyAvgEGP": 1889800
  }

series holds 7 entries (Mon..Sun).


2.3 GET /dashboard/institution-status

  {
   "schools": 35, "universities": 26,
   "breakdown": [
     { "label": "Active & Integrated",    "count": 51, "pct": 85 },
     { "label": "Active, Not Integrated", "count": 10, "pct": 16 },
     { "label": "Pending Approval",       "count": 5, "pct": 8 },
     { "label": "Suspended",              "count": 2, "pct": 3 }
   ]
  }



2.4 GET /dashboard/recent-transactions?limit=6
Returns { "data": [ TransactionSummary ] } - item shape equals Phase 4.1.




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 5 of 17
PHASE 3 - Institution Management

Source: Schools.tsx. Roles: bank-admin, bank-operations.
Institution: id, name, city, institutionType (School|University), subType, principal, phone, email, regStatus
(Approved|Pending|Under Review|Rejected), regNumber, students, feesSubmittedEGP, collectionsEGP, integStatus
(Integrated|Pending|Failed|Not Integrated), accountStatus (Active|Inactive|Suspended), registrationDate


3.1 GET /institutions
Query: search (name / id / city), type, status (matches regStatus OR accountStatus), page, pageSize. Returns a paginated list
including the count columns.


3.2 POST /institutions - register (US-06)

  { "institutionType": "School", "name": "...", "regNumber": "MOEDU-SCH-2026-0831",
   "city": "Cairo", "subType": "International", "principal": "...", "phone": "+20 2 ...",
   "email": "finance@x.edu.eg", "students": 500 }

Response 201:

  { "id": "SCH-013", "regStatus": "Pending", "accountStatus": "Inactive",
   "integStatus": "Not Integrated", "registrationDate": "2026-09-01" }

Errors: 400 validation_failed (details maps field -> reason), 409 duplicate_reg_number.


3.3 GET /institutions/{id}
Detail header plus the information fields block.


3.4 GET /institutions/{id}/students?page=
Items: { id, name, gradeOrFaculty, guardian, balanceEGP, status (Paid|Partial|Unpaid) }


3.5 GET /institutions/{id}/integration (US-13)
Payload varies by integStatus:
   • Integrated: apiEndpoint, protocol, lastSuccessfulSync, syncFrequency, integrationDate, connectionKeyMasked,
   recentSyncEvents: [ { time, event, detail } ]
   • Pending: setupChecklist: [ { label, done } ], estGoLive
   • Failed: lastAttempt, errorCode, lastSuccessfulConnection, consecutiveFailures, supportRef
   • Not Integrated: steps: [ string ], estSetupTime


3.6 GET /institutions/{id}/fee-submissions (US-14)

  { "summary": { "totalSubmissions": 4, "totalAmountEGP": 15300000, "lastSubmissionDate": "2026-08-28" },
   "data": [ { "id": "FS-SCH-001-001", "term": "Term 1 2026/27", "date": "2026-08-28T09:15:00Z",
               "students": 238, "amountEGP": 4284000, "status": "Processed" } ] }

status is one of Processed | Pending | Rejected.


3.7 GET /institutions/{id}/settlements (US-15)

  { "summary": { "totalSettledEGP": 14892000, "recordCount": 4, "lastSettlementDate": "2026-08-30" },
   "data": [ { "id": "SET-SCH-001-001", "date": "2026-08-30", "grossEGP": 4169760,
               "cibFeeEGP": 83395, "netEGP": 4086365, "status": "Completed",
               "txRef": "TX-20260830-0001", "reconRef": "RECON-20260830" } ] }

cibFeeEGP = 2% of gross. status is one of Completed | Processing | Pending | Failed.


3.8 Approval workflow (US-08 / US-09 / US-10)


                           CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 6 of 17
  Method                             Path                                    Body                                    Result


  GET                                /institutions/{id}/application
                                                                 -                                                   fields +
                                                                                                                     requiredDocuments: [ {
                                                                                                                     name, verified } ],
                                                                                                                     reviewedBy, pendingSince


  POST                               /institutions/{id}/approve              -                                       { regStatus:
                                                                                                                     "Approved",
                                                                                                                     accountStatus: "Active"
                                                                                                                     } 409
                                                                                                                     not_in_reviewable_state


  POST                               /institutions/{id}/reject               { "reason": "Incomplete                 { regStatus: "Rejected"
                                                                             documentation",                         } 400 reason_required
                                                                             "notes": "..." }


If Settings requireDualApproval = true, /approve must be called by two distinct admin / ops users; the first call returns {
"status": "awaiting_second_approval" }.


3.9 Account status (US-11)
   • POST /institutions/{id}/activate -> { accountStatus: "Active" } 409 if regStatus != Approved
   • POST /institutions/{id}/deactivate -> { accountStatus: "Inactive" } (suspends fee collection)


3.10 Dues ingestion (deck slide 18 + Settings)
POST /institutions/{id}/fee-submissions - multipart/form-data (file) or JSON { "rows": [...] }. Each row: nationalId,
feeType, amount, currency, collectionPeriod.

Response 202:

  { "submissionId": "FS-SCH-001-005", "accepted": 380, "rejected": 2,
   "errors": [ { "row": 14, "field": "nationalId", "code": "duplicate_in_batch" },
               { "row": 51, "code": "amount_le_zero" } ] }

Reject-the-row rules (never reject the whole file): duplicate_in_batch, amount_le_zero, unknown_fee_type,
collection_period_in_past. Format must be in Settings allowedUploadFormats; row count must be within maxStudentsPerUpload.

GET /institutions/{id}/fee-submissions/{submissionId} -> status plus the full error report.




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 7 of 17
PHASE 4 - Transactions & Payment Workflow

Source: Transactions.tsx. View: admin, operations, finance. Process Payment: admin, operations.
Transaction: id, institution, institutionType, student, feeType, amountEGP, method (CIB Debit Card|CIB Credit
Card|Cash|EPP|Bank Transfer), status (Successful|Pending|Failed|Refunded|Reversed), bankRef, settlementStatus
(Settled|Pending|Not Settled|Reversed), reconStatus (Matched|Exception|Pending|Not Required), timestamp,
partial?: { originalAmountEGP, previouslyPaidEGP }, idempotencyKey, channel


4.1 GET /transactions
Query: status, search (id / student), institution, institutionType, method, dateFrom, dateTo, page, pageSize.


4.2 GET /transactions/tab-counts

  { "All": 1284, "Successful": 1201, "Pending": 35, "Failed": 48, "Refunded": 8, "Reversed": 4 }



4.3 GET /transactions/{id}
Full record plus timeline: [ { label, time, done } ] (Initiated -> Bank Authorisation -> Captured -> Settlement -> Reconciliation),
the partial-payment breakdown, idempotencyKey, channel.


4.4 GET /transactions/export?&format=csv
Returns a file, or { "jobId": "..." } for async export.


4.5 GET /customers/fees?nationalId=29901011234567 (US-42 / US-43)

  { "customer": { "name": "Ahmed Hassan", "nationalIdMasked": "299*******4567",
     "institution": "Cairo International School", "institutionType": "School", "grade": "Grade 10" },
   "fees": [ { "id": "FEE-AH-001", "name": "Tuition - Term 1 2026/27",
       "originalAmountEGP": 18000, "paidEGP": 5000, "remainingEGP": 13000,
       "status": "Partial", "eligible": true } ] }

Errors: 400 invalid_national_id (must be exactly 14 digits), 404 customer_not_found, 403 not_authorized_for_customer. Every
lookup is audited (deck slide 19).


4.6 POST /payments (US-45 / US-46)
Header Idempotency-Key: <uuid> is required.

  { "nationalId": "29901011234567",
   "feeIds": ["FEE-AH-001", "FEE-AH-002"],
   "amountEGP": 13000,
   "method": "CIB Credit Card",
   "creditPaymentType": "epp",
   "eppTenor": 12,
   "cardToken": "tok_...",
   "processedBy": "USR-001" }

creditPaymentType is full or epp (credit card only). eppTenor required when creditPaymentType = epp. cardToken for card
methods only.
Response 201:

  { "transactionId": "TX-20260903-0007", "status": "Successful",
   "amountPaidEGP": 13000, "isPartial": true, "remainingBalanceEGP": 0,
   "method": "CIB Credit Card", "bankRef": "BNK-CIB-84740", "authCode": "A12345",
   "receiptRef": "RCP-20260903-0007",
   "epp": { "planId": "EPP-2026-011", "tenor": 12, "monthlyEGP": 1150 } }

Errors:




                         CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 8 of 17
 Status                                                                   code


 400                                                                      amount_exceeds_balance, amount_le_zero,
                                                                          epp_tenor_required, no_fees_selected


 402                                                                      authorization_declined (ERR-4012), insufficient_funds
                                                                          (code 51)


 409                                                                      idempotency_key_reused -> returns the original transaction, no
                                                                          second charge


 409                                                                      fee_changed -> a selected fee changed mid-payment (deck negative
                                                                          case)


 422                                                                      debit_card_not_eligible_for_epp,
                                                                          non_cib_card_not_eligible


 502 / 503                                                                gateway_unavailable (retryable - fail loud, never a silent success)



4.7 Related
  • GET /payments/{id} -> same as 4.3
  • POST /payments/{id}/retry (header: new Idempotency-Key) - after a failed attempt
  • GET /payments/{id}/receipt?format=pdf (US-47)
  • POST /transactions/{id}/refund POST /transactions/{id}/reverse - admin / finance only, body { "reason": "..."
  } (deferred, but implied by the Refunded / Reversed statuses)




                       CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 9 of 17
PHASE 5 - Reconciliation

Source: Reconciliation.tsx. All four roles view; assign / investigate / resolve available to all four (it is the reconciliation role's
whole job).
ReconRow: id, institution, institutionType, date, txCount, bankAmountEGP, systemAmountEGP, schoolAmountEGP,
status (Matched|Exception|Pending)

Exception: id, reconRowId, txRef, institution, institutionType, bankAmountEGP, systemAmountEGP, schoolAmountEGP,
differenceEGP, type, date, status (Open|Under Investigation|Resolved|Escalated), assignedTo, priority
(High|Medium|Low), txStatus, payMethod, bankRef, bankStatus, settlementDate, feeRef, collectionDate


  Method                                           Path                                                 Notes


  GET                                              /reconciliation/summary                              { totalTransactions: 1284,
                                                                                                        matched: 1278, pending: 3,
                                                                                                        exceptions: 3 }


  GET                                              /reconciliation/runs?date=&institution=&status=&page=
                                                                                         ReconRow list (the "Summary" tab)


  GET                                              /reconciliation/runs/{id}                            one row plus its transactions


  POST                                             /reconciliation/runs                                 { date, institutionId? } -> 202 job
                                                                                                        (deck: auto every 6h + daily)


  GET                                              /reconciliation/exceptions?status=&priority=&assignedTo=&includeResolved=false&page
                                                                                         Exception list


  GET                                              /reconciliation/exceptions/{id}                      detail + 3-way comparison rows +
                                                                                                        workflow: [ { step, done, desc }
                                                                                                        ] + sla: { percent, timeLeft }


  PATCH                                            /reconciliation/exceptions/{id}                      save resolution (see below)


  POST                                             /reconciliation/exceptions/{id}/assign{ assignedTo }


  GET                                              /reconciliation/assignees                            dropdown list (=
                                                                                                        /users?permission=investigate-exceptions)


  GET                                              /reconciliation/export?format=                       file


PATCH body (US-59 / US-60):

  { "status": "Resolved", "assignedTo": "Rania Mostafa",
   "reason": "Duplicate entry by institution",
   "resolutionAction": "Reverse duplicate transaction",
   "supportingReference": "BANK-ADV-8842", "notes": "..." }

Response 200: the updated Exception. Error: 400 resolution_action_required when status = Resolved with no action.




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 10 of 17
PHASE 6 - EPP Plans

Source: EPP.tsx. View: admin, operations, finance. Create: admin, operations.
EPPPlan: id, payRef, institution, institutionType, student, principalEGP, tenor (3|6|12|18), interestRatePct,
interestEGP, adminFeeEGP, totalEGP, monthlyEGP, paidInstallments, status (Active|Completed|Defaulted|Cancelled),
startDate


  Method                                         Path                                                 Notes


  GET                                            /epp/plans?search=&status=&tenor=&page=
                                                                                       search matches id / student / payRef


  GET                                            /epp/summary                                         { active, completed, defaulted,
                                                                                                      totalOutstandingEGP }


  GET                                            /epp/plans/{id}                                      plan plus derived progress


  GET                                            /epp/plans/{id}/schedule                             [ { number, dueDate,
                                                                                                      principalEGP, interestEGP,
                                                                                                      amountEGP, paidAmountEGP, status
                                                                                                      (Paid\|Due\|Upcoming) } ]


  POST                                           /epp/quote                                           pricing preview (see below)


  POST                                           /epp/cards/validate                                  { cardNumber } -> { result:
                                                                                                      "valid-credit"\|"rejected-debit"\|"rejected-no
                                                                                                      eligible }


  POST                                           /epp/plans                                           create (see below)


  PATCH                                          /epp/plans/{id}                                      { status, reason } - admin / ops


POST /epp/quote:

  // request
  { "principalEGP": 24000, "tenor": 12 }
  // response
  { "principalEGP": 24000, "tenor": 12, "interestRatePct": 14,
   "interestEGP": 3360, "adminFeeEGP": 240, "totalEGP": 27600, "monthlyEGP": 2300 }

Formula (deck slide 13): interest = principal * ratePct/100 * tenor/12; adminFee = principal * adminFeeRatePct (capped,
from Settings); total = principal + interest + adminFee; monthly = total / tenor. Default rates: 3 -> 10, 6 -> 12, 12 -> 14, 18
-> 16 (Settings-driven).
POST /epp/plans:

  { "cardToken": "tok_...", "studentName": "...", "nationalId": "...",
   "institution": "...", "feeDescription": "Tuition - Term 1 2026/27",
   "principalEGP": 24000, "tenor": 12, "sourcePaymentId": "TX-20260903-0007" }

Response 201: full EPPPlan (status: "Active", plus firstPaymentDate). Errors: 422 card_not_eligible, 400
principal_out_of_range (Settings min / max), 409 max_plans_per_student_exceeded, 409 source_payment_not_successful
(deck: the plan is created from a payment that already succeeded).




                        CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 11 of 17
PHASE 7 - Reports

Source: Reports.tsx. Roles: admin, finance.


7.1 GET /reports/catalogue

  [ { "id": "daily-collections", "title": "Daily Collections", "description": "...",
     "category": "Daily", "formats": ["PDF","XLSX","CSV"],
     "singleDate": true, "contextFilters": ["feeType","paymentStatus"],
     "lastGeneratedAt": "2026-08-31T08:00:00Z" } ]

IDs: network-collections, collections-by-institution, daily-collections, payments, failed-transactions,
epp-report, reconciliation, outstanding-balances, daily-report, collections-by-type.


7.2 POST /reports/generate

  { "reportId": "payments",
   "dateFrom": "2026-08-01", "dateTo": "2026-08-31",
   "format": "XLSX",
   "filters": { "institutionId": null, "feeType": "Tuition", "paymentStatus": "Successful",
                "paymentMethod": "Card", "eppTenor": null, "eppStatus": null, "reconStatus": null } }

For singleDate reports send "date": "2026-08-31" instead of the range. Response 202: { "jobId": "RPT-JOB-123", "status":
"processing" } Errors: 400 date_from_after_date_to, 400 unsupported_format_for_report.


7.3 GET /reports/jobs/{jobId}

  { "status": "ready", "filename": "report_2026-08-01_2026-08-31.xlsx",
   "downloadUrl": "/api/v1/reports/jobs/RPT-JOB-123/download",
   "preview": { "columns": [ { "key": "txId", "label": "Transaction ID", "align": "left" } ],
                "rows": [ { "txId": "TX-...", "amount": "18,000" } ],
                "total": "1,284 total transactions in range",
                "note": "Payments - 2026-08-01 to 2026-08-31" } }



7.4 GET /reports/jobs/{jobId}/download
File stream.


7.5 GET /reports/history?reportId=&page=
Prior runs (feeds the "Last generated" label). Server cron auto-generates daily-report and daily-collections end-of-day,
delivered per Settings.




                        CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 12 of 17
PHASE 8 - Notifications

Source: Notifications.tsx. All four roles (scoped to what the role can act on).
Notification: id, type (failed_payment|recon_exception|institution_issue|new_institution|system_alert), title,
body, meta?, severity (high|medium|low), read, createdAt, action?: { label, screen, entityId }


  Method                                           Path                                                 Notes


  GET                                              /notifications?type=&unread=&page=                   list plus { unreadCount }


  GET                                              /notifications/unread-count                          { count: 5 } - header bell badge

  POST                                             /notifications/{id}/read                             200


  POST                                             /notifications/read-all                              200


  DELETE                                           /notifications/{id}                                  204 (dismiss)


  GET                                              /notifications/stream                                SSE / WebSocket live push (optional)


Notifications are server-generated only - there is no create endpoint from this client. Event and channel toggles live in Settings (Phase
11.4).




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 13 of 17
PHASE 9 - Audit Logs

Source: AuditLogs.tsx. Role: admin only. Read-only, tamper-evident, 7-year retention.
AuditEntry: id, user, role, action, entity, entityId, prevValue, newValue, timestamp, ipAddress, severity
(info|warning|critical)


  Method                                           Path                                                 Notes


  GET                                              /audit-logs?search=&role=&severity=&dateFrom=&dateTo=&page=&pageSize=
                                                                                         search matches user / action / entity /
                                                                                         entityId


  GET                                              /audit-logs/{id}                                     expanded detail (entryId, entityId, full
                                                                                                        timestamp + TZ, session IP)


  GET                                              /audit-logs/stats                                    { critical: 5, warning: 6, info:
                                                                                                        4, total: 15 }


  GET                                              /audit-logs/roles                                    distinct role values for the filter dropdown


  GET                                              /audit-logs/export?<filters>&format=csv
                                                                                         file


No write endpoints - entries are emitted internally by every other mutating endpoint.




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 14 of 17
PHASE 10 - Users & Roles

Source: Users.tsx. Screen and all mutations: admin only.
BankUser: id, name, username, email, role (Bank Admin|Operations|Finance|Reconciliation), status
(Active|Inactive), lastLogin, department, createdAt


  Method                                          Path                                                 Notes


  GET                                             /users?search=&role=&status=&page=                   search matches name / username / email /
                                                                                                       role


  GET                                             /users/summary                                       active count per role: { "Bank Admin":
                                                                                                       1, "Operations": 4, "Finance": 3,
                                                                                                       "Reconciliation": 1 }


  POST                                            /users                                               { name, email, username?, role,
                                                                                                       department } -> 201. Errors 409
                                                                                                       email_exists, 409 username_taken,
                                                                                                       400 invalid_email. Username
                                                                                                       auto-generated when blank


  GET                                             /users/{id}                                          detail


  PATCH                                           /users/{id}                                          { name?, email?, username?,
                                                                                                       role?, department? } -> 200 (same
                                                                                                       409s)


  POST                                            /users/{id}/deactivate                               { status: "Inactive" } - immediate
                                                                                                       access loss, audit retained


  POST                                            /users/{id}/activate                                 { status: "Active" }


  POST                                            /users/{id}/reset-password                           200 { "message": "Password reset
                                                                                                       email sent" } (see audit AUD-012)


  GET                                             /roles                                               [ { role, permissions: [ string ]
                                                                                                       } ] - the permissions matrix


  GET                                             /roles/{role}/permissions                            one role's list


Role -> permission strings are fixed in the UI (rolePermissions in Users.tsx). Expose PUT /roles/{role}/permissions only if they
become editable later.




                         CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 15 of 17
PHASE 11 - System Settings

Source: Settings.tsx. Role: admin only. Every PUT writes a critical audit entry.


11.1 Fee Types
   • GET /settings/fee-types -> [ { id, name, code, taxable, active } ]
   • POST /settings/fee-types { name, code, taxable, active }
   • PATCH /settings/fee-types/{id} { name?, code?, taxable?, active? }

code is what Phase 3.10 validation checks for unknown_fee_type.


11.2 Payment Statuses (reference data, likely read-only)
GET /settings/payment-statuses -> [ { status, description, terminal } ] for Successful, Pending, Failed, Refunded,
Reversed, Voided.


11.3 EPP Configuration
GET / PUT /settings/epp

  { "tenors": { "3": true, "6": true, "12": true, "18": true },
   "minAmountEGP": 5000, "maxAmountEGP": 100000,
   "interestRatePct": { "3": 10, "6": 12, "12": 14, "18": 16 },
   "adminFeeRatePct": 1.0, "adminFeeCapEGP": 500,
   "requireApproval": false, "maxPlansPerStudent": 2 }

Consumed by POST /epp/quote and POST /epp/plans.


11.4 Notification Settings
GET / PUT /settings/notifications

  { "events": { "failedPayments": true, "reconExceptions": true, "schoolUploadErrors": true,
               "newSchoolReg": true, "systemAlerts": true, "dailySummary": true },
   "channels": { "inApp": true, "email": true, "sms": false, "slack": false } }



11.5 School / Institution Configuration
GET / PUT /settings/institutions

  { "requireDualApproval": true, "autoIntegrationAfterApproval": false,
   "requireMOECertificate": true, "maxStudentsPerUpload": 5000,
   "postApprovalActivationDelayHours": 24,
   "allowedUploadFormats": { "xlsx": true, "csv": true, "xml": false } }

Gates Phase 3.8 (dual approval) and Phase 3.10 (upload format + row cap).




                        CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 16 of 17
Consolidated Count & Open Questions

Approximate endpoint count: Auth 9, Dashboard 4, Institutions ~18, Transactions / Payments ~12, Reconciliation ~10, EPP 8,
Reports 5, Notifications 6, Audit 5, Users 11, Settings ~12 -> roughly 100 endpoints - the P0-02 contract the deck requires signed off in
the first three days.
Open questions before lock:
   1. Amounts - whole EGP integers (matches the UI) or minor units / piastres?
   2. National ID - confirm keyed-HMAC lookup index + AES-GCM at rest (deck slide 19); /customers/fees returns only the masked
   value.
   3. Idempotency - Idempotency-Key header on POST /payments and POST /institutions/{id}/fee-submissions - agreed?
   4. Report generation - always async (202 + jobId), or synchronous inline for small reports?
   5. Refund / reverse - in scope for the MVP or deferred? (statuses exist in the UI, no action wired)
   6. Dual approval - one endpoint with a state machine, or /approve + /second-approve?




                          CIB Tuition Fees Collection Network | Bank Back-Office Portal API Contract | Draft | page 17 of 17
