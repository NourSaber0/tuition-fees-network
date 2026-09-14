# Mock Banking Services - Postman Testing Guide

This guide contains end-to-end test scenarios and request specifications for testing the in-memory **Mock Banking Services** using **Postman** (or `curl`).

---

## 1. Quick Start

### 1.1 Start the Application
Make sure the Spring Boot backend is running locally:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;C:\Program Files\Git\cmd;C:\Program Files\GitHub CLI;$env:Path"
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
.\mvnw.cmd spring-boot:run
```
The application will start on **`http://localhost:8080`**.

### 1.2 One-Click Import into Postman
A pre-configured Postman collection is included in this repository:
📄 **`docs/Mock_Banking_Services.postman_collection.json`**

1. Open Postman.
2. Click **Import** (top left).
3. Select `docs/Mock_Banking_Services.postman_collection.json`.
4. The entire suite of 18 test requests will be loaded with variables `{{baseUrl}}` and `{{apiKey}}` pre-configured.

---

## 2. Authentication & Headers

All mock banking endpoints under `/api/v1/*` require the following headers:

| Header | Value | Notes |
| :--- | :--- | :--- |
| `Content-Type` | `application/json` | Required for all `POST` requests |
| `X-API-Key` | `wit-intern-2026` | Required for all `/api/v1/*` mock bank endpoints |

> **Note:** The public health check endpoint `GET /health` does not require authentication.

---

## 3. Test Cases Catalog

---

### Section 0: Utility & Admin Endpoints

#### 0.1 Health Check (Public)
* **Method:** `GET`
* **URL:** `http://localhost:8080/health`
* **Headers:** None
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "status": "ok"
}
```

#### 0.2 Get Test Data (Pre-seeded Cards & NIDs)
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/test-data`
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`
* **Expected Response:** Returns all test cards (Visa, Mastercard, Meeza, 3DS cards) and pre-configured Egyptian National IDs.

#### 0.3 Reset In-Memory Store
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/admin/reset`
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "status": "ok",
  "message": "All mock banking data cleared"
}
```

---

### Section 1: MOI National ID Validation Service

#### 1.1 Validate Valid Adult (Mona Samir)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/moi/validate`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "national_id": "29805150101023",
  "full_name": "Mona Samir Abdelrahman"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "verification_id": "ver_...",
  "valid": true,
  "national_id": "29805150101023",
  "status": "ACTIVE",
  "holder": {
    "full_name_en": "Mona Samir Abdelrahman",
    "birth_date": "1998-05-15",
    "gender": "FEMALE",
    "governorate": "Cairo"
  },
  "name_match": {
    "matched": true,
    "score": 1.0
  },
  "eligibility": {
    "eligible": true,
    "minimum_age": 21,
    "age_eligible": true
  },
  "reasons": []
}
```

#### 1.2 Validate Minor (Under 21)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/moi/validate`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "national_id": "31204010102041",
  "full_name": "Malak Hany Sobhy"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "valid": false,
  "status": "ACTIVE",
  "eligibility": {
    "eligible": false,
    "minimum_age": 21,
    "age_eligible": false
  },
  "reasons": [
    "UNDER_MINIMUM_AGE"
  ]
}
```

#### 1.3 Validate Deceased Citizen (Digits 10-13 = 8888)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/moi/validate`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "national_id": "28001010188881",
  "full_name": "Deceased Citizen"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "valid": false,
  "status": "DECEASED",
  "reasons": [
    "HOLDER_DECEASED"
  ]
}
```

#### 1.4 Validate Blocked Citizen (Digits 10-13 = 9999)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/moi/validate`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "national_id": "28001010199991",
  "full_name": "Blocked Citizen"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "valid": false,
  "status": "BLOCKED",
  "reasons": [
    "RECORD_BLOCKED_BY_AUTHORITY"
  ]
}
```

#### 1.5 Validate Invalid National ID Format
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/moi/validate`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "national_id": "12345"
}
```
* **Expected Status:** `400 Bad Request`
* **Expected Response:**
```json
{
  "code": "INVALID_NATIONAL_ID",
  "message": "Invalid national ID format, date or check digit"
}
```

#### 1.6 List All Verifications
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/moi/verifications`
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`

---

### Section 2: Card Payment Gateway (Acquiring)

#### 2.1 Process Card Payment - Approved (Visa Credit)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/payments/cards`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "card": {
    "number": "4111 1111 1111 1111",
    "expiry_month": 12,
    "expiry_year": 2028,
    "cvv": "123",
    "holder_name": "Mona Samir"
  },
  "amount": {
    "value": 15000.00,
    "currency": "EGP"
  },
  "capture": true
}
```
* **Expected Status:** `201 Created`
* **Expected Response:**
```json
{
  "payment_id": "pay_xxxxxxxxxxxxxxxx",
  "status": "CAPTURED",
  "success": true,
  "amount": 15000.00,
  "captured_amount": 15000.00,
  "card": {
    "masked_number": "411111******1111",
    "scheme": "VISA",
    "type": "CREDIT"
  },
  "auth_code": "123456",
  "response_code": "00",
  "response_message": "Approved"
}
```
> 💡 **Tip:** Copy the returned `payment_id` to test creating an EPP plan in Section 3!

#### 2.2 Process Card Payment - 3DS Challenge Flow (Step 1 of 2)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/payments/cards`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "card": {
    "number": "4000 0000 0000 3220",
    "expiry_month": 12,
    "expiry_year": 2028,
    "cvv": "123",
    "holder_name": "Ahmed Tarek"
  },
  "amount": {
    "value": 5000.00,
    "currency": "EGP"
  },
  "capture": true
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "payment_id": "pay_xxxxxxxxxxxxxxxx",
  "status": "PENDING_3DS",
  "success": false,
  "response_code": "00",
  "response_message": "3-D Secure authentication required"
}
```

#### 2.3 Confirm 3DS Challenge OTP (Step 2 of 2)
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/payments/cards/{payment_id}/3ds` *(replace `{payment_id}`)*
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "otp": "123456"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "payment_id": "pay_xxxxxxxxxxxxxxxx",
  "status": "CAPTURED",
  "success": true,
  "response_code": "00",
  "response_message": "Approved"
}
```

#### 2.4 Process Card Payment - Declined: Insufficient Funds
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/payments/cards`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "card": {
    "number": "4000 0000 0000 0002",
    "expiry_month": 12,
    "expiry_year": 2028,
    "cvv": "123",
    "holder_name": "Mona Samir"
  },
  "amount": {
    "value": 5000.00,
    "currency": "EGP"
  }
}
```
* **Expected Status:** `402 Payment Required`
* **Expected Response:**
```json
{
  "code": "CARD_DECLINED",
  "message": "Insufficient funds",
  "details": {
    "response_code": "51"
  }
}
```

#### 2.5 Process Card Payment - Declined: Expired Card
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/payments/cards`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "card": {
    "number": "4000 0000 0000 0069",
    "expiry_month": 1,
    "expiry_year": 2020,
    "cvv": "123",
    "holder_name": "Mona Samir"
  },
  "amount": {
    "value": 5000.00,
    "currency": "EGP"
  }
}
```
* **Expected Status:** `402 Payment Required`
* **Expected Response:**
```json
{
  "code": "CARD_EXPIRED",
  "message": "Card has expired",
  "details": {
    "response_code": "54"
  }
}
```

#### 2.6 Get Payment by ID
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/payments/cards/{payment_id}`
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`

---

### Section 3: Core Banking EPP Engine (Instalment Plans)

#### 3.1 Get EPP Quotes
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/epp/quotes?amount=24000`
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`
* **Expected Response:** Returns quotes for 3, 6, 12, 18, and 24 months with:
  * `tenor_months`
  * `interest_rate`
  * `monthly_installment`
  * `total_repayment`
  * `admin_fee`

#### 3.2 Create EPP Plan from Card Payment
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/epp`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "payment_id": "REPLACE_WITH_CAPTURED_PAYMENT_ID",
  "tenor_months": 12,
  "national_id": "29805150101023"
}
```
* **Expected Status:** `201 Created`
* **Expected Response:**
```json
{
  "plan_id": "epp_xxxxxxxxxxxxxxxx",
  "payment_id": "pay_xxxxxxxxxxxxxxxx",
  "principal": 15000.00,
  "tenor_months": 12,
  "monthly_installment": 1475.00,
  "status": "ACTIVE",
  "schedule": [
    {
      "installment_number": 1,
      "amount": 1475.00,
      "status": "PENDING"
    }
  ]
}
```

#### 3.3 Create EPP Plan - Debit Card Rejected
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/epp`
* **Headers:**
  * `Content-Type: application/json`
  * `X-API-Key: wit-intern-2026`
