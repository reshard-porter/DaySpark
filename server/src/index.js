'use strict';

const { google } = require('googleapis');
const { createVerifier } = require('./verify');
const { createRevokedStore } = require('./revoked-store');
const { syncVoidedPurchases } = require('./revocations');
const { createApp } = require('./app');

const env = (k, d) => process.env[k] || d;
const PACKAGE_NAME = env('PACKAGE_NAME', 'com.dayspark.app');
const PRODUCT_ID = env('PRODUCT_ID', 'dayspark_pro_themes');
const PORT = Number(env('PORT', '8080'));
const RTDN_SECRET = env('RTDN_SECRET', '');
const REVOKED_FILE = env('REVOKED_FILE', './data/revoked.json');
const SYNC_HOURS = Number(env('VOIDED_SYNC_HOURS', '6'));

// Credentials: a Google Cloud service account linked in Play Console, provided through
// GOOGLE_APPLICATION_CREDENTIALS (or the platform's default identity). Never ship it in the app.
const auth = new google.auth.GoogleAuth({
  scopes: ['https://www.googleapis.com/auth/androidpublisher'],
});
const publisher = google.androidpublisher({ version: 'v3', auth });
const revoked = createRevokedStore(REVOKED_FILE);

const verify = createVerifier({ publisher, packageName: PACKAGE_NAME, productId: PRODUCT_ID, revoked });
const app = createApp({ verify, revoked, rtdnSecret: RTDN_SECRET });

async function sweep() {
  try {
    await syncVoidedPurchases({ publisher, packageName: PACKAGE_NAME, revoked });
  } catch (e) {
    console.warn('Voided purchases sync failed:', e.message);
  }
}

app.listen(PORT, () => {
  console.log(`DaySpark verification service on :${PORT} for ${PACKAGE_NAME}/${PRODUCT_ID}`);
  if (!RTDN_SECRET) console.warn('RTDN_SECRET not set: /rtdn is disabled; relying on periodic sync.');
  sweep();
  setInterval(sweep, SYNC_HOURS * 3600 * 1000).unref();
});
