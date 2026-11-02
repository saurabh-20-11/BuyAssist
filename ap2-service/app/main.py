"""AP2 service — FastAPI wrapper around the official Google AP2 SDK.

Exposes the internal operations the Java backend calls:

    POST /ap2/checkout-mandate   -> createCheckoutMandate()  (human-present)
    POST /ap2/payment-mandate    -> createPaymentMandate()
    POST /ap2/intent-mandate     -> open mandate for autonomous (HNP) flow
    POST /ap2/verify             -> verifyMandate()
    POST /ap2/pay                -> simulated payment + receipt (createReceipt)
    GET  /health

All mandate objects are genuine ap2.models.* instances. Payments are simulated;
signatures are stubbed (see ap2_builder). This service holds no Android-facing
secrets and performs no AI — it is deterministic protocol + simulation only.
"""

from __future__ import annotations

from fastapi import FastAPI

from ap2.models.mandate import CartMandate, IntentMandate, PaymentMandate

from . import ap2_builder as builder
from .models import (
    CheckoutMandateRequest,
    IntentMandateRequest,
    PaymentMandateRequest,
    ServiceResponse,
    VerifyRequest,
)
from .payments import PAYMENT_METHODS, process_payment

app = FastAPI(title="BuyAssist AP2 Service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    return {"status": "up", "service": "ap2", "payment_methods": PAYMENT_METHODS}


@app.post("/ap2/checkout-mandate", response_model=ServiceResponse)
def create_checkout_mandate(req: CheckoutMandateRequest) -> ServiceResponse:
    mandate, signature = builder.build_cart_mandate(req)
    return ServiceResponse(
        ok=True,
        operation="createCheckoutMandate",
        detail=f"CartMandate created for {req.merchant_name}",
        data={
            "cart_mandate": mandate.model_dump(),
            "merchant_authorization": signature,
            "cart_expiry": mandate.contents.cart_expiry,
        },
    )


@app.post("/ap2/payment-mandate", response_model=ServiceResponse)
def create_payment_mandate(req: PaymentMandateRequest) -> ServiceResponse:
    mandate, user_auth = builder.build_payment_mandate(req)
    return ServiceResponse(
        ok=True,
        operation="createPaymentMandate",
        detail="PaymentMandate created with user authorization",
        data={
            "payment_mandate": mandate.model_dump(),
            "user_authorization": user_auth,
            "payment_mandate_id": req.payment_mandate_id,
        },
    )


@app.post("/ap2/intent-mandate", response_model=ServiceResponse)
def create_intent_mandate(req: IntentMandateRequest) -> ServiceResponse:
    mandate = builder.build_intent_mandate(req)
    return ServiceResponse(
        ok=True,
        operation="createIntentMandate",
        detail="Open IntentMandate created for autonomous flow",
        data={
            "intent_mandate": mandate.model_dump(),
            "intent_expiry": mandate.intent_expiry,
            "autonomous_allowed": not mandate.user_cart_confirmation_required,
        },
    )


@app.post("/ap2/verify", response_model=ServiceResponse)
def verify_mandate(req: VerifyRequest) -> ServiceResponse:
    """Structurally validate a mandate and check expiry.

    Re-parses the posted dict through the official model to confirm it conforms
    to the AP2 schema, then applies expiry checks. This is where real signature
    verification would also run in a production build.
    """
    try:
        if req.kind == "cart":
            m = CartMandate.model_validate(req.mandate)
            expired = builder.is_expired(m.contents.cart_expiry)
            signed = bool(m.merchant_authorization)
            return ServiceResponse(
                ok=(not expired and signed),
                operation="verifyMandate",
                detail=f"cart: expired={expired}, signed={signed}",
                data={"expired": expired, "signed": signed},
            )
        if req.kind == "payment":
            m = PaymentMandate.model_validate(req.mandate)
            signed = bool(m.user_authorization)
            return ServiceResponse(
                ok=signed,
                operation="verifyMandate",
                detail=f"payment: user_authorized={signed}",
                data={"user_authorized": signed},
            )
        if req.kind == "intent":
            m = IntentMandate.model_validate(req.mandate)
            expired = builder.is_expired(m.intent_expiry)
            return ServiceResponse(
                ok=(not expired),
                operation="verifyMandate",
                detail=f"intent: expired={expired}",
                data={"expired": expired, "autonomous_allowed": not m.user_cart_confirmation_required},
            )
        return ServiceResponse(ok=False, operation="verifyMandate",
                               detail=f"unknown kind '{req.kind}'")
    except Exception as e:  # pydantic validation or other
        return ServiceResponse(ok=False, operation="verifyMandate",
                               detail=f"invalid mandate: {e}")


@app.post("/ap2/pay", response_model=ServiceResponse)
def pay(req: PaymentMandateRequest) -> ServiceResponse:
    """Simulate payment for an authorized PaymentMandate and create a receipt."""
    result = process_payment(
        cart_id=req.cart_id,
        total=req.total,
        currency=req.currency,
        payment_method=req.payment_method,
    )
    receipt = None
    if result["authorized"]:
        receipt = {
            "receipt_id": builder.new_id("rcpt"),
            "cart_id": req.cart_id,
            "payment_mandate_id": req.payment_mandate_id,
            "merchant": req.merchant_name,
            "amount": result["amount"],
            "method": PAYMENT_METHODS.get(req.payment_method, req.payment_method),
            "status": "PAID",
            "issued_at": result["processed_at"],
        }
    return ServiceResponse(
        ok=result["authorized"],
        operation="pay+createReceipt",
        detail=result["detail"],
        data={"payment": result, "receipt": receipt},
    )
