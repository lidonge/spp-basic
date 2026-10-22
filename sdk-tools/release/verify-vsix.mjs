#!/usr/bin/env node
// Run from an independently trusted checkout, never from the unverified download.
import { readFile, mkdtemp, writeFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { verifyArtifact } from './vsix-integrity.mjs';

async function boundedRead(path, maximum) {
  if ((await stat(path)).size > maximum) throw new Error('Release input exceeds size limit');
  const bytes = await readFile(path);
  if (bytes.length > maximum) throw new Error('Release input exceeds size limit');
  return bytes;
}
async function main() {
  const options = {};
  for (let i = 2; i < process.argv.length; i++) {
    const name = process.argv[i];
    if (name === '--allow-local-test') options[name] = true;
    else if (['--artifact', '--manifest', '--signature', '--public-key', '--expected-version', '--expected-extension', '--install-with'].includes(name) && process.argv[i + 1] && !process.argv[i + 1].startsWith('--')) options[name] = process.argv[++i];
    else throw new Error('Unknown or incomplete argument: ' + name);
  }
  for (const required of ['--artifact', '--manifest', '--signature', '--expected-version'])
    if (!options[required]) throw new Error('Required: ' + required);
  // The checked-in key is for local testing only. Production consumers must obtain
  // the production key through an independent trusted distribution channel.
  const defaultKey = resolve(dirname(fileURLToPath(import.meta.url)), 'keys/vsix-local-test-public.pem');
  if (!options['--public-key'] && !options['--allow-local-test']) throw new Error('Supply an independently trusted production public key');
  const artifact = await boundedRead(options['--artifact'], 256 * 1024 * 1024);
  const manifest = verifyArtifact({ artifact,
    manifestBytes: await boundedRead(options['--manifest'], 16384),
    signature: await boundedRead(options['--signature'], 64),
    publicKey: await boundedRead(options['--public-key'] || defaultKey, 8192),
    expectedVersion: options['--expected-version'], expectedExtension: options['--expected-extension'],
    allowLocalTest: !!options['--allow-local-test'] });
  if (!options['--public-key'] && manifest.channel !== 'local-test') throw new Error('The bundled test key cannot authenticate production releases');
  console.log(JSON.stringify({ verified: true, version: manifest.version, extensionId: manifest.extensionId, channel: manifest.channel, keyId: manifest.keyId }));
  if (options['--install-with']) {
    if (!options['--install-with'].startsWith('/') && !/^[A-Za-z]:[\\/]/.test(options['--install-with'])) throw new Error('Installer executable must be an absolute trusted path');
    const directory = await mkdtemp(join(tmpdir(), 'servpp-verified-vsix-'));
    try {
      const verifiedPath = join(directory, manifest.fileName);
      // Install the verified bytes, not a mutable original download path.
      await writeFile(verifiedPath, artifact, { mode: 0o600, flag: 'wx' });
      const result = spawnSync(options['--install-with'], ['--install-extension', verifiedPath], { shell: false, stdio: 'inherit', timeout: 120000 });
      if (result.error || result.status !== 0) throw new Error('VS Code installation failed');
    } finally { await rm(directory, { recursive: true, force: true }); }
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
