import { createHash, createPublicKey, verify } from 'node:crypto';

export const digest = data => createHash('sha256').update(data).digest('hex');
export function publicKeyId(pem) {
  const key = createPublicKey(pem);
  if (key.asymmetricKeyType !== 'ed25519') throw new Error('Ed25519 release key required');
  return digest(key.export({ type: 'spki', format: 'der' }));
}
export function validateManifest(manifest) {
  if (manifest.schema !== 1 || manifest.kind !== 'servpp-vsix' ||
      !['local-test', 'release'].includes(manifest.channel) ||
      !/^\d+\.\d+\.\d+$/.test(manifest.version) ||
      !['servpp.spp-tools-preview', 'servpp.spp-tools-basic'].includes(manifest.extensionId) ||
      manifest.fileName !== `${manifest.extensionId.slice('servpp.'.length)}-${manifest.version}.vsix` ||
      !/^[a-f0-9]{64}$/.test(manifest.sha256) ||
      !/^[a-f0-9]{64}$/.test(manifest.keyId) ||
      !Number.isSafeInteger(manifest.bytes) || manifest.bytes <= 0 || manifest.bytes > 256 * 1024 * 1024)
    throw new Error('Invalid VSIX release manifest');
  return manifest;
}
export function verifyArtifact({ artifact, manifestBytes, signature, publicKey, expectedVersion,
                                 expectedExtension = 'servpp.spp-tools-preview', allowLocalTest = false }) {
  if (!expectedVersion) throw new Error('An independently selected expected version is required');
  if (!['servpp.spp-tools-preview', 'servpp.spp-tools-basic'].includes(expectedExtension))
    throw new Error('A supported independently selected extension identity is required');
  if (manifestBytes.length > 16384 || signature.length !== 64 ||
      !verify(null, manifestBytes, publicKey, signature)) throw new Error('Invalid release signature');
  const manifest = validateManifest(JSON.parse(manifestBytes.toString('utf8')));
  if (manifest.extensionId !== expectedExtension) throw new Error('Unexpected extension identity');
  if (manifest.keyId !== publicKeyId(publicKey)) throw new Error('Release key mismatch');
  if (manifest.channel === 'local-test' && !allowLocalTest) throw new Error('Local-test release requires explicit opt-in');
  if (manifest.version !== expectedVersion) throw new Error('Unexpected release version');
  if (artifact.length !== manifest.bytes || digest(artifact) !== manifest.sha256)
    throw new Error('VSIX integrity mismatch');
  return manifest;
}
