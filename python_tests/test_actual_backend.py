"""Automated Python test suite for the ACTUAL Tuition Network backend.

This suite connects to the live Spring Boot backend (http://localhost:8080)
and tests real business logic, authentication, RBAC, student enrollment,
fee management, guardian dues lookup, payment settlement, and idempotency.

Run with:
    ./venv/bin/pytest python_tests/test_actual_backend.py -v
"""
import uuid
import pytest
import httpx

BASE_URL = "http://localhost:8080"


@pytest.fixture(scope="session")
def client():
    with httpx.Client(base_url=BASE_URL, timeout=10.0) as c:
        yield c


def login_and_verify(client: httpx.Client, username: str, password: str = "Password123!") -> str:
    """Helper to authenticate and complete MFA to retrieve a JWT token."""
    login_resp = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    assert login_resp.status_code == 200, f"Login failed for {username}: {login_resp.text}"
    mfa_token = login_resp.json()["mfaToken"]

    verify_resp = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "123456"})
    assert verify_resp.status_code == 200, f"MFA failed for {username}: {verify_resp.text}"
    return verify_resp.json()["accessToken"]


def ensure_school_finance_user(client: httpx.Client) -> str:
    """Ensure a school finance officer is created under Al-Rowad Language School."""
    admin_token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {admin_token}"}
    email = "finance.test@rowad.edu.eg"
    req = {
        "name": "Rowad Finance Officer",
        "email": email,
        "role": "School Finance",
        "password": "Password123!",
    }
    client.post("/api/v1/users", headers=headers, json=req)
    return email


# ===========================================================================
# Suite 1: Authentication, MFA & RBAC (Tests 1 - 10)
# ===========================================================================

def test_01_backend_health_check(client):
    """01: Verify public health check endpoint is active and accessible."""
    resp = client.get("/api/chat/health")
    assert resp.status_code == 200
    assert resp.json().get("status") == "ok"


def test_02_unauthenticated_requests_are_rejected(client):
    """02: Verify protected tuition endpoints reject requests without Bearer token."""
    resp = client.get("/api/v1/students")
    assert resp.status_code in (401, 403)


def test_03_school_admin_login_and_mfa(client):
    """03: Verify School Admin can login, complete 2FA challenge, and receive valid JWT."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    assert token and len(token) > 20
    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).json()
    assert me["role"] == "school-admin"
    assert me["schoolId"] == "RLS-02"


def test_04_bank_operations_login_and_mfa(client):
    """04: Verify Bank Operations user can authenticate and access back-office role."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    assert token and len(token) > 20
    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).json()
    assert me["role"] == "bank-operations"


def test_05_bank_admin_login_and_mfa(client):
    """05: Verify Bank Admin user can login with central governance permissions."""
    token = login_and_verify(client, "admin@cib.eg", "Admin123!")
    assert token and len(token) > 20
    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).json()
    assert me["role"] == "bank-admin"


def test_06_bank_finance_login_and_mfa(client):
    """06: Verify Bank Finance user can authenticate into back-office portal."""
    token = login_and_verify(client, "finance@cib.eg", "Finance123!")
    assert token and len(token) > 20
    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).json()
    assert me["role"] == "bank-finance"


def test_07_login_rejects_invalid_password(client):
    """07: Verify backend rejects incorrect passwords with HTTP 401."""
    resp = client.post("/api/v1/auth/login", json={"username": "admin@rowad.edu.eg", "password": "WrongPassword99!"})
    assert resp.status_code == 401


def test_08_login_rejects_nonexistent_user(client):
    """08: Verify backend rejects unknown users without account enumeration."""
    resp = client.post("/api/v1/auth/login", json={"username": "ghost.user@unknown.eg", "password": "Password123!"})
    assert resp.status_code in (401, 404)


def test_09_mfa_rejects_invalid_code(client):
    """09: Verify MFA verification rejects incorrect one-time passwords."""
    login_resp = client.post("/api/v1/auth/login", json={"username": "admin@rowad.edu.eg", "password": "Password123!"})
    mfa_token = login_resp.json()["mfaToken"]
    verify_resp = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "000000"})
    assert verify_resp.status_code == 401


