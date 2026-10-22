'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function harness({ basic = false, trusted = true, fail = false, sdkMissing = false, sdkDirectory = false, configuredJar = "/trusted/cli.jar", configureSdk = false, refreshFailure, usageConsent, workspaceUsageConsent, environment, physicalProfile, telemetryEnabled=true } = {}) {
  const commands = new Map();
  const secrets = new Map();
  const calls = [];
  const capabilityCalls = [];
  const errors = [];
  const languageCalls = [];
  const visible = [];
  const settingsCalls = [];
  const fresh = {accessToken:"a".repeat(43),refreshToken:"r_"+"b".repeat(43),expiresAt:Math.floor(Date.now()/1000)+3600,installation:"11111111-2222-3333-4444-555555555555",receipt:"CLIENT1"};
  const vscode = {
    workspace: {
      isTrusted: trusted,
      workspaceFolders:[{uri:{scheme:'file',fsPath:'/workspace'}}],saveAll:async()=>true,
      getConfiguration: () => ({ get: (name, fallback) => name === 'cliJar' ? configuredJar : name === 'environment' ? environment ?? fallback : name === 'physicalProfile' ? physicalProfile ?? fallback : fallback,
        inspect:name=>name==='shareUsageEvents'?{globalValue:usageConsent,workspaceValue:workspaceUsageConsent}:undefined })
    },
    commands: { executeCommand: async (...args) => settingsCalls.push(args), registerCommand: (name, action) => { commands.set(name, action); return { dispose() {} }; } },
    window: {
      createOutputChannel: () => ({ append() {}, appendLine() {}, show() {}, dispose() {} }),
      showInputBox: async () => 'private-token',
      showOpenDialog:async()=>[{scheme:'file',fsPath:'/workspace/src/main/spp'}],
      showSaveDialog:async()=>({scheme:'file',fsPath:'/workspace/generated'}),
      showErrorMessage: value => { errors.push(value); return configureSdk ? 'Configure SDK' : undefined; },
      showInformationMessage(value) { visible.push(value); },
      withProgress: async (_options, action) => action({report:value=>visible.push(value.message)}, {})
    },
    env: {isTelemetryEnabled:telemetryEnabled,openExternal:async uri=>{visible.push(uri);return true;}},
    Uri: {parse:value=>value,file:fsPath=>({scheme:'file',fsPath})},
    ProgressLocation: { Notification: 1 }
  };
  const context = {
    subscriptions: [],
    asAbsolutePath: value => '/extension/' + value,
    secrets: {
      get: async key => secrets.get(key),
      store: async (key, value) => secrets.set(key, value),
      delete: async key => secrets.delete(key)
    }
  };
  const script = fs.readFileSync(require.resolve('../src/extension'), 'utf8');
  const sandbox = {
    module: { exports: {} },
    AbortController,
    require: name => {
      if (name === 'vscode') return vscode;
      if (name === 'node:timers/promises') return {setTimeout:async()=>{}};
      if (name === 'node:fs/promises') return { stat: async () => { if (sdkMissing) throw Object.assign(new Error('ENOENT: no such file'), { code: 'ENOENT' }); return { isFile: () => !sdkDirectory }; } };
      if (name === './runner') return { runCli: async (...args) => {
        if (args[2][0] === '--version') { capabilityCalls.push(args); return { edition: basic ? 'basic' : 'commercial', authorizationRequired: !basic }; }
        calls.push(args);
        if (fail) throw new Error('Authorization rejected.');
        if(args[2][0]==='auth-start')return {deviceCode:'private-device',userCode:'ABCD-2345',verificationUri:'https://servpp.test/sdk/activate',expiresIn:600,interval:5};
        if(args[2][0]==='auth-poll')return fresh;
        if(args[2][0]==='auth-refresh') {if(refreshFailure)throw Object.assign(new Error('refresh failed'),{code:refreshFailure});return {...fresh,accessToken:'c'.repeat(43),refreshToken:'r_'+'d'.repeat(43)};}
        return { expiresAt: 1800000000 };
      } };
      if (name === './language-client') return { createLanguageController: () => ({
        refresh: async () => { languageCalls.push('refresh'); }, stop() { languageCalls.push('stop'); }
      }) };
      return require(name);
    }
  };
  vm.runInNewContext(script, sandbox);
  sandbox.module.exports.activate(context);
  return { commands, secrets, calls, capabilityCalls, errors, languageCalls, visible, fresh, settingsCalls };
}

