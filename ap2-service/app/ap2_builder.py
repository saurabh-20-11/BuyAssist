"""Builds real Agent Payments Protocol objects using the official `ap2` SDK.

Everything here constructs genuine ap2.models.* Pydantic objects. Signing is
stubbed for the portfolio build (we record a deterministic pseudo-signature
rather than invoking real cryptographic key material), but the object shapes are
the authentic protocol shapes, so swapping in the SDK's real JWT/SD-JWT signing
later is a localized change.
"""

from __future__ import annotations

import hashlib
import json
import uuid
from datetime import UTC, datetime, timedelta

from ap2.models.mandate import (
    CartContents,
    CartMandate,
    IntentMandate,
    PaymentMandate,
    PaymentMandateContents,
)
from ap2.models.payment_request import (
    PaymentCurrencyAmount,
    PaymentDetailsInit,
    PaymentItem,
    PaymentMethodData,
    PaymentRequest,
    PaymentResponse,
)

from .models import (
    CheckoutMandateRequest,
    IntentMandateRequest,
    PaymentMandateRequest,
)


def _iso_in(**delta) -> str:
    return (datetime.now(UTC) + timedelta(**delta)).isoformat()


def _now_iso() -> str:
    return datetime.now(UTC).isoformat()


def _stub_signature(label: str, payload: dict) -> str:
    """A deterministic non-cryptographic stand-in for a real signature.

    Format mirrors how a real signature binds to a hash of the contents: we hash
    the canonical JSON and tag it. This is clearly NOT a real JWT; it exists so
    the audit trail has a verifiable integrity token in the simulated build.
    """
    canonical = json.dumps(payload, sort_keys=True, default=str).encode("utf-8")
    digest = hashlib.sha256(canonical).hexdigest()
    return f"stub-{label}:{digest}"


def build_cart_mandate(req: CheckoutMandateRequest) -> tuple[CartMandate, str]:
    """Construct a merchant-signed CartMandate for an exact purchase."""
    display_items = [
        PaymentItem(
            label=f"{li.quantity} x {li.label}",
            amount=PaymentCurrencyAmount(currency=req.currency, value=li.unit_price * li.quantity),
        )
        for li in req.items
    ]
    total_item = PaymentItem(
        label="Total",
        amount=PaymentCurrencyAmount(currency=req.currency, value=req.total),
    )
    payment_request = PaymentRequest(
        method_data=[PaymentMethodData(supported_methods="basic-card")],
        details=PaymentDetailsInit(
            id=req.cart_id,
            display_items=display_items,
            total=total_item,
        ),
    )
    contents = CartContents(
        id=req.cart_id,
        user_cart_confirmation_required=req.user_cart_confirmation_required,
        payment_request=payment_request,
        cart_expiry=_iso_in(minutes=req.cart_expiry_minutes),
        merchant_name=req.merchant_name,
    )
    signature = _stub_signature("merchant", contents.model_dump())
    return CartMandate(contents=contents, merchant_authorization=signature), signature


def build_payment_mandate(req: PaymentMandateRequest) -> tuple[PaymentMandate, str]:
    """Construct a PaymentMandate with the user's authorization (stubbed)."""
    total_item = PaymentItem(
        label="Total",
        amount=PaymentCurrencyAmount(currency=req.currency, value=req.total),
    )
    payment_response = PaymentResponse(
        request_id=req.cart_id,
        method_name=req.payment_method,
        details={"label": req.payment_method_label},
    )
    contents = PaymentMandateContents(
        payment_mandate_id=req.payment_mandate_id,
        payment_details_id=req.cart_id,
        payment_details_total=total_item,
        payment_response=payment_response,
        merchant_agent=req.merchant_name,
        timestamp=_now_iso(),
    )
    user_auth = _stub_signature("user", contents.model_dump())
    return PaymentMandate(payment_mandate_contents=contents, user_authorization=user_auth), user_auth


def build_intent_mandate(req: IntentMandateRequest) -> IntentMandate:
    """Construct an open IntentMandate for the autonomous (HNP) flow."""
    return IntentMandate(
        user_cart_confirmation_required=req.user_cart_confirmation_required,
        natural_language_description=req.natural_language_description,
        merchants=req.merchants,
        intent_expiry=_iso_in(days=req.expiry_days),
    )


def new_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:12]}"


def is_expired(iso_ts: str) -> bool:
    try:
        return datetime.fromisoformat(iso_ts) < datetime.now(UTC)
    except (ValueError, TypeError):
        return True
