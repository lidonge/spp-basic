'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const vscode = require('vscode');

async function run() {
  const configuration = JSON.parse(process.env.SPP_HOST_TEST_CONFIGURATION);
  const extension = vscode.extensions.getExtension('servpp.spp-tools-preview');
  assert.ok(extension, 'The staged extension must be loaded by the real VS Code host.');
  const capturedOutput = [];
  const createOutputChannel = vscode.window.createOutputChannel;
  vscode.window.createOutputChannel = function (...argumentsList) {
    const channel = createOutputChannel.apply(this, argumentsList);
    if (argumentsList[0] === 'S++') for (const method of ['append', 'appendLine']) {
      const original = channel[method];
      channel[method] = function (value) {
        capturedOutput.push(String(value));
        // Keep the complete stream so early authentication output cannot escape the final secret check.
        return original.call(this, value);
      };
    }
    return channel;
  };
  try {
    const bootstrap = await vscode.workspace.openTextDocument({ language: 'spp', content: 'domain bootstrap;\n' });
    await vscode.window.showTextDocument(bootstrap);
    for (let attempt = 0; attempt < 100 && !extension.isActive; attempt++)
      await new Promise(resolve => setTimeout(resolve, 100));
    assert.equal(extension.isActive, true, 'Opening an S++ document must activate the extension without a manual activate call.');
    await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
  } finally { vscode.window.createOutputChannel = createOutputChannel; }
  assert.equal(vscode.workspace.isTrusted, true);
  const manifest = extension.packageJSON;
  assert.equal(manifest.contributes.grammars[0].scopeName, 'source.spp');
  for (const resource of ['syntaxes/spp.tmLanguage.json', 'snippets/spp.json', 'language-configuration.json'])
    assert.ok((await fs.stat(path.join(extension.extensionPath, resource))).isFile(), resource);
  const scratch = await vscode.workspace.openTextDocument({ language: 'spp', content: 'domain scratch;\n' });
  const editor = await vscode.window.showTextDocument(scratch);
  editor.selection = new vscode.Selection(0, 0, 0, 0);
  for (let attempt = 0; attempt < 20 && !scratch.getText().startsWith('//'); attempt++) {
    await vscode.commands.executeCommand('editor.action.commentLine');
    if (!scratch.getText().startsWith('//')) await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.match(scratch.getText(), /^\/\/\s*domain scratch;/);
  await vscode.commands.executeCommand('editor.action.commentLine');
  assert.equal(scratch.getText(), 'domain scratch;\n');
  editor.selection = new vscode.Selection(1, 0, 1, 0);
  const snippets = JSON.parse(await fs.readFile(path.join(extension.extensionPath, 'snippets/spp.json'), 'utf8'));
  await editor.insertSnippet(new vscode.SnippetString(snippets.Role.body.join('\n')));
  assert.match(scratch.getText(), /Role Facts \{/);
  await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
  const commands = await vscode.commands.getCommands(true);
  for (const command of ['spp.signIn', 'spp.signOut', 'spp.status', 'spp.check', 'spp.generate'])
    assert.ok(commands.includes(command), `${command} must be registered`);

  const originals = {};
  const errors = [];
  const replacements = {
    showInputBox: async () => configuration.accessToken,
    showOpenDialog: async () => [vscode.Uri.file(configuration.source)],
    showSaveDialog: async () => vscode.Uri.file(configuration.output),
    showInformationMessage: async () => undefined,
    showErrorMessage: async message => { errors.push(message); }
  };
  const openExternal=vscode.env.openExternal;
  vscode.env.openExternal=async uri=>{
    assert.equal(uri.toString(),configuration.origin+'/sdk/activate');
    // Synthetic browser consent only; registration/business/browser form flow has separate real browser coverage.
    await new Promise((resolve,reject)=>{
      const request=require('node:https').request(configuration.origin+'/fixture/approve',{method:'POST',ca:require('node:fs').readFileSync(configuration.certificateFile)},response=>{
        response.resume();response.on('end',()=>response.statusCode===200?resolve():reject(new Error('Fixture approval failed')));
      });request.on('error',reject);request.end();
    });return true;
  };
  try {
    for (const [name, implementation] of Object.entries(replacements)) {
      originals[name] = vscode.window[name];
      vscode.window[name] = implementation;
    }
    const languageResults = {};
    let languageDocument;
    if (configuration.language) {
      languageDocument = await vscode.workspace.openTextDocument(vscode.Uri.file(path.join(configuration.source, 'ledger.spp')));
      await vscode.window.showTextDocument(languageDocument);
      const position = new vscode.Position(2, languageDocument.lineAt(2).text.indexOf('amount'));
      let definitions;
      for (let attempt = 0; attempt < 100; attempt++) {
        definitions = await vscode.commands.executeCommand('vscode.executeDefinitionProvider', languageDocument.uri, position);
        if (definitions?.length) break;
        await new Promise(resolve => setTimeout(resolve, 200));
      }
      const languageStatus = await vscode.commands.executeCommand('spp.languageStatus');
      assert.equal(definitions?.length, 1, `The real Java language service must return a definition: ${JSON.stringify(languageStatus)}\n${capturedOutput.join('\n').slice(-6000)}`);
      assert.equal(definitions[0].targetUri.fsPath, languageDocument.uri.fsPath);
      assert.equal(definitions[0].targetSelectionRange.start.line, 1);
      assert.equal(definitions[0].targetSelectionRange.start.character, 17);
      const declarations = await vscode.commands.executeCommand('vscode.executeDeclarationProvider', languageDocument.uri, position);
      assert.deepEqual(declarations[0].targetSelectionRange, definitions[0].targetSelectionRange);
      const hovers = await vscode.commands.executeCommand('vscode.executeHoverProvider', languageDocument.uri, position);
      assert.ok(hovers.some(hover => hover.contents.some(content => /int(?:\s|&nbsp;|\\ )+amount/.test(String(content.value || content)))), JSON.stringify(hovers));
      const outline = await vscode.commands.executeCommand('vscode.executeDocumentSymbolProvider', languageDocument.uri);
      assert.ok(outline.some(symbol => symbol.name === 'ledger' && symbol.children.some(type => type.name === 'Facts'
        && type.children.some(child => child.name === 'amount'))));
      const symbols = await vscode.commands.executeCommand('vscode.executeWorkspaceSymbolProvider', 'twice');
      assert.ok(symbols.some(symbol => symbol.name === 'twice'));
      Object.assign(languageResults, { definitions: true, declarations: true, hover: true, outline: true, workspaceSymbols: true });
      const originalText = languageDocument.getText();
      const renameEditor = await vscode.window.showTextDocument(languageDocument);
      const replaceAll = async text => renameEditor.edit(builder => builder.replace(new vscode.Range(
        languageDocument.positionAt(0), languageDocument.positionAt(languageDocument.getText().length)), text));
      assert.equal(await replaceAll(originalText.replaceAll('amount', 'balance')), true);
      assert.equal(languageDocument.isDirty, true);
      assert.equal(await fs.readFile(languageDocument.uri.fsPath, 'utf8'), originalText);
      const renamedPosition = new vscode.Position(2, languageDocument.lineAt(2).text.indexOf('balance'));
      const renamed = await vscode.commands.executeCommand('vscode.executeDefinitionProvider', languageDocument.uri, renamedPosition);
      assert.equal(languageDocument.getText(renamed[0].targetSelectionRange), 'balance');
      assert.equal(await replaceAll(originalText), true);
      const restored = await vscode.commands.executeCommand('vscode.executeDefinitionProvider', languageDocument.uri, position);
      assert.equal(languageDocument.getText(restored[0].targetSelectionRange), 'amount');
      languageResults.unsavedNavigation = true;
      if (configuration.uiArtifacts) {
        const { execFileSync } = require('node:child_process');
        const settings = vscode.workspace.getConfiguration();
        for (const [key, value] of Object.entries({ 'editor.fontSize': 16, 'editor.lineHeight': 24,
          'editor.minimap.enabled': false, 'breadcrumbs.enabled': false, 'editor.multiCursorModifier': 'alt' }))
          await settings.update(key, value, vscode.ConfigurationTarget.Global);
        await vscode.commands.executeCommand('workbench.action.closeSidebar');
        await vscode.commands.executeCommand('workbench.action.closePanel');
        const editor = await vscode.window.showTextDocument(languageDocument);
        const call = new vscode.Position(3, languageDocument.lineAt(3).text.indexOf('twice;'));
        editor.selection = new vscode.Selection(call, call);
        editor.revealRange(new vscode.Range(0, 0, 3, 0));
        for (const [theme, filename] of [['Default Dark Modern', 'dark.png'], ['Default Light Modern', 'light.png']]) {
          await settings.update('workbench.colorTheme', theme, vscode.ConfigurationTarget.Global);
          await new Promise(resolve => setTimeout(resolve, 900));
          execFileSync('import', ['-window', 'root', path.join(configuration.uiArtifacts, filename)], { timeout: 10000 });
        }
        await fs.writeFile(path.join(configuration.uiArtifacts, 'ready.json'), JSON.stringify({
          call: { line: call.line, character: call.character }, target: { line: 2, character: 17 }
        }));
        let control;
        for (let attempt = 0; attempt < 450; attempt++) {
          try { control = JSON.parse(await fs.readFile(path.join(configuration.uiArtifacts, 'click.json'), 'utf8')); break; }
          catch (error) { if (error.code !== 'ENOENT') throw error; }
          await new Promise(resolve => setTimeout(resolve, 200));
        }
        assert.ok(control && Number.isInteger(control.x) && Number.isInteger(control.y)
          && control.x >= 0 && control.x < 5000 && control.y >= 0 && control.y < 5000, 'Supply click.json coordinates after inspecting the captured editor.');
        await vscode.commands.executeCommand('workbench.action.focusActiveEditorGroup');
        execFileSync('xdotool', ['keydown', 'ctrl', 'mousemove', '--sync', String(control.x), String(control.y),
          'sleep', '0.5', 'click', '1', 'keyup', 'ctrl'], { timeout: 10000 });
        async function assertNativeTarget() {
          for (let attempt = 0; attempt < 50; attempt++) {
            const active = vscode.window.activeTextEditor;
            if (active?.document.uri.toString() === languageDocument.uri.toString()
              && active.selection.start.isEqual(new vscode.Position(2, 17))) return;
            await new Promise(resolve => setTimeout(resolve, 100));
          }
          execFileSync('import', ['-window', 'root', path.join(configuration.uiArtifacts, 'navigation-failed.png')], { timeout: 10000 });
          assert.fail('Native input did not navigate to the service definition.');
        }
        await assertNativeTarget();
        editor.selection = new vscode.Selection(call, call);
        execFileSync('xdotool', ['key', 'F12'], { timeout: 10000 });
        await assertNativeTarget();
        Object.assign(languageResults, { themeScreenshots: true, nativeCtrlClick: true, nativeF12: true });
      }
    }
    await vscode.commands.executeCommand('spp.signIn');
    await vscode.commands.executeCommand('spp.status');
    await vscode.commands.executeCommand('spp.check');
    await vscode.commands.executeCommand('spp.generate');
    assert.deepEqual(errors, []);
    assert.ok((await fs.stat(path.join(configuration.output, 'pom.xml'))).isFile());
    assert.ok((await fs.stat(path.join(configuration.output,
      'src/main/java/com/servpp/generated/ledger/model/Facts.java'))).isFile());

    // The HTTPS fixture really drops the connection. The SDK must use an existing signed v2 proof.
    await fs.writeFile(configuration.offlineFile,'offline');
    await vscode.commands.executeCommand('spp.check');
    assert.deepEqual(errors,[],'A temporary connection loss may use only the signed remaining allowance.');
    await fs.unlink(configuration.offlineFile);

    await fs.writeFile(configuration.revokedFile, 'revoked');
    if (configuration.language) {
      await vscode.commands.executeCommand('spp.restartLanguageServer');
      const results = await vscode.commands.executeCommand('vscode.executeDefinitionProvider', languageDocument.uri, new vscode.Position(2, 38));
      assert.equal(results?.length, 1, 'Revoking a compiler seat must not disable free editor navigation.');
      languageResults.freeEditorAfterRevocation = true;
    }
    await vscode.commands.executeCommand('spp.check');
    assert.match(errors.at(-1), /AUTH_DENIED/);
    assert.equal(errors.length, 1);
    await vscode.commands.executeCommand('spp.signOut');
    if (configuration.language) {
      const results = await vscode.commands.executeCommand('vscode.executeDefinitionProvider', languageDocument.uri, new vscode.Position(2, 38));
      assert.equal(results?.length, 1, 'Signing out must not disable free editor navigation.');
      languageResults.freeEditorAfterSignOut = true;
    }
    await vscode.commands.executeCommand('spp.status');
    assert.match(errors.at(-1), /Sign In/);
    assert.equal(errors.length, 2);
    for(const secret of [configuration.accessToken,configuration.refreshedToken,configuration.refreshToken,configuration.nextRefreshToken,configuration.deviceCode])
      assert.equal(capturedOutput.join(' ').includes(secret),false,'Authentication secrets must not enter output');
    await fs.writeFile(configuration.resultFile, JSON.stringify({ deviceLogin:true, automaticRefresh:true, offlineSignedProof:true, secretsAbsentFromOutput:true, activated: true, generated: true,
      revokedSeatRejected: true, signedOut: true, basicEditing: true, automaticActivation: true, ...languageResults }));
    console.log('S++ actual extension host: activation, sign-in, status, check, generate, seat revocation and sign-out passed.');
  } finally {
    vscode.env.openExternal=openExternal;
    for (const [name, implementation] of Object.entries(originals)) vscode.window[name] = implementation;
  }
}

module.exports = { run };
