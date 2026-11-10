# BuyAssist

**🔗 Live demo:** https://buyassist-backend.onrender.com
*(Free-tier hosting — the first visit may take ~30–60s to wake the service.)*

**A voice-first AI procurement agent** that turns spoken purchasing requests into
constrained, auditable payment authorizations using Google's **Agent Payments
Protocol (AP2)**. It deliberately separates four kinds of intelligence:

- **Gemini** understands speech and communicates (intent extraction, clarification, explanations).
- **Jev** (TypeSafe AI) makes bounded, typed, probabilistic decisions (semantic match, ambiguity, human-review flags).
- **Java / Spring Boot** enforces deterministic business rules (budget, quantity, features, warranty, permissions, thresholds).
- **AP2** provides verifiable authorization (Checkout/Payment Mandates, verification, receipts).

> Payment authority lives only in the deterministic Java policy engine and AP2 —
> never in a model. A spoken "yes" is interaction; the AP2 mandate is authorization.

This is a **portfolio / learning project**. It uses simulated merchants, products,
and payments — no real financial transactions.

## Architecture at a glance

```
Voice → Gemini (understand) → structured intent
      → deterministic scoring (Java)
      → Jev decision layer (semantic judgments + confidence)
      → deterministic policy gate (hard rules + thresholds)
      → AP2 (mandates, verification, receipts) → mock payment
```

Full detail in [`docs/architecture/`](docs/architecture/):
- [`overview.md`](docs/architecture/overview.md) — the four layers and the three-way ranking
- [`jev-decision-layer.md`](docs/architecture/jev-decision-layer.md) — Jev questions, wire format, confidence gating
- [`ap2-flow.md`](docs/architecture/ap2-flow.md) — human-present and autonomous flows
- [`components.md`](docs/architecture/components.md) — monorepo layout and responsibilities

## Repository layout

```
android-app/   Java Android voice client
java-backend/  Spring Boot: scoring, Jev, policy engine, audit
ap2-service/   Python: official AP2 mandates/receipts
mock-data/     simulated merchants, products, pricing
docs/          architecture, AP2 flow, demo, screenshots
```

## The three-way ranking

Vendor selection is never delegated to a single model:

1. **Deterministic score** (Java): price 35%, feature match 25%, warranty 20%, delivery 10%, reliability 10%.
2. **Jev semantic evaluation**: does the top option match the *nuance* of the request? (Choice / Score / Noul + calibrated confidence.)
3. **Hard policy constraints** (Java): budget, quantity, features, warranty, allowed merchants, permissions — a candidate failing any is rejected regardless of score.

## Build order (status)

1. ✅ Monorepo skeleton
2. ✅ JDK 21 + Gradle
3. ✅ Spring Boot backend
4. ✅ Mock merchant/product data (24 monitors, 3 merchants)
5. ✅ Deterministic scoring engine
6. ✅ Decision layer (pluggable: Gemini default, Jev swappable)
7. ✅ Python AP2 service (official SDK)
8. ✅ Audit / Decision Trace
9. ✅ Gemini voice → structured intent (+ browser mic/speech on the web console)
10. ✅ Human-Present AP2 flow (Trusted Surface authorization)
11. ✅ Human-Not-Present AP2 flow (autonomous, fail-safe gated)
12. ⏳ Native Android voice app (optional; web console is fully voice-first today)

### Try it (web console)

1. Start the AP2 service: in `ap2-service/`, `.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8081`
2. Start the backend: in `java-backend/`, set `JAVA_HOME`, then `java -jar build/libs/buyassist-backend-0.0.1-SNAPSHOT.jar`
3. Open `http://localhost:8080` in Chrome/Edge. Tap the mic and say a request
   (e.g. "five 27-inch USB-C monitors under $1,200 with a 3-year warranty"), or
   type it. The AI extracts the constraints, the pipeline runs, and you can
   authorize the purchase through the deterministic Trusted Surface.

## Secrets

All API keys are **server-side only** (`java-backend` and `ap2-service`). The
Android app holds no keys. See [`.env.example`](.env.example) for variable names.

## Credits / references

- Google AP2 — <https://github.com/google-agentic-commerce/AP2>
- Google Gen AI Java SDK — <https://github.com/googleapis/java-genai>
- Jev / TypeSafe AI — <https://jevapi.dev/>

_Descriptions of third-party services were rephrased from their public
documentation for licensing compliance._
