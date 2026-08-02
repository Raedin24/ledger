# app — Android layer

The Android application that wraps `:core-domain`. Kotlin, Jetpack Compose
(Material 3), MVVM + Repository, Hilt, Room + SQLCipher. A warm editorial look —
the palette lives in `ui/theme/Color.kt`, the type scale in `ui/theme/Type.kt`.

## Layout

```
app/src/main/
├── AndroidManifest.xml        RECEIVE_SMS · READ_SMS · USE_BIOMETRIC ·
│                              POST_NOTIFICATIONS · INTERNET removed ·
│                              allowBackup=false
├── java/com/ledger/app/
│   ├── LedgerApp.kt           @HiltAndroidApp · loads SQLCipher libs
│   ├── MainActivity.kt        FLAG_SECURE · app lock · Compose host
│   ├── sms/
│   │   ├── SmsReceiver.kt     protected SMS_RECEIVED → repository.ingest()
│   │   └── SmsInboxImporter   one-time backfill of the existing inbox (READ_SMS)
│   ├── notifications/         CaptureNotifier — post-capture alert, deep-links a tab
│   ├── data/
│   │   ├── crypto/            Keystore-wrapped SQLCipher passphrase
│   │   ├── db/                Room entities, DAOs, database, migrations
│   │   ├── backup/            LedgerTransfer (JSON/CSV) · BackupCrypto (.ledger)
│   │   ├── prefs/             SetupPrefs — onboarding / first-run state
│   │   ├── mapper/            domain ↔ entity
│   │   └── repository/        the single parse→dedup→categorize→persist seam
│   ├── di/AppModule.kt        Hilt wiring (domain services + encrypted DB)
│   ├── security/
│   │   ├── AppLockManager     PIN state and verification
│   │   ├── BiometricGate      BiometricPrompt in front of the PIN
│   │   └── CapturePermission  RECEIVE_SMS / READ_SMS grant checks
│   ├── tutorial/              first-run coach marks
│   └── ui/
│       ├── theme/             Ledger palette + type
│       ├── components/        LedgerCard, rows, cedi formatting, permission banner
│       ├── vm/                ViewModels.kt — one per screen
│       ├── screens/           Overview · Review · History · Detail · Breakdown ·
│       │                      Settings · Data · Categories · CategorySetup ·
│       │                      Rules · Lock · Splash · OnboardingSender
│       └── LedgerApp.kt       bottom-nav scaffold (Overview·Review·History·Settings)
│                              plus detail/{id}, breakdown, rules, categories routes
└── res/…                      adaptive icon, cream launch theme, backup rules
```

## How capture flows

`SmsReceiver` (protected `SMS_RECEIVED`) → `LedgerRepository.ingest()` →
`SmsParser` → `TransactionValidator` (OTP guard) → `DuplicateDetector` →
`CategorizationEngine` → Room. The raw body never touches disk. A `goAsync()`
coroutine keeps the receiver alive for the single insert.

`SmsInboxImporter` runs the same pipeline over the existing inbox for the
one-time historical backfill, so imported messages get identical parsing,
dedup and categorization rather than a second code path.

There is no manual entry: `ingest()` and `importBackup()` are the only ways a
transaction reaches the database.

## Security posture wired in

- **No INTERNET permission** — plus `tools:node="remove"` to strip any that a
  merged library manifest tries to add. Verify the *merged* manifest each release.
- **`allowBackup="false"` + data-extraction rules** exclude the DB from cloud
  backup and device-to-device transfer.
- **SQLCipher** DB; passphrase is 32 random bytes wrapped by an Android Keystore
  key (StrongBox when available), never stored in plaintext.
- **FLAG_SECURE** on the activity (no Recents preview, no screenshots) — except
  in the `screenshot` build type, see below.
- **BiometricPrompt** app-open lock with device-credential fallback. It is a
  UI-session gate, intentionally *separate* from the DB key so the background
  receiver can still write while the phone is locked.

## Build types

| Type | For |
|---|---|
| `debug` | Day-to-day development. |
| `release` | Minified and resource-shrunk, `debuggable=false`, signed. Fails rather than emit an unsigned APK — see [`docs/RELEASING.md`](../docs/RELEASING.md). |
| `profile` | Frame timings. `release` minus minification, debug-signed so it installs. Never profile the debug build: `debuggable` costs ART most of its optimisation. |
| `screenshot` | Store/README captures. `debug` with `ALLOW_SCREENSHOTS=true`, which is the only thing that lifts `FLAG_SECURE`. |

`ALLOW_SCREENSHOTS` defaults to `false` in `defaultConfig`, so release — and any
build type added later — keeps `FLAG_SECURE` without having to remember to ask
for it.

The `screenshot` type carries `applicationIdSuffix = ".screenshot"`, so it
installs as a separate package with its own database and its own Keystore entry.
Screenshots are taken against seeded demo data; the real ledger is never opened,
never cleared and never photographed. Uninstall the variant when done.

## Still open

- **Fonts.** Drop `Newsreader` and `Public Sans` TTFs into `res/font/` and point
  `ui/theme/Type.kt` at them (currently falls back to system serif/sans to stay
  offline and buildable).
- **Battery-optimisation exemption.** Not requested anywhere yet; without it the
  receiver can be delayed on aggressive OEM builds.
- **Manual entry.** There is none: `ingest()` and `importBackup()` are the only
  ways a transaction reaches the database, so cash spent outside mobile money
  cannot be recorded at all.
- **Sender toggles.** `enableSender` has no counterpart, so the Live/Paused badge
  in Settings can only ever read Live once a sender is added.
