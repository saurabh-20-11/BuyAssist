# Jev Decision Layer

Jev is **TypeSafe AI's "System One" decision model**. Unlike a generative LLM, it
does not write prose. You send it an application **state** plus a set of typed
**questions**, and it returns **typed answers with calibrated probabilities** in
roughly 70–500 ms. In BuyAssist, Jev sits between Gemini (understanding) and the
deterministic Java policy engine (hard rules).

> Jev is **advisory decision-support inside thresholds we control. It does not
> grant payment authority and never overrides a hard constraint.**

## Where Jev fits

```
Gemini  →  structured procurement state  →  Jev  →  Java policy engine  →  AP2
         (intent + candidates, JSON)       (typed      (hard gate +        (authorize)
                                            decisions)   thresholds)
```

Gemini produces the structured state (intent + candidate list). The Java backend
first computes the deterministic quantitative score, then asks Jev a bounded set
of questions about the *same* state, then applies hard constraints and confidence
thresholds, then (and only then) proceeds to AP2.

## What we ask Jev (the bounded questions)

All questions are evaluated in parallel against one shared state. Keys below are
ours; they come back as answer keys.

| Key | Type | Question | How Java uses the answer |
|-----|------|----------|--------------------------|
| `best_match` | **Choice** | Which qualifying candidate best matches the user's stated preferences? (options are the policy-valid candidate names + `none`) | Compared against the deterministic #1. Divergence is surfaced in the Decision Trace; it does not by itself change the winner. |
| `match_quality` | **Score** | How strongly does the recommended option satisfy the user's semantic preferences? Levels: `poor`, `acceptable`, `good`, `excellent` | Thresholded. Below `acceptable` → recommend clarification / human review. |
| `needs_clarification` | **Noul** | Is there unresolved ambiguity that should be clarified with the user before continuing? | Above a tuned cutoff → Gemini asks a clarifying question before proceeding. |
| `needs_human_review` | **Noul** | Does this proposed action warrant human review beyond the normal AP2 step? | Above a tuned cutoff → escalate; in autonomous mode this *stops* automatic execution. |

Each question is atomic (one judgment). Arithmetic, counting, and numeric
comparisons are **never** asked of Jev — the backend does those in code, because
Jev is documented to be weak at math, counting, dates, and raw numbers.

## State we send (text/JSON, never audio)

Prefer named fields over a single blob. Example after Gemini interprets a
request:

```json
{
  "intent": {
    "product": "27 inch monitor",
    "quantity": 5,
    "budget": 1200,
    "required_features": ["USB-C"],
    "minimum_warranty_years": 3,
    "notes": "for the analytics team"
  },
  "candidates": [
    { "name": "Dell",   "total_price": 1095, "warranty_years": 3, "delivery_days": 2, "features": ["USB-C","HDMI"], "quantitative_score": 91 },
    { "name": "Lenovo", "total_price": 1075, "warranty_years": 1, "delivery_days": 1, "features": ["USB-C"],        "quantitative_score": 84 }
  ]
}
```

Only policy-valid candidates (those that passed hard constraints) are offered to
the `best_match` Choice, plus a `none` option.

## Wire format (api.typesafe.ai/v1/systemone)

Request:

```http
POST https://api.typesafe.ai/v1/systemone
Authorization: Bearer $TYPESAFE_API_KEY
Content-Type: application/json
```

```json
{
  "model": "jev-1.13.0",
  "state": { "...": "structured procurement state above" },
  "questions": {
    "best_match": {
      "type": "choice",
      "instructions": "Which candidate best matches the user's stated preferences and notes?",
      "criteria": { "Dell": null, "Lenovo": null, "none": "No candidate is a good match" }
    },
    "match_quality": {
      "type": "score",
      "instructions": "How strongly does the top candidate satisfy the user's semantic preferences?",
      "criteria": ["poor", "acceptable", "good", "excellent"]
    },
    "needs_clarification": {
      "type": "noul",
      "instructions": "There is unresolved ambiguity that should be clarified with the user before purchasing"
    },
    "needs_human_review": {
      "type": "noul",
      "instructions": "This proposed purchase warrants human review beyond the normal authorization step"
    }
  }
}
```

Response (shape):