test('browser sign-in stores tokens only after actual authorization and shows only the human code', async () => {
  const host = harness();
  await host.commands.get('spp.signIn')();
  assert.deepEqual(host.calls.map(call=>String(call[2][0])),['auth-start','auth-poll','status']);
  assert.equal(JSON.parse(host.secrets.get('spp.accessToken')).accessToken,host.fresh.accessToken);
  assert.match(host.visible.join(' '),/ABCD-2345/);
  for(const secret of ['private-device',host.fresh.accessToken,host.fresh.refreshToken])assert.equal(host.visible.join(' ').includes(secret),false);
  assert.deepEqual(host.languageCalls, []);
});

test('failed sign-in does not save credentials', async () => {
  const host = harness({ fail: true });
  await host.commands.get('spp.signIn')();
  assert.equal(host.secrets.size, 0);
  assert.equal(host.errors.length, 1);
});

test('untrusted workspace refuses execution even if command is called directly', async () => {
  const host = harness({ trusted: false });
  await host.commands.get('spp.signIn')();
  assert.equal(host.calls.length, 0);
  assert.equal(host.secrets.size, 0);
  assert.match(host.errors[0], /Trust this workspace/);
});

test('sign-out removes only the extension credential', async () => {
  const host = harness();
  host.secrets.set('spp.accessToken', 'private-token');
  await host.commands.get('spp.signOut')();
  assert.equal(host.secrets.size, 0);
  assert.equal(host.calls.length, 1);
  assert.equal(host.calls[0][2][0], 'logout');
  assert.equal(host.calls[0][3], 'private-token');
  assert.deepEqual(host.languageCalls, []);
});

test('status requires login', async () => {
  const host = harness();
  await host.commands.get('spp.status')();
  assert.equal(host.calls.length, 0);
  assert.match(host.errors[0], /Sign In/);
});

test('expired extension credentials refresh and persist before status; old proofs are scoped away',async()=>{
  const host=harness();host.secrets.set('spp.accessToken',JSON.stringify({...host.fresh,expiresAt:1}));
  await host.commands.get('spp.status')();
  assert.deepEqual(host.calls.map(call=>String(call[2][0])),['auth-refresh','logout','status']);
  assert.equal(JSON.parse(host.secrets.get('spp.accessToken')).accessToken,'c'.repeat(43));
  assert.equal(host.calls.at(-1)[3],'c'.repeat(43));
});
test('temporary refresh outage still delegates signed proof verification to CLI',async()=>{
  const host=harness({refreshFailure:'AUTH_UNAVAILABLE'});host.secrets.set('spp.accessToken',JSON.stringify({...host.fresh,expiresAt:1}));
  await host.commands.get('spp.status')();
  assert.deepEqual(host.calls.map(call=>String(call[2][0])),['auth-refresh','status']);assert.equal(host.secrets.size,1);
});
test('explicit refresh denial deletes extension secrets and its proofs',async()=>{
  const host=harness({refreshFailure:'AUTH_DENIED'});host.secrets.set('spp.accessToken',JSON.stringify({...host.fresh,expiresAt:1}));
  await host.commands.get('spp.status')();assert.equal(host.secrets.size,0);
  assert.deepEqual(host.calls.map(call=>String(call[2][0])),['auth-refresh','logout']);assert.equal(host.errors.length,1);
});
test('unregister confirms server revocation before clearing local credential',async()=>{
  const host=harness();host.secrets.set('spp.accessToken',JSON.stringify(host.fresh));
  await host.commands.get('spp.unregister')();assert.equal(host.secrets.size,0);
  assert.deepEqual(host.calls.map(call=>String(call[2][0])),['auth-revoke','logout']);
});

