# BuyAssist — Demo Script

A tight ~3-minute walkthrough for a recruiter or interviewer. It shows the one
idea that makes this project interesting: **AI understands and recommends, but
deterministic code and AP2 authorize payment — the model can never spend on its
own.**

## Before you start

1. Double-click **`run-demo.ps1`** (or right-click → Run with PowerShell). It
   starts both services and opens the console at `http://localhost:8080`.
2. Use **Chrome or Edge** (for the microphone). Allow mic access when prompted.
3. If the AI panel ever says "unavailable," just click again — it's a transient
   free-tier rate limit, and the deterministic results always work.

## The one-line pitch (say this first)

> "This is a voice-first AI procurement agent. You describe what you need in
> plain speech; the AI turns it into structured requirements and recommends a
> vendor — but it deliberately can't authorize payment. A deterministic policy
> engine enforces the hard rules, and Google's Agent Payments Protocol issues the
> actual authorization. So the AI advises; it never spends."

---

## Scenario 1 — Human-present purchase (~90 seconds)

1. **Speak** (tap the mic):
   > "I need five 27-inch monitors for our analytics team. Keep the total under
   > twelve hundred dollars. They must have USB-C and at least a three-year
   > warranty."

   *Point out:* the live transcript, then the form auto-fills with the extracted
   constraints. Say: **"That's unstructured speech becoming structured purchasing
   intent — the AI's job."**

2. Click **Evaluate**. Walk through what appears:
   - **Deterministic ranking** — vendors scored by a transparent model
     (price/spec/warranty/delivery/reliability). Say: **"Vendor selection is a
     reproducible scoring model, not the LLM's opinion."**
   - **Rejected by hard rules** — options that failed budget/warranty/feature.
     Say: **"These were rejected by deterministic code before the AI ever
     weighed in."**
   - **AI decision layer** — the bounded judgment (best match, quality,
     confidence) that *agrees with* the deterministic winner. Say: **"The AI is
     advisory and gated — it re-ranks within the valid set and flags concerns."**

3. Click **Prepare purchase authorization**. The **Trusted Surface** appears.
   Say: **"This is the AP2 moment. These numbers are computed by the server from
   the catalog — the AI and the browser can't alter them. A spoken 'yes' is not
   authorization; this screen is."**

4. Click **AUTHORIZE** → a **receipt** appears (simulated payment).

5. Click **View audit timeline**. Say: **"Every step is logged and labeled by
   which layer produced it — you can see exactly where AI reasoning stops and
   deterministic authorization begins."**

*(Optional 15-sec security beat:)* "If I tamper with the price in the request,
the server recomputes from the catalog and refuses it before any payment." (You
can mention this; it's verified in the code/tests.)

---

## Scenario 2 — Autonomous purchase with a fail-safe (~60 seconds)

1. Scroll to **Autonomous mandate**. Click **Create + authorize autonomous
   mandate**. Say: **"Here I delegate authority under constraints — buy five
   monitors automatically if a qualifying offer appears, within my rules."**

2. Click **Run autonomous check**. It reports **no qualifying offer** (nothing
   currently fits). Say: **"Right now nothing qualifies, so it does nothing."**

3. Click **Simulate price drop**, then **Run autonomous check** again.
   - If it **purchases**: show the autonomous receipt and the timeline. Say:
     **"The price dropped, every hard rule passed, the AI cleared its review
     gate, and AP2 verified my prior authorization — so it bought autonomously
     and logged the whole chain."**
   - If it **escalates**: Say: **"It flagged this for human review instead of
     buying — which is the fail-safe. The AI can block or clear a purchase, but
     it can never cause one on its own."**

*(The fail-safe is the strongest point: a missing or low-confidence AI decision
escalates to a human — it never auto-spends.)*

---

## If asked "why is this architecture interesting?"

> "Four layers, four different jobs. Gemini understands and communicates. A
> bounded decision layer makes typed, gated judgments. Deterministic Java owns
> every hard rule and all the arithmetic. AP2 issues verifiable authorization.
> Payment authority lives only in the deterministic layer and AP2 — never in the
> model. The decision layer is also swappable behind an interface: I run it on
> Gemini today, and I can drop in a calibrated-confidence model like Jev via one
> config flag."

## Honest framing (if it comes up)

- Payments are **simulated** (a mock processor) — this is a portfolio/learning
  build, which is exactly what AP2's own docs encourage.
- It uses **Google's official AP2 SDK** for the real mandate objects.
- The console is **voice-first in the browser**; a native Android client is a
  planned next step.
