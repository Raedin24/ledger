# core-domain

Framework-free Kotlin domain logic for the Personal Expense Tracker — the
capture pipeline's critical path, with **no Android dependencies** so it stays
unit-testable on the JVM and auditable in one sitting.

## Pipeline

```
IncomingSms (transient, never persisted)
   │  Institution.fromSenderId      → scope to known senders (efficiency filter)
   ▼
SmsParser                            → extract discrete fields via labelled regexes
   │  FieldExtractors                  amount / balance / fee / reference / counterparty
   ▼
TransactionValidator                 → SOLE OTP guard: strict positive template match
   │                                    (direction + amount + BALANCE; ref optional)
   ├─ NotTransaction  → Discarded     (OTPs, marketing, balance replies)
   ├─ Incomplete      → NeedsAttention (format drift → review queue)
   └─ Valid           → Parsed(Transaction)
                          │
                          ▼
DuplicateDetector        → reference key, composite fallback (idempotent)
                          │
                          ▼
CategorizationEngine     → rules keyed on {counterparty, direction}
                          → first-seen → review → confirm mints a rule
RecurringChargeDetector  → same counterparty+amount on a cadence → "recurring?"
```

## Privacy invariants encoded here

- The raw SMS body lives only in `IncomingSms`, is never stored/logged, and is
  dropped after parsing. `Transaction` has no body field.
- OTP safety is *positive-match only* — no blocklist. The **post-transaction
  balance** is the discriminator: every real wallet/account movement reports one
  and no OTP/marketing text ever does. An OTP can't satisfy the template even
  when it quotes an amount (the MTN "approval code for payment of GHS50.00 … is
  456123" hard case), because it carries no balance.
- The provider **transaction id** (labelled, or Telecel's leading confirmation
  code) is the persisted `reference` / dedup key. The reference is optional:
  real interest-credit / bundle messages have none and fall back to the
  composite dedup key rather than being bounced to review.
- The "Ref:"/"Reference:"/"Message:" **narration** is captured separately as
  `referenceHint` — a display-only aid shown during review (`uber`, `hospital`,
  `Interest for July to September 2025`). It is never a matching or dedup key,
  since it is user/terminal-authored and unreliable for that purpose.
- **Scope** is MTN MoMo, GhanaPay, Telecel Cash — the providers with a confirmed
  message template. AirtelTigo Cash is not yet supported and awaits real sample
  messages. Adding a provider is an `Institution` value plus a `SenderTemplate`.
- Amounts are `Money` (minor units / `Long`) — never `Double`.
- Categorization matches on counterparty identifier, never on the unreliable
  free-text SMS reason.

## Roadmap mapping

| File | Roadmap phase |
|---|---|
| `model/*` | Phase 1 data model |
| `parser/*`, `validation/*` | Phase 1 capture + strict validation |
| `dedup/*` | Phase 1 idempotency |
| `categorization/*` | Phase 2 categorization + review queue |

## Verification

`./gradlew :core-domain:test` — plain JUnit 5 on the JVM, no emulator.

| Test | What it pins down |
|---|---|
| `SmsParserTest` | The hard exit criterion: positive matches for every sender/type, plus OTP near-misses that must be rejected. |
| `RealCorpusTest` | The same pipeline over real captured messages, so template drift shows up as a failure rather than a surprise in the review queue. |
| `OwnAccountMatcherTest` | Self-transfers between the user's own wallets are recognised and not counted as spending. |
| `AccountKeyTest` | Account identity across the different shapes providers print. |
| `DomainLogicTest` | Dedup, categorization and recurring-charge behaviour. |

## Consumed by

The Android layer in [`../app`](../app/README.md) — `SmsReceiver` and
`SmsInboxImporter` both feed this pipeline through `LedgerRepository.ingest()`,
so live capture and historical backfill share one code path. Nothing in this
module depends on Android, and none of its logic changes to accommodate the
wrapper.
