"""Request/response DTOs for the AP2 service's internal REST API.

These are the shapes the Java backend posts and receives. They are intentionally
small and flat; internally we translate them into the official AP2 Pydantic
models (ap2.models.*) so the real protocol objects are what we build, sign-stub,
and verify.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class LineItem(BaseModel):
    label: str
    unit_price: float
    quantity: int


class CheckoutMandateRequest(BaseModel):
    """Human-present: build a CartMandate for an exact, chosen purchase."""

    cart_id: str
    merchant_name: str
    currency: str = "USD"
    items: list[LineItem]
    total: float
    user_cart_confirmation_required: bool = True
    cart_expiry_minutes: int = 15


class PaymentMandateRequest(BaseModel):
    """Build a PaymentMandate once the user authorizes on the Trusted Surface."""

    cart_id: str
    payment_mandate_id: str
    merchant_name: str
    currency: str = "USD"
    total: float
    payment_method: str = "CARD"
    payment_method_label: str = "Corporate Visa 4242"


class IntentMandateRequest(BaseModel):
    """Human-not-present: capture the open authorization constraints."""

    natural_language_description: str
    merchants: list[str] | None = None
    max_total: float
    currency: str = "USD"
    required_features: list[str] = Field(default_factory=list)
    minimum_warranty_years: int = 0
    quantity: int = 1
    expiry_days: int = 7
    # If false, the agent may purchase autonomously once conditions are met.
    user_cart_confirmation_required: bool = False


class VerifyRequest(BaseModel):
    """Verify a mandate structurally and against expiry/constraints."""

    kind: str  # "cart" | "payment" | "intent"
    mandate: dict


class ServiceResponse(BaseModel):
    ok: bool
    operation: str
    detail: str | None = None
    data: dict | None = None