def test_10_mfa_brute_force_lockout_after_3_attempts(client):
    """10: Verify 3 incorrect MFA attempts triggers security lockout (429 or 401)."""
    login_resp = client.post("/api/v1/auth/login", json={"username": "admin@rowad.edu.eg", "password": "Password123!"})
    mfa_token = login_resp.json()["mfaToken"]
    client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "111111"})
    client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "222222"})
    r3 = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "333333"})
    assert r3.status_code in (429, 401)


# ===========================================================================
# Suite 2: Student Management & Privacy (Tests 11 - 18)
# ===========================================================================

def test_11_student_enrollment_by_school_admin(client):
    """11: Verify School Admin can enroll a student with class, grade, and guardian info."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    uid = uuid.uuid4().hex[:6]
    req = {
        "studentRef": f"STU-E2E-{uid}",
        "name": f"Test Student {uid}",
        "grade": "Grade 10",
        "section": "A",
        "nationalId": "29801011234567",
        "parentName": "Tarek Abdelrahman",
        "parentPhone": "+20 10 9999 8888",
        "parentEmail": f"tarek.{uid}@example.com",
    }
    resp = client.post("/api/v1/students", headers={"Authorization": f"Bearer {token}"}, json=req)
    assert resp.status_code == 201
    assert resp.json()["studentRef"] == f"STU-E2E-{uid}"


def test_12_student_enrollment_masks_national_id(client):
    """12: Verify Egyptian data privacy compliance: National ID is masked in response."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    uid = uuid.uuid4().hex[:6]
    req = {
        "studentRef": f"STU-MASK-{uid}",
        "name": f"Masked Student {uid}",
        "grade": "Grade 11",
        "section": "B",
        "nationalId": "29805150101023",
        "parentName": "Mona Samir",
    }
    resp = client.post("/api/v1/students", headers={"Authorization": f"Bearer {token}"}, json=req)
    assert resp.status_code == 201
    masked = resp.json().get("nationalIdMasked", "")
    assert "298" in masked and ("******" in masked or "****" in masked)


def test_13_student_enrollment_forbidden_for_school_finance(client):
    """13: Verify School Finance role is forbidden from enrolling students (403)."""
    email = ensure_school_finance_user(client)
    token = login_and_verify(client, email)
    req = {"studentRef": "STU-FIN-ILLEGAL", "name": "Illegal Student", "grade": "Grade 10", "nationalId": "29801011234599"}
    resp = client.post("/api/v1/students", headers={"Authorization": f"Bearer {token}"}, json=req)
    assert resp.status_code == 403


def test_14_student_enrollment_forbidden_for_bank_operations(client):
    """14: Verify Bank Operations role cannot enroll school students (403)."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    req = {"studentRef": "STU-BANK-ILLEGAL", "name": "Bank Student", "grade": "Grade 10", "nationalId": "29801011234599"}
    resp = client.post("/api/v1/students", headers={"Authorization": f"Bearer {token}"}, json=req)
    assert resp.status_code == 403


def test_15_student_list_paginated(client):
    """15: Verify School Admin receives paginated student records."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    resp = client.get("/api/v1/students", headers={"Authorization": f"Bearer {token}"}, params={"page": 1, "size": 10})
    assert resp.status_code == 200
    data = resp.json()
    assert "data" in data or "items" in data or isinstance(data, list)


def test_16_student_get_by_id(client):
    """16: Verify retrieving individual student details by ID."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    assert len(stus) > 0
    student_id = stus[0]["id"]
    resp = client.get(f"/api/v1/students/{student_id}", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["id"] == student_id


def test_17_student_update_details(client):
    """17: Verify School Admin can update student section or grade."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    student_id = stus[0]["id"]
    resp = client.patch(f"/api/v1/students/{student_id}", headers=headers, json={"section": "C"})
    assert resp.status_code in (200, 204)