```json
{
  "model": "jev-1.13.0",
  "answers": {
    "best_match":   { "type": "choice", "choice": "Dell", "confidence": 0.82, "probabilities": { "Dell": 0.82, "Lenovo": 0.18, "none": 0.0 } },
    "match_quality":{ "type": "score",  "score": 2.6, "legend": {"0":"poor","1":"acceptable","2":"good","3":"excellent"}, "probabilities": {"0":0.0,"1":0.1,"2":0.2,"3":0.7}, "confidence": 0.88 },
    "needs_clarification": { "type": "noul", "noul": 0.12 },
    "needs_human_review":  { "type": "noul", "noul": 0.07 }
  },
  "usage": { "input_tokens": 0, "output_tokens": 0 }
}
```

Notes:
- **Pin the model** (`jev-1.13.0`), because aliases like `jev-latest` move on new
  releases and would silently shift tuned thresholds. Always log the response's
  `model` field.
- **Choice/Score carry `confidence`; Noul does not** — threshold the Noul value
  itself.
- Errors to handle: `401` bad key, `422` validation (body names the field),
  `429` rate limit (honor `retry-after`, exponential backoff), `529` overloaded
  (retry after short delay).

## Confidence gating (risk-scaled)

`probabilities` tell you *what*; `confidence` tells you *whether to act*.
Thresholds scale with risk — a purchase is a financial action, so cutoffs are
conservative. All thresholds are owned by the Java policy engine and are
configuration, not model output.

| Signal | Example cutoff | Action if not met |
|--------|---------------|-------------------|
| `best_match.confidence` | ≥ 0.80 to auto-proceed | confirm with user / fall back to deterministic #1 |
| `match_quality.score` | ≥ `good` to recommend strongly | surface caveat; suggest clarification |
| `needs_clarification.noul` | ≥ 0.50 → clarify | Gemini asks a question before continuing |
| `needs_human_review.noul` | ≥ 0.40 → escalate | **autonomous mode stops and escalates to the user** |

These are starting points, tuned later on logged history.

## Integration shape (Spring Boot, server-side only)

Jev is called **directly from the Java backend** — no separate microservice. The
`TYPESAFE_API_KEY` lives in server environment/config and is **never** shipped to
Android.

- `JevClient` — thin HTTP client for `POST /v1/systemone` (auth header, retry on
  429/529, error mapping). Model id is config.
- `JevDecisionRequest` — our domain input: intent + policy-valid candidates +
  which questions to ask.
- `JevDecisionResult` — typed result: best-match choice + probabilities,
  match-quality score + legend, clarification/human-review probabilities, the
  answering model id, and raw usage, for audit.
- `JevDecisionService` — builds the `questions` map, calls `JevClient`, maps the
  response into `JevDecisionResult`, applies no business logic itself (the policy
  engine consumes the result and makes the gated decision).

## Audit requirements

Every Jev call contributes to the audit trail / Decision Trace:

- the state sent (redacted of anything sensitive),
- each question's typed answer **and** its probabilities/confidence,
- the answering `model` id,
- token `usage`,
- the resulting application action (proceed / clarify / escalate / fall back),
- the thresholds in force at decision time.

This makes the model's contribution visible and reproducible rather than hidden
in logs, and it is what the **Decision Trace** screen renders.

## Autonomous (Human-Not-Present) role

In the autonomous flow Jev is specifically featured, but still fenced:

1. Java validates **every** hard mandate constraint deterministically.
2. Jev evaluates the bounded semantic/review questions on the qualifying offer.
3. If confidence/review policy requires human review → **stop and escalate**.
4. If deterministic conditions **and** Jev thresholds pass → AP2 verifies the
   stored authorization.
5. Simulated payment executes.
6. Receipt + full audit trail are created.

Jev can **block or flag** an autonomous purchase; it can never **cause** one on
its own authority. Spend requires the deterministic gate to pass *and* AP2 to
verify the user's prior authorization.

## Sources

- Jev API overview: <https://jevapi.dev/>
- Jev cheatsheet (endpoint, question types, confidence): <https://jev-cheatsheet.vercel.app/>
- Build-your-first-decision tutorial: <https://huggingface.co/blog/sora-2/jev-ai-api-tutorial-build-your-first-structured-de>

_Third-party API details were rephrased from public documentation for licensing
compliance._
