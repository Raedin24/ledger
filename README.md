# Ledger

An offline expense tracker for Ghanaian mobile money. It reads the transaction
alerts your provider already sends you, parses them on the device, and files
them away — no account, no sync, no server.

The app holds **no `INTERNET` permission**. Not a promise in a privacy policy —
the permission is stripped from the merged manifest with `tools:node="remove"`,
so Android refuses a network call even if a bundled library attempted one. Your
transactions cannot leave the phone except through an export you perform
yourself.

Supported senders: **MTN MoMo**, **GhanaPay**, **Telecel Cash** (formerly
Vodafone Cash). Amounts in cedis.

## What it does

- **Automatic capture** — an alert arrives, and the amount, counterparty, fee and
  balance are extracted and saved. The raw message body is never written to disk.
- **Review queue** — anything the parser is unsure about waits for you to confirm
  or correct instead of quietly guessing.
- **Categories** — spending is sorted automatically; confirming a first-seen
  counterparty mints a rule that sticks.
- **History and breakdown** — filter by any date range and see where the money went.
- **Import your past** — a one-time scan of the existing inbox brings in
  transactions received before installing.
- **Export** — JSON or CSV for a spreadsheet, or a passphrase-encrypted `.ledger`
  backup you can restore onto another device.

Anything captured can be edited, recategorized or deleted afterwards.

## What it does not do

**There is no manual entry.** Transactions arrive from SMS — live capture or the
one-time inbox scan — or from restoring a backup, and that is all. Cash you
spend outside mobile money is invisible to the app, and the SMS permissions are
effectively required rather than optional: decline them and there is nothing to
capture.

## Privacy and security posture

| Measure | Where |
|---|---|
| No `INTERNET` permission, explicitly removed from the merged manifest | `app/src/main/AndroidManifest.xml` |
| Database encrypted with SQLCipher; the 32-byte passphrase is wrapped by an Android Keystore key (StrongBox where available) and never stored in plaintext | `app/…/data/crypto/` |
| Optional biometric app-open lock, deliberately separate from the DB key so the receiver can still write while the phone is locked | `app/…/security/BiometricGate` |
| `FLAG_SECURE` — no screenshots, no Recents preview | `MainActivity` |
| `allowBackup="false"` + data-extraction rules, so the ledger is never swept into a cloud backup | `AndroidManifest.xml` |
| Raw SMS bodies are transient — parsed, then dropped. `Transaction` has no body field | `core-domain` |
| No analytics, no advertising, no third-party services | — |

### On the SMS permissions

Automatic capture is what `RECEIVE_SMS` and `READ_SMS` are for. The app is
deliberately *not* a replacement SMS client: it cannot send messages and never
becomes the default SMS handler. Only messages from known mobile money senders
are examined; everything else is ignored, stored nowhere and transmitted
nowhere.

OTP safety is positive-match only, with no blocklist: the discriminator is the
**post-transaction balance**, which every real account movement reports and no
OTP or marketing message ever does. See
[`core-domain/README.md`](core-domain/README.md) for the full pipeline.

## Layout

| Module | What it is |
|---|---|
| [`core-domain`](core-domain/README.md) | Framework-free Kotlin. Parse, validate, dedup, categorize. No Android dependencies, so it stays unit-testable on the JVM and auditable in one sitting. |
| [`app`](app/README.md) | The Android layer. Compose (Material 3), MVVM + Repository, Hilt, Room + SQLCipher. |

## Building

Requires a JDK 17 or newer and the Android SDK. `core-domain` pins a Java 17
toolchain, so Gradle will auto-provision a JDK 17 (via the foojay resolver) if
one is not already installed.

```sh
# JAVA_HOME must point at a JDK — e.g. Android Studio's bundled runtime
export JAVA_HOME="/path/to/Android Studio/jbr"

./gradlew :core-domain:test :app:assembleDebug
./gradlew :app:assembleRelease      # needs signing configured; see docs/RELEASING.md
```

On Windows use `gradlew.bat`. Keep the checkout path free of spaces — a space
breaks KSP's Room schema export.

Room schemas are exported to `app/schemas/` and committed; migrations that move
data are hand-written `Migration(n, n+1)` classes rather than `AutoMigration`.

## Install

**[Download the latest release](https://github.com/Raedin24/ledger/releases/latest)**
and open the `.apk` on your phone.

Requires **Android 8.0 (Oreo) or newer** — the baseline for Keystore StrongBox
and `BiometricPrompt`.

Ledger is not on the Play Store, so Android treats it as an app from an unknown
source and will put a few warnings in the way. That is normal for a sideloaded
app and none of it means something is wrong:

1. **Allow the install.** Tapping the downloaded file brings up *"For your
   security, your phone is not allowed to install unknown apps from this
   source."* Tap **Settings**, turn on **Allow from this source**, then go back.
   You are granting this to your browser or file manager, not to Ledger.
2. **Play Protect may object.** If you see *"Unsafe app blocked"* or *"App
   scan"*, choose **Install anyway** / **More details → Install anyway**. Google
   flags apps it has not seen before; it is not a finding about this one.
3. **Open it.** The first screen explains what the app does before asking for
   anything.
4. **Allow SMS access** when prompted. This is what fills the ledger — Ledger
   reads transaction alerts from MTN MoMo, GhanaPay and Telecel Cash and parses
   them on the phone. Nothing is uploaded; the app has no network permission at
   all. Decline it and there is nothing for the app to record.
5. **Import your history.** *Settings → Data → Import past transactions* runs a
   one-time scan of alerts already in your inbox, so you do not start empty.

### Verifying the download

Every release is signed with the same key. You can check a downloaded APK
matches it:

```sh
apksigner verify --print-certs ledger-<version>.apk
```

The SHA-256 digest is published in each release's notes. If it ever differs,
the file did not come from this project — do not install it.

### Updates

The app cannot check for its own updates, because it has no network permission.
[Obtainium](https://github.com/ImranR98/Obtainium) watches this repository's
releases and handles that; point it at `https://github.com/Raedin24/ledger`.
Otherwise, check the releases page now and then.

Installing a newer version keeps your data. Uninstalling erases it — the
database is encrypted with a key held by the device, so an uninstall is
unrecoverable. Export a backup first (*Settings → Data → Create backup*) if you
ever need to remove the app.

## Status

Version 0.1.0. A passion project, built for its author and for whoever else
finds it useful. Scope is the providers that actually send the author SMS;
AirtelTigo Cash awaits real message samples, and adding a provider is a small
change — an `Institution` value plus a `SenderTemplate`.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE).

> This program is free software: you can redistribute it and/or modify it under
> the terms of the GNU General Public License as published by the Free Software
> Foundation, either version 3 of the License, or (at your option) any later
> version.
>
> This program is distributed in the hope that it will be useful, but WITHOUT
> ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
> FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
