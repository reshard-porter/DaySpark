# DaySpark test report

Date: 12 November 2026. The build environment had JDK 21 and Node 22 but **no Android SDK and
no access to Google's Maven repository**. So anything that compiles, packages or runs the
Android app itself is still to do. Results are grouped by what has actually run.

## Completed

| Area | What ran | Result |
|---|---|---|
| Purchase rules | `PremiumEngineTest`: 21 JUnit tests against the real `PremiumEngine` with fake Play, server, storage and clock | **21/21 pass** |
| Streak maths | `StreakCalculatorTest`: 6 JUnit tests (rest days, open today, best streak, 30-day rate) | **6/6 pass** |
| Tests catch bugs | Mutation check: removing the duplicate-callback guard fails 1 test; letting pending purchases through fails 2 tests | Confirmed |
| Verification service | 12 `node:test` tests: verified, server acknowledgement, acknowledgement failure, pending, canceled, consumed, unknown token, Play outage, wrong package/product, RTDN refund, voided sweep, HTTP routing and RTDN auth | **12/12 pass** |
| Server start | `npm start` with the real `googleapis` 189: health check and request validation answer; Play calls fail only for missing credentials (expected) | Pass |
| Java syntax | All 45 Java files (main, debug, release, test) parsed by `javac` | Pass |
| Resources | `tools/check_resources.py`: every XML parses; every `@string/@color/@dimen/@style/@drawable/@id` and every `R.*` reference in Java resolves for both debug and release; all five themes define identical roles | Pass |
| Contrast | All text, button and outline pairs in all five themes meet WCAG AA (table in `DESIGN.md`) | Pass |

Purchase scenarios covered by unit tests: success with acknowledgement; pending never unlocks
(both Play-pending and server-pending); unverified stays locked and retries with backoff;
forged token rejected; cancel; decline (insufficient funds sub-code); purchase sheet fails to
open; already owned → restore; duplicate callbacks verify and acknowledge once; acknowledgement
retry with backoff; server-side acknowledgement isn't repeated; interrupted checkout recovered
on start; reinstall restore; unacknowledged purchase acknowledged on next start; restore with
nothing owned; refund detected via Play; refund detected via server; offline within 30-day
grace keeps access; offline beyond grace locks without deleting ownership and unlocks after
verification; a failed Play query is not treated as a refund.

## Awaiting an Android SDK build

Run `./gradlew test lint assembleDebug bundleRelease` in Android Studio. Not yet done:

- Full compilation against Android, AndroidX, Material 1.13 and Play Billing 9.1.0. Library
  calls were checked against current documentation, but expect possible small API fixes on
  the first build.
- Android Lint.
- Producing the debug APK and release AAB.

## Awaiting a device or emulator

| # | Test | Expected |
|---|---|---|
| H1 | First launch | Launch screen until the dashboard loads; empty state "Start your first habit" |
| H2 | Create habit | Name required ("Give your habit a name"); at least one day; "Habit created" |
| H3 | Mark complete | Check fills with the accent color, progress card updates, streak updates, TalkBack says "Completed" |
| H4 | Edit, delete | Changes saved; delete asks for confirmation and removes history |
| H5 | Detail | Streaks, 30-day %, total, five-week grid; each cell readable with TalkBack |
| H6 | Data survives update | Install build N, add habits, install build N+1 over it: all habits and history remain |
| H7 | Theme persistence | Apply a Pro theme, force-stop, reopen: same theme on every screen |
| H8 | Free users | Without a purchase, every habit feature works; Pro themes can be previewed, not applied |
| A1 | Font size 200% | No clipped text; grid cells grow |
| A2 | TalkBack | All controls labelled; headings navigable; purchase status announced |
| A3 | Small phone (360×640 dp) and tablet | Layouts fit; content capped at 640 dp on tablets |
| D1–D10 | Debug simulator | Run each scenario in Settings → Billing test tools; outcomes match the purchase scenarios above |
| S1 | Screenshots and demo | Record dashboard → create → complete, and gallery → preview → purchase, with `adb shell screenrecord` |

## Awaiting Play Console setup (external)

Tests B1–B14 in `PLAY_CONSOLE_SETUP.md` need: the app in a Play Console account; a bundle on
internal testing; the active `dayspark_pro_themes` product; license testers; the deployed
verification service with a linked service account; optionally RTDN.

## Remaining dependencies

1. **Android SDK build**: compile, lint, APK and AAB. Not possible in this environment.
2. **Upload keystore** and `keystore.properties`. Owner's decision; never commit them.
3. **Play Console**: app, internal track, product `dayspark_pro_themes`, license testers.
4. **Google Cloud**: Play Developer API enabled, service account invited in Play Console.
5. **Hosting** for `server/` (HTTPS URL), then build with `-PdaysparkVerifyUrl=...`.
6. **Optional**: Pub/Sub RTDN topic; a shared database for revoked tokens if you scale out;
   Play Integrity.
7. **Store listing**: screenshots (from S1), privacy policy, Data safety form.
8. If DaySpark replaces an existing app: its source, application ID, signing key and schema.
   The upgrade path (H6) must then be tested against real user data from that app.