* **Request Body:**
```json
{
  "amount": 10000,
  "tenor_months": 12,
  "card": {
    "number": "4000 0566 5566 5556"
  }
}
```
* **Expected Status:** `422 Unprocessable Entity`
* **Expected Response:**
```json
{
  "code": "CARD_NOT_ELIGIBLE",
  "message": "Debit cards are not eligible for EPP instalment plans"
}
```

#### 3.4 List All EPP Plans
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/epp`
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`

#### 3.5 Cancel EPP Plan
* **Method:** `POST`
* **URL:** `http://localhost:8080/api/v1/epp/{plan_id}/cancel` *(replace `{plan_id}`)*
* **Headers:**
  * `X-API-Key: wit-intern-2026`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "plan_id": "epp_xxxxxxxxxxxxxxxx",
  "status": "CANCELLED"
}
```

---

### Section 4: Security & Error Scenarios

#### 4.1 Missing API Key
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/test-data`
* *(No `X-API-Key` header)*
* **Expected Status:** `401 Unauthorized`
* **Expected Response:**
```json
{
  "code": "MISSING_API_KEY",
  "message": "The X-API-Key header is missing or empty"
}
```

#### 4.2 Invalid API Key
* **Method:** `GET`
* **URL:** `http://localhost:8080/api/v1/test-data`
* **Headers:**
  * `X-API-Key: invalid-key-123`
* **Expected Status:** `401 Unauthorized`
* **Expected Response:**
```json
{
  "code": "INVALID_API_KEY",
  "message": "The provided X-API-Key is invalid"
}
```