test('corrupt stored credential can still be signed out',async()=>{
  const host=harness();host.secrets.set('spp.accessToken','{broken');
  await host.commands.get('spp.signOut')();assert.equal(host.secrets.size,0);assert.deepEqual(host.errors,[]);
});

test('generation observes only explicit user consent and honors disabled VS Code telemetry',async()=>{
  for(const [settings,expected] of [[{},false],[{workspaceUsageConsent:true},false],[{usageConsent:true},true],[{usageConsent:true,telemetryEnabled:false},false],[{usageConsent:false,workspaceUsageConsent:true},false]]) {
    const host=harness(settings);host.secrets.set('spp.accessToken',JSON.stringify(host.fresh));
    await host.commands.get('spp.generate')();assert.deepEqual(host.errors,[]);
    assert.equal(host.calls.at(-1)[3]?.usageEvents === true,expected);
    assert.equal(host.calls.at(-1)[2].includes('--usage-events'),false);
    assert.equal(host.calls.at(-1)[2][0],'generate');
  }
  const host=harness({usageConsent:true});host.secrets.set('spp.accessToken',JSON.stringify(host.fresh));await host.commands.get('spp.check')();assert.equal(host.calls.at(-1)[2].includes('--usage-events'),false);
});

 test('check and generate pass the selected APP environment separately from the physical profile',async()=>{
  const host=harness({environment:'testing',physicalProfile:'postgres'});await host.commands.get('spp.signIn')();
  for(const operation of ['check','generate']) {await host.commands.get('spp.'+operation)();const args=host.calls.at(-1)[2];assert.equal(args[args.indexOf('--environment')+1],'testing');assert.equal(args[args.indexOf('--profile')+1],'postgres');}
  assert.deepEqual(host.errors,[]);
});

for (const operation of ['signIn', 'check', 'generate']) test(`missing SDK gives actionable guidance for ${operation} without launching Java`, async () => {
  const host = harness({ sdkMissing: true, configuredJar: '' });
  await host.commands.get(`spp.${operation}`)();
  assert.equal(host.calls.length, 0);
  assert.equal(host.secrets.size, 0);
  assert.match(host.errors[0], /spp.cliJar/);
  assert.doesNotMatch(host.errors[0], /ENOENT|stat /);
  await host.commands.get('spp.restartLanguageServer')();
  assert.deepEqual(host.languageCalls, ['refresh']);
});
test('missing SDK configuration action opens user settings', async () => {
  const host = harness({ sdkMissing: true, configureSdk: true });
  await host.commands.get('spp.signIn')();
  assert.deepEqual(host.settingsCalls, [['workbench.action.openSettings', 'spp.cliJar']]);
});
test('an SDK path pointing to a directory is rejected before Java execution', async () => {
  const host = harness({ sdkDirectory: true });
  await host.commands.get('spp.signIn')();
  assert.equal(host.calls.length, 0);
  assert.match(host.errors[0], /SDK JAR is unavailable/);
});

 test('uses bundled CLI for sign-in when no user override is configured', async () => {
  const h = harness({ configuredJar: '' });
  await h.commands.get('spp.signIn')();
  assert.equal(h.errors.length, 0);
  assert.ok(h.calls.length > 0);
  for (const call of h.calls) assert.equal(call[1], '/extension/server/spp-cli.jar');
});


test('basic commands compile without credentials, refresh, login or usage reporting', async () => {
  const host = harness({ basic: true, usageConsent: true });
  for (const operation of ['check', 'generate']) {
    await host.commands.get('spp.' + operation)();
    assert.deepEqual(host.errors, []);
    const call = host.calls.at(-1);
    assert.equal(call[2][0], operation);
    assert.equal(call[3], undefined);
    assert.equal(call[4].unauthenticated, true);
  }
  await host.commands.get('spp.signIn')();
  await host.commands.get('spp.status')();
  assert.deepEqual(host.calls.map(call => call[2][0]), ['check', 'generate']);
  assert.equal(host.capabilityCalls.length, 4);
  assert.ok(host.capabilityCalls.every(call => call[4].unauthenticated));
  assert.equal(host.secrets.size, 0);
});
