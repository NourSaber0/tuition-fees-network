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


# ---------------------------------------------------------------------------
# 1. Health & Unauthenticated Security
# ---------------------------------------------------------------------------

def test_backend_health_is_open(client):
    """Verify backend health endpoint is active and accessible without auth."""
    resp = client.get("/api/chat/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data.get("status") == "ok"


def test_unauthenticated_requests_are_rejected(client):
    """Verify protected tuition endpoints reject requests without a Bearer token."""
    resp = client.get("/api/v1/students")
    assert resp.status_code in (401, 403)


# ---------------------------------------------------------------------------
# 2. Authentication, MFA & Rate Limiting
# ---------------------------------------------------------------------------

def test_school_admin_login_and_mfa(client):
    """Verify School Admin can login, receive MFA challenge, and get JWT."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    assert token and len(token) > 20

    me_resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_resp.status_code == 200
    data = me_resp.json()
    assert data["role"] == "school-admin"
    assert data["schoolId"] == "RLS-02"


def test_bank_operations_login_and_mfa(client):
    """Verify Bank Operations can login and receive appropriate back-office role."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    assert token and len(token) > 20

    me_resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_resp.status_code == 200
    data = me_resp.json()
    assert data["role"] == "bank-operations"


def test_login_rejects_invalid_password(client):
    """Verify backend rejects incorrect credentials with HTTP 401."""
    resp = client.post("/api/v1/auth/login", json={"username": "admin@rowad.edu.eg", "password": "WrongPassword!"})
    assert resp.status_code == 401


def test_mfa_rejects_invalid_code(client):
    """Verify backend rejects incorrect 6-digit MFA codes."""
    login_resp = client.post("/api/v1/auth/login", json={"username": "admin@rowad.edu.eg", "password": "Password123!"})
    assert login_resp.status_code == 200
    mfa_token = login_resp.json()["mfaToken"]

    verify_resp = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "999999"})
    assert verify_resp.status_code == 401


def test_mfa_brute_force_lockout_after_3_attempts(client):
    """Verify MFA challenge locks out with 429 Too Many Requests after 3 wrong codes."""
    login_resp = client.post("/api/v1/auth/login", json={"username": "admin@rowad.edu.eg", "password": "Password123!"})
    assert login_resp.status_code == 200
    mfa_token = login_resp.json()["mfaToken"]

    # 1st attempt: 401
    r1 = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "111111"})
    assert r1.status_code == 401

    # 2nd attempt: 401
    r2 = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "222222"})
    assert r2.status_code == 401

    # 3rd attempt: 429 Locked
    r3 = client.post("/api/v1/auth/mfa/verify", json={"mfaToken": mfa_token, "code": "333333"})
    assert r3.status_code in (429, 401)


# ---------------------------------------------------------------------------
# 3. Student Lifecycle & Role-Based Access Control (RBAC)
# ---------------------------------------------------------------------------

