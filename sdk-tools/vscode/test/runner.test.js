'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { PassThrough } = require('node:stream');
const { runCli } = require('../src/runner');

function fakeSpawn(result, code = 0, inspect = () => {}) {
  return (executable, argumentsList, options) => {
    const child = new EventEmitter();
    child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
    child.kill = () => child.emit('close', null);
    inspect(executable, argumentsList, options, child);
    child.stdin.on('finish', () => {
      child.stdout.write(result);
      child.emit('close', code);
    });
    return child;
  };
}

test('passes credentials over stdin and does not invoke a shell', async () => {
  let received = '';
  const result = await runCli('java', '/trusted sdk/cli.jar', ['check', '--source', '/source with spaces'], 'secret', {
    spawn: fakeSpawn('{"ok":true,"result":{"checked":true}}', 0, (executable, args, options, child) => {
      assert.equal(executable, 'java');
      assert.equal(options.shell, false);
      assert.equal(args.includes('secret'), false);
      assert.equal(args.at(-1), '--token-stdin');
      child.stdin.on('data', value => { received += value; });
    })
  });
  assert.equal(result.checked, true);
  assert.equal(JSON.parse(received).accessToken, 'secret');
});

test('rejects authorization failure without echoing arbitrary server messages', async () => {
  await assert.rejects(runCli('java', '/cli.jar', ['generate'], 'secret', {
    spawn: fakeSpawn('{"ok":false,"error":{"code":"AUTH_DENIED","message":"secret"}}', 3)
  }), error => error.message.includes('AUTH_DENIED') && !error.message.includes('secret'));
});

test('rejects invalid protocol', async () => {
  await assert.rejects(runCli('java', '/cli.jar', ['check'], 'secret', {
    spawn: fakeSpawn('not json')
  }), /Invalid S\+\+ CLI response/);
});

test('redacts token from diagnostics', async () => {
  let diagnostic = '';
  await runCli('java', '/cli.jar', ['check'], 'secret', {
    spawn: fakeSpawn('{"ok":true,"result":{}}', 0, (_executable, _args, _options, child) => {
      process.nextTick(() => child.stderr.write('token secret'));
    }),
    onDiagnostic: value => { diagnostic += value; }
  });
  await new Promise(resolve => process.nextTick(resolve));
  assert.equal(diagnostic, 'token [redacted]');
});

test('kills a timed-out compiler', async () => {
  let killed = false;
  await assert.rejects(runCli('java', '/cli.jar', ['check'], 'secret', {
    timeout: 5,
    spawn: () => {
      const child = new EventEmitter();
      child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
      child.kill = () => { killed = true; };
      return child;
    }
  }), /timed out/);
  assert.equal(killed, true);
});

test('manifest permits only basic editing without trust and requires filesystem for execution', () => {
  const manifest = require('../package.json');
  assert.equal(manifest.capabilities.untrustedWorkspaces.supported, 'limited');
  assert.equal(manifest.capabilities.virtualWorkspaces.supported, false);
  assert.equal(manifest.contributes.configuration.properties['spp.cliJar'].scope, 'machine');
  assert.deepEqual(manifest.extensionKind, ['workspace']);
});

test('redacts tokens split across diagnostic chunks', async () => {
  let diagnostic = '';
  await runCli('java', '/cli.jar', ['check'], 'private-token', {
    spawn: fakeSpawn('{"ok":true,"result":{}}', 0, (_executable, _args, _options, child) => {
      process.nextTick(() => {
        child.stderr.write('message private-');
        child.stderr.write('token done');
      });
    }),
    onDiagnostic: value => { diagnostic += value; }
  });
  assert.equal(diagnostic, 'message [redacted] done');
});

test('preserves unicode JSON split across output chunks', async () => {
  const result = await runCli('java', '/cli.jar', ['check'], 'secret', {
    spawn: (_executable, _args, _options) => {
      const child = new EventEmitter();
      child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
      child.kill = () => {};
      child.stdin.on('finish', () => {
        const response = Buffer.from('{"ok":true,"result":{"source":"工程"}}');
        for (const byte of response) child.stdout.write(Buffer.from([byte]));
        child.emit('close', 0);
      });
      return child;
    }
  });
  assert.equal(result.source, '工程');
});

test('already-cancelled operations never start a compiler', async () => {
  await assert.rejects(runCli('java', '/cli.jar', ['check'], 'secret', {
    cancellation: { isCancellationRequested: true },
    spawn: () => { throw new Error('Compiler should not start'); }
  }), /cancelled/);
});

test('private authentication IPC redacts every secret across chunk boundaries and never uses arguments', async () => {
  let diagnostic='',received='';
  const input={clientId:'spp-vscode',accessToken:'private-access',refreshToken:'private-refresh',deviceCode:'private-device'};
  await runCli('java','/trusted.jar',['auth-refresh'],input,{
    spawn:fakeSpawn('{"ok":true,"result":{}}',0,(_exe,args,_options,child)=>{
      for(const secret of Object.values(input).slice(1))assert.equal(args.includes(secret),false);
      child.stdin.on('data',chunk=>{received+=chunk;});
      process.nextTick(()=>{for(const chunk of ['private-ac','cess private-ref','resh private-de','vice done'])child.stderr.write(chunk);});
    }),onDiagnostic:message=>{diagnostic+=message;}
  });
  assert.deepEqual(JSON.parse(received),input);assert.equal(diagnostic,'[redacted] [redacted] [redacted] done');
});

test('withdrawn edition advises replacement without echoing server diagnostics',async()=>{
  await assert.rejects(runCli('java','/cli.jar',['check'],'secret',{
    spawn:fakeSpawn('{"ok":false,"error":{"code":"SDK_RELEASE_UNAVAILABLE","message":"secret"}}',3)
  }),error=>error.code==='SDK_RELEASE_UNAVAILABLE' && /supported SDK edition/.test(error.message) && !error.message.includes('secret'));
});


test('keeps the bounded conversion timeout code without echoing private messages', async () => {
  await assert.rejects(runCli('java', '/cli.jar', ['generate'], 'secret', {
    spawn: fakeSpawn('{"ok":false,"error":{"code":"COMPILATION_TIMEOUT","message":"secret"}}', 2)
  }), error => error.code === 'COMPILATION_TIMEOUT' && !error.message.includes('secret'));
});

test('reports membership quota errors without displaying arbitrary CLI message contents',async()=>{await assert.rejects(runCli('java','/cli.jar',['generate'],'secret',{spawn:fakeSpawn('{"ok":false,"error":{"code":"SCENARIO_LIMIT_EXCEEDED","message":"secret"}}',2)}),error=>error.code==='SCENARIO_LIMIT_EXCEEDED' && error.message.includes('scenario limit') && !error.message.includes('secret'));});


test('offline compiler and capability probe receive no credential arguments or stdin', async () => {
  let received = '';
  await runCli('java', '/basic.jar', ['--version'], undefined, {
    unauthenticated: true,
    spawn: fakeSpawn('{"ok":true,"result":{"edition":"basic","authorizationRequired":false}}', 0, (_executable, args, _options, child) => {
      assert.equal(args.includes('--token-stdin'), false);
      child.stdin.on('data', value => { received += value; });
    })
  });
  assert.equal(received, '');
});
