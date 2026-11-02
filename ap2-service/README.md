# AP2 Service (Python)

An isolated FastAPI service that wraps Google's **official AP2 SDK** (the `ap2`
package, v0.1) to create and verify Agent Payments Protocol mandates and to run
simulated payments. The Java backend calls it over internal REST.

> All mandate objects are genuine `ap2.models.*` instances. Payments are
> simulated and signatures are stubbed (deterministic hash stand-ins) for this
> portfolio build — the object shapes are the real protocol shapes, so swapping
> in the SDK's real JWT/SD-JWT signing later is a localized change.

## Endpoints

| Method | Path | AP2 operation |
|--------|------|---------------|
| GET  | `/health` | liveness + available simulated payment methods |
| POST | `/ap2/checkout-mandate` | `createCheckoutMandate()` — CartMandate (human-present) |
| POST | `/ap2/payment-mandate`  | `createPaymentMandate()` |
| POST | `/ap2/intent-mandate`   | open IntentMandate (autonomous / human-not-present) |
| POST | `/ap2/verify`           | `verifyMandate()` — schema + expiry (+ signature later) |
| POST | `/ap2/pay`              | simulated payment + `createReceipt()` |

## Setup (Windows PowerShell)

The official AP2 SDK is **not on PyPI**, so it is installed from the Git repo.

```powershell
cd ap2-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
# Install the official AP2 SDK from GitHub:
.\.venv\Scripts\python.exe -m pip install "git+https://github.com/google-agentic-commerce/AP2.git"
```

## Run

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8081
```

Then `http://localhost:8081/health` and the interactive docs at
`http://localhost:8081/docs`.

## Simulated payment statuses

`AUTHORIZED`, `DECLINED`, `CONSTRAINT_VIOLATION`, `EXPIRED_MANDATE`. A total whose
cents equal `.13` is deterministically declined so the demo can show the
non-happy path without randomness.

## Sources

- Official AP2 repo / SDK: <https://github.com/google-agentic-commerce/AP2>

_The AP2 SDK is Apache-2.0 licensed. This service depends on it; it does not copy
its source._
