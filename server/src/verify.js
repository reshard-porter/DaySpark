'use strict';

/**
 * Checks a one-time purchase token with the Google Play Developer API.
 * Pure logic: the Play client ("publisher") is injected so it can be tested without network.
 *
 * Response contract with the app (BackendPurchaseVerifier.java):
 *   200 {status: "verified", acknowledged: bool}
 *   200 {status: "pending"}
 *   200 {status: "revoked", reason}
 *   400 {status: "invalid", reason}
 *   503 {status: "error"}   -> app treats as temporarily unavailable and keeps its offline policy
 */

const PURCHASED = 0;
const CANCELED = 1;
const PENDING = 2;

function createVerifier({ publisher, packageName, productId, revoked, log = console }) {
  return async function verify(input) {
    const token = input && input.purchaseToken;
    if (!input || typeof token !== 'string' || token.length < 10 || token.length > 4096) {
      return reply(400, { status: 'invalid', reason: 'malformed token' });
    }
    if (input.packageName !== packageName) {
      return reply(400, { status: 'invalid', reason: 'wrong package' });
    }
    if (input.productId !== productId) {
      return reply(400, { status: 'invalid', reason: 'wrong product' });
    }
    if (revoked.has(token)) {
      return reply(200, { status: 'revoked', reason: 'voided' });
    }

    let data;
    try {
      const res = await publisher.purchases.products.get({ packageName, productId, token });
      data = res.data;
    } catch (err) {
      const code = httpCode(err);
      if (code === 400 || code === 404 || code === 410) {
        return reply(400, { status: 'invalid', reason: 'unknown token' });
      }
      log.error('Play API error', code, err.message);
      return reply(503, { status: 'error' });
    }

    switch (data.purchaseState) {
      case PENDING:
        return reply(200, { status: 'pending' });
      case CANCELED:
        return reply(200, { status: 'revoked', reason: 'canceled' });
      case PURCHASED:
        break;
      default:
        return reply(503, { status: 'error' });
    }

    // A non-consumable that has been consumed no longer represents ownership.
    if (data.consumptionState === 1) {
      return reply(200, { status: 'revoked', reason: 'consumed' });
    }

    let acknowledged = data.acknowledgementState === 1;
    if (!acknowledged) {
      try {
        await publisher.purchases.products.acknowledge({
          packageName, productId, token, requestBody: {},
        });
        acknowledged = true;
      } catch (err) {
        // The app acknowledges as a fallback; ownership is still verified.
        log.warn('Server acknowledgement failed; client will retry', httpCode(err));
      }
    }
    return reply(200, { status: 'verified', acknowledged });
  };
}

function httpCode(err) {
  return (err && (err.code || (err.response && err.response.status))) || 0;
}

function reply(httpStatus, body) {
  return { httpStatus, body };
}

module.exports = { createVerifier };
