'use strict';

const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');

function rejectCommercialEntries(entries) {
  const artifacts = ['appframe-enhanced', 'appframe-mybatis', 'appframe-seata', 'generator-enhanced', 'spp-sdk-core', 'spp-cli'];
  const classes = [
    'com/servpp/sdk/OnlineAuthorizer', 'com/servpp/sdk/LicensedCompiler',
    'com/servpp/service/runtime/OfficialPropertyChanges',
    'com/servpp/service/transaction/OfficialUpdateScheduling', 'com/servpp/service/transaction/TransactionTasks',
    'com/servpp/service/idempotency/DeclaredRequestIdempotency', 'com/servpp/service/idempotency/RequestClaimExecution',
    'com/servpp/service/idempotency/RequestParticipantReferences',
    'com/servpp/service/technical/OfficialSystemTechnicalServices', 'com/servpp/service/technical/InvocationTraceService',
    'com/servpp/service/technical/PartialUpdateService'
  ];
  if (entries.some(raw => {
    const entry = raw.replace(/^META-INF\/versions\/\d+\//, '');
    return artifacts.some(artifact => entry.startsWith(`META-INF/maven/com.servpp/${artifact}/`))
      || entry === 'sdk-release.json' || entry.includes('/enhanced/')
      || entry.startsWith('com/servpp/service/runtime/seata/') || entry.startsWith('com/servpp/service/repository/mybatis/')
      || entry.startsWith('com/servpp/service/idempotency/Official')
      || classes.some(name => entry === name + '.class' || entry.startsWith(name + '$'));
  })) throw new Error('Basic packaging refuses commercial compiler or runtime components.');
}

function validateReleaseConfiguration(release) {
  try {
    const endpoint = new URL(release.authorizationUrl);
    const key = crypto.createPublicKey({ key: Buffer.from(release.publicKey, 'base64'), format: 'der', type: 'spki' });
    if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password || endpoint.search || endpoint.hash
      || key.asymmetricKeyType !== 'ed25519' || typeof release.issuer !== 'string' || !release.issuer.trim()
      || typeof release.audience !== 'string' || !release.audience.trim()) throw new Error();
  } catch {
    throw new Error('Release packaging requires a fixed HTTPS authorization endpoint and valid Ed25519 public key.');
  }
}

