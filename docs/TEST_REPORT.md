# DaySpark test report

Date: 12 November 2026. Code: `main` at the commit named in the `ci-build-outputs` branch.
Builds and device tests ran on GitHub Actions (`.github/workflows/android.yml`) with the real
Android SDK, on a Pixel 6 emulator running Android 14 (API 34).

## Summary

| Area | Result |
|---|---|
| Compile (Android SDK 36, AGP 8.13, Play Billing 9.1.0) | **Pass** |
| Android Lint | **0 errors**, 40 warnings (see below) |
| Unit tests (JUnit) | **27/27 pass**: 21 purchase rules, 6 streak maths |
| Verification server tests (Node) | **12/12 pass** |
| Emulator phase 1: habit flow + purchase flow | **Pass** |
| Emulator phase 2: app update + restart keeps data, theme, ownership | **Pass** |
| Emulator phase 3: largest system text size (200%) | **Pass** |
| Emulator phase 4: Google Accessibility Test Framework on every interaction | **Pass** |
| Debug APK, release AAB | Built; AAB signed with the upload key |

## Emulator test details

Phase 1 (`A1_HabitAndPurchaseFlowTest`) on a fresh install, using the debug billing simulator:
empty dashboard → name validation → create two habits (every day, weekdays) → name error
clears while typing → mark complete (progress card, streak, check state update) → habit detail
→ theme gallery with localized price → full-screen Forest preview → Unlock dialog that says
"one-time purchase … not a subscription" → **payment pending: theme stays DaySpark** (asserted)
→ payment approves → Forest applied automatically → dashboard in Forest → Settings shows
"Owned". Recorded as `video/demo-habit-and-purchase.mp4`.

Phase 2 (`A2_AfterUpdateTest`): the APK is reinstalled over itself (an app update) and the
process is killed. Both habits, today's check-in, the Forest theme and verified ownership are
all still there.

Phase 3 (`A3_LargeTextTest`): font scale 2.0. Dashboard, detail and gallery screenshots show
text wrapping, not clipping.

Phase 4 (`A4_AccessibilityChecksTest`): ATF checks (labels, touch-target size, contrast,
duplicate descriptions) on dashboard, check toggles, detail, the create form, gallery and
settings. No findings.

## Issues found by the device run and fixed

1. The name field kept showing "Give your habit a name" after the user typed a name. It now
   clears while typing, and the test asserts this.
2. On the theme preview, the purchase card covered half the screen. It's now compact.
3. When a pending payment cleared later, the theme the user had chosen wasn't applied
   automatically. A cleared pending payment now counts as a completed purchase (unit-tested).
4. Lint: an unlabelled decorative image, a redundant `-v26` icon folder and an unused color.

## Lint warnings left on purpose (40)

- 11 newer library versions (appcompat 1.8, core 1.19, Material 1.14 and others) and 1 "not
  the latest target SDK". Upgrading may require compileSdk 37; do it as its own change, then
  re-run this workflow.
- 12 `UnusedAttribute`: `accessibilityHeading` and similar only take effect on newer Android.
  That's intended.
- 7 `Overdraw`, 4 `UseCompoundDrawables`, 2 `ApplySharedPref` (debug tools use `commit()` on
  purpose before restarting), 1 `PluralsCandidate`, 1 `NotifyDataSetChanged`, 1 Gradle
  patch version. These are minor style or performance notes.

## Not yet run: needs Play Console (external)

Real-money purchase tests B1–B14 in `PLAY_CONSOLE_SETUP.md` need a Play developer account,
the app on the internal testing track, the active `dayspark_pro_themes` product, license
testers and the deployed verification server. The simulator runs the same purchase logic,
but these tests confirm Google's side.

## Before-and-after screenshots

No original app was supplied, so there is no "before" set. `screenshots/` holds the redesigned
app: 01–06 habit flow, 07–13 purchase flow, 14 after update, 15–17 large text.