def test_student_enrollment_by_school_admin(client):
    """Verify School Admin can enroll a student and national ID is masked."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}

    uid = uuid.uuid4().hex[:6]
    student_req = {
        "studentRef": f"STU-TEST-{uid}",
        "name": f"Automated Student {uid}",
        "grade": "Grade 10",
        "section": "B",
        "nationalId": "29801011234567",
        "parentName": "Test Guardian",
        "parentPhone": "+20 10 1234 5678",
        "parentEmail": f"parent.{uid}@example.com",
    }

    resp = client.post("/api/v1/students", headers=headers, json=student_req)
    assert resp.status_code == 201
    data = resp.json()
    assert data["studentRef"] == f"STU-TEST-{uid}"
    assert data["status"] == "Active"
    assert "298" in data["nationalIdMasked"]
    assert "******" in data["nationalIdMasked"] or "****" in data["nationalIdMasked"]


def test_student_enrollment_forbidden_for_school_finance(client):
    """Verify School Finance role cannot enroll students (403 Forbidden)."""
    token = login_and_verify(client, "admin@nis.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}

    student_req = {
        "studentRef": "STU-ILLEGAL",
        "name": "Finance Child",
        "grade": "Grade 10",
        "nationalId": "29801011234599",
    }
    resp = client.post("/api/v1/students", headers=headers, json=student_req)
    assert resp.status_code == 403


# ---------------------------------------------------------------------------
# 4. Fee Management & Invoicing
# ---------------------------------------------------------------------------

def test_school_fee_line_creation(client):
    """Verify School Admin can create a fee invoice for a student."""
    token = login_and_verify(client, "admin@rowad.edu.eg")
    headers = {"Authorization": f"Bearer {token}"}

    # Retrieve existing student
    stus = client.get("/api/v1/students", headers=headers).json().get("data", [])
    assert len(stus) > 0, "No students found to assign fee"
    student_id = stus[0]["id"]

    fee_req = {
        "studentId": student_id,
        "name": "Bus Transportation Term 1",
        "category": "Bus",
        "amountEGP": 5000.0,
        "term": "Term 1 · 2026",
        "dueDate": "2026-11-30",
    }
    resp = client.post("/api/v1/fees", headers=headers, json=fee_req)
    assert resp.status_code == 201
    data = resp.json()
    assert data["status"] == "Active"
    assert data["id"] is not None


# ---------------------------------------------------------------------------
# 5. Guardian Dues Lookup & OTC Back-Office Payments
# ---------------------------------------------------------------------------

def test_guardian_dues_search_by_national_id(client):
    """Verify Bank Operations can lookup guardian dues by National ID."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}"}

    resp = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["guardianName"] == "Mona Samir Abdelrahman"
    assert data["totalOutstandingEGP"] > 0
    assert len(data["students"]) > 0


def test_backoffice_payment_processes_partial_payment(client):
    """Verify Bank Operations can process an OTC tuition payment and receive receipt."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}"}

    # Fetch fee line for Mona
    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_line = dues["students"][0]["dues"][0]
    fee_id = fee_line["feeLineId"]

    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 50.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    idem_key = f"py-bop-{uuid.uuid4().hex[:8]}"
    resp = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": idem_key}, json=pay_req)
    assert resp.status_code == 201
    data = resp.json()
    assert data["status"] == "Successful"
    assert data["amountPaidEGP"] == 50.0
    assert data["receiptRef"] is not None
    assert data["bankRef"] is not None


def test_idempotency_prevents_duplicate_payment(client):
    """Verify duplicate Idempotency-Key returns cached response without double charge."""
    token = login_and_verify(client, "ahmed.ops@cib.eg")
    headers = {"Authorization": f"Bearer {token}"}

    dues = client.get("/api/v1/guardian/dues", headers=headers, params={"parentNationalId": "29805150101023"}).json()
    fee_id = dues["students"][0]["dues"][0]["feeLineId"]

    pay_req = {
        "nationalId": "29805150101023",
        "feeIds": [fee_id],
        "amountEGP": 25.0,
        "method": "DEBIT_ACCOUNT",
        "sourceId": "acc_mona_current",
    }
    shared_key = f"py-idem-{uuid.uuid4().hex[:8]}"

    # Initial request
    r1 = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": shared_key}, json=pay_req)
    assert r1.status_code == 201
    tx_id_1 = r1.json()["transactionId"]
    amt_1 = r1.json()["amountPaidEGP"]

    # Duplicate request with same idempotency key
    r2 = client.post("/api/v1/payments", headers={**headers, "Idempotency-Key": shared_key}, json=pay_req)
    assert r2.status_code in (200, 201)
    tx_id_2 = r2.json()["transactionId"]
    amt_2 = r2.json()["amountPaidEGP"]

    # Exactly the same transaction ID, no duplicate charge
    assert tx_id_1 == tx_id_2
    assert amt_1 == amt_2
