# Ledger

[![Download for Android](https://img.shields.io/badge/Download-Android_APK-8F4A28?style=for-the-badge&logo=android&logoColor=white)](https://raedin24.github.io/ledger/)

An offline expense tracker for Ghanaian mobile money. It reads the transaction
alerts your provider already sends you, parses them on the phone, and files
them away. There is no account, no sync and no server.

The app has **no `INTERNET` permission**. This isn't just a promise in a
privacy policy: the permission is removed from the merged manifest with
`tools:node="remove"`, so Android blocks network calls even if a bundled
library tried one. Your transactions can only leave the phone through an
export you make yourself.

Supported providers: **MTN MoMo**, **GhanaPay** and **Telecel Cash** (formerly
Vodafone Cash). Amounts are in cedis.

## What it does

- **Automatic capture.** When an alert arrives, the amount, counterparty, fee
  and balance are read and saved. The message text itself is never written to
  disk.
- **Review queue.** Anything the parser isn't sure about waits for you to
  confirm or correct, instead of being guessed. After importing old messages,
  you can clear the backlog in one go (everything before this month, or all of
  it). Cleared items stay in History.
- **Categories.** Spending is sorted automatically. When you confirm a
  counterparty for the first time, you can turn it into a rule for next time.
- **History and breakdown.** Filter by any date range and see where the money
  went.
- **Budgets.** Set a monthly limit per category. You get one notification as
  spending gets close, and one if it goes over.
- **Cash.** Record cash received and spent by hand on a calculator keypad, which
  can add up several amounts as you type, and see what's on hand. A wallet
  cash-out can be moved to the cash ledger, so the withdrawal and what you
  later buy with it aren't both counted as spending. Cash you already had is
  recorded as an adjustment, not as income.
- **Your own month.** Start the month on your payday (any day from 1 to 31;
  short months use their last day), and optionally carry each month's leftover
  savings into the next.
- **Import your past.** A one-time scan of your inbox brings in transactions
  from before you installed the app.
- **Export.** JSON, CSV for a spreadsheet, or a passphrase-encrypted `.ledger`
  backup. Restoring a backup merges it with what's already on the phone:
  nothing is deleted, and a transaction in both is kept once.

Anything captured can be edited, re-categorised or deleted afterwards.

## What it doesn't do

**Manual entry is for cash only.** Wallet transactions come from SMS (live
capture or the one-time inbox scan) or from restoring a backup. Every supported
provider is captured automatically. There is no per-provider setup, because
supporting a new provider means building and testing a parser against real
messages, which happens in a new release.

## Privacy and security

| Measure | Where |
|---|---|
| No `INTERNET` permission, explicitly removed from the merged manifest | `app/src/main/AndroidManifest.xml` |
| Database encrypted with SQLCipher. The 32-byte passphrase is encrypted with an Android Keystore key (StrongBox where available) and never stored in plain text | `app/…/data/crypto/` |
| Optional app lock (PIN, with biometrics on top). It is separate from the database key, so new alerts can still be saved while the phone is locked | `app/…/security/` |
| `FLAG_SECURE`: no screenshots, no Recents preview | `MainActivity` |
| `allowBackup="false"` and data-extraction rules, so the ledger is never included in a cloud backup | `AndroidManifest.xml` |
| SMS text is only held while it is parsed, then dropped. `Transaction` has no field for it | `core-domain` |
| No analytics, no advertising, no third-party services | |

### About the SMS permissions

`RECEIVE_SMS` and `READ_SMS` are what make automatic capture work. Ledger is
not an SMS app: it can't send messages and never becomes your default SMS app.
Only messages from supported mobile-money senders are looked at. Everything
else is ignored, and nothing is stored or sent anywhere.

OTPs are kept out by requiring a positive match, not by a blocklist. The key is
the **balance after the transaction**: every real wallet movement reports one,
and OTPs and marketing messages never do. See
[`core-domain/README.md`](core-domain/README.md) for how the parsing works.

## Layout

| Module | What it is |
|---|---|
| [`core-domain`](core-domain/README.md) | Plain Kotlin with no Android dependencies: parsing, validation, dedup and categorisation. It can be unit tested on the JVM. |
| [`app`](app/README.md) | The Android app: Compose (Material 3), MVVM with a repository, Hilt, and Room with SQLCipher. |

## Building

You need JDK 17 or newer and the Android SDK. `core-domain` asks for a Java 17
toolchain, so Gradle downloads JDK 17 (through the foojay resolver) if it isn't
installed.

```sh
# JAVA_HOME must point at a JDK, for example Android Studio's bundled runtime
export JAVA_HOME="/path/to/Android Studio/jbr"

./gradlew :core-domain:test :app:assembleDebug
./gradlew :app:assembleRelease      # needs a signing key in keystore.properties
```

On Windows, use `gradlew.bat`. Keep the checkout path free of spaces, because a
space breaks Room's schema export.

Room schemas are exported to `app/schemas/` and committed. Migrations that move
data are written by hand as `Migration(n, n+1)` classes rather than generated.

## Install

**[Open the download page](https://raedin24.github.io/ledger/)** on your phone
and tap *Download for Android*. The file is always
[`ledger.apk`](https://github.com/Raedin24/ledger/releases/latest/download/ledger.apk),
from the latest release.

Ledger needs **Android 8.0 (Oreo) or newer**.

Ledger isn't on the Play Store, so Android treats it as an app from an unknown
source and shows a few warnings. That is normal for an app installed this way,
and none of it means something is wrong:

1. **Allow the install.** Tapping the downloaded file shows *"For your
   security, your phone is not allowed to install unknown apps from this
   source."* Tap **Settings**, turn on **Allow from this source**, then go back.
   This permission is for your browser or file manager, not for Ledger.
2. **Play Protect will probably block it.** Google blocks apps from outside the
   Play Store that read SMS, usually with no *Install anyway* option. Open the
   **Play Store**, tap your profile picture, then **Play Protect → ⚙ Settings**,
   turn off **Scan apps with Play Protect**, install Ledger, and turn it back on.
3. **Open it.** The first screen explains what the app does before asking for
   anything.
4. **Allow SMS access** when asked. This is what fills the ledger: Ledger reads
   transaction alerts from MTN MoMo, GhanaPay and Telecel Cash and parses them
   on the phone. Nothing is uploaded, and the app has no network permission at
   all. Without SMS access there is nothing for the app to record.
5. **Import your history.** *Settings → Data → Import past transactions* scans
   the alerts already in your inbox once, so you don't start empty.

### Checking the download

Every release is signed with the same key. You can check that a downloaded APK
matches it:

```sh
apksigner verify --print-certs ledger.apk
```

The SHA-256 digest is published in each release's notes. If it ever differs,
the file didn't come from this project. Don't install it.

### Updates

The app can't check for updates itself, because it has no network permission.
[Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository's
releases for you: point it at `https://github.com/Raedin24/ledger`. Otherwise,
download from the same page now and then and install over the old version.

Installing a newer version keeps your data. Uninstalling erases it: the
database is encrypted with a key held by the phone, so after an uninstall the
data can't be recovered. Make a backup first (*Settings → Data → Create
backup*) if you ever need to remove the app.

## Status

Version 0.2.0. A passion project, built for its author and for anyone else who
finds it useful. It supports the providers the author receives SMS from.
AirtelTigo Cash needs real message samples first. Adding a provider is a small
change: an `Institution` value plus a `SenderTemplate`.

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
