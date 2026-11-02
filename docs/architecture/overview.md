# BuyAssist — Architecture Overview

BuyAssist is a **voice-first AI procurement agent**. A user describes a purchasing
need in natural speech; the system interprets it, compares vendors with
transparent analytics, lets a bounded decision model judge semantic fit, enforces
hard business rules deterministically, and authorizes the transaction through
Google's **Agent Payments Protocol (AP2)** using simulated payments.

The whole point of the project is to demonstrate a clean **separation of
responsibility** between four very different kinds of "intelligence":

| Layer | Technology | Responsibility | What it must NOT do |
|-------|-----------|----------------|---------------------|
| **Generative AI** | Gemini (Java GenAI SDK) | Understand speech, extract structured intent, ask clarifications, explain results in prose/voice | Must not authorize payment or decide the winning vendor on its own |
| **Decision layer** | Jev (TypeSafe AI, HTTP API) | Bounded, typed, probabilistic judgments: best semantic match, match quality, ambiguity, human-review flag | Must not grant payment authority or override hard rules |
| **Policy engine** | Java / Spring Boot | Deterministic hard constraints: budget, quantity, required features, warranty, allowed merchants, permissions, confidence thresholds | Must not defer a hard rule to an AI model |
| **Authorization** | AP2 (official Python SDK, isolated service) | Verifiable user authorization, Checkout/Payment Mandates, mandate verification, receipts, audit | Must not trust a conversational "yes" as authorization |

> **The one-sentence story:**
> **Gemini understands and communicates. Jev makes bounded probabilistic
> decisions. Java enforces deterministic business rules. AP2 provides verifiable
> authorization.** Payment authority lives only in Java + AP2, never in a model.

## End-to-end flow

```
                         USER
                           │  🎙 voice
                           ▼
                 ┌───────────────────┐
                 │  Android App (Java)│  mic, waveform, transcript,
                 │                    │  product cards, Trusted Surface,
                 │                    │  mandates, receipts, Decision Trace
                 └─────────┬──────────┘
                           │  REST (HTTPS)
                           ▼
          ┌──────────────────────────────────────┐
          │  Gemini / Voice AI                    │
          │  speech → structured procurement      │
          │  intent · clarifications · prose       │
          │  summaries of results                  │
          └───────────────────┬──────────────────┘
                              │  structured procurement state (JSON, not audio)
                              ▼
          ┌──────────────────────────────────────┐
          │  Spring Boot backend (Java)           │
          │                                        │
          │  1. Deterministic scoring engine       │  quantitative ranking
          │     (price 35 / match 25 / warranty 20 │
          │      / delivery 10 / reliability 10)   │
          │                                        │
          │  2. Jev Decision Layer (JevClient)     │  semantic judgments +
          │     Choice / Score / Noul + confidence │  calibrated probabilities
          │                                        │
          │  3. Deterministic Policy Engine        │  HARD gate: budget, qty,
          │     budget / qty / features / warranty │  features, warranty,
          │     / merchants / permissions /         │  merchants, permissions,
          │     confidence thresholds              │  confidence thresholds
          │                                        │
          │  4. Audit + Decision Trace recorder     │
          └───────────────────┬──────────────────┘
                              │  internal REST
                              ▼
          ┌──────────────────────────────────────┐
          │  AP2 service (Python, official SDK)   │
          │  Checkout Mandate · Payment Mandate    │
          │  verification · receipts               │
          └───────────────────┬──────────────────┘
                              ▼
                        Mock PSP → Receipt
```

## The three-way ranking (deliberate design)

Vendor selection is **not** delegated to any single model. It is the combination
of three independent signals:

1. **Deterministic quantitative score** (Java) — the weighted rubric
   (price 35%, feature match 25%, warranty 20%, delivery 10%, reliability 10%).
   Fully explainable, reproducible, no model involved.
2. **Jev semantic evaluation** — does the top-ranked option actually align with
   the *nuance* of what the user said? Returns a typed Choice (best match),
   a Score (match quality), and Noul probabilities (ambiguity / human-review).
3. **Hard policy constraints** (Java) — the non-negotiable gate. Budget,
   quantity, required features, warranty floor, allowed merchants, permissions.
   A candidate that fails any hard constraint is rejected regardless of score.

Jev **re-ranks within the set of policy-valid candidates** and flags concerns;
it never promotes a candidate that fails a hard constraint, and it never
authorizes anything.

## Why each boundary exists

- **Gemini ≠ authorization.** AP2 defines the Trusted Surface as *non-agentic*.
  A spoken "yes" is interaction, not authorization. The deterministic Trusted
  Surface and the AP2 mandate are what authorize.
- **Jev ≠ payment authority.** Jev is advisory decision-support operating inside
  thresholds the Java policy engine owns. Its own docs warn against "high-stakes
  actions without code-level permission checks" — so every Jev decision that
  could lead to spend is gated by deterministic Java checks and, for autonomous
  flows, by AP2 verification.
- **Java = the source of truth for anything comparable in code.** Jev is
  explicitly weak at math, counting, and raw numbers, so all budget/quantity/
  warranty/date arithmetic stays in Java. This is both correct design and a
  direct mitigation of Jev's documented limitations.

## Documents in this folder

- `overview.md` — this file.
- `jev-decision-layer.md` — Jev's role, questions, request/response shapes,
  confidence gating, and audit requirements.
- `ap2-flow.md` — human-present and human-not-present AP2 sequences.
- `components.md` — module responsibilities across the monorepo.

## Sources

- Google AP2: <https://github.com/google-agentic-commerce/AP2>
- Google Gen AI Java SDK: <https://github.com/googleapis/java-genai>
- Jev API (TypeSafe AI): <https://jevapi.dev/> and the Jev cheatsheet
  <https://jev-cheatsheet.vercel.app/>

_Content describing third-party services was rephrased from their public
documentation for licensing compliance._
