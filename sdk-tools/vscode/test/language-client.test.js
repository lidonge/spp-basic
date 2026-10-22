'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function harness({ trusted = true, refused = false, startupBarrier } = {}) {
  const clients = [];
  const messages = [];
  const timers = [];
  const document = { languageId: 'spp', uri: { scheme: 'file', fsPath: '/workspace/src/main/spp/test.spp', toString: () => 'file:///workspace/src/main/spp/test.spp' },
    version: 1, content: 'domain sample;', getText() { return this.content; } };
  const folder = { index: 0, uri: { scheme: 'file', fsPath: '/workspace', toString: () => 'file:///workspace' } };
  const workspace = {
    isTrusted: trusted, textDocuments: [document], getWorkspaceFolder: () => folder,
    getConfiguration: () => ({ get: (_name, fallback) => fallback }),
    createFileSystemWatcher: () => ({ dispose() {} }),
    onDidOpenTextDocument: () => ({ dispose() {} }),
    onDidGrantWorkspaceTrust: () => ({ dispose() {} }),
    onDidChangeWorkspaceFolders: () => ({ dispose() {} }),
    onDidChangeConfiguration: () => ({ dispose() {} })
  };
  class Client {
    constructor(_id, _name, server, options) {
      this.server = server;
      this.options = options;
      this.requests = [];
      this.stopped = 0;
      clients.push(this);
    }
    async start() {
      if (startupBarrier) await startupBarrier;
      if (refused) throw new Error('Runtime unavailable');
    }
    async stop() { this.stopped++; }
    async sendRequest(method, params) {
      this.requests.push({ method, params });
      throw new Error('Editor must not make authorization requests');
    }
    async sendNotification() { }
  }
  const context = {
    subscriptions: [], asAbsolutePath: relative => '/extension/' + relative,
    secrets: { get: async () => { throw new Error('Editor must not read account credentials'); } }
  };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(require.resolve('../src/language-client'), 'utf8'), {
    module, setTimeout: action => { timers.push(action); return 1; }, clearTimeout() {},
    require: name => {
      if (name === 'vscode') return { workspace, RelativePattern: class {
        constructor(base, pattern) { this.base = base; this.pattern = pattern; }
      } };
      if (name === 'node:fs/promises') return { stat: async () => ({ isFile: () => true }) };
      if (name === '../client-dist/language-runtime') return { LanguageClient: Client, CloseAction: { DoNotRestart: 0 }, ErrorAction: { Shutdown: 0 } };
      return require(name);
    }
  });
  const controller = module.exports.createLanguageController(context, { appendLine: message => messages.push(message) });
  return { controller, clients, timers, messages, document, workspace };
}

test('semantic service never starts without workspace trust', async () => {
  for (const options of [{ trusted: false }]) {
    const host = harness(options);
    await host.controller.refresh();
    assert.equal(host.clients.length, 0);
    host.controller.dispose();
  }
});

test('editor works without credentials, authorization requests or lease timers', async () => {
  const host = harness();
  await host.controller.refresh();
  const client = host.clients.at(-1);
  assert.ok(client);
  assert.ok(!JSON.stringify(client.server).includes('private-token'));
  assert.ok(client.server.args.includes('-Xmx512m'));
  assert.equal(client.options.documentSelector[0].pattern.base, '/workspace/src/main/spp');
  assert.equal(client.requests.length, 0);
  assert.equal(host.controller.status()[0].state, 'ready');
  let opened = false;
  await client.options.middleware.didOpen(host.document, () => { opened = true; });
  assert.equal(opened, true);
  assert.equal(host.timers.length, 0);
  host.controller.stop();
  assert.ok(client.stopped > 0);
  host.controller.dispose();
});

test('runtime errors stop providers without asking the user to sign in', async () => {
  const host = harness({ refused: true });
  await host.controller.refresh();
  const client = host.clients.at(-1);
  assert.ok(client.stopped > 0);
  assert.ok(host.messages.every(message => !message.includes('private-token')));
  assert.ok(host.messages.every(message => !/login|sign in|authorization/i.test(message)));
  let opened = false;
  await client.options.middleware.didOpen(host.document, () => { opened = true; });
  assert.equal(opened, false);
  host.controller.dispose();
});

test('stopping during async startup cannot resurrect a prior session', async () => {
  const host = harness();
  const refresh = host.controller.refresh();
  host.controller.stop();
  await refresh;
  assert.ok(host.clients.every(client => client.stopped > 0));
  host.controller.dispose();
});

test('typing during startup does not deadlock or lose the latest unsaved text', async () => {
  let release;
  const barrier = new Promise(resolve => { release = resolve; });
  const host = harness({ startupBarrier: barrier });
  const refreshing = host.controller.refresh();
  await new Promise(resolve => setImmediate(resolve));
  const client = host.clients.at(-1);
  assert.equal(client.requests.length, 0);
  let opened;
  await client.options.middleware.didOpen(host.document, document => { opened = document.getText(); });
  host.document.content = 'domain sample; Role Unsaved;';
  host.document.version = 2;
  let changed = false;
  await client.options.middleware.didChange({ document: host.document }, () => { changed = true; });
  assert.equal(changed, false);
  release();
  await refreshing;
  assert.equal(opened, 'domain sample; Role Unsaved;');
  host.controller.dispose();
});

test('late language responses are discarded after document changes or cancellation', async () => {
  const host = harness();
  await host.controller.refresh();
  const client = host.clients.at(-1);
  let release;
  const response = new Promise(resolve => { release = resolve; });
  const cancellation = { isCancellationRequested: false };
  const request = client.options.middleware.provideDefinition(host.document, {}, cancellation, () => response);
  await new Promise(resolve => setImmediate(resolve));
  host.document.version++;
  release(['stale-location']);
  assert.equal(await request, undefined);
  cancellation.isCancellationRequested = true;
  let executed = false;
  const cancelled = await client.options.middleware.provideHover(host.document, {}, cancellation,
    () => { executed = true; return 'should-not-run'; });
  assert.equal(cancelled, undefined);
  assert.equal(executed, false);
  host.controller.dispose();
});
