'use strict';

const path = require('node:path');
const fs = require('node:fs/promises');

async function build() {
  await require('esbuild').build({
  entryPoints: [path.resolve(__dirname, '../src/language-runtime.js')],
  outfile: path.resolve(__dirname, '../client-dist/language-runtime.js'),
  bundle: true,
  platform: 'node',
  target: 'node18',
  format: 'cjs',
  external: ['vscode'],
  legalComments: 'linked',
  logLevel: 'warning'
  });
  const directory = path.resolve(__dirname, '../client-dist');
  const script = require.resolve('vscode-languageclient/lib/node/terminateProcess.sh');
  await fs.copyFile(script, path.join(directory, 'terminateProcess.sh'));
  await fs.chmod(path.join(directory, 'terminateProcess.sh'), 0o755);
  await fs.mkdir(path.join(directory, 'licenses'), { recursive: true });
  for (const dependency of ['vscode-languageclient', 'vscode-jsonrpc', 'vscode-languageserver-protocol',
    'vscode-languageserver-types', 'minimatch', 'semver', 'brace-expansion', 'balanced-match']) {
    const packageRoot = path.resolve(__dirname, '../node_modules', dependency);
    const license = (await fs.readdir(packageRoot)).find(name => /^licen[cs]e(?:\.|$)/i.test(name));
    if (!license) throw new Error(`Missing license for ${dependency}.`);
    await fs.copyFile(path.join(packageRoot, license), path.join(directory, 'licenses', `${dependency}.txt`));
  }
}

build().catch(error => { process.stderr.write(error.message + '\n'); process.exitCode = 1; });