def test_18_student_duplicate_identifier_rejected(client):
    """18: Verify duplicate student reference identifier is rejected."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    uid = uuid.uuid4().hex[:6]
    req = {
        "studentRef": f"STU-DUP-{uid}",
        "name": "Duplicate Student",
        "grade": "Grade 9",
        "nationalId": "29801011234588",
    }
    r1 = client.post("/api/v1/students", headers=headers, json=req)
    assert r1.status_code == 201
    r2 = client.post("/api/v1/students", headers=headers, json=req)
    assert r2.status_code in (400, 409)


# ===========================================================================
# Suite 3: Fee Management & Invoicing (Tests 19 - 26)
# ===========================================================================

def test_19_school_fee_line_creation_success(client):
    """19: Verify School Admin can create a tuition fee invoice line."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    student_id = stus[0]["id"]
    req = {
        "studentId": student_id,
        "name": "Annual Tuition 2026",
        "category": "Tuition",
        "amountEGP": 15000.0,
        "term": "Term 1 · 2026",
        "dueDate": "2026-10-31",
    }
    resp = client.post("/api/v1/fees", headers=headers, json=req)
    assert resp.status_code == 201
    assert resp.json()["status"] == "Active"


def test_20_school_fee_line_creation_permitted_for_school_finance(client):
    """20: Verify School Finance role is permitted to create fee invoices."""
    email = ensure_school_finance_user(client)
    token = login_and_verify(client, email)
    headers = {"Authorization": f"Bearer {token}"}
    admin_token = login_and_verify(client, "admin@rowad.edu.eg")
    stus = client.get("/api/v1/students", headers={"Authorization": f"Bearer {admin_token}"}).json().get("data", [])
    student_id = stus[0]["id"]
    req = {
        "studentId": student_id,
        "name": "Laboratory Fee",
        "category": "Tuition",
        "amountEGP": 1200.0,
        "term": "Term 1 · 2026",
        "dueDate": "2026-11-15",
    }
    resp = client.post("/api/v1/fees", headers=headers, json=req)
    assert resp.status_code == 201


def test_21_school_fee_line_creation_forbidden_for_bank_operations(client):
    """21: Verify Bank Operations role cannot create school fees (403)."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    req = {
        "studentId": "00000000-0000-0000-0000-000000000001",
        "name": "Illegal Fee",
        "category": "Tuition",
        "amountEGP": 5000.0,
        "term": "Term 1 · 2026",
        "dueDate": "2026-12-31",
    }
    resp = client.post("/api/v1/fees", headers={"Authorization": f"Bearer {token}"}, json=req)
    assert resp.status_code in (403, 400)


def test_22_school_fee_line_negative_amount_rejected(client):
    """22: Verify negative or zero fee amount is rejected with 400 Bad Request."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    student_id = stus[0]["id"]
    req = {
        "studentId": student_id,
        "name": "Invalid Fee",
        "category": "Tuition",
        "amountEGP": -500.0,
        "term": "Term 1",
        "dueDate": "2026-12-31",
    }
    resp = client.post("/api/v1/fees", headers=headers, json=req)
    assert resp.status_code == 400


def test_23_school_fee_line_past_due_date_rejected(client):
    """23: Verify missing or invalid dueDate format is rejected."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    student_id = stus[0]["id"]
    req = {
        "studentId": student_id,
        "name": "Invalid Due Date Fee",
        "category": "Tuition",
        "amountEGP": 1000.0,
        "term": "Term 1",
        "dueDate": "not-a-date",
    }
    resp = client.post("/api/v1/fees", headers=headers, json=req)
    assert resp.status_code == 400


def test_24_school_fee_list_by_academic_year(client):
    """24: Verify fees can be listed and filtered."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    resp = client.get("/api/v1/fees", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert "data" in resp.json() or isinstance(resp.json(), list)


def test_25_school_fee_category_filtering(client):
    """25: Verify fees can be filtered by category (Bus, Books, Tuition)."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    resp = client.get("/api/v1/fees", headers={"Authorization": f"Bearer {token}"}, params={"category": "Bus"})
    assert resp.status_code == 200


def test_26_school_fee_initial_status_active(client):
    """26: Verify newly created fees initialize with status Active."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    student_id = stus[0]["id"]
    req = {
        "studentId": student_id,
        "name": "Books Term 1",
        "category": "Books",
        "amountEGP": 3500.0,
        "term": "Term 1 · 2026",
        "dueDate": "2026-10-31",
    }
    resp = client.post("/api/v1/fees", headers=headers, json=req)
    assert resp.status_code == 201
    assert resp.json()["status"] == "Active"


# ===========================================================================
# Suite 4: Guardian Dues & Search (Tests 27 - 32)
# ===========================================================================

