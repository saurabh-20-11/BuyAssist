# Components & Monorepo Layout

```
BuyAssist/
├── android-app/            Java Android client (voice-first UI)
│   ├── voice/              mic capture, waveform, transcription plumbing
│   ├── ui/                 home, intent, comparison, Trusted Surface,
│   │                       mandates, receipts, Decision Trace
│   ├── api/                REST client to the backend
│   ├── models/             DTOs mirrored from backend
│   └── mandates/           authorization-screen rendering (deterministic)
│
├── java-backend/           Spring Boot (the brain, owns all hard rules)
│   ├── controller/         REST endpoints for the app
│   ├── service/            orchestration
│   ├── agent/              Gemini integration (intent, clarification, prose)
│   ├── jev/                Jev decision layer
│   │                         JevClient, JevDecisionService,
│   │                         JevDecisionRequest, JevDecisionResult
│   ├── procurement/        procurement state, policy engine (HARD gate)
│   ├── scoring/            deterministic weighted scoring model
│   ├── merchant/           mock merchant/product access + pricing
│   ├── audit/              audit trail + Decision Trace assembly
│   ├── model/              domain entities
│   └── repository/         persistence
│
├── ap2-service/            Python, official AP2 SDK (isolated service)
│   ├── mandates/           checkout + payment mandate creation
│   ├── verification/       mandate verification
│   ├── payments/           mock PSP integration
│   └── receipts/           receipt generation
│
├── mock-data/              seed data for the simulation
│   ├── merchants/          OfficeHub, TechMart, BusinessDirect
│   ├── products/           ~20–30 monitors across merchants
│   └── pricing/            price schedules (can change → autonomous demo)
│
└── docs/
    ├── architecture/       overview, jev-decision-layer, ap2-flow, components
    ├── ap2-flow/           sequence diagrams / notes
    ├── screenshots/        UI captures for the portfolio
    └── demo/               demo script (both AP2 modes)
```

## Responsibility summary

- **android-app (Java):** voice I/O, visual rendering, the *deterministic*
  Trusted Surface, Decision Trace display. Holds **no** secrets (no Jev key, no
  Gemini key — those are backend-side).
- **java-backend (Spring Boot):** authoritative for all hard business rules,
  scoring, Jev orchestration, policy gating, audit. Keeps `TYPESAFE_API_KEY` and
  Gemini credentials server-side.
- **ap2-service (Python):** the only component that creates/verifies AP2 mandates
  and receipts. Uses Google's official AP2 implementation.
- **mock-data:** simulated merchants/products/pricing. No real commerce.

## Config / secrets (server-side only)

| Secret | Lives in | Never in |
|--------|----------|----------|
| `TYPESAFE_API_KEY` (Jev) | java-backend env/config | Android, repo |
| Gemini API key | java-backend env/config | Android, repo |
| AP2 signing material | ap2-service env/config | Android, repo |

`.env` files are git-ignored; an `.env.example` documents the variable names.
