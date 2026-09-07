# Women in Tech — Mock Banking Services

Three fake banking services you can run on your own laptop while you build your project. No bank, no internet, no real money.

| Service | Endpoint | Description |
| :--- | :--- | :--- |
| **1. MOI validation** | `POST /api/v1/moi/validate` | Check an Egyptian national ID |
| **2. Card payment** | `POST /api/v1/payments/cards` | Authorise / capture / refund a card |
| **3. Create EPP payment** | `POST /api/v1/epp` | Turn a purchase into instalments |

Everything is stored in memory. Restart the server and you start from a clean slate.

⚠️ **All names, national IDs and card numbers in this project are invented for training. Nothing here connects to a real bank or a real person.**

---

## 1. Run it

You need either Docker or Python 3.9+. Pick one.

### Option A — Docker (nothing else to install)

`docker compose up --build`

### Option B — Python

**macOS / Linux**
`./run.sh`

**Windows** — double-click `run.bat`, or from cmd:
`run.bat`

The script creates a virtual environment and installs everything the first time. Later runs start immediately.

### Check it works

Open http://localhost:8000/docs in your browser. You should see the interactive API explorer with the three services listed. 
Or from a terminal:
`curl http://localhost:8000/health`

*Using a different port: `PORT=9000 ./run.sh`, or edit the `ports:` line in `docker-compose.yml`.*

### See all three services in one go

With the server running, open a second terminal:
`./demo.sh`

It validates a national ID, takes a card payment, quotes the instalment plans, creates one, and then shows the failure paths — a declined card, 3-D Secure, a blocked ID and an EPP on a debit card. Read it as a worked example of the calls you need to make.

---

## 2. Authentication

Every `/api/**` request needs this header:
`X-API-Key: wit-intern-2026`

Without it you get `401 MISSING_API_KEY`. `/health` and `/docs` are open.

In the Swagger UI at `/docs`, the key goes in the header field of each request — or just use curl / Postman with the examples below.

---

## 3. The three services

### Service 1 — MOI validation

Checks a 14-digit Egyptian national ID: parses it, looks the person up, and says whether they can be onboarded.

`curl -X POST http://localhost:8000/api/v1/moi/validate \
  -H "X-API-Key: wit-intern-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "national_id": "29805150101023",
    "full_name": "Mona Samir Abdelrahman",
    "purpose": "CARD_ISSUANCE"
  }'`

`{
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
}`

When `valid` is false, `reasons` tells you why: `NO_MOI_RECORD`, `HOLDER_DECEASED`, `RECORD_BLOCKED_BY_AUTHORITY`, `UNDER_MINIMUM_AGE`, `NAME_MISMATCH`.

**How the ID is read**
`2 980515 01 0102 3
│   │    │   │   └── check digit
│   │    │   └────── serial; its last digit: odd = male, even = female
│   │    └────────── governorate (01 = Cairo, 21 = Giza, …)
│   └─────────────── date of birth, YYMMDD
└─────────────────── century: 2 = born 1900s, 3 = born 2000s`

Other endpoints: `GET /api/v1/moi/verifications/{id}`, `GET /api/v1/moi/verifications`.

### Service 2 — Card payment

`curl -X POST http://localhost:8000/api/v1/payments/cards \
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
  }'`

`{
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
}`

**What you get back**

| HTTP | Meaning |
| :--- | :--- |
| **201** | Approved — status CAPTURED (or AUTHORISED if you sent `capture: false`) |
| **200** | Status PENDING_3DS — the customer must confirm an OTP |
| **400** | The card number is malformed (`INVALID_CARD_NUMBER`) |
| **402** | Declined by the issuer (`CARD_DECLINED`, `CARD_EXPIRED`) |
| **502** | Issuer unavailable — safe to retry |

**Follow-up actions**
`POST /api/v1/payments/cards/{id}/3ds        {"otp": "123456"}
POST /api/v1/payments/cards/{id}/capture    {"amount": 5000}   (optional, partial)
POST /api/v1/payments/cards/{id}/refund     {"amount": 2000}   (optional, partial)
POST /api/v1/payments/cards/{id}/void
GET  /api/v1/payments/cards/{id}
GET  /api/v1/payments/cards?status=CAPTURED`

> Send an `Idempotency-Key` header when creating a payment. Retrying with the same key returns the original payment instead of charging twice.

### Service 3 — Create EPP payment

EPP = Easy Payment Plan: a purchase split into fixed monthly instalments.

**Step 1 — show the customer the options (creates nothing):**
`curl "http://localhost:8000/api/v1/epp/quotes?amount=24000" \
  -H "X-API-Key: wit-intern-2026"`

**Step 2 — create the plan from the payment you took in service 2:**
`curl -X POST http://localhost:8000/api/v1/epp \
  -H "X-API-Key: wit-intern-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "payment_id": "pay_b85f6902bd8648ec",
    "tenor_months": 12,
    "product_name": "Laptop"
  }'`

`{
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
}`

*You can also create a plan without a prior payment by sending `card` + `amount` instead of `payment_id`.*

**Pricing (flat rate, the way Egyptian card EPPs are quoted):**

