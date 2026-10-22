'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { existsSync } = require('node:fs');
const { spawn } = require('node:child_process');

const jar = path.resolve(__dirname, '../../language-server/target/spp-language-server-1.0-SNAPSHOT.jar');

test('packaged LSP provides navigation with no account or authorization backend', { skip: !existsSync(jar), timeout: 20000 }, async () => {
  const child = spawn(process.env.SPP_TEST_JAVA || 'java', ['-jar', jar], { stdio: ['pipe', 'pipe', 'pipe'], shell: false });
  const pending = new Map();
  let buffer = Buffer.alloc(0);
  let nextId = 0;
  let diagnostics = '';
  let framingFailure;
  const exited = new Promise(resolve => child.once('exit', (code, signal) => resolve({ code, signal })));
  child.stderr.on('data', chunk => { diagnostics += chunk.toString(); });
  child.stdout.on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk]);
    try {
      while (buffer.length) {
        const separator = buffer.indexOf('\r\n\r\n');
        if (separator < 0) break;
        const header = buffer.subarray(0, separator).toString('ascii');
        const length = Number(/Content-Length: (\d+)/i.exec(header)?.[1]);
        assert.ok(Number.isSafeInteger(length) && length > 0 && length < 1024 * 1024, header);
        if (buffer.length < separator + 4 + length) break;
        const message = JSON.parse(buffer.subarray(separator + 4, separator + 4 + length).toString('utf8'));
        buffer = buffer.subarray(separator + 4 + length);
        if (pending.has(message.id)) {
          pending.get(message.id)(message);
          pending.delete(message.id);
        }
      }
    } catch (error) { framingFailure = error; child.kill(); }
  });
  function send(method, params, notification = false) {
    const id = ++nextId;
    const data = Buffer.from(JSON.stringify({ jsonrpc: '2.0', ...(notification ? {} : { id }), method, params }));
    const response = notification ? Promise.resolve() : new Promise(resolve => pending.set(id, resolve));
    child.stdin.write(`Content-Length: ${data.length}\r\n\r\n`);
    child.stdin.write(data);
    return response;
  }
  const timeout = setTimeout(() => child.kill('SIGKILL'), 18000);
  try {
    const workspace = path.resolve(__dirname, '../../..');
    const rootUri = require('node:url').pathToFileURL(workspace).toString();
    const initialized = await send('initialize', { processId: process.pid, rootUri,
      capabilities: {}, workspaceFolders: [{ uri: rootUri, name: 'test' }] });
    assert.equal(initialized.result.capabilities.definitionProvider, true);
    assert.equal(initialized.result.capabilities.declarationProvider, true);
    await send('initialized', {}, true);
    const uri = `${rootUri}/src/main/spp/test.spp`;
    await send('textDocument/didOpen', { textDocument: { uri, languageId: 'spp', version: 1,
      text: 'domain ledger;\nRole Facts { int amount; }\natomic calculate twice(Facts) { Facts.amount *= 2; }\n' } }, true);
    const definition = await send('textDocument/definition', { textDocument: { uri }, position: { line: 2, character: 38 } });
    assert.equal(definition.error, undefined);
    assert.equal(definition.result.length, 1);
    assert.deepEqual(definition.result[0].targetSelectionRange.start, { line: 1, character: 17 });
    await send('shutdown', null);
    await send('exit', null, true);
    child.stdin.end();
    const result = await exited;
    assert.equal(result.code, 0, diagnostics);
    assert.equal(framingFailure, undefined);
    assert.ok(!diagnostics.includes('isolated-process-fixture-token'));
  } finally {
    clearTimeout(timeout);
    if (child.exitCode === null) child.kill('SIGKILL');
  }
});
