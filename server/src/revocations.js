'use strict';

/**
 * Two ways refunds reach the server:
 *  1. Real-time developer notifications (Pub/Sub push) -> handleRtdn()
 *  2. A periodic Voided Purchases API sweep, which also catches notifications that were missed.
 */

function handleRtdn(body, revoked, log = console) {
  const data = body && body.message && body.message.data;
  if (!data) return false;
  let note;
  try {
    note = JSON.parse(Buffer.from(data, 'base64').toString('utf8'));
  } catch (e) {
    log.warn('Unreadable RTDN payload');
    return false;
  }
  const voided = note.voidedPurchaseNotification;
  if (voided && voided.purchaseToken) {
    if (revoked.add(voided.purchaseToken)) log.info('Revoked via RTDN', voided.orderId || '');
    return true;
  }
  return false;
}

async function syncVoidedPurchases({ publisher, packageName, revoked, log = console, days = 30 }) {
  const startTime = String(Date.now() - days * 24 * 3600 * 1000);
  let pageToken;
  let added = 0;
  do {
    const res = await publisher.purchases.voidedpurchases.list({
      packageName, startTime, token: pageToken, maxResults: 1000,
    });
    for (const v of res.data.voidedPurchases || []) {
      if (revoked.add(v.purchaseToken)) added++;
    }
    pageToken = res.data.tokenPagination && res.data.tokenPagination.nextPageToken;
  } while (pageToken);
  if (added) log.info(`Voided purchases sync: ${added} new`);
  return added;
}

module.exports = { handleRtdn, syncVoidedPurchases };
