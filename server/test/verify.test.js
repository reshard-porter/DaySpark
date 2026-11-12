'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { createVerifier } = require('../src/verify');
const { createRevokedStore } = require('../src/revoked-store');
const { handleRtdn, syncVoidedPurchases } = require('../src/revocations');

const PKG = 'com.dayspark.app';
const PID = 'dayspark_pro_themes';
const TOKEN = 'token-abcdefghijk';
const quiet = { info() {}, warn() {}, error() {} };

function fakePublisher({ data, error, ackError, voided = [] } = {}) {
  const calls = { get: 0, ack: 0 };
  return {
    calls,
    purchases: {
      products: {
        async get() { calls.get++; if (error) throw error; return { data }; },
        async acknowledge() { calls.ack++; if (ackError) throw ackError; return {}; },
      },
      voidedpurchases: {
        async list() { return { data: { voidedPurchases: voided } }; },
      },
    },
  };
}

function make(pubOpts) {
  const publisher = fakePublisher(pubOpts);
  const revoked = createRevokedStore(null);
  const verify = createVerifier({ publisher, packageName: PKG, productId: PID, revoked, log: quiet });
  return { publisher, revoked, verify };
}

const body = (o = {}) => ({ packageName: PKG, productId: PID, purchaseToken: TOKEN, ...o });

test('purchased + unacknowledged: verified and acknowledged by server', async () => {
  const { verify, publisher } = make({ data: { purchaseState: 0, acknowledgementState: 0, consumptionState: 0 } });
  const r = await verify(body());
  assert.deepStrictEqual(r, { httpStatus: 200, body: { status: 'verified', acknowledged: true } });
  assert.strictEqual(publisher.calls.ack, 1);
});

test('already acknowledged: no second acknowledge', async () => {
  const { verify, publisher } = make({ data: { purchaseState: 0, acknowledgementState: 1 } });
  const r = await verify(body());
  assert.strictEqual(r.body.acknowledged, true);
  assert.strictEqual(publisher.calls.ack, 0);
});

test('server acknowledge failure still verifies; client acknowledges instead', async () => {
  const { verify } = make({ data: { purchaseState: 0, acknowledgementState: 0 }, ackError: { code: 503 } });
  const r = await verify(body());
  assert.deepStrictEqual(r.body, { status: 'verified', acknowledged: false });
});

test('pending purchase is not verified', async () => {
  const { verify } = make({ data: { purchaseState: 2 } });
  assert.strictEqual((await verify(body())).body.status, 'pending');
});

test('canceled purchase is revoked', async () => {
  const { verify } = make({ data: { purchaseState: 1 } });
  assert.strictEqual((await verify(body())).body.status, 'revoked');
});

test('consumed non-consumable is revoked', async () => {
  const { verify } = make({ data: { purchaseState: 0, consumptionState: 1, acknowledgementState: 1 } });
  assert.strictEqual((await verify(body())).body.status, 'revoked');
});

test('unknown token is invalid (400)', async () => {
  const { verify } = make({ error: { code: 404, message: 'not found' } });
  const r = await verify(body());
  assert.strictEqual(r.httpStatus, 400);
  assert.strictEqual(r.body.status, 'invalid');
});

test('Play outage is a 503 so the app keeps its offline policy', async () => {
  const { verify } = make({ error: { code: 500, message: 'backend' } });
  assert.strictEqual((await verify(body())).httpStatus, 503);
});

test('wrong package, wrong product and malformed token are rejected without calling Play', async () => {
  const { verify, publisher } = make({ data: { purchaseState: 0 } });
  assert.strictEqual((await verify(body({ packageName: 'evil.app' }))).httpStatus, 400);
  assert.strictEqual((await verify(body({ productId: 'other' }))).httpStatus, 400);
  assert.strictEqual((await verify(body({ purchaseToken: 'x' }))).httpStatus, 400);
  assert.strictEqual(publisher.calls.get, 0);
});

test('RTDN voided notification revokes the token', async () => {
  const { verify, revoked } = make({ data: { purchaseState: 0, acknowledgementState: 1 } });
  const payload = { voidedPurchaseNotification: { purchaseToken: TOKEN, orderId: 'GPA.1', productType: 2 } };
  const handled = handleRtdn({ message: { data: Buffer.from(JSON.stringify(payload)).toString('base64') } }, revoked, quiet);
  assert.ok(handled);
  assert.strictEqual((await verify(body())).body.status, 'revoked');
});

test('voided purchases sweep revokes missed refunds', async () => {
  const { publisher, revoked, verify } = make({
    data: { purchaseState: 0, acknowledgementState: 1 },
    voided: [{ purchaseToken: TOKEN }],
  });
  assert.strictEqual(await syncVoidedPurchases({ publisher, packageName: PKG, revoked, log: quiet }), 1);
  assert.strictEqual((await verify(body())).body.status, 'revoked');
});
