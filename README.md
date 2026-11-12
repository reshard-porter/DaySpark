# DaySpark

A calm habit tracker for Android (Java + XML) with a one-time **Pro theme pack** sold through
Google Play Billing.

- Free: create habits, choose which weekdays they repeat, mark them complete, track current and
  best streaks, 30-day completion rate and a five-week history. Default theme: warm white, deep
  navy and teal.
- Pro theme pack (`dayspark_pro_themes`, one-time, non-consumable): Forest, Ocean, Lavender and
  Sunset. Anyone can preview them; applying one requires a verified purchase.

> This project was written from scratch to match the DaySpark brief because no existing app
> source was supplied. "Reskin" and "preserve the original app's data" items are therefore
> handled as forward-compatibility rules (see *Data preservation*).

## Continuous build

Every push runs `.github/workflows/android.yml`: unit tests, lint, debug APK and release AAB,
server tests, then four emulator phases (habit and purchase flow, update survival, 200% text,
accessibility checks) that produce screenshots and a screen recording. Outputs are uploaded as
workflow artifacts and also pushed to the `ci-build-outputs` and `ci-emulator-outputs`
branches. Results: `docs/TEST_REPORT.md`.

## Project layout

```
app/src/main/java/com/dayspark/app/
  DaySparkApp.java          App start: reconciles purchases, handles refunds
  data/                     SQLite storage, habit model, streak maths (pure Java)
  theme/                    Theme list, saved choice, "which theme applies" rule
  billing/                  Purchase components (see Architecture)
  ui/                       Activities, adapter, shared purchase UI
app/src/debug/...           Billing simulator + debug tools (never in release)
app/src/release/...         Release billing factory: real Play + server verification only
app/src/test/...            JUnit tests (streaks, purchase engine)
app/src/main/res/           Layouts, themes, colors, dimens, strings, icons
server/                     Minimal purchase verification service (Node.js)
tools/                      Theme generator and offline resource checker
docs/                       Design, Play Console setup, test report
```

## Build and install

Requirements: Android Studio (Narwhal or newer) or the command line with JDK 17+ and Android SDK
platform 36. The Gradle wrapper (8.14.3) downloads everything else.

```bash
# Unit tests
./gradlew test

# Debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release bundle for Google Play  -> app/build/outputs/bundle/release/app-release.aab
./gradlew bundleRelease -PdaysparkVerifyUrl=https://verify.example.com
```

The debug build starts with the **billing simulator** enabled, so the whole purchase flow works
with no Play Console setup. Switch to real Google Play in *Settings → Billing test tools*.

### Release signing

Create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=../keys/dayspark-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Create the upload key once with
`keytool -genkeypair -v -keystore keys/dayspark-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`.
Enrol in **Play App Signing** when you upload the first bundle: Google holds the app signing key
and you keep only the upload key. Back up the upload key and passwords; losing them means a
key-reset request to Google.

Keep `applicationId` (`com.dayspark.app`) unchanged after the first upload. Play ties purchases
and the listing to it. If you are replacing an existing published app, set `applicationId` to
that app's ID and sign with its existing upload key.

### Configuration

| Setting | Where | Notes |
|---|---|---|
| Verification service URL | `-PdaysparkVerifyUrl=...` or `gradle.properties` | Required for release. While it's empty, release builds never unlock Pro themes. |
| Product ID | `app/build.gradle` (`PRO_THEMES_PRODUCT_ID`) | `dayspark_pro_themes` |
| Theme colors | `tools/generate_themes.py`, then run it | Regenerates `colors.xml` and `themes.xml` |

## Architecture

Billing, purchase ownership and theme application are separate components:

| Component | Responsibility | Android-free? |
|---|---|---|
| `BillingGateway` / `PlayBillingGateway` | Talks to Google Play Billing Library 9.1.0 | – |
| `PurchaseVerifier` / `BackendPurchaseVerifier` | Asks the server whether a token is real | – |
| `PremiumEngine` | All ownership rules: verify → grant → acknowledge, retries, refunds, offline policy | Yes (unit tested) |
| `EntitlementStorage` / `PrefsEntitlementStorage` | Last *verified* ownership only | – |
| `PremiumManager` | Wires the above to Android threads | – |
| `ThemeController` | Picks the theme to apply; only asks "is premium active?" | – |

Purchase rules (enforced in `PremiumEngine`, tested in `PremiumEngineTest`):

1. **Nothing unlocks before verification.** Pending or unverified purchases never apply a Pro
   theme. Pending purchases show "Payment pending".
2. **Acknowledgement** happens only after verification (the server does it first; the app is
   the fallback). Failures retry at 2 s, 5 s, 15 s, 1 min and 5 min, then again on every app
   start until Play reports the purchase as acknowledged. Google refunds purchases that stay
   unacknowledged for three days.
3. **Duplicate callbacks** for a token already being verified or acknowledged are ignored.
4. **Recovery.** On every launch and on resume (at most once a minute) the app queries Play for
   owned purchases. That covers app restarts, reinstalls, new devices and checkouts that were
   interrupted after payment.
5. **Refunds and revocations.** Access is removed when the server reports the token as revoked
   or invalid, or when Play successfully reports that the account no longer owns the product.
   The app then returns to the default theme and tells the user once. Habit data is never
   touched.
6. **Offline policy.** A purchase that was verified before stays active for **30 days** after
   its last successful verification while the server or Play can't be reached. After that,
   themes fall back to the default until verification succeeds; ownership isn't deleted, so
   access comes back automatically. A failed query is never treated as "not owned". Ownership
   is re-verified at most once every 24 hours.

The cached entitlement is excluded from Android backup (`res/xml`), so a restored device has to
re-establish ownership with Google Play.

### Debug versus real billing

| | Debug build | Release build |
|---|---|---|
| Billing source | Simulator (default) or real Google Play | Real Google Play only |
| Verification | Simulator; server; or "trust locally" (debug only) | Server only |
| Test tools | Settings → Billing test tools | Not compiled in |

Simulator scenarios: success, cancel, decline, pending (complete it from the panel), already
owned, launch error, acknowledgement fails twice, duplicate callbacks, verification offline,
verification rejected, plus "simulate refund" and "reset".

## Design system

The colors, type scale and spacing are all resources:

- `values/colors.xml`, `values/themes.xml`: generated from one role table, so all five themes
  define the same roles. All text and button colors pass WCAG AA (see `docs/DESIGN.md`).
- `values/styles.xml`: type scale in `sp` (follows the system font size), component styles.
- `values/dimens.xml`: 4 dp spacing scale, 48 dp minimum touch targets; `values-w600dp`
  widens margins, and content is capped at 640 dp on tablets and in landscape.

Accessibility: every control has a label; check buttons announce "Completed" or "Not completed";
history cells read as "Monday, 9 November, completed"; section titles are marked as headings;
purchase status is a live region; there are no fixed text heights.

## Data preservation

- The database (`dayspark.db`) only gets **additive** migrations in `HabitDbHelper.onUpgrade`.
  Tables are never dropped or recreated.
- The theme choice and the purchase state are stored separately from habits. A refund changes
  the theme only.
- If DaySpark replaces an existing app, keep its `applicationId`, signing key, database file
  name and preference names, and map its schema in `onUpgrade` (see `docs/TEST_REPORT.md`,
  *Upgrade test*).

## Tools

```bash
python3 tools/check_resources.py   # XML parses, every @/R. reference resolves, themes match
python3 tools/generate_themes.py   # regenerate colors.xml + themes.xml
cd server && npm test              # verification service tests
```

See `docs/PLAY_CONSOLE_SETUP.md` for the store setup and `server/README.md` for deploying
the verification service.