async function stageExtension({ extensionRoot, cliJar, languageServerJar, preview = false, basic = false, jarExecutable = 'jar', manifestPath }) {
  if (basic && !cliJar) throw new Error('Basic packaging requires --cli-jar from the basic CLI build.');
  if (!preview && !cliJar) throw new Error('Release packaging requires --cli-jar from a trusted CLI build.');
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'spp-vsix-'));
  try {
    const manifest = JSON.parse(await fs.readFile(manifestPath || path.join(extensionRoot, 'package.json'), 'utf8'));
    if (preview) {
      manifest.name = 'spp-tools-preview';
      manifest.displayName = 'S++ Tools (development preview)';
      manifest.description = 'Free local S++ editing; compilation and Java generation require an authorized SDK account.';
      manifest.preview = true;
    }
    if (basic) {
      manifest.name = 'spp-tools-basic';
      manifest.displayName = 'S++ Tools (basic)';
      manifest.description = 'Local S++ editing, offline compilation and Java generation without an account or scenario quota.';
      manifest.license = 'Apache-2.0';
      manifest.contributes.commands = manifest.contributes.commands.filter(command => !['spp.signIn', 'spp.signOut', 'spp.unregister'].includes(command.command));
      manifest.contributes.commands.find(command => command.command === 'spp.status').title = 'S++: Compiler Edition';
      delete manifest.contributes.configuration.properties['spp.shareUsageEvents'];
    }
    delete manifest.scripts;
    delete manifest.dependencies;
    delete manifest.devDependencies;
    manifest.files = ['src/**', 'client-dist/**', 'syntaxes/**', 'snippets/**', 'language-configuration.json', 'README.md', 'package.json'];
    if (basic) manifest.files.push('LICENSE', 'NOTICE');
    if (cliJar || languageServerJar) manifest.files.push('server/**');
    await fs.writeFile(path.join(directory, 'package.json'), JSON.stringify(manifest, null, 2) + '\n');
    await fs.cp(path.join(extensionRoot, 'src'), path.join(directory, 'src'), { recursive: true });
    try {
      await fs.access(path.join(extensionRoot, 'client-dist/language-runtime.js'));
    } catch {
      throw new Error('Run npm ci --ignore-scripts and npm run build:client before packaging.');
    }
    await fs.cp(path.join(extensionRoot, 'client-dist'), path.join(directory, 'client-dist'), { recursive: true });
    for (const resource of ['syntaxes', 'snippets', 'language-configuration.json'])
      await fs.cp(path.join(extensionRoot, resource), path.join(directory, resource), { recursive: true });
    await fs.copyFile(path.join(extensionRoot, basic ? 'README.basic.md' : 'README.md'), path.join(directory, 'README.md'));
    if (basic) {
      await fs.copyFile(path.join(extensionRoot, 'LICENSE.basic'), path.join(directory, 'LICENSE'));
      await fs.copyFile(path.join(extensionRoot, 'NOTICE.basic'), path.join(directory, 'NOTICE'));
    }
    if (cliJar) {
      const mainClass = basic ? 'com/servpp/sdk/basic/Main.class' : 'com/servpp/sdk/Main.class';
      const configurationFile = basic ? 'spp-edition.json' : 'sdk-release.json';
      const listing = spawnSync(jarExecutable, ['tf', path.resolve(cliJar)],
        { encoding: 'utf8', shell: false, timeout: 30000, maxBuffer: 16 * 1024 * 1024 });
      const entries = (listing.stdout || '').split(/\r?\n/);
      if (listing.status !== 0 || !entries.includes(mainClass)
          || !entries.includes('com/servpp/generator/GeneratorUtil.class') || !entries.includes(configurationFile))
        throw new Error('The CLI JAR must contain the CLI, generator and matching edition configuration.');
      if (basic) rejectCommercialEntries(entries);
      const extracted = spawnSync(jarExecutable, ['xf', path.resolve(cliJar), configurationFile],
        { cwd: directory, shell: false, encoding: 'utf8', timeout: 30000 });
      if (extracted.status !== 0) throw new Error('Cannot inspect SDK edition configuration.');
      const release = JSON.parse(await fs.readFile(path.join(directory, configurationFile), 'utf8'));
      if (basic) {
        if (release.edition !== 'basic' || release.authorizationRequired !== false) throw new Error('Invalid basic edition configuration.');
      } else if (!preview) validateReleaseConfiguration(release);
      await fs.unlink(path.join(directory, configurationFile));
      const server = path.join(directory, 'server');
      await fs.mkdir(server);
      await fs.copyFile(cliJar, path.join(server, 'spp-cli.jar'));
      const hash = crypto.createHash('sha256').update(await fs.readFile(cliJar)).digest('hex');
      await fs.writeFile(path.join(server, 'bundle.json'), JSON.stringify({ version: manifest.version, edition: basic ? 'basic' : 'commercial', cliSha256: hash }, null, 2) + '\n');
    }
    if (!preview && !languageServerJar) throw new Error('Release packaging requires --language-server-jar from a trusted build.');
    if (languageServerJar) {
      const listing = spawnSync(jarExecutable, ['tf', path.resolve(languageServerJar)],
        { encoding: 'utf8', shell: false, timeout: 30000, maxBuffer: 16 * 1024 * 1024 });
      if (listing.status !== 0 || !listing.stdout.split(/\r?\n/).includes('com/servpp/sdk/editor/EditorMain.class'))
        throw new Error('Invalid S++ language server JAR.');
      if (basic) rejectCommercialEntries(listing.stdout.split(/\r?\n/));
      const server = path.join(directory, 'server');
      await fs.mkdir(server, { recursive: true });
      await fs.copyFile(languageServerJar, path.join(server, 'spp-language-server.jar'));
      const metadataFile = path.join(server, 'bundle.json');
      const metadata = cliJar ? JSON.parse(await fs.readFile(metadataFile, 'utf8')) : { version: manifest.version };
      metadata.languageServerSha256 = crypto.createHash('sha256').update(await fs.readFile(languageServerJar)).digest('hex');
      await fs.writeFile(metadataFile, JSON.stringify(metadata, null, 2) + '\n');
    }
    return { directory, manifest };
  } catch (error) {
    await fs.rm(directory, { recursive: true, force: true });
    throw error;
  }
}

async function main(argumentsList) {
  const options = {};
  for (let index = 0; index < argumentsList.length; index++) {
    const argument = argumentsList[index];
    if (argument === '--basic') options.basic = true;
    else if (argument === '--preview') options.preview = true;
    else if (['--cli-jar', '--language-server-jar', '--vsce', '--out', '--manifest'].includes(argument) && argumentsList[index + 1]
      && !argumentsList[index + 1].startsWith('--')) options[argument] = argumentsList[++index];
    else throw new Error('Usage: node tools/package-vsix.js --vsce /path/to/vsce [--preview] [--basic] [--cli-jar FILE] [--language-server-jar FILE] [--out FILE] [--manifest FILE]');
  }
  if (!options['--vsce']) throw new Error('Supply the official @vscode/vsce JavaScript entry point with --vsce.');
  const extensionRoot = path.resolve(__dirname, '..');
  const staged = await stageExtension({ extensionRoot, cliJar: options['--cli-jar'], languageServerJar: options['--language-server-jar'], preview: options.preview, basic: options.basic, manifestPath: options['--manifest'] });
  try {
    const output = options['--out'] ? path.resolve(options['--out'])
      : path.join(extensionRoot, 'dist', `${staged.manifest.name}-${staged.manifest.version}.vsix`);
    await fs.mkdir(path.dirname(output), { recursive: true });
    const result = spawnSync(process.execPath, [path.resolve(options['--vsce']), 'package', '--no-dependencies',
      '--allow-missing-repository', '--skip-license', '--pre-release', '--out', output],
    { cwd: staged.directory, shell: false, stdio: 'inherit', timeout: 120000 });
    if (result.error || result.status !== 0) throw new Error('Official VSIX packaging failed.');
    process.stdout.write(output + '\n');
  } finally {
    await fs.rm(staged.directory, { recursive: true, force: true });
  }
}

if (require.main === module) main(process.argv.slice(2)).catch(error => {
  process.stderr.write(error.message + '\n');
  process.exitCode = 1;
});

module.exports = { stageExtension, validateReleaseConfiguration };
