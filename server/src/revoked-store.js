'use strict';

const fs = require('fs');
const path = require('path');

/**
 * Tokens of refunded / charged-back / revoked purchases. Kept in memory and mirrored to a JSON
 * file so restarts don't forget them. For several server instances, swap this for a shared
 * database table with the same has/add interface.
 */
function createRevokedStore(file) {
  const tokens = new Set();
  if (file && fs.existsSync(file)) {
    for (const t of JSON.parse(fs.readFileSync(file, 'utf8'))) tokens.add(t);
  }
  function persist() {
    if (!file) return;
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file + '.tmp', JSON.stringify([...tokens]));
    fs.renameSync(file + '.tmp', file);
  }
  return {
    has: (t) => tokens.has(t),
    add(t) {
      if (!t || tokens.has(t)) return false;
      tokens.add(t);
      persist();
      return true;
    },
    size: () => tokens.size,
  };
}

module.exports = { createRevokedStore };
