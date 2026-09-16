# Women in Tech — Mock Banking Services

Fake banking services you can run on your own laptop while you build your project. No bank, no internet, no real money.

| # | Service | Endpoint |
| :---: | :--- | :--- |
| **1** | **MOI validation** — check an Egyptian national ID | `POST /api/v1/moi/validate` |
| **2** | **Card payment** — authorise / capture / refund a card | `POST /api/v1/payments/cards` |
| **3** | **Create EPP payment** — turn a purchase into instalments | `POST /api/v1/epp` |
| **4** | **Customer lookup** — find a bank customer and their accounts and cards | `GET /api/v1/customers` |
| **5** | **Back-office payment** — pay from an account or card the customer already holds | `POST /api/v1/backoffice/payments` |

Services 1–3 are what an online checkout needs. Services 4 and 5 are for a back-office portal used by bank staff. See [Two ways to use it](#3-two-ways-to-use-it) to pick yours.

Everything is stored in memory. Restart the server and you start from a clean slate.

⚠️ **All names, national IDs, accounts and card numbers in this project are invented for training. Nothing here connects to a real bank or a real person.**

---

## 1. Run it

You need either Docker or Python 3.9+. Pick one.

### Option A — Docker (nothing else to install)

```bash
docker compose up --build
```

### Option B — Python

**macOS / Linux**

```bash
./run.sh
```

**Windows** — double-click `run.bat`, or from cmd:

```cmd
run.bat
```

The script creates a virtual environment and installs everything the first time. Later runs start immediately.

### Check it works

Open http://localhost:8000/docs in your browser. You should see the interactive API explorer with every service listed.

Or from a terminal:

```bash
curl http://localhost:8000/health
```

*Using a different port: `PORT=9000 ./run.sh`, or edit the `ports:` line in `docker-compose.yml`.*

### See every service in one go

With the server running, open a second terminal:

```bash
./demo.sh
```

It runs two journeys. Read it as a worked example of the calls you need to make.

* **Online checkout:** validates a national ID, takes a card payment, quotes the instalment plans and creates one. Then it shows the failure paths: a declined card, 3-D Secure, a blocked ID and an EPP on a debit card.
* **Back-office portal:** looks a customer up, pays from their account and their card, and turns the card payment into instalments. Then it looks the customer up again to show the balances have moved, and ends with the failure paths: insufficient funds, a blocked card and someone who is not a customer.

---

## 2. Authentication

Every `/api/**` request needs this header:

```http
X-API-Key: wit-intern-2026
```

Without it you get `401 MISSING_API_KEY`. `/health` and `/docs` are open.

In the Swagger UI at `/docs`, the key goes in the header field of each request — or just use curl / Postman with the examples below.

---

## 3. Two ways to use it

The services support two kinds of app. Pick the one you are building.

| | Online checkout | Back-office portal |
| :--- | :--- | :--- |
| **Who uses it** | The customer, on a website | Bank staff, inside the bank |
| **How the customer pays** | Types a card number, expiry and CVV | Staff pick one of the customer's existing accounts or cards. No card number, no CVV |
| **Services** | 1 → 2 → 3 | 4 → 5 → 3 |

### Online checkout

```http
POST /api/v1/moi/validate                is this national ID real?
POST /api/v1/payments/cards              charge the card the customer typed
POST /api/v1/epp                         (optional) split it into instalments
```

### Back-office portal

```http
GET  /api/v1/customers?national_id=...   who is this, and what do they hold?
POST /api/v1/backoffice/payments         pay from the account or card staff picked
POST /api/v1/epp                         (optional) split a card payment into instalments
```

A portal can still call service 1 first if you want to show the MOI check too.

Both flows share the same API key, error format and EPP service. The checkout flow did not change when the back-office services were added.

---

## 4. The services

### Service 1 — MOI validation

Checks a 14-digit Egyptian national ID: parses it, looks the person up, and says whether they can be onboarded.

```bash
curl -X POST http://localhost:8000/api/v1/moi/validate \
  -H "X-API-Key: wit-intern-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "national_id": "29805150101023",
    "full_name": "Mona Samir Abdelrahman",
    "purpose": "CARD_ISSUANCE"
  }'
```

```json
{
  "verification_id": "ver_dd6a2086adf74b7f",
  "valid": true,
  "national_id": "29805150101023",
  "record_status": "ACTIVE",
  "holder": {
    "full_name_en": "Mona Samir Abdelrahman",
    "birth_date": "1998-05-15",
    "age": 28,
    "gender": "FEMALE",
    "governorate": "Cairo",
    "checksum_valid": true
  },
  "name_match": { "matched": true, "score": 1.0 },
  "eligibility": { "is_adult": true, "minimum_age": 21, "can_be_issued_card": true },
  "reasons": []
}
```

When `valid` is false, `reasons` tells you why: `NO_MOI_RECORD`, `HOLDER_DECEASED`, `RECORD_BLOCKED_BY_AUTHORITY`, `UNDER_MINIMUM_AGE`, `NAME_MISMATCH`.

**How the ID is read**

```text
2 980515 01 0102 3
│   │    │   │   └── check digit
│   │    │   └────── serial; its last digit: odd = male, even = female
│   │    └────────── governorate (01 = Cairo, 21 = Giza, …)
│   └─────────────── date of birth, YYMMDD
└─────────────────── century: 2 = born 1900s, 3 = born 2000s
```

Other endpoints: `GET /api/v1/moi/verifications/{id}`, `GET /api/v1/moi/verifications`.

---

### Service 2 — Card payment

```bash
curl -X POST http://localhost:8000/api/v1/payments/cards \
  -H "X-API-Key: wit-intern-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "card": {
      "number": "4111 1111 1111 1111",
      "holder_name": "Mona Samir",
      "expiry_month": 12,
      "expiry_year": 2030,
      "cvv": "123"
    },
    "amount": { "value": 24000, "currency": "EGP" },
    "order_reference": "ORD-100234",
    "customer": { "national_id": "29805150101023", "mobile": "01001234567" },
    "description": "Laptop",
    "capture": true
  }'
```

```json
{
  "payment_id": "pay_b85f6902bd8648ec",
  "status": "CAPTURED",
  "approved": true,
  "amount": 24000.0,
  "captured_amount": 24000.0,
  "card": { "masked_number": "411111******1111", "scheme": "VISA", "type": "CREDIT" },
  "auth_code": "195000",
  "rrn": "335531777886",
  "response_code": "00",
  "response_message": "Approved"
}
```

**What you get back**

| HTTP | Meaning |
| :--- | :--- |
| **201** | Approved — status CAPTURED (or AUTHORISED if you sent `capture: false`) |
| **200** | Status PENDING_3DS — the customer must confirm an OTP |
| **400** | The card number is malformed (`INVALID_CARD_NUMBER`) |
| **402** | Declined by the issuer (`CARD_DECLINED`, `CARD_EXPIRED`) |
| **502** | Issuer unavailable — safe to retry |

**Follow-up actions**

```http
POST /api/v1/payments/cards/{id}/3ds        {"otp": "123456"}
POST /api/v1/payments/cards/{id}/capture    {"amount": 5000}   (optional, partial)
POST /api/v1/payments/cards/{id}/refund     {"amount": 2000}   (optional, partial)
POST /api/v1/payments/cards/{id}/void
GET  /api/v1/payments/cards/{id}
GET  /api/v1/payments/cards?status=CAPTURED
```

Send an `Idempotency-Key` header when creating a payment. Retrying with the same key returns the original payment instead of charging twice.

---

### Service 3 — Create EPP payment

EPP = Easy Payment Plan: a purchase split into fixed monthly instalments.

**Step 1 — show the customer the options (creates nothing):**

```bash
curl "http://localhost:8000/api/v1/epp/quotes?amount=24000" \
  -H "X-API-Key: wit-intern-2026"
```

**Step 2 — create the plan from the payment you took in service 2:**

```bash
curl -X POST http://localhost:8000/api/v1/epp \
  -H "X-API-Key: wit-intern-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "payment_id": "pay_b85f6902bd8648ec",
    "tenor_months": 12,
    "product_name": "Laptop"
  }'
```

```json
{
  "plan_id": "epp_2563bb79e4a84102",
  "status": "ACTIVE",
  "payment_id": "pay_b85f6902bd8648ec",
  "principal": 24000.0,
  "tenor_months": 12,
  "annual_rate": 0.14,
  "interest_amount": 3360.0,
  "admin_fee": 240.0,
  "total_payable": 27600.0,
  "monthly_installment": 2300.0,
  "first_due_date": "2026-09-04",
  "last_due_date": "2027-08-04",
  "schedule": [
    { "number": 1, "due_date": "2026-09-04", "amount": 2300.0, "status": "DUE" },
    { "number": 2, "due_date": "2026-10-04", "amount": 2300.0, "status": "DUE" }
  ]
}
```

You can also create a plan without a prior payment by sending `card` + `amount` instead of `payment_id`.

**From the back office. There are two ways:**

* **From a payment:** `payment_id` also accepts a service 5 payment (`bop_...`) made on a credit card.
* **Without a payment:** send `card_id` + `amount` instead of `card` + `amount`. The amount is held on the card's available limit until the plan is cancelled.

```json
{ "card_id": "card_mona_visa", "amount": { "value": 9000, "currency": "EGP" }, "tenor_months": 6 }
```

Plans made from a customer's card come back with a `card_id` field.

**Pricing (flat rate, the way Egyptian card EPPs are quoted):**

| Tenor | Annual rate | Admin fee |
| :--- | :--- | :--- |
| **3 months** | 0% | waived |
| **6 months** | 12% | 1%, capped at 500 EGP |
| **12 months** | 14% | 1%, capped at 500 EGP |
| **18 months** | 15% | 1%, capped at 500 EGP |
| **24 months** | 16% | 1%, capped at 500 EGP |

```text
interest   = principal × annual_rate × tenor / 12
total      = principal + interest + admin_fee
instalment = total / tenor       (the last one absorbs the rounding difference)
```

**Rules the service enforces:**
* The amount must be between 1,000 and 500,000 EGP, and in EGP.
* Credit cards only. A debit card or an account payment is rejected with `CARD_NOT_ELIGIBLE`.
* One plan per payment.
* The tenor must be on the list.
* A `card_id` plan must fit in the card's available limit, or you get `CARD_DECLINED`.

Other endpoints: `GET /api/v1/epp/{id}`, `GET /api/v1/epp`, `POST /api/v1/epp/{id}/cancel`.

---

### Service 4 — Customer lookup (back office)

Finds a bank customer by national ID and returns every account and card they hold, with live balances and limits.

```bash
curl "http://localhost:8000/api/v1/customers?national_id=29805150101023" \
  -H "X-API-Key: wit-intern-2026"
```

```json
{
  "customer_id": "cif_100001",
  "national_id": "29805150101023",
  "full_name_en": "Mona Samir Abdelrahman",
  "full_name_ar": "منى سمير عبدالرحمن",
  "mobile": "01001234567",
  "status": "ACTIVE",
  "accounts": [
    { "account_id": "acc_mona_current", "account_number": "1001000012345", "type": "CURRENT",
      "currency": "EGP", "available_balance": 50000.0, "status": "ACTIVE" },
    { "account_id": "acc_mona_savings", "account_number": "1001000012346", "type": "SAVINGS",
      "currency": "EGP", "available_balance": 200000.0, "status": "ACTIVE" }
  ],
  "cards": [
    { "card_id": "card_mona_visa", "masked_number": "411111******1111", "scheme": "VISA",
      "type": "CREDIT", "holder_name": "MONA SAMIR", "expiry": "12/2030", "status": "ACTIVE",
      "credit_limit": 100000.0, "available_limit": 100000.0, "linked_account_id": null },
    { "card_id": "card_mona_debit", "masked_number": "507803******7890", "scheme": "MEEZA",
      "type": "DEBIT", "holder_name": "MONA SAMIR", "expiry": "12/2030", "status": "ACTIVE",
      "credit_limit": null, "available_limit": null, "linked_account_id": "acc_mona_current" }
  ]
}
```

**What you get back**

| HTTP | Meaning |
| :--- | :--- |
| **200** | Found |
| **400** | `INVALID_NATIONAL_ID` — not a valid 14-digit ID |
| **404** | `CUSTOMER_NOT_FOUND` — the ID is fine, but the person does not bank here |

This is the bank's own customer register, not the MOI. Unlike service 1, an ID that is not in the test data is not found. Only the bank customers exist.

In your portal, list the accounts and cards and grey out anything whose status is not `ACTIVE`. Only offer instalments on `CREDIT` cards.

---

### Service 5 — Back-office payment

Takes a payment from an account or card the customer already holds. Send its `account_id` or `card_id` from service 4 as `source_id`.

```bash
curl -X POST http://localhost:8000/api/v1/backoffice/payments \
  -H "X-API-Key: wit-intern-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "source_id": "card_mona_visa",
    "amount": { "value": 24000, "currency": "EGP" },
    "reference": "BO-100234",
    "description": "Laptop",
    "capture": true
  }'
```

```json
{
  "payment_id": "bop_4c1d0e2a9b7f4e11",
  "status": "CAPTURED",
  "approved": true,
  "amount": 24000.0,
  "captured_amount": 24000.0,
  "reference": "BO-100234",
  "customer_id": "cif_100001",
  "source_id": "card_mona_visa",
  "source": { "type": "CARD", "product_type": "CREDIT",
              "masked_number": "411111******1111", "scheme": "VISA" },
  "response_code": "00",
  "response_message": "Approved"
}
```

**What happens to the money**

| Source | Status | What moves |
| :--- | :--- | :--- |
| **Account** | POSTED, straight away | The account's `available_balance` goes down |
| **Credit card** | CAPTURED, or AUTHORISED if you sent `capture: false` | The card's `available_limit` goes down |
| **Debit card** | Same as a credit card | The linked account's `available_balance` goes down |

Look the customer up again to see the new numbers. Refunds and voids put the money back.

**What you get back**

| HTTP | Meaning |
| :--- | :--- |
| **201** | Approved |
| **402** | Not enough money: `INSUFFICIENT_FUNDS` (account) or `CARD_DECLINED` (card). Also `CARD_EXPIRED` |
| **404** | `SOURCE_NOT_FOUND` — no account or card with that id |
| **422** | The product cannot be used: `ACCOUNT_NOT_ACTIVE` or `CARD_BLOCKED`. Also `INVALID_REQUEST` for `capture: false` on an account |
| **502** | Issuer unavailable — safe to retry |

There is no 3-D Secure step: the customer is not the one at the keyboard.

**Follow-up actions**

```http
POST /api/v1/backoffice/payments/{id}/capture   {"amount": 5000}   (cards only, optional, partial)
POST /api/v1/backoffice/payments/{id}/refund    {"amount": 2000}   (optional, partial)
POST /api/v1/backoffice/payments/{id}/void                         (cards only, before capture)
GET  /api/v1/backoffice/payments/{id}
GET  /api/v1/backoffice/payments?customer_id=cif_100001
```

A partial capture gives the rest of the authorised amount back to the customer. `Idempotency-Key` works the same way as in service 2.

---

## 5. Test data

Also available live at `GET /api/v1/test-data`.

### Cards

Used by services 2 and 3. Use expiry `12 / 2030` and CVV `123` unless you are testing expiry handling.

| Card number | Result |
| :--- | :--- |
| `4111 1111 1111 1111` | Approved (Visa credit) |
| `5555 5555 5555 4444` | Approved (Mastercard credit) |
| `5105 1051 0510 5100` | Approved (Mastercard credit) |
| `5078 0312 3456 7890` | Approved (Meeza credit) |
| `4000 0566 5566 5556` | Approved — but debit, so EPP rejects it |
| `4000 0000 0000 0077` | Approved up to 10,000 EGP, declines above that |
| `4000 0000 0000 0002` | Declined — insufficient funds (51) |
| `4000 0000 0000 9995` | Declined — do not honour (05) |
| `4000 0000 0000 0101` | Declined — incorrect CVV (82) |
| `4000 0000 0000 0069` | Declined — expired card (54) |
| `4000 0000 0000 0119` | HTTP 502, issuer unavailable (retryable) |
| `4000 0000 0000 3220` | Needs 3-D Secure — confirm with OTP `123456` |

*Any other card number that passes the Luhn check is approved as a normal credit card, so you can invent your own.*

### National IDs

Used by service 1.

| National ID | Person | Result |
| :--- | :--- | :--- |
| `29805150101023` | Mona Samir Abdelrahman | Valid — female, Cairo, born 1998-05-15 |
| `30103222103442` | Nour Khaled Fahmy | Valid — female, Giza, born 2001-03-22 |
| `29511020204536` | Ahmed Tarek Mahmoud | Valid — male, Alexandria, born 1995-11-02 |
| `29001301202283` | Salma Hosny Ibrahim | Valid — female, Dakahlia, born 1990-01-30 |
| `30007091301775` | Youssef Adel Nabil | Valid — male, Sharqia, born 2000-07-09 |
| `28809252506666` | Heba Mostafa Zaki | Valid — female, Asyut, born 1988-09-25 |
| `31204010102041` | Malak Hany Sobhy | Invalid — UNDER_MINIMUM_AGE |
| `29805150100000` | — | NOT_FOUND |
| `29805150188889` | — | DECEASED |
| `29805150199996` | — | BLOCKED |

*Any well-formed ID that is not listed is treated as a valid, unknown citizen. To force an outcome on an ID of your own, set digits 10–13 to `0000` (not found), `8888` (deceased) or `9999` (blocked).*

### Bank customers

Used by services 4 and 5, and by EPP with a `card_id`. Amounts are opening balances in EGP. They change as you take payments, and `POST /api/v1/admin/reset` restores them.

| National ID | Customer | Accounts | Cards | Use it to test |
| :--- | :--- | :--- | :--- | :--- |
| `29805150101023` | Mona Samir Abdelrahman | `acc_mona_current` 50,000<br>`acc_mona_savings` 200,000 | `card_mona_visa` credit, limit 100,000<br>`card_mona_debit` Meeza debit, spends from `acc_mona_current` | The happy path, EPP |
| `30103222103442` | Nour Khaled Fahmy | `acc_nour_current` 2,500 | `card_nour_mastercard` credit, limit 10,000 | `INSUFFICIENT_FUNDS`, `CARD_DECLINED` over the limit |
| `29511020204536` | Ahmed Tarek Mahmoud | `acc_ahmed_current` FROZEN | `card_ahmed_visa` BLOCKED<br>`card_ahmed_mastercard` credit, limit 40,000 | `ACCOUNT_NOT_ACTIVE`, `CARD_BLOCKED` |
| `29001301202283` | Salma Hosny Ibrahim | `acc_salma_savings` DORMANT | `card_salma_visa` EXPIRED | A customer with nothing usable |
| `30007091301775` | Youssef Adel Nabil | `acc_youssef_current` 20,000 | `card_youssef_debit` Visa debit | No credit card, so no EPP |
| `28809252506666` | Heba Mostafa Zaki | none | `card_heba_visa` credit, limit 80,000 | `ISSUER_UNAVAILABLE` (502), an empty accounts list |

*Any other national ID returns `404 CUSTOMER_NOT_FOUND` from service 4, even one that service 1 accepts, such as Malak's `31204010102041`.*

---

## 6. Errors

Every failure has the same shape, so you only need to write one error handler:

```json
{
  "error": {
    "code": "CARD_DECLINED",
    "message": "Insufficient funds",
    "details": { "payment_id": "pay_...", "response_code": "51" }
  },
  "request_id": "req_9f2c1a8b4de0"
}
```

| Code | HTTP | When |
| :--- | :--- | :--- |
| `MISSING_API_KEY` / `INVALID_API_KEY` | 401 | The `X-API-Key` header is missing or wrong |
| `VALIDATION_ERROR` | 422 | A field is missing or the wrong type — see `details.fields` |
| `INVALID_REQUEST` | 422 | The fields are valid but do not fit together, e.g. `payment_id` and `card` in one EPP request |
| `INVALID_NATIONAL_ID` | 400 | Not 14 digits, impossible date, bad century digit |
| `INVALID_CARD_NUMBER` | 400 | Failed the Luhn check |
| `UNSUPPORTED_SCHEME` | 400 | Not Visa / Mastercard / Meeza / Amex |
| `CARD_EXPIRED` | 402 | The expiry date has passed |
| `CARD_DECLINED` | 402 | The issuer said no, or the amount is over the card's available limit |
| `INSUFFICIENT_FUNDS` | 402 | The account balance is lower than the amount |
| `CUSTOMER_NOT_FOUND` | 404 | The national ID is not a customer of the bank |
| `SOURCE_NOT_FOUND` | 404 | No account or card with that `source_id` |
| `CARD_NOT_FOUND` | 404 | No card with that `card_id` (EPP) |
| `ACCOUNT_NOT_ACTIVE` | 422 | The account is FROZEN or DORMANT |
| `CARD_BLOCKED` | 422 | The customer's card is BLOCKED |
| `CURRENCY_MISMATCH` | 422 | The amount is not in the account's currency |
| `ISSUER_UNAVAILABLE` | 502 | Temporary failure — retry |
| `INVALID_PAYMENT_STATE` | 409 | e.g. capturing a payment that is already captured |
| `PAYMENT_NOT_CONVERTIBLE` | 409 | The payment was declined or voided |
| `ALREADY_CONVERTED` | 409 | That payment already has an EPP plan |
| `CARD_NOT_ELIGIBLE` | 422 | EPP on a debit card or on an account payment |
| `AMOUNT_OUT_OF_RANGE` | 422 | Outside the 1,000–500,000 EGP EPP limits |
| `UNSUPPORTED_TENOR` | 422 | Tenor is not 3, 6, 12, 18 or 24 |
| `SERVICE_UNAVAILABLE` | 503 | Injected failure (see CHAOS_PERCENT below) |

---

## 7. Settings

Set these as environment variables, or in the `environment:` block of `docker-compose.yml`.

| Variable | Default | What it does |
| :--- | :--- | :--- |
| `API_KEY` | `wit-intern-2026` | The key clients must send |
| `REQUIRE_API_KEY` | `true` | Set to `false` to turn auth off |
| `LATENCY_MS` | `0` | Delay every response — good for testing loading states |
| `CHAOS_PERCENT` | `0` | Fail this % of calls with 503 — good for testing retries |
| `STRICT_NID_CHECKSUM` | `false` | Also enforce the national-ID check digit |
| `EPP_MIN_AMOUNT` | `1000` | Smallest amount eligible for instalments |
| `EPP_MAX_AMOUNT` | `500000` | Largest amount eligible for instalments |

Example:

```bash
LATENCY_MS=800 CHAOS_PERCENT=15 ./run.sh
```

`POST /api/v1/admin/reset` clears all stored payments, plans and verifications and restores the customers' opening balances, without restarting the server.

---

## 8. Calling it from your app

CORS is wide open, so a front-end on any port can call it directly.

```javascript
const BASE = "http://localhost:8000";
const HEADERS = {
  "Content-Type": "application/json",
  "X-API-Key": "wit-intern-2026",
};

async function payByCard(body) {
  const res = await fetch(`${BASE}/api/v1/payments/cards`, {
    method: "POST",
    headers: HEADERS,
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error.message);   // 402 = declined
  return data;
}
```

For a back-office portal:

```javascript
async function findCustomer(nationalId) {
  const res = await fetch(`${BASE}/api/v1/customers?national_id=${nationalId}`, {
    headers: HEADERS,
  });
  const data = await res.json();
  if (res.status === 404) return null;                // not a customer of the bank
  if (!res.ok) throw new Error(data.error.message);
  return data;                                        // data.accounts, data.cards
}

async function payFrom(sourceId, amount, reference) {
  const res = await fetch(`${BASE}/api/v1/backoffice/payments`, {
    method: "POST",
    headers: HEADERS,
    body: JSON.stringify({
      source_id: sourceId,
      amount: { value: amount, currency: "EGP" },
      reference,
    }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error.message);   // 402 = not enough money
  return data;
}
```

`GET /openapi.json` gives you the full machine-readable spec if you want to generate a client.

**Sharing one server with your team:** the server already listens on all network interfaces. Anyone on the same Wi-Fi can reach it at `http://<your-laptop-ip>:8000`.

Everyone on a shared server sees the same customer balances. A payment one person takes lowers the balance for everybody, and `admin/reset` resets it for everybody. If that gets in your way, run your own copy.

---

## 9. Suggested exercises

### Online checkout

* Build a checkout page: validate the national ID, take the card payment, then offer instalment options and create the plan.
* Handle every outcome properly — approved, declined, 3-D Secure, network error.
* Turn on `CHAOS_PERCENT=20` and add retries that do not double-charge (use `Idempotency-Key`).
* Show the instalment schedule in a table with the due dates.
* Add a "cancel plan" button and handle the `INSTALMENTS_ALREADY_PAID` case.

### Back-office portal

* Build a customer search: type a national ID, then show the customer's accounts and cards. Handle `CUSTOMER_NOT_FOUND` with a clear message.
* Let staff pick an account or card and take a payment. Grey out anything that is not `ACTIVE`, and try Ahmed and Salma to check that you do.
* After a payment, refresh the customer and show the new balance. Then try paying more than 2,500 EGP from Nour's account.
* Offer instalments only when the chosen source is a credit card.

---

## 10. Troubleshooting

| Problem | Fix |
| :--- | :--- |
| **Address already in use** | Something is on port 8000. Use `PORT=9000 ./run.sh` |
| **./run.sh: Permission denied** | `chmod +x run.sh` |
| **python: command not found** | Install Python 3 and tick "Add to PATH" on Windows |
| **Windows blocks run.bat** | Right-click → Properties → Unblock |
| **401 on every call** | The `X-API-Key` header is missing |
| **422 with details.fields** | A field is missing or the wrong type — read the list |
| **Data disappeared** | Expected: it is in memory and resets when the server restarts |
| **A customer's balance is not what the table says** | Payments already moved it, maybe someone else's on a shared server. `POST /api/v1/admin/reset` restores it |
| **CUSTOMER_NOT_FOUND for an ID that service 1 accepts** | Expected: only the six bank customers exist |

---

## 11. Project layout

```text
app/
  main.py          app setup, /health, /api/v1/test-data, /docs
  config.py        environment settings
  schemas.py       request/response models (these drive the Swagger docs)
  seed.py          the test cards, national IDs and bank customers
  routers/
    moi.py         service 1
    cards.py       service 2
    epp.py         service 3
    customers.py   service 4
    backoffice.py  service 5
  cardlib.py       Luhn / scheme / expiry checks shared by services 2 and 3
  corebank.py      customers' accounts, cards and balances, shared by services 3, 4 and 5
  utils.py         national-ID parsing, money rounding, date maths
  store.py         in-memory storage
tests/
  test_services.py    19 tests covering services 1–3
  test_backoffice.py  30 tests covering services 4 and 5, and EPP from the back office
demo.sh            both journeys, end to end
requests.http      one-click requests for VS Code REST Client
postman_collection.json
```

**Run the tests:**

```bash
pip install -r requirements-dev.txt
pytest -q
```