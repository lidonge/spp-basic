'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const { stageExtension, validateReleaseConfiguration } = require('../tools/package-vsix');
const { generateKeyPairSync } = require('node:crypto');
const { existsSync } = require('node:fs');
const { createHash } = require('node:crypto');

const extensionRoot = path.resolve(__dirname, '..');
const builtJar = path.resolve(__dirname, '../../cli/target/spp-cli-1.0-SNAPSHOT.jar');
const languageJar = path.resolve(__dirname, '../../language-server/target/spp-language-server-1.0-SNAPSHOT.jar');

test('free language server bundles without authorization configuration', { skip: !existsSync(languageJar) }, async () => {
  const staged = await stageExtension({ extensionRoot, preview: true, languageServerJar: languageJar });
  try {
    const bundled = await fs.readFile(path.join(staged.directory, 'server/spp-language-server.jar'));
    assert.deepEqual(bundled, await fs.readFile(languageJar));
    const metadata = JSON.parse(await fs.readFile(path.join(staged.directory, 'server/bundle.json'), 'utf8'));
    assert.equal(metadata.languageServerSha256, createHash('sha256').update(bundled).digest('hex'));
    assert.equal(existsSync(path.join(staged.directory, 'sdk-release.json')), false);
  } finally {
    await fs.rm(staged.directory, { recursive: true, force: true });
  }
});

test('release packaging refuses a missing compiler', async () => {
  await assert.rejects(stageExtension({ extensionRoot }), /requires --cli-jar/);
});

test('release packaging refuses an invalid or incomplete CLI JAR', async () => {
  await assert.rejects(stageExtension({ extensionRoot, cliJar: '/nonexistent/spp-cli.jar' }), /must contain the CLI/);
});

test('preview mode cannot bundle an incomplete CLI JAR', async () => {
  await assert.rejects(stageExtension({ extensionRoot, preview: true, cliJar: '/nonexistent/spp-cli.jar' }), /must contain the CLI/);
});

test('preview packaging is clearly named and contains only supported extension files', async () => {
  const staged = await stageExtension({ extensionRoot, preview: true });
  try {
    assert.equal(staged.manifest.name, 'spp-tools-preview');
    assert.equal(staged.manifest.preview, true);
    assert.equal(staged.manifest.scripts, undefined);
    assert.equal(await fs.readFile(path.join(staged.directory, 'src/extension.js'), 'utf8'),
      await fs.readFile(path.join(extensionRoot, 'src/extension.js'), 'utf8'));
    assert.deepEqual((await fs.readdir(staged.directory)).sort(),
      ['README.md', 'client-dist', 'language-configuration.json', 'package.json', 'snippets', 'src', 'syntaxes']);
    for (const resource of ['language-configuration.json', 'syntaxes/spp.tmLanguage.json', 'snippets/spp.json'])
      assert.equal(await fs.readFile(path.join(staged.directory, resource), 'utf8'),
        await fs.readFile(path.join(extensionRoot, resource), 'utf8'));
  } finally {
    await fs.rm(staged.directory, { recursive: true, force: true });
  }
});

test('release configuration accepts only fixed HTTPS and Ed25519 trust settings', () => {
  const publicKey = generateKeyPairSync('ed25519').publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  const configuration = { authorizationUrl: 'https://servpp.test/v1/sdk/authorize',
    issuer: 'https://servpp.test', audience: 'spp-sdk', publicKey };
  assert.doesNotThrow(() => validateReleaseConfiguration(configuration));
  for (const change of [
    { authorizationUrl: '' }, { authorizationUrl: 'http://servpp.test/auth' },
    { authorizationUrl: 'https://user@servpp.test/auth' }, { authorizationUrl: 'https://servpp.test/auth?key=override' },
    { authorizationUrl: 'https://servpp.test/auth#fragment' }, { issuer: '' }, { audience: '' }, { publicKey: '' }
  ]) assert.throws(() => validateReleaseConfiguration({ ...configuration, ...change }), /requires a fixed HTTPS/);
  const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 }).publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  assert.throws(() => validateReleaseConfiguration({ ...configuration, publicKey: rsa }), /Ed25519/);
});

test('bundled preview accepts the real large CLI JAR and records its digest', { skip: !existsSync(builtJar) }, async () => {
  const staged = await stageExtension({ extensionRoot, preview: true, cliJar: builtJar });
  try {
    const bundled = await fs.readFile(path.join(staged.directory, 'server/spp-cli.jar'));
    const original = await fs.readFile(builtJar);
    assert.deepEqual(bundled, original);
    const metadata = JSON.parse(await fs.readFile(path.join(staged.directory, 'server/bundle.json'), 'utf8'));
    assert.equal(metadata.cliSha256, createHash('sha256').update(original).digest('hex'));
    assert.equal(staged.manifest.preview, true);
    assert.equal(existsSync(path.join(staged.directory, 'sdk-release.json')), false);
  } finally {
    await fs.rm(staged.directory, { recursive: true, force: true });
  }
});

test('normal bundle rejects a real default-deny development SDK', { skip: !existsSync(builtJar) }, async () => {
  await assert.rejects(stageExtension({ extensionRoot, cliJar: builtJar }), /requires a fixed HTTPS/);
});


