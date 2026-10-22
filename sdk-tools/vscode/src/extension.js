'use strict';

const vscode = require('vscode');
const fs = require('node:fs/promises');
const path = require('node:path');
const { runCli } = require('./runner');
const { createLanguageController } = require('./language-client');
const { setTimeout: delay } = require('node:timers/promises');
const { performance } = require('node:perf_hooks');

function activate(context) {
  const output = vscode.window.createOutputChannel('S++');
  context.subscriptions.push(output);
  const language = createLanguageController(context, output);
  let busy = false;
  let commandBusy = false;
  const secretKey = 'spp.accessToken';

  function register(name, action) {
    context.subscriptions.push(vscode.commands.registerCommand(name, async () => {
      const accountCommand = !['spp.restartLanguageServer','spp.languageStatus'].includes(name);
      if (accountCommand && commandBusy) { vscode.window.showErrorMessage('An S++ account or compiler operation is already running.'); return; }
      if (accountCommand) commandBusy = true;
      try {
        if (!vscode.workspace.isTrusted) throw new Error('Trust this workspace before running S++ tools.');
        return await action();
      } catch (error) {
        if (error.code === 'SDK_NOT_CONFIGURED') {
          const choice = await vscode.window.showErrorMessage(error.message, 'Configure SDK');
          if (choice === 'Configure SDK') await vscode.commands.executeCommand('workbench.action.openSettings', 'spp.cliJar');
        } else vscode.window.showErrorMessage(error.message);
      } finally { if (accountCommand) commandBusy = false; }
    }));
  }

  async function cliJar() {
    const configured = vscode.workspace.getConfiguration('spp').get('cliJar');
    const jar = configured || context.asAbsolutePath('server/spp-cli.jar');
    let available = false;
    if (path.isAbsolute(jar)) {
      try { available = (await fs.stat(jar)).isFile(); }
      catch (error) { if (!['ENOENT', 'ENOTDIR', 'EACCES', 'EPERM'].includes(error.code)) throw error; }
    }
    if (!available) throw Object.assign(new Error('S++ SDK JAR is unavailable. Free editing, navigation and outline do not require it. To sign in, check projects or generate Java, install a trusted SDK and set spp.cliJar to its spp-cli.jar in User Settings.'), { code: 'SDK_NOT_CONFIGURED' });
    return jar;
  }

  async function invoke(argumentsList, token, cwd, suppliedCancellation, unauthenticated = false) {
    if (busy) throw new Error('An S++ operation is already running.');
    busy = true;
    try {
      const configuration = vscode.workspace.getConfiguration('spp');
      const jar = await cliJar();
      output.show(true);
      const execute = cancellation => runCli(configuration.get('javaExecutable', 'java'), jar, argumentsList, token,
        { cwd, cancellation, unauthenticated, onDiagnostic: message => output.append(message) });
      if (suppliedCancellation) return await execute(suppliedCancellation);
      return await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification,
        title: 'S++', cancellable: true }, async (_progress, cancellation) => execute(cancellation));
    } finally {
      busy = false;
    }
  }

  async function basicEdition() {
    const capabilities = await invoke(['--version'], undefined, undefined, undefined, true);
    return capabilities.edition === 'basic' && capabilities.authorizationRequired === false;
  }

  function savedCredential(value) {
    if (!value) return null;
    if (!value.startsWith('{')) return { accessToken: value };
    try {
      const data = JSON.parse(value);
      if (!/^[A-Za-z0-9_-]{43}$/.test(data.accessToken) || !/^r_[A-Za-z0-9_-]{43}$/.test(data.refreshToken)
          || !Number.isSafeInteger(data.expiresAt) || !/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(data.installation)
          || !/^[A-Za-z0-9_-]{1,80}$/.test(data.receipt)) throw new Error();
      return data;
    } catch { throw new Error('Stored S++ credential is invalid; sign out and sign in again.'); }
  }
  async function removeLocal(value) {
    // The stdin form removes only this extension credential's proofs and never the CLI profile.
    try { if (value) await invoke(['logout'], value.accessToken); }
    finally { await context.secrets.delete(secretKey); }
  }
  async function token() {
    let value = savedCredential(await context.secrets.get(secretKey));
    if (!value) throw new Error('Run S++: Sign In first.');
    if (value.refreshToken && value.expiresAt <= Math.floor(Date.now()/1000)+30) {
      try {
        const next = await invoke(['auth-refresh'], { clientId:'spp-vscode', refreshToken:value.refreshToken });
        if(next.installation !== value.installation || next.receipt !== value.receipt)
          throw Object.assign(new Error('S++ refresh returned a different installation.'),{code:'AUTH_PROTOCOL'});
        // Store rotated credentials before another command can use them.
        await context.secrets.store(secretKey,JSON.stringify(next));
        await invoke(['logout'],value.accessToken);
        value = next;
      } catch(error) {
        if (error.code !== 'AUTH_UNAVAILABLE') {
          if (error.code !== 'CANCELLED') await removeLocal(value);
          throw error;
        }
        // The CLI still verifies a signed proof's refresh point and hard expiry; an old token grants nothing alone.
      }
    }
    return value.accessToken;
  }
  register('spp.signIn', async () => {
    if (await basicEdition()) { vscode.window.showInformationMessage('S++ basic compilation works offline and needs no sign-in.'); return; }
    await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'S++ Sign In',cancellable:true},async (progress,cancellation) => {
      const started = await invoke(['auth-start'],{clientId:'spp-vscode'},undefined,cancellation);
      const deadline = performance.now()+started.expiresIn*1000;
      // Only the human code enters UI. Device, access and refresh secrets stay in private pipes/SecretStorage.
      progress.report({message:`Open ${started.verificationUri} and enter ${started.userCode}. Compare this code before approving.`});
      const opened = await vscode.env.openExternal(vscode.Uri.parse(started.verificationUri));
      if (!opened) vscode.window.showInformationMessage(`Open ${started.verificationUri} and enter ${started.userCode}.`);
      let interval = started.interval;
      while(true) {
        if (cancellation.isCancellationRequested) throw new Error('S++ sign-in cancelled.');
        if (performance.now()+interval*1000 >= deadline) throw new Error('S++ sign-in expired; start again.');
        const abort = new AbortController();
        const subscription = cancellation.onCancellationRequested?.(()=>abort.abort());
        try { await delay(interval*1000,undefined,{signal:abort.signal}); }
        catch { throw new Error('S++ sign-in cancelled.'); }
        finally { subscription?.dispose(); }
        const polled = await invoke(['auth-poll'],{clientId:'spp-vscode',deviceCode:started.deviceCode},undefined,cancellation);
        if (polled.pending) { interval = Math.max(interval,polled.interval); continue; }
        await invoke(['status'],polled.accessToken,undefined,cancellation);
        let previous;
        try { previous = savedCredential(await context.secrets.get(secretKey)); } catch { previous = null; }
        await context.secrets.store(secretKey,JSON.stringify(polled));
        if (previous) await invoke(['logout'],previous.accessToken,undefined,cancellation);
        vscode.window.showInformationMessage('S++ client authorized.');
        return;
      }
    });
  });
  register('spp.signOut', async () => {
    let value;
    try { value = savedCredential(await context.secrets.get(secretKey)); }
    catch { await context.secrets.delete(secretKey); vscode.window.showInformationMessage('Invalid S++ extension credential removed.'); return; }
    await removeLocal(value);
    vscode.window.showInformationMessage('S++ extension credentials and cached proofs removed.');
  });
  register('spp.unregister', async () => {
    const accessToken = await token();
    await invoke(['auth-revoke'],{clientId:'spp-vscode',accessToken});
    await removeLocal(savedCredential(await context.secrets.get(secretKey)));
    vscode.window.showInformationMessage('S++ client authorization revoked and local credentials removed.');
  });
  register('spp.restartLanguageServer', async () => { await language.refresh(); });
  register('spp.languageStatus', async () => {
    const state = language.status();
    output.appendLine(JSON.stringify(state));
    output.show(true);
    return state;
  });
  register('spp.status', async () => {
    if (await basicEdition()) { vscode.window.showInformationMessage('S++ basic edition: offline compilation, no account or scenario quota.'); return; }
    const result = await invoke(['status'], await token());
    vscode.window.showInformationMessage(`S++ authorized until ${new Date(result.expiresAt * 1000).toISOString()}`);
  });

  for (const operation of ['check', 'generate']) register(`spp.${operation}`, async () => {
    await cliJar();
    const basic = await basicEdition();
    const accessToken = basic ? undefined : await token();
    const folders = vscode.workspace.workspaceFolders || [];
    const folder = folders.length === 1 ? folders[0] : await vscode.window.showWorkspaceFolderPick();
    if (!folder || folder.uri.scheme !== 'file') throw new Error('Open a filesystem workspace first.');
    if (!(await vscode.workspace.saveAll(false))) throw new Error('Save your files before running S++.');
    const source = await vscode.window.showOpenDialog({ canSelectFiles: false, canSelectFolders: true,
      canSelectMany: false, title: 'Select S++ source directory',
      defaultUri: vscode.Uri.file(path.join(folder.uri.fsPath, 'src/main/spp')) });
    if (!source?.length || source[0].scheme !== 'file') return;
    const configuration = vscode.workspace.getConfiguration('spp', folder.uri);
    const argumentsList = [operation, '--source', source[0].fsPath, '--package', configuration.get('basePackage', 'com.servpp.generated')];
    const profile = configuration.get('physicalProfile', '');
    if (profile) argumentsList.push('--profile', profile);
    argumentsList.push('--environment', configuration.get('environment', 'develop'));
    let usageConsent=false;
    if (operation === 'generate') {
      const destination = await vscode.window.showSaveDialog({ title: 'Choose a NEW output directory (not an existing directory)',
        defaultUri: vscode.Uri.file(path.join(folder.uri.fsPath, 'target/spp-sdk')) });
      if (!destination || destination.scheme !== 'file') return;
      argumentsList.push('--output', destination.fsPath);
      // A repository/workspace setting must never opt its developer into reporting.
      const consent=vscode.workspace.getConfiguration('spp').inspect('shareUsageEvents')?.globalValue === true;
      usageConsent=!basic && consent && vscode.env.isTelemetryEnabled !== false;
    }
    // Older SDKs ignore this optional stdin field and still generate successfully.
    const result = await invoke(argumentsList, usageConsent?{accessToken,usageEvents:true}:accessToken, folder.uri.fsPath, undefined, basic);
    output.appendLine(JSON.stringify(result));
    vscode.window.showInformationMessage(operation === 'check' ? 'S++ check passed.' : 'S++ Java artifacts generated.');
  });
}

module.exports = { activate };
