# app: the Android layer

The Android app built around `:core-domain`. It uses Kotlin, Jetpack Compose
(Material 3), MVVM with a repository, Hilt for dependency injection, and Room
with SQLCipher for encrypted storage. The colour palette lives in
`ui/theme/Color.kt` and the type scale in `ui/theme/Type.kt`.

## Layout

```
app/src/main/
├── AndroidManifest.xml        RECEIVE_SMS · READ_SMS · USE_BIOMETRIC ·
│                              POST_NOTIFICATIONS · INTERNET removed ·
│                              allowBackup=false
├── java/com/ledger/app/
│   ├── LedgerApp.kt           @HiltAndroidApp, loads the SQLCipher library
│   ├── MainActivity.kt        FLAG_SECURE, app lock, Compose host
│   ├── DemoSeed.kt            made-up data for the screenshot build only
│   ├── sms/
│   │   ├── SmsReceiver.kt     SMS_RECEIVED → repository.ingest()
│   │   └── SmsInboxImporter   one-time import of the existing inbox (READ_SMS)
│   ├── notifications/         capture alerts and budget alerts
│   ├── data/
│   │   ├── crypto/            the Keystore-protected SQLCipher passphrase
│   │   ├── db/                Room entities, DAOs, database and migrations
│   │   ├── backup/            JSON and CSV export, encrypted .ledger backups
│   │   ├── budget/            budget levels and when to alert
│   │   ├── prefs/             first-run state, the month's start day
│   │   ├── mapper/            domain types ↔ Room entities
│   │   └── repository/        where every capture is parsed, checked and saved
│   ├── di/AppModule.kt        Hilt setup (domain services and the database)
│   ├── security/
│   │   ├── AppLockManager     PIN storage, checking and lockouts
│   │   ├── BiometricGate      biometric unlock on top of the PIN
│   │   └── CapturePermission  RECEIVE_SMS and READ_SMS checks
│   ├── tutorial/              the setup guide's steps and progress
│   └── ui/
│       ├── theme/             palette and type
│       ├── components/        cards, charts, guide cards, shared widgets
│       ├── vm/                ViewModels.kt, one ViewModel per screen
│       ├── screens/           Overview · Review · History · Detail ·
│       │                      Breakdown · Budgets · Cash · Settings · Data ·
│       │                      Categories · CategorySetup · Rules · Lock ·
│       │                      KeyLost · Splash
│       └── LedgerApp.kt       bottom navigation (Overview, Review, History,
│                              Settings) and the other routes
└── res/…                      adaptive icon, cream launch theme, backup rules
```

## How capture works

1. `SmsReceiver` receives `SMS_RECEIVED`, which only Android can send.
2. It passes the message to `LedgerRepository.ingest()`.
3. The repository runs it through `SmsParser`, then `TransactionValidator`
   (which keeps OTPs out), `DuplicateDetector` and `CategorizationEngine`, and
   saves the result with Room.

The message text is never written to disk. `goAsync()` keeps the receiver
alive for the short database write.

`SmsInboxImporter` runs the same steps over messages already in the inbox, so
imported messages are parsed, deduplicated and categorised exactly like live
ones.

Wallet transactions reach the database only through `ingest()` or by restoring
a backup (`importBackup()`). Cash is the exception: cash received and spent is
entered by hand on the Cash screen.

## Security measures

- **No INTERNET permission.** `tools:node="remove"` also strips it if a library
  tries to add it. Check the merged manifest before each release.
- **No backups.** `allowBackup="false"` and the data-extraction rules keep the
  database out of cloud backup and device-to-device transfer.
- **Encrypted database.** The SQLCipher passphrase is 32 random bytes,
  encrypted with an Android Keystore key (StrongBox when available) and never
  stored in plain text.
- **FLAG_SECURE** on the activity, so there is no Recents preview and no
  screenshots. The `screenshot` build type is the only exception (see below).
- **Optional app lock.** A PIN, with biometric unlock on top. It is a screen
  lock, separate from the database key, so the SMS receiver can still save
  alerts while the phone is locked. Wrong PIN guesses lead to growing waits.

## Build types

| Type | For |
|---|---|
| `debug` | Everyday development. |
| `release` | Minified and resource-shrunk, `debuggable=false`, signed with the key set in `keystore.properties`. The build fails rather than produce an unsigned APK. |
| `profile` | Measuring performance. Like `release` but not minified, and signed with the debug key so it installs. Don't profile the debug build, because a debuggable app runs much slower. |
| `screenshot` | README screenshots. `debug` with `ALLOW_SCREENSHOTS=true`, the only setting that turns off `FLAG_SECURE`. |

`ALLOW_SCREENSHOTS` is `false` in `defaultConfig`, so release, and any build
type added later, keeps `FLAG_SECURE` without having to ask for it.

The `screenshot` type has `applicationIdSuffix = ".screenshot"`, so it installs
as a separate app with its own database and Keystore entry. Screenshots use
made-up demo data, and the real ledger is never opened. Uninstall it when done.

## Not done yet

- **Fonts.** The design uses Newsreader and Public Sans. To use them, add the
  TTF files to `res/font/` and point `ui/theme/Type.kt` at them. Until then the
  app uses the system serif and sans-serif.
- **Battery optimisation.** The app doesn't ask to be exempted, so on some
  phones with aggressive battery saving the receiver can be delayed.
- **Recurring charges.** `RecurringChargeDetector` in `core-domain` works, but
  no screen uses it yet.
