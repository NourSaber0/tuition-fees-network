"""Smoke tests covering the happy path and the main failure paths.

    pip install -r requirements-dev.txt
    pytest -q
"""
import pytest
from fastapi.testclient import TestClient

import httpx

THREE_DS_OTP = "123456"

HEADERS = {"X-API-Key": "wit-intern-2026"}
GOOD_CARD = {
    "number": "4111111111111111",
    "holder_name": "MONA SAMIR",
    "expiry_month": 12,
    "expiry_year": 2030,
    "cvv": "123",
}
NID = "29805150101023"          # Mona Samir, seeded


@pytest.fixture()
def client():
    with httpx.Client(base_url="http://localhost:8080") as c:
        c.post("/api/v1/admin/reset", headers={"X-API-Key": "wit-intern-2026"})
        yield c


def pay(client, card=None, amount=12000.0, capture=True, ref="ORD-1"):
    return client.post(
        "/api/v1/payments/cards",
        headers=HEADERS,
        json={
            "card": card or GOOD_CARD,
            "amount": {"value": amount, "currency": "EGP"},
            "order_reference": ref,
            "capture": capture,
        },
    )


# -- plumbing --------------------------------------------------------------


def test_health_is_open(client):
    assert client.get("/health").status_code == 200


def test_api_key_is_required(client):
    r = client.post("/api/v1/moi/validate", json={"national_id": NID})
    assert r.status_code == 401
    assert r.json()["error"]["code"] == "MISSING_API_KEY"


# -- service 1: MOI --------------------------------------------------------


def test_moi_validates_a_seeded_id(client):
    r = client.post("/api/v1/moi/validate", headers=HEADERS,
                    json={"national_id": NID, "full_name": "Mona Samir Abdelrahman"})
    body = r.json()
    assert r.status_code == 200
    assert body["valid"] is True
    assert body["holder"]["gender"] == "FEMALE"
    assert body["holder"]["governorate"] == "Cairo"
    assert body["holder"]["birth_date"] == "1998-05-15"
    assert body["holder"]["checksum_valid"] is True
    assert body["name_match"]["matched"] is True


def test_moi_rejects_a_malformed_id(client):
    r = client.post("/api/v1/moi/validate", headers=HEADERS,
                    json={"national_id": "123"})
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "INVALID_NATIONAL_ID"


def test_moi_serial_override_marks_record_blocked(client):
    r = client.post("/api/v1/moi/validate", headers=HEADERS,
                    json={"national_id": "29805150199990"})
    body = r.json()
    assert body["record_status"] == "BLOCKED"
    assert body["valid"] is False
    assert "RECORD_BLOCKED_BY_AUTHORITY" in body["reasons"]


def test_moi_flags_a_minor(client):
    r = client.post("/api/v1/moi/validate", headers=HEADERS,
                    json={"national_id": "31204010102041"})
    body = r.json()
    assert body["eligibility"]["is_adult"] is False
    assert "UNDER_MINIMUM_AGE" in body["reasons"]


# -- service 2: card payment ----------------------------------------------


def test_payment_is_approved_and_captured(client):
    r = pay(client)
    body = r.json()
    assert r.status_code == 201
    assert body["status"] == "CAPTURED"
    assert body["approved"] is True
    assert body["card"]["masked_number"] == "411111******1111"
    assert body["card"]["scheme"] == "VISA"
    assert body["captured_amount"] == 12000.0


def test_payment_is_declined_for_insufficient_funds(client):
    card = dict(GOOD_CARD, number="4000000000000002")
    r = pay(client, card=card)
    assert r.status_code == 402
    assert r.json()["error"]["code"] == "CARD_DECLINED"
    payment_id = r.json()["error"]["details"]["payment_id"]
    stored = client.get("/api/v1/payments/cards/" + payment_id, headers=HEADERS)
    assert stored.json()["status"] == "DECLINED"


def test_bad_card_number_is_rejected(client):
    card = dict(GOOD_CARD, number="4111111111111112")
    assert pay(client, card=card).json()["error"]["code"] == "INVALID_CARD_NUMBER"


def test_three_ds_flow(client):
    card = dict(GOOD_CARD, number="4000000000003220")
    started = pay(client, card=card)
    assert started.status_code == 200
    assert started.json()["status"] == "PENDING_3DS"
    payment_id = started.json()["payment_id"]

    wrong = client.post("/api/v1/payments/cards/{}/3ds".format(payment_id),
                        headers=HEADERS, json={"otp": "000000"})
    assert wrong.status_code == 402
    ok = client.post("/api/v1/payments/cards/{}/3ds".format(payment_id),
                     headers=HEADERS, json={"otp": THREE_DS_OTP})
    assert ok.json()["status"] == "CAPTURED"


