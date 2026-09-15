"""Back-office flow: find the customer by national ID, pick one of their
accounts or cards, pay from it, and optionally turn it into instalments.

    pip install -r requirements-dev.txt
    pytest -q
"""
import pytest
from fastapi.testclient import TestClient

import httpx

HEADERS = {"X-API-Key": "wit-intern-2026"}
MONA = "29805150101023"
NOUR = "30103222103442"
AHMED = "29511020204536"
HEBA = "28809252506666"
MALAK = "31204010102041"        # has an MOI record, but is not a bank customer


@pytest.fixture()
def client():
    with httpx.Client(base_url="http://localhost:8080") as c:
        c.post("/api/v1/admin/reset", headers={"X-API-Key": "wit-intern-2026"})
        yield c


def lookup(client, nid):
    return client.get("/api/v1/customers", headers=HEADERS, params={"national_id": nid})


def product(client, nid, product_id):
    body = lookup(client, nid).json()
    for item in body["accounts"] + body["cards"]:
        if product_id in (item.get("account_id"), item.get("card_id")):
            return item
    raise AssertionError("{} not found on {}".format(product_id, nid))


def pay(client, source_id, amount=5000.0, capture=True, headers=None):
    return client.post(
        "/api/v1/backoffice/payments",
        headers=headers or HEADERS,
        json={
            "source_id": source_id,
            "amount": {"value": amount, "currency": "EGP"},
            "reference": "BO-1",
            "capture": capture,
        },
    )


def action(client, payment_id, name, body=None):
    return client.post("/api/v1/backoffice/payments/{}/{}".format(payment_id, name),
                       headers=HEADERS, json=body or {})


def epp(client, **body):
    return client.post("/api/v1/epp", headers=HEADERS, json=body)


# -- service 4: customer lookup --------------------------------------------


def test_lookup_returns_the_customers_accounts_and_cards(client):
    r = lookup(client, MONA)
    body = r.json()
    assert r.status_code == 200
    assert body["customer_id"] == "cif_100001"
    assert body["full_name_en"] == "Mona Samir Abdelrahman"
    assert [a["account_id"] for a in body["accounts"]] == ["acc_mona_current", "acc_mona_savings"]
    assert [c["card_id"] for c in body["cards"]] == ["card_mona_visa", "card_mona_debit"]
    debit = body["cards"][1]
    assert debit["type"] == "DEBIT"
    assert debit["linked_account_id"] == "acc_mona_current"


def test_lookup_hides_internal_card_flags(client):
    card = lookup(client, HEBA).json()["cards"][0]
    assert not any(key.startswith("_") for key in card)


def test_lookup_of_someone_who_is_not_a_customer(client):
    r = lookup(client, MALAK)
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "CUSTOMER_NOT_FOUND"


def test_lookup_rejects_a_malformed_id(client):
    r = lookup(client, "123")
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "INVALID_NATIONAL_ID"


# -- service 5: paying from an account ---------------------------------------


def test_account_payment_posts_and_reduces_the_balance(client):
    r = pay(client, "acc_mona_current", amount=12000)
    body = r.json()
    assert r.status_code == 201
    assert body["status"] == "POSTED"
    assert body["customer_id"] == "cif_100001"
    assert body["source"]["type"] == "ACCOUNT"
    assert product(client, MONA, "acc_mona_current")["available_balance"] == 38000.0


def test_account_without_enough_money_is_declined(client):
    r = pay(client, "acc_nour_current", amount=3000)
    assert r.status_code == 402
    error = r.json()["error"]
    assert error["code"] == "INSUFFICIENT_FUNDS"
    stored = client.get("/api/v1/backoffice/payments/" + error["details"]["payment_id"],
                        headers=HEADERS)
    assert stored.json()["status"] == "DECLINED"
    assert product(client, NOUR, "acc_nour_current")["available_balance"] == 2500.0


@pytest.mark.parametrize("account_id", ["acc_ahmed_current", "acc_salma_savings"])
def test_frozen_and_dormant_accounts_cannot_be_debited(client, account_id):
    r = pay(client, account_id)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "ACCOUNT_NOT_ACTIVE"


def test_account_debits_cannot_be_authorise_only(client):
    r = pay(client, "acc_mona_current", capture=False)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "INVALID_REQUEST"


def test_account_payment_cannot_be_captured_or_voided(client):
    payment_id = pay(client, "acc_mona_current").json()["payment_id"]
    for name in ("capture", "void"):
        r = action(client, payment_id, name)
        assert r.status_code == 409
        assert r.json()["error"]["code"] == "INVALID_PAYMENT_STATE"


def test_refund_puts_the_money_back_on_the_account(client):
    payment_id = pay(client, "acc_mona_current", amount=10000).json()["payment_id"]
    r = action(client, payment_id, "refund", {"amount": 4000})
    assert r.json()["status"] == "PARTIALLY_REFUNDED"
    assert product(client, MONA, "acc_mona_current")["available_balance"] == 44000.0


# -- service 5: paying by card -----------------------------------------------


def test_credit_card_payment_uses_the_available_limit(client):
    r = pay(client, "card_mona_visa", amount=24000)
    body = r.json()
    assert r.status_code == 201
    assert body["status"] == "CAPTURED"
    assert body["source"]["masked_number"] == "411111******1111"
    card = product(client, MONA, "card_mona_visa")
    assert card["credit_limit"] == 100000.0
    assert card["available_limit"] == 76000.0


def test_debit_card_spends_from_its_linked_account(client):
    assert pay(client, "card_mona_debit", amount=1500).status_code == 201
    assert product(client, MONA, "acc_mona_current")["available_balance"] == 48500.0


