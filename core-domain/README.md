# core-domain

The app's core logic in plain Kotlin: reading SMS alerts, checking them,
removing duplicates and categorising transactions. It has **no Android
dependencies**, so all of it can be unit tested on the JVM.

## How a message is processed

```
IncomingSms (only held in memory, never saved)
   │  Institution.fromSenderId      → only supported providers go further
   ▼
SmsParser                            → reads each field with its own small regex
   │  FieldExtractors                  amount, balance, fee, reference, counterparty
   ▼
TransactionValidator                 → the only thing that keeps OTPs out: needs
   │                                    a direction, an amount and a balance
   │                                    (the reference is optional)
   ├─ NotTransaction  → Discarded     (OTPs, marketing, balance replies)
   ├─ Incomplete      → NeedsAttention (format may have changed → review queue)
   └─ Valid           → Parsed(Transaction)
                          │
                          ▼
DuplicateDetector        → key from the reference, or from other fields if none
                          │
                          ▼
CategorizationEngine     → rules match on counterparty and direction
                          → first-seen sender → review → confirming can create a rule
RecurringChargeDetector  → same counterparty and amount at regular intervals
                           (tested, but no screen uses it yet)
```

`BalanceChain` sits alongside the validator. Each alert reports the new wallet
balance, so consecutive alerts form a chain. The app uses this to send a
transaction to review when its balance doesn't follow from the last one.

## Rules this module enforces

- **The SMS text is never kept.** It only exists in `IncomingSms` while it is
  parsed, is never logged, and is dropped afterwards. `Transaction` has no
  field for it.
- **OTPs are kept out by a positive match, not a blocklist.** The deciding
  field is the **balance after the transaction**: every real wallet movement
  reports one, and OTPs and marketing messages never do. That includes an OTP
  that quotes an amount, such as MTN's "approval code for payment of GHS50.00
  … is 456123", because it has no balance.
- **The provider's transaction id is the key.** It is either labelled, or, for
  Telecel, the confirmation code at the start of the message. It is stored as
  `reference` and used for dedup. Some real messages (interest credits, some
  bundles) have no id; they are deduplicated with a key built from their other
  fields instead of being sent to review.
- **The narration is only a hint.** The "Ref:", "Reference:" or "Message:"
  text is stored separately as `referenceHint` and shown during review (`uber`,
  `hospital`, `Interest for July to September 2025`). It is never used for
  matching or dedup, because the sender writes it and it can't be relied on.
- **Amounts above GHS 10,000,000 are rejected** (`Money.CEILING`). No real
  wallet comes close.
- **Money is `Money`** (minor units in a `Long`), never `Double`.
- **Rules match on the counterparty** (a number or merchant name), never on the
  free-text narration.

## Supported providers

MTN MoMo, GhanaPay and Telecel Cash, the providers with a confirmed message
format. AirtelTigo Cash isn't supported yet and needs real sample messages.
Adding a provider means an `Institution` value plus a `SenderTemplate`.

## Other pieces

| File | What it does |
|---|---|
| `model/AccountKey`, `model/OwnAccountMatcher` | Recognise the user's own wallets, so transfers between them don't count as spending. |
| `model/FiscalMonth` | A month that starts on any day the user picks. |
| `model/AmountExpression` | The sums typed on the cash keypad, such as `120+45`. |

## Tests

`./gradlew :core-domain:test` runs plain JUnit 5 on the JVM, with no emulator.

| Test | What it checks |
|---|---|
| `SmsParserTest` | A positive match for every provider and message type, plus OTP-like messages that must be rejected. |
| `RealCorpusTest` | The same steps over the shapes of real messages (with made-up names and numbers), so a format change shows up as a failing test. |
| `BalanceChainTest` | Balance arithmetic, including fees and a guessed balance that is slightly off. |
| `CeilingTest` | Amounts, balances and fees above the ceiling are dropped. |
| `OwnAccountMatcherTest` | Transfers between the user's own wallets are recognised. |
| `AccountKeyTest` | The same number written in different ways gives the same key. |
| `CashInputTest` | Fiscal months and keypad sums. |
| `DomainLogicTest` | Dedup, categorisation and recurring charges. |

## Used by

The Android app in [`../app`](../app/README.md). `SmsReceiver` and
`SmsInboxImporter` both send messages through `LedgerRepository.ingest()`, so
live capture and the inbox import use the same code. Nothing in this module
depends on Android.
