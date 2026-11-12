'use strict';

const http = require('http');
const crypto = require('crypto');
const { handleRtdn } = require('./revocations');

const MAX_BODY = 16 * 1024;
const RATE_LIMIT = 30;           // requests
const RATE_WINDOW_MS = 60_000;   // per minute per IP

function createApp({ verify, revoked, rtdnSecret, log = console }) {
  const hits = new Map();

  function rateLimited(ip) {
    const now = Date.now();
    const entry = hits.get(ip) || { count: 0, start: now };
    if (now - entry.start > RATE_WINDOW_MS) { entry.count = 0; entry.start = now; }
    entry.count++;
    hits.set(ip, entry);
    return entry.count > RATE_LIMIT;
  }

  return http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    try {
      if (req.method === 'GET' && url.pathname === '/healthz') {
        return send(res, 200, { ok: true });
      }
      if (req.method === 'POST' && url.pathname === '/v1/purchases/verify') {
        if (rateLimited(req.socket.remoteAddress || '')) return send(res, 429, { status: 'error' });
        const body = await readJson(req);
        const out = await verify(body);
        return send(res, out.httpStatus, out.body);
      }
      if (req.method === 'POST' && url.pathname === '/rtdn') {
        const given = url.searchParams.get('secret') || '';
        if (!rtdnSecret || !safeEqual(given, rtdnSecret)) return send(res, 403, {});
        handleRtdn(await readJson(req), revoked, log);
        res.writeHead(204).end(); // always ack so Pub/Sub doesn't redeliver forever
        return;
      }
      send(res, 404, {});
    } catch (err) {
      if (err.statusCode) return send(res, err.statusCode, { status: 'invalid', reason: err.message });
      log.error(err);
      send(res, 503, { status: 'error' });
    }
  });
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(Object.assign(new Error('body too large'), { statusCode: 413 }));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => {
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')); }
      catch (e) { reject(Object.assign(new Error('invalid JSON'), { statusCode: 400 })); }
    });
    req.on('error', reject);
  });
}

function send(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

function safeEqual(a, b) {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

module.exports = { createApp };
