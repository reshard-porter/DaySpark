# Google Play Console setup and billing tests

Everything here happens outside the codebase and needs access to your Play Console and Google
Cloud accounts.

## 1. App and first upload

1. Play Console → **Create app** → name "DaySpark", app, free, accept declarations.
2. Build `./gradlew bundleRelease -PdaysparkVerifyUrl=<your server URL>` with your upload key
   (see README).
3. **Testing → Internal testing → Create release**, upload `app-release.aab`, accept Play App
   Signing, then roll out to internal testing. Play only shows in-app products for apps with a
   bundle on some track.
4. Add yourself and your testers to the internal testing list and open the opt-in link on
   each test device.

## 2. The one-time product

**Monetize with Play → Products → One-time products → Create**

| Field | Value |
|---|---|
| Product ID | `dayspark_pro_themes` (can't be changed or reused later) |
| Name | DaySpark Pro themes |
| Description | Four extra themes: Forest, Ocean, Lavender and Sunset. One-time purchase. |
| Purchase option | Buy, one default offer |
| Price | Your base price; let Play convert it to local prices |

Activate the product. Play has no "non-consumable" switch: the product is non-consumable
because DaySpark never consumes it (it only acknowledges), and the server treats a consumed
token as revoked.

## 3. License testers

**Settings → License testing**: add the Google accounts of everyone testing purchases, and set
*License response* to `RESPOND_NORMALLY`. On their devices these accounts see test payment
methods and are never charged:

| Test instrument | Use it to test |
|---|---|
| Test card, always approves | Successful purchase |
| Test card, always declines | Declined payment |
| Slow test card, approves after a few minutes | Pending → purchased |
| Slow test card, declines after a few minutes | Pending → failed |

The test device must be signed in to the Play Store with a license tester account, and the app
must be installed from the internal testing track at least once (afterwards a locally built
debug APK with the same application ID works too).

## 4. Server credentials (keeps service credentials off the device)

> **Already done (12 Nov 2026):** Google Cloud project **DaySpark** (`dayspark-514513`), with the
> Google Play Android Developer API enabled and service account
> `dayspark-verify@dayspark-514513.iam.gserviceaccount.com` created (no keys).
> Still to do: step 3 (invite that email in Play Console once your developer account exists)
> and step 4 (hosting needs a Cloud billing account).


1. Google Cloud console → create or pick a project → **enable "Google Play Android Developer API"**.
2. **IAM → Service accounts → Create**, for example `dayspark-verify@<project>.iam.gserviceaccount.com`.
   Prefer the hosting platform's attached identity (Cloud Run service identity). Create a JSON
   key only if your host needs one, and store it as a secret.
3. Play Console → **Users and permissions → Invite new user** → the service account email →
   *App permissions* for DaySpark: **View financial data** and **Manage orders and
   subscriptions**. Permissions can take up to 24 hours to take effect.
4. Deploy `server/` (see `server/README.md`) and use its HTTPS URL as `daysparkVerifyUrl`.

## 5. Refund notifications (recommended)

1. Cloud console → **Pub/Sub → Create topic** `play-rtdn`.
2. On the topic, grant `google-play-developer-notifications@system.gserviceaccount.com` the
   **Pub/Sub Publisher** role.
3. Create a **push** subscription to `https://<server>/rtdn?secret=<RTDN_SECRET>`.
4. Play Console → **Monetize with Play → Monetization setup → Real-time developer
   notifications** → topic `projects/<project>/topics/play-rtdn` → enable notifications for
   one-time products and voided purchases → **Send test notification**.

The server also sweeps the Voided Purchases API every 6 hours, so refunds are caught even
without step 5.

## 6. Billing test plan (license testers)

Run on a real device. Use a debug build with *Billing test tools → Billing source: Google
Play*, or the internal-testing release build. Record results in `docs/TEST_REPORT.md`.

| # | Scenario | Steps | Expected |
|---|---|---|---|
| B1 | Price | Open Themes | Card shows "Unlock for <local price>", not a hard-coded value |
| B2 | Success | Unlock → "always approves" | "Confirming your purchase…" then "Pro themes unlocked"; Play Console order shows **acknowledged** |
| B3 | Cancel | Unlock → back out of the Play sheet | "Purchase cancelled. You haven't been charged."; still locked |
| B4 | Decline | Unlock → "always declines" | Declined or error message; still locked |
| B5 | Pending | Unlock → "slow test card, approves" | "Payment pending"; themes stay locked; unlocks on its own minutes later (keep the app open, or reopen it) |
| B6 | Pending fails | "slow test card, declines" | Stays locked; pending message clears after reopening |
| B7 | Interrupted checkout | Approve, then force-stop the app before "unlocked" appears; reopen | Purchase is verified and acknowledged on launch; "Pro themes restored" |
| B8 | Restore | Clear app data (or reinstall) → Settings → Restore purchases | "Pro themes restored"; your previous theme choice is reset, so pick it again |
| B9 | Already owned | After B2, clear app data, turn on airplane mode, open Themes, turn airplane mode off and tap Unlock straight away | Play reports the item as owned; "You already own Pro themes. Restoring them now." then unlocked, with no second charge |
| B10 | Duplicate callbacks | Tap Unlock repeatedly while the sheet opens | Only one Play sheet; server logs show one verification per token |
| B11 | Acknowledgement retry | Block the server (airplane mode right after payment approves), then reconnect | Stays "Payment received… retrying"; after reconnecting it's verified and acknowledged without paying again |
| B12 | Refund | Play Console → Order management → refund **and revoke** the test order | Within a minute of reopening (RTDN) or after the next sweep: "purchase was refunded", default theme, habits intact |
| B13 | Offline grace | After B2, airplane mode, relaunch | Pro theme still applied |
| B14 | No backend | Build release with an empty verify URL and buy | Never unlocks; shows "still confirming" |

## 7. Before production

- Publish a privacy policy (the app sends purchase tokens and a random install ID to your
  server; habits stay on the device).
- Complete the Data safety form: purchase history is collected for app functionality.
- Move the server's revoked-token store to a shared database if you run more than one
  instance.
- Optionally add Play Integrity checks on the `/v1/purchases/verify` endpoint.
