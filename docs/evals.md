# BuyAssist — Evaluation Methodology

How we measure whether BuyAssist actually works. The guiding principle mirrors
the project's core thesis:

> **Grade deterministically wherever correctness is objective; reserve
> LLM-as-judge only for genuinely subjective quality — and report it with honest
> caveats.**

This matters because current research on agent evaluation shows LLM-as-judge has
real blind spots: a deployed multi-turn transaction agent study found its LLM
judge caught well under a quarter of the defects human review confirmed. So for
anything a computer can check exactly — budget math, constraint logic, ranking
order, safety gates — we check it in code, not with a model.

## What we evaluate

Agent-evaluation practice measures task completion, correct tool use, trajectory
soundness, groundedness, safety, and cost. For BuyAssist that maps to:

| Dimension | How it's graded | Where |
|-----------|-----------------|-------|
| **Hard-constraint correctness** (budget, quantity, features, warranty) | Deterministic golden cases | `PolicyGateEvalTest` |
| **Vendor ranking** (scoring rubric incl. reviews) | Deterministic expected order + component bounds | `ScoringEvalTest` |
| **Autonomous safety / fail-safe** | Deterministic scenario assertions | `AutonomousFailSafeEvalTest` |
| **Intent extraction** (speech → structured) | Field-match against a labeled dataset (live, opt-in) | `IntentExtractionEvalTest` |
| **Semantic recommendation quality** | LLM-as-judge, grounded, caveated (live, opt-in) | `RecommendationJudgeEvalTest` |
| **Trajectory** (correct stage order) | Assert the audit timeline sequence | *partially via E2E* |

## Running the evals

```powershell
cd java-backend
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot"
.\gradlew.bat test --tests "ai.buyassist.evals.*"
```

Current status: **18 deterministic tests, 0 failures** (10 policy + 4 scoring +
4 fail-safe). These run offline on every build.

The **live evals** (intent extraction + the LLM judge) are opt-in and run against
a running backend:

```powershell
# start the backend first, then:
.\gradlew.bat test --tests "ai.buyassist.evals.IntentExtractionEvalTest" `
  --tests "ai.buyassist.evals.RecommendationJudgeEvalTest" "-Deval.live=true"
```

Without `-Deval.live=true` they **skip** (JUnit assumptions), so the default build
stays deterministic and offline. If the model returns a transient 503 mid-run,
individual cases skip rather than fail — a flaky free-tier API must never show up
as a red test.

## The deterministic evals (the backbone)

### Policy gate — `PolicyGateEvalTest`
Loads `src/test/resources/evals/policy-cases.json`: 10 golden cases pairing an
intent with a candidate and the exact expected pass/fail per hard constraint
(including multi-failure and boundary cases like budget-exactly-equal). Correctness
is a fact here, so any mismatch is a real bug. This is the strongest eval because
it directly proves the hard rules — the part that must never be wrong — are correct.

### Scoring — `ScoringEvalTest`
Uses the production weights (price 30, feature 25, warranty 15, delivery 10,
reliability 10, reviews 10) and asserts expected ranking order, that the review
dimension reflects rating (4.8/5 → 96), that unknown reviews map to a neutral 60
(not a punishing 0), and that every component stays within [0, 100].

### Autonomous fail-safe — `AutonomousFailSafeEvalTest` (the most important)
Proves the structural safety guarantee: the decision layer can **block or clear**
an autonomous purchase but can **never cause** one. A controllable fake decision
provider drives four scenarios:

- decision layer **unavailable** → **ESCALATED**, no receipt
- **needs_human_review ≥ 0.40** → **ESCALATED**, no receipt
- **best-match confidence < 0.80** → **ESCALATED**, no receipt
- high confidence + low review (the only clear path) → **PURCHASED**, receipt issued

A purchase requires the deterministic hard gate to pass **and** the decision gate
to clear **and** AP2 to verify the user's prior authorization. Fail-safe default:
when in doubt, escalate.

## The LLM-as-judge evals (bounded, honest)

Some quality is genuinely subjective — e.g. did the spoken recommendation actually
match the nuance of the request? For these we use an LLM judge, but:

- **grounded**: the judge sees the deterministic winner and constraint results, not
  a blank slate, so it scores alignment rather than re-deciding;
- **bounded**: it answers typed questions (match quality, nuance adherence), not
  free-form verdicts;
- **caveated**: results are reported as advisory, with the known limitation that
  LLM judges under-detect defects, so they never gate correctness — the
  deterministic evals do that.

## Why this is the right design

The evals are themselves a demonstration of the project's thesis. Payment
correctness, constraint logic, and safety are proven deterministically and
reproducibly; AI judgment is used only where no objective answer exists, and even
then it is grounded and never authoritative. That is exactly how a trustworthy
agentic-commerce system should be measured.

## References (public, rephrased for compliance)

- Agent evaluation dimensions & trace-based evals: confident-ai.com, iternal.ai (2026).
- LLM-as-judge blind spots in production transaction agents: arXiv 2606.10315.
- LLM-as-judge techniques: deepeval.com (2026).
