# DaySpark verification service

A small Node.js service (one dependency: `googleapis`) that the app calls before it unlocks
Pro themes. It keeps the Google service account on the server, never on the device.

| Endpoint | Purpose |
|---|---|
| `POST /v1/purchases/verify` | `{packageName, productId, purchaseToken}` → `verified` / `pending` / `revoked` / `invalid` (and acknowledges with Google when needed) |
| `POST /rtdn?secret=…` | Pub/Sub push endpoint for real-time developer notifications (refunds) |
| `GET /healthz` | Health check |

It also sweeps the Voided Purchases API every 6 hours, so missed notifications are still caught.

## Configuration

| Env var | Default | |
|---|---|---|
| `PACKAGE_NAME` | `com.dayspark.app` | Only this app's tokens are accepted |
| `PRODUCT_ID` | `dayspark_pro_themes` | Only this product is accepted |
| `GOOGLE_APPLICATION_CREDENTIALS` | – | Path to the service-account key, if the platform has no attached identity |
| `RTDN_SECRET` | – | Shared secret in the push URL. `/rtdn` is disabled when empty |
| `REVOKED_FILE` | `./data/revoked.json` | Revoked tokens. Use a database when running several instances |
| `PORT` | `8080` | |

## Run

```bash
npm install
npm test
GOOGLE_APPLICATION_CREDENTIALS=./service-account.json RTDN_SECRET=change-me npm start
```

## Deploy (Cloud Run example)

```bash
gcloud run deploy dayspark-verify --source . --region us-central1 \
  --service-account dayspark-verify@PROJECT.iam.gserviceaccount.com \
  --set-env-vars RTDN_SECRET=change-me --allow-unauthenticated
```

Cloud Run's filesystem is temporary: either keep a single instance and accept that the
revoked list is rebuilt by the 6-hourly sweep after a restart, or replace `revoked-store.js` with
a Firestore or SQL table (same `has`/`add` interface).

The endpoint has a basic per-IP rate limit and accepts only DaySpark's package and product.
For stronger protection against scripted abuse, add Play Integrity verification.
