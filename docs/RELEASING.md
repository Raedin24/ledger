# Releasing

Ledger is published from two GitHub repositories:

| Repo | Visibility | Contents |
|---|---|---|
| `Raedin24/ledger-dev` | private | The real repository. Full development history, all branches. Day-to-day work happens here. |
| `Raedin24/ledger` | public | One commit per release, tagged with its version, each parented on the release before it. No development history, ever. |

Both are remotes of the same local clone: `origin` is the private one, `release`
is the public one.

## The rule

**No commit on the public repository may reach the development history.**

This is deliberate and load-bearing, not tidiness. Early development commits
contain real message samples that were replaced with invented stand-ins later,
and git preserves both sides of a replacement — the originals stay readable in
any clone that carries the history. A squash would not help, because a squashed
commit still has parents leading back to the same place.

So the public repo has its own history, made of release snapshots only. The
first was parentless; each release since is parented on the release before it,
giving a public log that reads v0.1.1 → v0.1.2 → … and never anything else. A
snapshot's tree comes from `main`, but its ancestry does not.

That means the safety check is **not** "is this commit parentless" — only the
first one was. It is "can this commit reach the development root", which is
step 5 below and must be run every time.

So: never `git push --all`, never `git push --tags`, and never `git push release
main`. Push to `release` with explicit refspecs, always.

## Versioning

Semantic versioning. The tag is `v<versionName>` and must match
`app/build.gradle.kts`:

| Change | versionName | versionCode |
|---|---|---|
| Breaking (data format, restore compatibility) | major | +1 |
| New capability, backwards compatible | minor | +1 |
| Fixes only | patch | +1 |

`versionCode` only ever increases, by one, every release — Android refuses to
install an APK whose code is not higher than the installed one. It is unrelated
to the semver numbers; do not try to encode one in the other.

Below 1.0.0 a minor bump is allowed to break things. Reaching 1.0.0 is a promise
that restoring an old backup keeps working.

## Cutting a release

Everything below runs from the normal clone, on `main`, with a clean tree.

**1. Bump the version.** Edit `versionName` and `versionCode` in
`app/build.gradle.kts`, commit, and push to the private repo:

```sh
git push origin main
```

**2. Build.**

```sh
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :core-domain:test :app:assembleRelease
```

