'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { createApp } = require('../src/app');
const { createRevokedStore } = require('../src/revoked-store');

async function withServer(fn) {
  const revoked = createRevokedStore(null);
  const verify = async (b) => ({ httpStatus: 200, body: { status: 'verified', acknowledged: true, echo: b.purchaseToken } });
  const app = createApp({ verify, revoked, rtdnSecret: 's3cret', log: { info() {}, warn() {}, error() {} } });
  await new Promise((r) => app.listen(0, r));
  const base = `http://127.0.0.1:${app.address().port}`;
  try { await fn(base, revoked); } finally { app.close(); }
}

test('HTTP: verify endpoint, health, RTDN auth, bad JSON', async () => {
  await withServer(async (base, revoked) => {
    let r = await fetch(`${base}/healthz`);
    assert.strictEqual(r.status, 200);

    r = await fetch(`${base}/v1/purchases/verify`, { method: 'POST', body: JSON.stringify({ purchaseToken: 'abc' }) });
    assert.strictEqual(r.status, 200);
    assert.strictEqual((await r.json()).echo, 'abc');

    r = await fetch(`${base}/v1/purchases/verify`, { method: 'POST', body: '{nope' });
    assert.strictEqual(r.status, 400);

    const data = Buffer.from(JSON.stringify({ voidedPurchaseNotification: { purchaseToken: 'tok-1234567890' } })).toString('base64');
    r = await fetch(`${base}/rtdn?secret=wrong`, { method: 'POST', body: JSON.stringify({ message: { data } }) });
    assert.strictEqual(r.status, 403);
    r = await fetch(`${base}/rtdn?secret=s3cret`, { method: 'POST', body: JSON.stringify({ message: { data } }) });
    assert.strictEqual(r.status, 204);
    assert.ok(revoked.has('tok-1234567890'));
  });
});
