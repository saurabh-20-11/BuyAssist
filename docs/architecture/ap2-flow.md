# AP2 Flows

Two flows, both ending in a verifiable AP2 authorization and a receipt. In both,
**Jev is advisory and the deterministic gate + AP2 hold payment authority.**

## Human-Present (Trusted Surface authorization)

```
User speaks request
  → Gemini extracts structured intent (+ verbal confirmation of interpretation)
  → Backend: deterministic scoring over candidates
  → Backend: Jev evaluates best_match / match_quality / needs_clarification / needs_human_review
  → Backend policy gate:
        if needs_clarification above cutoff  → Gemini asks a clarifying question (loop)
        reject any candidate failing a hard constraint
        choose winner (deterministic #1; divergence from Jev surfaced in trace)
  → Gemini explains recommendation verbally
  → User: "prepare the purchase"
  → Backend builds exact checkout details → AP2 createCheckoutMandate()
  → Android shows DETERMINISTIC Trusted Surface (exact item, merchant, qty,
       unit price, total, constraints, mandate status). No generative AI alters
       these numbers.
  → User taps AUTHORIZE  → AP2 createPaymentMandate() + verifyMandate()
  → Mock PSP executes simulated payment
  → AP2 createReceipt()  → receipt + full audit trail shown
```

A conversational "yes" is **not** authorization. Only the Trusted Surface
AUTHORIZE action + AP2 mandate authorizes.

## Human-Not-Present (autonomous mandate) — Jev featured

Setup:

```
User: "Buy 5 monitors this week if you find something under $1,000 with USB-C
       and a 3-year warranty."
  → Gemini extracts: product constraint, quantity, max price, required features,
       warranty floor, expiration, merchant restrictions
  → Android shows DETERMINISTIC autonomous-mandate Trusted Surface
  → User explicitly AUTHORIZES the open/autonomous mandate (AP2 stores the
       constraints under which the agent may later authorize a closed checkout)
```

Later, when an offer appears (e.g. $970):

```
1. Java validates EVERY hard mandate constraint deterministically
      (price ≤ 1000, qty = 5, USB-C present, warranty ≥ 3y, merchant allowed,
       mandate not expired).  Any failure → reject, no spend.
2. Jev evaluates the bounded semantic/review questions on the qualifying offer.
3. Policy gate:
      if needs_human_review ≥ cutoff OR best_match confidence < cutoff
            → STOP and escalate to the user (no automatic purchase)
4. If deterministic conditions AND Jev thresholds pass
            → AP2 verifies the stored authorization (closed mandate)
5. Mock PSP executes simulated payment
6. AP2 createReceipt() + full audit trail; user is notified
```

Jev can **block or flag** an autonomous purchase. It can never **cause** one:
spend requires the deterministic gate to pass **and** AP2 to verify the user's
prior authorization.

## Internal AP2 service operations

Exposed to the Java backend over internal REST:

- `createCheckoutMandate()`
- `createPaymentMandate()`
- `verifyMandate()`
- `closeAutonomousMandate()`
- `createReceipt()`

## Audit timeline (example)

```
10:31:02 User intent received
10:31:05 Constraints extracted (Gemini)
10:31:06 Deterministic scores computed
10:31:07 Jev decision: best_match=Dell (conf 0.82), match_quality=excellent,
         clarify=0.12, human_review=0.07  (model jev-1.13.0)
10:31:08 Policy gate passed
10:31:08 User confirmed intent
10:31:13 Dell selected (deterministic #1, Jev agrees)
10:31:25 Checkout mandate created
10:31:31 User authorized mandate (Trusted Surface)
10:31:32 Payment mandate verified
10:31:34 Payment simulated (Authorized)
10:31:35 Receipt created
```
