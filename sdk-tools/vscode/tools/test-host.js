'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const https = require('node:https');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const { stageExtension } = require('./package-vsix');

function command(executable, argumentsList, cwd, timeout=30000) {
  const result = spawnSync(executable, argumentsList, { cwd, shell: false, encoding: 'utf8', timeout });
  if (result.error || result.status !== 0) throw new Error(`${path.basename(executable)} failed while preparing the isolated host test.`);
}

async function main(argumentsList) {
  const options = {};
  for (let index = 0; index < argumentsList.length; index++) {
    if (!['--cli-jar', '--language-server-jar', '--ui-artifacts', '--test-tool', '--version', '--cache', '--maven-repo', '--vscode-executable', '--release-tool', '--proguard-classpath-file', '--result-file'].includes(argumentsList[index]) || !argumentsList[index + 1])
      throw new Error('Usage: node tools/test-host.js --cli-jar FILE --test-tool /path/to/@vscode/test-electron [--version VERSION] [--cache DIR]');
    options[argumentsList[index]] = argumentsList[++index];
  }
  if (!options['--cli-jar'] || !options['--test-tool']) throw new Error('Supply the real packaged CLI and official @vscode/test-electron tool.');
  if(Boolean(options['--release-tool']) !== Boolean(options['--proguard-classpath-file'])) throw new Error('Supply both release tooling and its ProGuard classpath.');
  const testTool = require(path.resolve(options['--test-tool']));
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'spp-host-test-'));
  await fs.chmod(directory, 0o700);
  let staged;
  let server;
  try {
    const privateKeyFile = path.join(directory, 'tls.key');
    const certificateFile = path.join(directory, 'tls.crt');
    command('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', privateKeyFile,
      '-out', certificateFile, '-days', '1', '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], directory);
    const trustStore = path.join(directory, 'trust.p12');
    command('keytool', ['-importcert', '-alias', 'fixture', '-file', certificateFile, '-keystore', trustStore,
      '-storetype', 'PKCS12', '-storepass', 'test-only-password', '-noprompt'], directory);
    const signingKeys = crypto.generateKeyPairSync('ed25519');
    const accessToken = 'a'.repeat(43), refreshedToken='c'.repeat(43);
    const refreshToken='r_'+'b'.repeat(43), nextRefreshToken='r_'+'d'.repeat(43), deviceCode='e'.repeat(43);
    let approved=false, rotated=false;
    const origin=()=>`https://127.0.0.1:${server.address().port}`;
    const revokedFile = path.join(directory, 'revoked'), offlineFile=path.join(directory,'offline');
    let requests = 0, offlineAttempts=0;
    server = https.createServer({ key: await fs.readFile(privateKeyFile), cert: await fs.readFile(certificateFile) }, async (request, response) => {
      try {
        if(request.method==='POST' && request.url==='/fixture/approve') {
          approved=true;response.writeHead(200).end('{}');return;
        }
        if(request.method==='POST' && request.url.startsWith('/v1/sdk/device/')) {
          const chunks=[];for await(const chunk of request)chunks.push(chunk);
          const input=new URLSearchParams(Buffer.concat(chunks).toString('utf8'));
          const send=(status,value)=>response.writeHead(status,{'Content-Type':'application/json'}).end(JSON.stringify(value));
          if(request.url.endsWith('/code')) {approved=false;rotated=false;send(200,{device_code:deviceCode,user_code:'ABCD-2345',verification_uri:origin()+'/sdk/activate',expires_in:600,interval:1});return;}
          if(input.get('grant_type')==='refresh_token') {
            if(!approved || rotated || input.get('refresh_token')!==refreshToken) {send(400,{error:'invalid_grant'});return;}
            rotated=true;
          }else if(!approved) {send(400,{error:'authorization_pending',interval:1});return;}
          else if(input.get('device_code')!==deviceCode) {send(400,{error:'invalid_grant'});return;}
          send(200,{token_type:'Bearer',access_token:rotated?refreshedToken:accessToken,refresh_token:rotated?nextRefreshToken:refreshToken,
            expires_at:Math.floor(Date.now()/1000)+(rotated?3600:20),installation:'11111111-2222-3333-4444-555555555555',receipt:'CLIENT1'});return;
        }
        if (request.method !== 'POST' || request.url !== '/v1/sdk/authorize'
          || request.headers.authorization !== `Bearer ${rotated?refreshedToken:accessToken}`) {
          response.writeHead(401).end();
          return;
        }
        try {await fs.access(offlineFile);offlineAttempts++;response.destroy();return;}
        catch(error){if(error.code!=='ENOENT')throw error;}
        requests++;
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        const input = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        if (input.audience !== 'spp-sdk' || !['check', 'generate'].includes(input.feature)) throw new Error();
        try {
          await fs.access(revokedFile);
          response.writeHead(403).end();
          return;
        } catch (error) {
          if (error.code !== 'ENOENT') throw error;
        }
        const now = Math.floor(Date.now() / 1000);
        const header = Buffer.from(JSON.stringify({ alg: 'EdDSA', typ: 'JWT' })).toString('base64url');
        // Deliberately put renewal in the past: each normal operation checks online without timing-dependent sleeps.
        const payload = Buffer.from(JSON.stringify({ iss: 'https://servpp.test', aud: 'spp-sdk', sub: 'fixture-account',
          seat: 'fixture-seat', iat: now-2, nbf: now-2, exp: now + 300, features: [input.feature],
          leaseVersion:2,clientVersion:'0.1.0',releaseVersion:input.releaseVersion,refreshAt:now-1,
          credentialHash:crypto.createHash('sha256').update(rotated?refreshedToken:accessToken).digest('hex') })).toString('base64url');
        const signed = `${header}.${payload}`;
        const lease = `${signed}.${crypto.sign(null, Buffer.from(signed), signingKeys.privateKey).toString('base64url')}`;
        response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ lease }));
      } catch {
        response.writeHead(500).end();
      }
    });
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    let fixtureJar = path.join(directory, 'test-only-sdk.jar');
    await fs.copyFile(path.resolve(options['--cli-jar']), fixtureJar);
    await fs.writeFile(path.join(directory, 'sdk-release.json'), JSON.stringify({
      authorizationUrl: `https://127.0.0.1:${server.address().port}/v1/sdk/authorize`, issuer: 'https://servpp.test',
      audience: 'spp-sdk', publicKey: signingKeys.publicKey.export({ format: 'der', type: 'spki' }).toString('base64')
    }));
    command('jar', ['uf', fixtureJar, '-C', directory, 'sdk-release.json'], directory);
    let protectedArtifact=false;
    if(options['--release-tool']) {
      const key=path.join(directory,'isolated-release-key.pem');
      const releaseKeys=crypto.generateKeyPairSync('ed25519');
      await fs.writeFile(key,releaseKeys.privateKey.export({format:'pem',type:'pkcs8'}),{mode:0o600});
      command('python3',[path.resolve(options['--release-tool']),'build','--input',fixtureJar,
        '--output',path.join(directory,'release-public'),'--private-dir',path.join(directory,'release-private'),
        '--signing-key',key,'--proguard-classpath-file',path.resolve(options['--proguard-classpath-file']),
        '--version','isolated-desktop-host','--channel','local-test'],directory,120000);
      fixtureJar=path.join(directory,'release-public','spp-cli.jar');
      protectedArtifact=true;
    }
    const artifactSha256=crypto.createHash('sha256').update(await fs.readFile(fixtureJar)).digest('hex');
    let fixtureLanguageJar;
    if (options['--language-server-jar']) {
      fixtureLanguageJar = path.join(directory, 'test-only-language-server.jar');
      await fs.copyFile(path.resolve(options['--language-server-jar']), fixtureLanguageJar);
    }
    staged = await stageExtension({ extensionRoot: path.resolve(__dirname, '..'), cliJar: fixtureJar,
      languageServerJar: fixtureLanguageJar, preview: true });
    const bundledHash=()=>fs.readFile(path.join(staged.directory,'server/spp-cli.jar')).then(bytes=>crypto.createHash('sha256').update(bytes).digest('hex'));
    if(await bundledHash()!==artifactSha256)throw new Error('Bundled SDK bytes differ from the verified artifact.');
    const suite = path.join(staged.directory, 'test/host-suite.js');
    await fs.mkdir(path.dirname(suite));
    await fs.copyFile(path.resolve(__dirname, '../test/host-suite.js'), suite);
    const workspace = path.join(directory, 'workspace');
    const source = path.join(workspace, 'src/main/spp');
    await fs.mkdir(source, { recursive: true });
    await fs.writeFile(path.join(source, 'ledger.spp'), 'domain ledger;\nRole Facts { int amount; }\natomic calculate twice(Facts) { Facts.amount *= 2; }\nScenario serial preview(Facts) { twice; }\n');
    const resultFile = path.join(directory, 'result.json');
    const configuration = { accessToken, refreshedToken, refreshToken, nextRefreshToken, deviceCode, certificateFile, origin:origin(), source, output: path.join(workspace, 'target/generated'), revokedFile, offlineFile, resultFile,
      language: Boolean(fixtureLanguageJar) };
    if (options['--ui-artifacts']) {
      configuration.uiArtifacts = path.resolve(options['--ui-artifacts']);
      await fs.mkdir(configuration.uiArtifacts);
    }
    const vscode = options['--vscode-executable'] ? path.resolve(options['--vscode-executable'])
      : await testTool.downloadAndUnzipVSCode({ version: options['--version'] || '1.140.0',
        cachePath: options['--cache'] ? path.resolve(options['--cache']) : path.join(os.tmpdir(), 'spp-sdk-vscode-electron') });
    await testTool.runTests({ vscodeExecutablePath: vscode, extensionDevelopmentPath: staged.directory, extensionTestsPath: suite,
      extensionTestsEnv: { SPP_HOST_TEST_CONFIGURATION: JSON.stringify(configuration),
        ELECTRON_RUN_AS_NODE: undefined, VSCODE_IPC_HOOK_CLI: undefined, SPP_HOME:path.join(directory,'sdk-home'),
        JAVA_TOOL_OPTIONS: `-Djavax.net.ssl.trustStore=${trustStore} -Djavax.net.ssl.trustStorePassword=test-only-password` },
      launchArgs: [workspace, '--user-data-dir', path.join(directory, 'profile'), '--extensions-dir', path.join(directory, 'extensions'),
        '--disable-extensions', '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes', '--disable-updates',
        '--disable-telemetry', '--password-store=basic', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'] });
    if(await bundledHash()!==artifactSha256)throw new Error('The SDK artifact changed during host validation.');
    const results = JSON.parse(await fs.readFile(resultFile, 'utf8'));
    if (requests !== 5 || offlineAttempts<1 || !rotated || Object.values(results).some(value => value !== true))
      throw new Error(`Actual host validation was incomplete: ${requests} authorization requests.`);
    const buildArguments = ['-B', '-f', path.join(configuration.output, 'pom.xml'), 'verify'];
    if (options['--maven-repo']) buildArguments.unshift(`-Dmaven.repo.local=${path.resolve(options['--maven-repo'])}`);
    const build = spawnSync('mvn', buildArguments, { cwd: directory, shell: false, encoding: 'utf8', timeout: 120000 });
    if (build.error || build.status !== 0) throw new Error('Real-host generated project Maven verification failed.\n'
      + (build.stdout || '').slice(-5000) + (build.stderr || '').slice(-1000));
    const evidence={ok:true,authorizedRequests:requests,offlineAttempts,...results,generatedProjectCompiled:true,protectedArtifact,artifactSha256};
    if(options['--result-file']) {
      const destination=path.resolve(options['--result-file']);
      await fs.mkdir(path.dirname(destination),{recursive:true});
      await fs.writeFile(destination,JSON.stringify(evidence,null,2)+'\n',{flag:'wx'});
    }
    process.stdout.write(JSON.stringify(evidence) + '\n');
  } finally {
    if (server) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
    if (staged) await fs.rm(staged.directory, { recursive: true, force: true });
    await fs.rm(directory, { recursive: true, force: true });
  }
}

main(process.argv.slice(2)).catch(error => {
  process.stderr.write(error.message + '\n');
  process.exitCode = 1;
});
