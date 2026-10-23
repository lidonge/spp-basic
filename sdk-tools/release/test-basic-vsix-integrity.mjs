import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { verifyArtifact, publicKeyId, digest } from './vsix-integrity.mjs';

function fixture() {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const pem = publicKey.export({ type: 'spki', format: 'pem' });
  const artifact = Buffer.from('basic VSIX test bytes');
  const manifest = { schema: 1, kind: 'servpp-vsix', channel: 'local-test', keyId: publicKeyId(pem),
    fileName: 'spp-tools-basic-0.1.2.vsix', extensionId: 'servpp.spp-tools-basic',
    version: '0.1.2', sha256: digest(artifact), bytes: artifact.length };
  const manifestBytes = Buffer.from(JSON.stringify(manifest));
  return { artifact, manifestBytes, signature: sign(null, manifestBytes, privateKey),
    publicKey: pem, expectedVersion: '0.1.2', expectedExtension: 'servpp.spp-tools-basic', allowLocalTest: true };
}

test('basic edition signature requires independently selected basic identity and version', () => {
  assert.equal(verifyArtifact(fixture()).extensionId, 'servpp.spp-tools-basic');
  assert.throws(() => verifyArtifact({ ...fixture(), expectedExtension: undefined }), /Unexpected extension identity/);
  assert.throws(() => verifyArtifact({ ...fixture(), expectedExtension: 'servpp.spp-tools-preview' }), /Unexpected extension identity/);
  assert.throws(() => verifyArtifact({ ...fixture(), expectedVersion: '0.1.3' }), /Unexpected release version/);
});

test('basic edition rejects corrupted bytes, wrong keys and implicit local-test trust', () => {
  assert.throws(() => verifyArtifact({ ...fixture(), artifact: Buffer.from('modified') }), /integrity mismatch/);
  assert.throws(() => verifyArtifact({ ...fixture(), publicKey: fixture().publicKey }), /Invalid release signature/);
  assert.throws(() => verifyArtifact({ ...fixture(), allowLocalTest: false }), /explicit opt-in/);
});


test('downstream CLI default key retains explicit opt-in and cannot authenticate production', async () => {
  const { main } = await import('./verify-vsix.mjs');
  const { mkdtemp, writeFile, rm } = await import('node:fs/promises');
  const { tmpdir } = await import('node:os');
  const { join } = await import('node:path');
  const directory = await mkdtemp(join(tmpdir(), 'basic-cli-default-key-'));
  try {
    const { privateKey, publicKey } = generateKeyPairSync('ed25519');
    const pem = publicKey.export({ type: 'spki', format: 'pem' });
    const artifact = Buffer.from('default key compatibility fixture');
    const key = join(directory, 'key.pem');
    const file = join(directory, 'artifact.vsix');
    const catalog = join(directory, 'manifest.json');
    const signature = join(directory, 'manifest.sig');
    const argumentsList = ['--artifact', file, '--manifest', catalog, '--signature', signature,
      '--expected-version', '0.1.2', '--expected-extension', 'servpp.spp-tools-basic'];
    await writeFile(key, pem); await writeFile(file, artifact);
    for (const channel of ['local-test', 'release']) {
      const manifest = { schema: 1, kind: 'servpp-vsix', channel, keyId: publicKeyId(pem),
        fileName: 'spp-tools-basic-0.1.2.vsix', extensionId: 'servpp.spp-tools-basic',
        version: '0.1.2', sha256: digest(artifact), bytes: artifact.length };
      const bytes = Buffer.from(JSON.stringify(manifest));
      await writeFile(catalog, bytes); await writeFile(signature, sign(null, bytes, privateKey));
      await assert.rejects(main(argumentsList, { defaultKey: key }), /independently trusted/);
      if (channel === 'local-test') await main([...argumentsList, '--allow-local-test'], { defaultKey: key });
      else await assert.rejects(main([...argumentsList, '--allow-local-test'], { defaultKey: key }), /cannot authenticate production/);
    }
  } finally { await rm(directory, { recursive: true, force: true }); }
});
