'use strict';

const fs = require('node:fs');
const { runCli } = require('../src/runner');

const configuration = JSON.parse(fs.readFileSync(0, 'utf8'));
async function execute() {
  const invoke=(argumentsList,input)=>runCli(configuration.executable,configuration.jar,argumentsList,input,
    {cwd:configuration.cwd,onDiagnostic:message=>process.stderr.write(message)});
  if(!configuration.deviceLogin) return invoke(configuration.arguments,configuration.accessToken);
  // Private IPC results are intentionally consumed here, never forwarded to logs or the outer JSON result.
  const start=await invoke(['auth-start'],{clientId:'spp-vscode'});
  const pair=await invoke(['auth-poll'],{clientId:'spp-vscode',deviceCode:start.deviceCode});
  if(pair.pending || !pair.accessToken)throw new Error('Synthetic device exchange was not completed.');
  await invoke(['status'],pair.accessToken);
  const next=await invoke(['auth-refresh'],{clientId:'spp-vscode',refreshToken:pair.refreshToken});
  if(next.accessToken===pair.accessToken || next.installation!==pair.installation)throw new Error('Rotation failed.');
  await invoke(['status'],next.accessToken);
  await invoke(['auth-revoke'],{clientId:'spp-vscode',accessToken:next.accessToken});
  await invoke(['logout'],next.accessToken);
  return {deviceLogin:true,rotated:true,revoked:true,scopedLogout:true};
}
execute().then(result => process.stdout.write(JSON.stringify({ ok: true, result }) + '\n'))
  .catch(error => {
    process.stdout.write(JSON.stringify({ ok: false, error: error.message }) + '\n');
    process.exitCode = 1;
  });