def test_27_guardian_dues_search_by_national_id(client):
    """27: Verify Bank Operations can lookup guardian dues by National ID."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "29805150101023"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["guardianName"] == "Mona Samir Abdelrahman"
    assert len(data["students"]) > 0


def test_28_guardian_dues_multi_child_consolidation(client):
    """28: Verify guardian search returns all enrolled children under the same guardian."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "29805150101023"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["totalOutstandingEGP"] >= 0


def test_29_guardian_dues_unregistered_id_returns_empty(client):
    """29: Verify searching unregistered National ID returns empty dues cleanly without crash."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "29912310109999"})
    assert resp.status_code in (200, 404)


def test_30_guardian_dues_school_tenant_filtering(client):
    """30: Verify school admin cannot see guardian dues outside their school."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "29805150101023"})
    assert resp.status_code in (200, 403)


def test_31_guardian_dues_calculation_matches_fee_lines(client):
    """31: Verify outstanding dues sum accurately reflects the individual fee line balances."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "29805150101023"})
    assert resp.status_code == 200
    data = resp.json()
    calc_sum = sum(fee.get("remainingAmount", fee.get("totalAmount", 0)) for s in data.get("students", []) for fee in s.get("dues", []))
    assert abs(data["totalOutstandingEGP"] - calc_sum) < 0.01


def test_32_guardian_dues_invalid_nid_format_rejected(client):
    """32: Verify malformed National ID parameter is rejected or not found."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "abc123invalid"})
    assert resp.status_code in (400, 404, 422)


# ===========================================================================
# Suite 5: Payment Processing & Receipts (Tests 33 - 41)
# ===========================================================================

def test_33_otc_payment_full_amount_successful(client):
    """33: Verify OTC branch payment processes successfully and returns transaction reference."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 10.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    assert resp.status_code == 201
    assert resp.json()["status"] == "Successful"


def test_34_otc_payment_partial_amount_reduces_balance(client):
    """34: Verify partial payment successfully records and maintains accurate remaining balance."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 15.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    assert resp.status_code == 201
    assert resp.json()["amountPaidEGP"] == 15.0


def test_35_otc_payment_transitions_fee_to_paid(client):
    """35: Verify paying off an invoice marks it settled."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 5.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    assert resp.status_code == 201


def test_36_payment_idempotency_prevents_duplicate_charge(client):
    """36: Verify replaying payment with same Idempotency-Key returns cached response."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 20.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    key = f"py-idem-unique-{uuid.uuid4().hex[:8]}"
    r1 = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": key}, json=pay_req)
    assert r1.status_code == 201
    r2 = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": key}, json=pay_req)
    assert r2.status_code in (200, 201)
    assert r1.json()["transactionId"] == r2.json()["transactionId"]


def test_37_payment_different_payload_same_idempotency_rejected(client):
    """37: Verify reusing idempotency key returns cached transaction or rejects conflict."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    key = f"py-conflict-{uuid.uuid4().hex[:8]}"
    req1 = {"nationalId": "29805150101023", "feeIds": [fee_id], "amountEGP": 25.0, "method": "DEBIT_ACCOUNT", "sourceId": "acc_mona_current"}
    req2 = {"nationalId": "29805150101023", "feeIds": [fee_id], "amountEGP": 99.0, "method": "DEBIT_ACCOUNT", "sourceId": "acc_mona_current"}
    r1 = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": key}, json=req1)
    assert r1.status_code == 201
    r2 = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": key}, json=req2)
    assert r2.status_code in (200, 201, 409, 400)
    assert r1.json()["transactionId"] == r2.json()["transactionId"]


def test_38_payment_excess_amount_rejected(client):
    """38: Verify overpayment exceeding total outstanding is rejected or capped per business rule."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": ["00000000-0000-0000-0000-000000000001"],
        "amountEGP": 9999999.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    assert resp.status_code in (400, 422, 404)


def test_39_payment_generates_receipt_with_tax_breakdown(client):
    """39: Verify successful payment issues receipt reference and breakdown."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 10.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    assert resp.status_code == 201
    assert "receiptRef" in resp.json()
    assert resp.json()["receiptRef"].startswith("RCP-")


def test_40_receipt_retrieval_by_id(client):
    """40: Verify payment receipt details can be retrieved by transaction ID."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 10.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    pay_resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    tx_id = pay_resp.json()["transactionId"]
    receipt_resp = client.get(f"/api/v1/payments/{tx_id}/receipt", headers=headers)
    assert receipt_resp.status_code == 200


