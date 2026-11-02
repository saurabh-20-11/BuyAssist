"""Simulated payment processor (mock PSP).

Returns realistic transaction statuses. No real financial credentials or rails
are used. Deterministic rules keep the demo reproducible while still exercising
the non-happy paths (decline, constraint violation, expired mandate).
"""

from __future__ import annotations

from datetime import UTC, datetime

# Simulated payment methods available to the procurement account.
PAYMENT_METHODS = {
    "CARD": "Corporate Visa 4242",
    "PROCUREMENT": "Procurement Account",
}


def process_payment(
    *,
    cart_id: str,
    total: float,
    currency: str,
    payment_method: str,
    mandate_expiry_iso: str | None = None,
) -> dict:
    """Simulate authorizing a payment. Returns a status envelope.

    Statuses: AUTHORIZED | DECLINED | CONSTRAINT_VIOLATION | EXPIRED_MANDATE.
    """
    now = datetime.now(UTC).isoformat()

    if mandate_expiry_iso:
        try:
            if datetime.fromisoformat(mandate_expiry_iso) < datetime.now(UTC):
                return _result("EXPIRED_MANDATE", cart_id, total, currency, now,
                               "The mandate had expired before settlement.")
        except (ValueError, TypeError):
            return _result("EXPIRED_MANDATE", cart_id, total, currency, now,
                           "The mandate expiry timestamp was invalid.")

    if total <= 0:
        return _result("CONSTRAINT_VIOLATION", cart_id, total, currency, now,
                       "Total must be positive.")

    # Simulated decline rule: totals ending in .13 are declined (lets the demo
    # show a decline deterministically without randomness).
    if round(total % 1, 2) == 0.13:
        return _result("DECLINED", cart_id, total, currency, now,
                       "Issuer declined the simulated authorization.")

    return _result("AUTHORIZED", cart_id, total, currency, now,
                   f"Authorized on {PAYMENT_METHODS.get(payment_method, payment_method)}.")


def _result(status, cart_id, total, currency, ts, detail) -> dict:
    return {
        "status": status,
        "cart_id": cart_id,
        "amount": {"currency": currency, "value": total},
        "processed_at": ts,
        "detail": detail,
        "authorized": status == "AUTHORIZED",
    }
