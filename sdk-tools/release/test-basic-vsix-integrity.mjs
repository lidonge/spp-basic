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