def test_authorise_then_capture_then_refund(client):
    payment_id = pay(client, capture=False).json()["payment_id"]
    assert client.get("/api/v1/payments/cards/" + payment_id,
                      headers=HEADERS).json()["status"] == "AUTHORISED"

    captured = client.post("/api/v1/payments/cards/{}/capture".format(payment_id),
                           headers=HEADERS, json={})
    assert captured.json()["status"] == "CAPTURED"

    partial = client.post("/api/v1/payments/cards/{}/refund".format(payment_id),
                          headers=HEADERS, json={"amount": 2000})
    assert partial.json()["status"] == "PARTIALLY_REFUNDED"

    rest = client.post("/api/v1/payments/cards/{}/refund".format(payment_id),
                       headers=HEADERS, json={})
    assert rest.json()["status"] == "REFUNDED"
    assert rest.json()["captured_amount"] == 0.0


def test_idempotency_key_returns_the_same_payment(client):
    body = {
        "card": GOOD_CARD,
        "amount": {"value": 500, "currency": "EGP"},
        "order_reference": "ORD-IDEM",
    }
    headers = dict(HEADERS, **{"Idempotency-Key": "abc-123"})
    first = client.post("/api/v1/payments/cards", headers=headers, json=body)
    second = client.post("/api/v1/payments/cards", headers=headers, json=body)
    assert first.status_code == 201 and second.status_code == 200
    assert first.json()["payment_id"] == second.json()["payment_id"]
    r = client.get("/api/v1/payments/cards", headers=HEADERS)
    assert len(r.json()) == 1


# -- service 3: EPP --------------------------------------------------------


def test_quotes_cover_every_tenor(client):
    r = client.get("/api/v1/epp/quotes?amount=12000", headers=HEADERS)
    options = r.json()["options"]
    assert [o["tenor_months"] for o in options] == [3, 6, 12, 18, 24]
    zero_percent = options[0]
    assert zero_percent["interest_amount"] == 0.0
    assert zero_percent["admin_fee"] == 0.0
    assert zero_percent["monthly_installment"] == 4000.0


def test_plan_created_from_a_payment(client):
    payment_id = pay(client).json()["payment_id"]
    r = client.post("/api/v1/epp", headers=HEADERS,
                    json={"payment_id": payment_id, "tenor_months": 6,
                          "product_name": "Laptop"})
    plan = r.json()
    assert r.status_code == 201
    assert plan["status"] == "ACTIVE"
    assert len(plan["schedule"]) == 6
    # 12000 x 12% x 6/12 = 720 interest, admin fee capped at 120 (1%)
    assert plan["interest_amount"] == 720.0
    assert plan["admin_fee"] == 120.0
    assert plan["total_payable"] == 12840.0
    assert sum(i["amount"] for i in plan["schedule"]) == plan["total_payable"]

    linked = client.get("/api/v1/payments/cards/" + payment_id, headers=HEADERS)
    assert linked.json()["epp_plan_id"] == plan["plan_id"]


def test_a_payment_cannot_be_converted_twice(client):
    payment_id = pay(client).json()["payment_id"]
    body = {"payment_id": payment_id, "tenor_months": 6}
    client.post("/api/v1/epp", headers=HEADERS, json=body)
    again = client.post("/api/v1/epp", headers=HEADERS, json=body)
    assert again.status_code == 409
    assert again.json()["error"]["code"] == "ALREADY_CONVERTED"


def test_debit_cards_are_not_eligible(client):
    payment_id = pay(client, card=dict(GOOD_CARD, number="4000056655665556")).json()["payment_id"]
    r = client.post("/api/v1/epp", headers=HEADERS,
                    json={"payment_id": payment_id, "tenor_months": 12})
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "CARD_NOT_ELIGIBLE"


def test_small_amounts_are_rejected(client):
    payment_id = pay(client, amount=300).json()["payment_id"]
    r = client.post("/api/v1/epp", headers=HEADERS,
                    json={"payment_id": payment_id, "tenor_months": 6})
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "AMOUNT_OUT_OF_RANGE"


def test_unsupported_tenor_is_rejected(client):
    payment_id = pay(client).json()["payment_id"]
    r = client.post("/api/v1/epp", headers=HEADERS,
                    json={"payment_id": payment_id, "tenor_months": 9})
    assert r.json()["error"]["code"] == "UNSUPPORTED_TENOR"


def test_plan_can_be_cancelled(client):
    payment_id = pay(client).json()["payment_id"]
    plan_id = client.post("/api/v1/epp", headers=HEADERS,
                          json={"payment_id": payment_id,
                                "tenor_months": 3}).json()["plan_id"]
    r = client.post("/api/v1/epp/{}/cancel".format(plan_id), headers=HEADERS)
    assert r.json()["status"] == "CANCELLED"
    assert all(i["status"] == "CANCELLED" for i in r.json()["schedule"])