`assembleRelease` fails deliberately when signing is not configured — see
[Signing](#signing). It must produce a **signed** APK before going further.

**3. Run the signed APK on a real device.** Not optional, and not covered by
anything above.

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c
adb shell am start -n io.github.raedin24.ledger/com.ledger.app.MainActivity
sleep 8
adb shell pidof io.github.raedin24.ledger                    # must print a pid
adb logcat -d | grep -iE "FATAL|AndroidRuntime"              # must print nothing
adb shell dumpsys gfxinfo io.github.raedin24.ledger | grep "Total frames"
```

`release` is the only variant R8 touches — `debug` is not minified and `profile`
sets `isMinifyEnabled = false` — so minification bugs are invisible to every
other build. v0.1.0 shipped an APK that crashed on launch with `CompositionLocal
LocalLifecycleOwner not present`, having passed a green build, a full test suite
and a valid signature, because nobody had started it.

A frame count in the thousands with no fatal is the evidence that matters.
`uiautomator dump` reports `could not get idle state` on these screens, which is
the app animating, not a fault.

Note `adb install` bypasses Play Protect. It is a developer path, not proof that
a user can install the APK — see [Play Protect](#play-protect).

**4. Build the snapshot commit.** Pure plumbing: `commit-tree` writes a new
commit object directly and touches neither the working tree nor any branch. The
tree comes from `main`; the parent is the previous release.

```sh
git fetch release
PREV=$(git rev-parse release/main)          # the last published snapshot
TREE=$(git rev-parse main^{tree})

SNAP=$(printf 'Ledger v0.2.0\n\nOne-line summary of the release.\n' \
  | GIT_AUTHOR_NAME="Raedin" \
    GIT_AUTHOR_EMAIL="79509805+Raedin24@users.noreply.github.com" \
    GIT_COMMITTER_NAME="Raedin" \
    GIT_COMMITTER_EMAIL="79509805+Raedin24@users.noreply.github.com" \
    git commit-tree "$TREE" -p "$PREV")

echo "$SNAP"
```

Drop `-p "$PREV"` only when publishing the very first release into an empty
repo. The noreply address is intentional: the private repo keeps the real email,
the public one does not publish it.

**5. Check it before it leaves the machine.** All four must hold:

```sh
# identical file tree to main — prints nothing
git diff --stat main "$SNAP"

# builds on the published release — prints the previous snapshot's sha
git rev-list --parents -n1 "$SNAP" | tr ' ' '\n' | tail -n +2

# adds exactly one commit to the public history — prints 1
git rev-list --count "$PREV".."$SNAP"

# cannot reach the development root — prints "clean". THE one that matters.
git merge-base --is-ancestor $(git rev-list --max-parents=0 main) "$SNAP" \
  && echo "LEAK — do not push" || echo "clean"
```

**6. Tag and push.** Explicit refspecs only:

```sh
git tag -a v0.2.0 "$SNAP" -m "Ledger v0.2.0"
git push release "$SNAP":refs/heads/main
git push release v0.2.0
```

No `--force`: the snapshot descends from what is already published, so this is
an ordinary fast-forward. If git rejects it as non-fast-forward, the parent was
wrong — rebuild from the real `release/main` rather than forcing past it.

**7. Attach the APK.** `gh release create v0.2.0 --repo Raedin24/ledger` with
the signed APK from `app/build/outputs/apk/release/`.

## Signing

Every release is signed; `assembleRelease` fails rather than emit an unsigned
APK. Credentials come from `keystore.properties` in the project root
(gitignored), or from the environment for CI:

```properties
storeFile=ledger-release.jks
storePassword=...
keyAlias=ledger
keyPassword=...
```

The keystore is generated once, from the project root. `keytool` ships inside a
JDK and there is no JDK on `PATH` on this machine, so it has to be called by
full path — Android Studio's bundled runtime has one:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
    -keystore ledger-release.jks -alias ledger -keyalg RSA -keysize 4096 -validity 10000
```

It prompts for a keystore password, then the distinguished-name fields, then a
key password (Enter reuses the keystore one). `*.jks` and `keystore.properties`
are both gitignored.

**Back it up somewhere durable.** Android refuses to update an app whose signing
certificate changed, and this app's database is encrypted with a key that does
not leave the device — so a lost keystore means every user must uninstall,
losing their entire ledger, to move to a later version. There is no recovery
path and no way to migrate them.

## Play Protect

Play Protect **hard-blocks** installation of this APK on a stock device. Not a
warning that can be dismissed — on the device this was tested on there was no
*Install anyway*, under *More details* or anywhere else.

The trigger is the permissions, not the code. Google blocks sideloaded apps
declaring `RECEIVE_SMS`, `READ_SMS`, `NOTIFICATION_LISTENER` or `ACCESSIBILITY`,
because SMS-reading financial malware is the archetype those rules exist to
stop. Ledger declares two of the four, and from the outside is shaped exactly
like the thing being blocked. Signing, open source and a clean build do not
change the verdict; nothing about the artifact can.

This is a closed loop rather than a bug to fix: Play restricts `READ_SMS` to
default SMS handlers and a narrow exemption list that an expense tracker does
not fall under, which is why the app is sideloaded — and sideloading is what
Play Protect blocks for those permissions. Switching capture to a
`NotificationListenerService` does not escape it either; that is on the same
list.

What actually exists:

- **A user can turn Play Protect scanning off**, install, and turn it back on
  (Play Store → profile → Play Protect → gear → *Scan apps with Play Protect*).
  Workable for people who already trust the author. It is a bad thing to ask of
  strangers, because "disable your malware scanner to install my finance app" is
  indistinguishable from what malware asks.
- **`adb install` bypasses it entirely**, which is why step 3 above works. That
  is a developer path and proves nothing about what a user can do.
- **Appeal to Google.** There is a review path for apps flagged in error, and an
  open-source app with no network permission at all is a strong case. It is the
  only route that fixes this for everyone rather than one phone at a time.

Documented here so it is not rediscovered as a build problem. It is not one.

## Installing updates

The app has no `INTERNET` permission and cannot check for its own updates.
[Obtainium](https://github.com/ImranR98/Obtainium) can watch the public repo's
releases and handle that — though it cannot bypass the block above either.