def test_payment_above_the_credit_limit_is_declined(client):
    r = pay(client, "card_nour_mastercard", amount=12000)
    assert r.status_code == 402
    assert r.json()["error"]["code"] == "CARD_DECLINED"
    assert r.json()["error"]["details"]["response_code"] == "61"


def test_blocked_card_is_rejected(client):
    r = pay(client, "card_ahmed_visa")
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "CARD_BLOCKED"


def test_expired_card_is_rejected(client):
    r = pay(client, "card_salma_visa")
    assert r.status_code == 402
    assert r.json()["error"]["code"] == "CARD_EXPIRED"


def test_issuer_outage_is_retryable(client):
    r = pay(client, "card_heba_visa")
    assert r.status_code == 502
    assert r.json()["error"]["code"] == "ISSUER_UNAVAILABLE"
    assert r.json()["error"]["details"]["retryable"] is True


def test_authorise_then_void_releases_the_hold(client):
    payment = pay(client, "card_mona_visa", amount=30000, capture=False).json()
    assert payment["status"] == "AUTHORISED"
    assert product(client, MONA, "card_mona_visa")["available_limit"] == 70000.0

    assert action(client, payment["payment_id"], "void").json()["status"] == "VOIDED"
    assert product(client, MONA, "card_mona_visa")["available_limit"] == 100000.0


def test_partial_capture_releases_the_rest(client):
    payment_id = pay(client, "card_mona_visa", amount=30000, capture=False).json()["payment_id"]
    r = action(client, payment_id, "capture", {"amount": 20000})
    assert r.json()["status"] == "CAPTURED"
    assert product(client, MONA, "card_mona_visa")["available_limit"] == 80000.0


def test_unknown_source_is_not_found(client):
    r = pay(client, "acc_nobody")
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "SOURCE_NOT_FOUND"


def test_idempotency_key_charges_once(client):
    headers = dict(HEADERS, **{"Idempotency-Key": "bo-abc"})
    first = pay(client, "acc_mona_current", amount=1000, headers=headers)
    second = pay(client, "acc_mona_current", amount=1000, headers=headers)
    assert first.status_code == 201 and second.status_code == 200
    assert first.json()["payment_id"] == second.json()["payment_id"]
    assert product(client, MONA, "acc_mona_current")["available_balance"] == 49000.0


def test_list_filters_by_customer(client):
    pay(client, "acc_mona_current")
    pay(client, "card_ahmed_mastercard")
    r = client.get("/api/v1/backoffice/payments", headers=HEADERS,
                   params={"customer_id": "cif_100001"})
    assert r.json()["total"] == 1


# -- service 3: EPP on top of the back-office flow ---------------------------


def test_card_payment_can_become_an_instalment_plan(client):
    payment_id = pay(client, "card_mona_visa", amount=12000).json()["payment_id"]
    r = epp(client, payment_id=payment_id, tenor_months=6)
    plan = r.json()
    assert r.status_code == 201
    assert plan["card_id"] == "card_mona_visa"
    assert plan["customer"]["customer_id"] == "cif_100001"
    assert plan["total_payable"] == 12840.0

    linked = client.get("/api/v1/backoffice/payments/" + payment_id, headers=HEADERS)
    assert linked.json()["epp_plan_id"] == plan["plan_id"]
    # the payment already used the limit; converting it must not use it again
    assert product(client, MONA, "card_mona_visa")["available_limit"] == 88000.0


def test_account_payment_cannot_become_an_instalment_plan(client):
    payment_id = pay(client, "acc_mona_current", amount=12000).json()["payment_id"]
    r = epp(client, payment_id=payment_id, tenor_months=6)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "CARD_NOT_ELIGIBLE"


def test_cancelling_a_converted_plan_frees_the_payment(client):
    payment_id = pay(client, "card_mona_visa", amount=12000).json()["payment_id"]
    plan_id = epp(client, payment_id=payment_id, tenor_months=3).json()["plan_id"]
    client.post("/api/v1/epp/{}/cancel".format(plan_id), headers=HEADERS)
    linked = client.get("/api/v1/backoffice/payments/" + payment_id, headers=HEADERS)
    assert linked.json()["epp_plan_id"] is None


def test_plan_straight_from_a_card_holds_the_limit_until_cancelled(client):
    r = epp(client, card_id="card_mona_visa", tenor_months=3,
            amount={"value": 9000, "currency": "EGP"})
    assert r.status_code == 201
    assert product(client, MONA, "card_mona_visa")["available_limit"] == 91000.0

    client.post("/api/v1/epp/{}/cancel".format(r.json()["plan_id"]), headers=HEADERS)
    assert product(client, MONA, "card_mona_visa")["available_limit"] == 100000.0


def test_plan_on_a_debit_card_id_is_not_eligible(client):
    r = epp(client, card_id="card_mona_debit", tenor_months=6,
            amount={"value": 9000, "currency": "EGP"})
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "CARD_NOT_ELIGIBLE"


def test_plan_above_the_available_limit_is_declined(client):
    r = epp(client, card_id="card_nour_mastercard", tenor_months=6,
            amount={"value": 12000, "currency": "EGP"})
    assert r.status_code == 402
    assert r.json()["error"]["code"] == "CARD_DECLINED"


def test_plan_needs_exactly_one_source(client):
    r = epp(client, card_id="card_mona_visa", payment_id="pay_x", tenor_months=6)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "INVALID_REQUEST"


# -- reset -----------------------------------------------------------------


def test_reset_restores_opening_balances(client):
    pay(client, "acc_mona_current", amount=20000)
    client.post("/api/v1/admin/reset", headers=HEADERS)
    assert product(client, MONA, "acc_mona_current")["available_balance"] == 50000.0
    assert client.get("/api/v1/backoffice/payments", headers=HEADERS).json()["total"] == 0