const basicJar = path.resolve(__dirname, '../../basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar');
test('basic bundle rejects an embedded official scheduling implementation without Maven metadata', { skip: !existsSync(basicJar) }, async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'spp-basic-contamination-'));
  try {
    const contaminated = path.join(directory, 'contaminated.jar');
    await fs.copyFile(basicJar, contaminated);
    const entry = 'com/servpp/service/transaction/OfficialUpdateScheduling.class';
    await fs.mkdir(path.dirname(path.join(directory, entry)), { recursive: true });
    await fs.writeFile(path.join(directory, entry), 'test-only class marker');
    const patched = spawnSync('jar', ['uf', contaminated, '-C', directory, entry], { encoding: 'utf8' });
    assert.equal(patched.status, 0, patched.stderr);
    await assert.rejects(stageExtension({ extensionRoot, basic: true, cliJar: contaminated }), /refuses commercial/);
  } finally { await fs.rm(directory, { recursive: true, force: true }); }
});
test('basic bundle carries offline compiler and hides membership commands', { skip: !existsSync(basicJar) || !existsSync(languageJar) }, async () => {
  const staged = await stageExtension({ extensionRoot, basic: true, cliJar: basicJar, languageServerJar: languageJar });
  try {
    assert.equal(staged.manifest.name, 'spp-tools-basic');
    assert.equal(staged.manifest.license, 'Apache-2.0');
    assert.match(await fs.readFile(path.join(staged.directory, 'LICENSE'), 'utf8'), /Apache License/);
    assert.match(await fs.readFile(path.join(staged.directory, 'NOTICE'), 'utf8'), /Third-party/);
    assert.match(await fs.readFile(path.join(staged.directory, 'README.md'), 'utf8'), /no account/);
    assert.ok(staged.manifest.contributes.commands.every(command => !['spp.signIn', 'spp.signOut', 'spp.unregister'].includes(command.command)));
    const bundle = JSON.parse(await fs.readFile(path.join(staged.directory, 'server/bundle.json'), 'utf8'));
    assert.equal(bundle.edition, 'basic');
    assert.equal(bundle.cliSha256, createHash('sha256').update(await fs.readFile(basicJar)).digest('hex'));
  } finally { await fs.rm(staged.directory, { recursive: true, force: true }); }
});
test('basic bundle rejects commercial code hidden in a multi-release language server entry', { skip: !existsSync(basicJar) || !existsSync(languageJar) }, async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'spp-basic-lsp-contamination-'));
  try {
    const contaminated = path.join(directory, 'language-server.jar');
    await fs.copyFile(languageJar, contaminated);
    const entry = 'META-INF/versions/17/com/servpp/service/technical/PartialUpdateService.class';
    await fs.mkdir(path.dirname(path.join(directory, entry)), { recursive: true });
    await fs.writeFile(path.join(directory, entry), 'test-only class marker');
    const patched = spawnSync('jar', ['uf', contaminated, '-C', directory, entry], { encoding: 'utf8' });
    assert.equal(patched.status, 0, patched.stderr);
    await assert.rejects(stageExtension({ extensionRoot, basic: true, cliJar: basicJar, languageServerJar: contaminated }), /refuses commercial/);
  } finally { await fs.rm(directory, { recursive: true, force: true }); }
});
test('basic packaging rejects licensed CLI and basic without a CLI', async () => {
  await assert.rejects(stageExtension({ extensionRoot, basic: true }), /requires --cli-jar/);
  if (existsSync(builtJar)) await assert.rejects(stageExtension({ extensionRoot, basic: true, cliJar: builtJar }), /must contain the CLI|refuses commercial/);
});

test('downstream manifest reuses shared sources without changing the basic manifest', async () => {
  const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'spp-downstream-manifest-'));
  const original = await fs.readFile(path.join(extensionRoot, 'package.json'), 'utf8');
  const manifest = JSON.parse(original);
  manifest.publisher = 'downstream-test';
  manifest.contributes.configuration.properties['spp.cliJar'].default = '/configured/member.jar';
  const manifestPath = path.join(temporary, 'member.json');
  await fs.writeFile(manifestPath, JSON.stringify(manifest));
  let staged;
  try {
    staged = await stageExtension({ extensionRoot, preview: true, manifestPath });
    assert.equal(staged.manifest.publisher, 'downstream-test');
    assert.equal(staged.manifest.contributes.configuration.properties['spp.cliJar'].default, '/configured/member.jar');
    assert.deepEqual(await fs.readFile(path.join(staged.directory, 'src/extension.js')),
      await fs.readFile(path.join(extensionRoot, 'src/extension.js')));
    assert.equal(await fs.readFile(path.join(extensionRoot, 'package.json'), 'utf8'), original);
    assert.equal(existsSync(path.join(staged.directory, 'member.json')), false);
    await assert.rejects(stageExtension({ extensionRoot, manifestPath }), /requires --cli-jar/);
  } finally {
    if (staged) await fs.rm(staged.directory, { recursive: true, force: true });
    await fs.rm(temporary, { recursive: true, force: true });
  }
});