| Tenor | Annual rate | Admin fee |
| :--- | :--- | :--- |
| **3 months** | 0% | waived |
| **6 months** | 12% | 1%, capped at 500 EGP |
| **12 months** | 14% | 1%, capped at 500 EGP |
| **18 months** | 15% | 1%, capped at 500 EGP |
| **24 months** | 16% | 1%, capped at 500 EGP |

*   `interest = principal × annual_rate × tenor / 12`
*   `total = principal + interest + admin_fee`
*   `instalment = total / tenor` (the last one absorbs the rounding difference)

**Rules the service enforces** — amount between 1,000 and 500,000 EGP, credit cards only, one plan per payment, EGP only, tenor must be on the list.
Other endpoints: `GET /api/v1/epp/{id}`, `GET /api/v1/epp`, `POST /api/v1/epp/{id}/cancel`.

---

## 4. Test data

Also available live at `GET /api/v1/test-data`.

### Cards

Use expiry `12 / 2030` and CVV `123` unless you are testing expiry handling.

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

---

## 5. Errors

Every failure has the same shape, so you only need to write one error handler:

`{
  "error": {
    "code": "CARD_DECLINED",
    "message": "Insufficient funds",
    "details": { "payment_id": "pay_...", "response_code": "51" }
  },
  "request_id": "req_9f2c1a8b4de0"
}`

| Code | HTTP | When |
| :--- | :--- | :--- |
| `MISSING_API_KEY` / `INVALID_API_KEY` | 401 | The `X-API-Key` header is missing or wrong |
| `VALIDATION_ERROR` | 422 | A field is missing or the wrong type — see `details.fields` |
| `INVALID_NATIONAL_ID` | 400 | Not 14 digits, impossible date, bad century digit |
| `INVALID_CARD_NUMBER` | 400 | Failed the Luhn check |
| `UNSUPPORTED_SCHEME` | 400 | Not Visa / Mastercard / Meeza / Amex |
| `CARD_EXPIRED` | 402 | The expiry date has passed |
| `CARD_DECLINED` | 402 | The issuer said no |
| `ISSUER_UNAVAILABLE` | 502 | Temporary failure — retry |
| `INVALID_PAYMENT_STATE` | 409 | e.g. capturing a payment that is already captured |
| `PAYMENT_NOT_CONVERTIBLE` | 409 | The payment was declined or voided |
| `ALREADY_CONVERTED` | 409 | That payment already has an EPP plan |
| `CARD_NOT_ELIGIBLE` | 422 | EPP on a debit card |
| `AMOUNT_OUT_OF_RANGE` | 422 | Outside the 1,000–500,000 EGP EPP limits |
| `UNSUPPORTED_TENOR` | 422 | Tenor is not 3, 6, 12, 18 or 24 |
| `SERVICE_UNAVAILABLE` | 503 | Injected failure (see CHAOS_PERCENT below) |

---

## 6. Settings

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
`LATENCY_MS=800 CHAOS_PERCENT=15 ./run.sh`

`POST /api/v1/admin/reset` clears all stored payments, plans and verifications without restarting the server.

---

## 7. Calling it from your app

CORS is wide open, so a front-end on any port can call it directly.

`const BASE = "http://localhost:8000";
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
  if (!res.ok) throw new Error(data.error.message); // 402 = declined
  return data;
}`

`GET /openapi.json` gives you the full machine-readable spec if you want to generate a client.

**Sharing one server with your team:** the server already listens on all network interfaces. Anyone on the same Wi-Fi can reach it at `http://<your-laptop-ip>:8000`.

---

## 8. Suggested exercises

*   **Build a checkout page:** validate the national ID, take the card payment, then offer instalment options and create the plan.
*   **Handle every outcome properly:** approved, declined, 3-D Secure, network error.
*   **Add retries:** Turn on `CHAOS_PERCENT=20` and add retries that do not double-charge (use `Idempotency-Key`).
*   **Build a schedule view:** Show the instalment schedule in a table with the due dates.
*   **Add cancellations:** Add a "cancel plan" button and handle the `INSTALMENTS_ALREADY_PAID` case.

---

## 9. Troubleshooting

| Problem | Fix |
| :--- | :--- |
| **Address already in use** | Something is on port 8000. Use `PORT=9000 ./run.sh` |
| **./run.sh: Permission denied** | `chmod +x run.sh` |
| **python: command not found** | Install Python 3 and tick "Add to PATH" on Windows |
| **Windows blocks run.bat** | Right-click → Properties → Unblock |
| **401 on every call** | The `X-API-Key` header is missing |
| **422 with details.fields** | A field is missing or the wrong type — read the list |
| **Data disappeared** | Expected: it is in memory and resets when the server restarts |

---

## 10. Project layout

`app/
  main.py          app setup, /health, /api/v1/test-data, /docs
  config.py        environment settings
  schemas.py       request/response models (these drive the Swagger docs)
  seed.py          the test cards and national IDs
  routers/
    moi.py         service 1
    cards.py       service 2
    epp.py         service 3
  cardlib.py       Luhn / scheme / expiry checks shared by services 2 and 3
  utils.py         national-ID parsing, money rounding, date maths
  store.py         in-memory storage
tests/
  test_services.py 19 tests covering all three services
demo.sh            the whole customer journey, end to end
requests.http      one-click requests for VS Code REST Client
postman_collection.json`

**Run the tests:**
`pip install -r requirements-dev.txt
pytest -q`