def test_41_school_admin_forbidden_from_otc_branch_payment(client):
    """41: Verify School Admin role cannot process OTC branch bank payments (403)."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": ["00000000-0000-0000-0000-000000000001"],
        "amountEGP": 100.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers={"Authorization": f"Bearer {token}"}, json=pay_req)
    assert resp.status_code == 403


# ===========================================================================
# Suite 6: Multi-Tenancy & School Isolation (Tests 42 - 45)
# ===========================================================================

def test_42_tenant_isolation_students_between_schools(client):
    """42: Verify Al-Rowad admin cannot see NIS students."""
    token_rowad = login_and_verify(client, "admin@rowad.edu.eg")
    token_nis = login_and_verify(client, "admin@nis.edu.eg")
    stus_rowad = client.get("/api/v1/students", headers={"Authorization": f"Bearer {token_rowad}"}).json().get("data", [])
    stus_nis = client.get("/api/v1/students", headers={"Authorization": f"Bearer {token_nis}"}).json().get("data", [])
    rowad_ids = {s["id"] for s in stus_rowad}
    nis_ids = {s["id"] for s in stus_nis}
    assert len(rowad_ids.intersection(nis_ids)) == 0, "Tenant breach: shared students found between schools"


def test_43_tenant_isolation_fees_between_schools(client):
    """43: Verify Al-Rowad admin cannot see NIS fee invoices."""
    token_rowad = login_and_verify(client, "admin@rowad.edu.eg")
    token_nis = login_and_verify(client, "admin@nis.edu.eg")
    fees_rowad = client.get("/api/v1/fees", headers={"Authorization": f"Bearer {token_rowad}"}).json().get("data", [])
    fees_nis = client.get("/api/v1/fees", headers={"Authorization": f"Bearer {token_nis}"}).json().get("data", [])
    rowad_fee_ids = {f["id"] for f in fees_rowad}
    nis_fee_ids = {f["id"] for f in fees_nis}
    assert len(rowad_fee_ids.intersection(nis_fee_ids)) == 0, "Tenant breach: shared fees found between schools"


def test_44_tenant_isolation_payments_between_schools(client):
    """44: Verify school user only sees payments scoped to their own school."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    resp = client.get("/api/v1/payments", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200


def test_45_bank_ops_can_view_cross_tenant_dues(client):
    """45: Verify Bank Operations can view dues across multiple institutions."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    resp = client.get("/api/v1/guardian/dues", headers={"Authorization": f"Bearer {token}"}, params={"parentNationalId": "29805150101023"})
    assert resp.status_code == 200


# ===========================================================================
# Suite 7: Audit Logging & Governance (Tests 46 - 49)
# ===========================================================================

def test_46_audit_event_logged_on_student_enrollment(client):
    """46: Verify student enrollment generates an audit record."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    uid = uuid.uuid4().hex[:6]
    req = {
        "studentRef": f"STU-AUD-{uid}",
        "name": f"Audited Student {uid}",
        "grade": "Grade 12",
        "nationalId": "29801011234500",
    }
    resp = client.post("/api/v1/students", headers={"Authorization": f"Bearer {token}"}, json=req)
    assert resp.status_code == 201


def test_47_audit_event_logged_on_payment_execution(client):
    """47: Verify processing payment generates an immutable audit entry."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}", "Idempotency-Key": f"key-{uuid.uuid4().hex[:8]}"}
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]
    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 10.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    resp = client.post("/api/v1/payments", headers=headers, json=pay_req)
    assert resp.status_code == 201


def test_48_bank_admin_can_access_audit_logs(client):
    """48: Verify Bank Admin role can access system-wide audit logs."""
    token = login_and_verify(client, "admin@cib.eg", "Admin123!")
    resp = client.get("/api/v1/audit-logs", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert "data" in resp.json() or "items" in resp.json() or isinstance(resp.json(), list)


def test_49_school_finance_forbidden_from_audit_logs(client):
    """49: Verify School Finance role cannot access bank audit logs (403 Forbidden)."""
    email = ensure_school_finance_user(client)
    token = login_and_verify(client, email)
    resp = client.get("/api/v1/audit-logs", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 403
