const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const {webcrypto} = require('node:crypto');
const source = readFileSync(require('node:path').resolve(__dirname, '../../main/resources/web/app.js'), 'utf8');
const helper = source.match(/function newRequestId\(\) \{[\s\S]*?\n\}/)[0];
const generate = crypto => runInNewContext(helper + '\nnewRequestId();', {crypto});

test('uses the native UUID API when available', () => {
  const crypto = {randomUUID() { assert.equal(this, crypto); return 'native-uuid'; }};
  assert.equal(generate(crypto), 'native-uuid');
});

test('LAN HTTP fallback produces distinct UUID v4 request serials', () => {
  const crypto = {getRandomValues: bytes => webcrypto.getRandomValues(bytes)};
  const values = new Set();
  for (let index = 0; index < 100; index++) {
    const value = generate(crypto);
    assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    values.add(value);
  }
  assert.equal(values.size, 100);
});

test('does not substitute weak randomness when crypto is unavailable', () => {
  assert.throws(() => generate(undefined), /does not provide secure random numbers/);
});
