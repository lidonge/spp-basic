'use strict';

const vscode = require('vscode');
const path = require('node:path');
const fs = require('node:fs/promises');

function createLanguageController(context, output) {
  const entries = new Map();
  let generation = 0;
  let disposed = false;

  function stopEntry(entry) {
    entry.release(false);
    entry.watcher?.dispose();
    if (entry.client) entry.client.stop(3000).catch(() => undefined);
  }

  function stop() {
    generation++;
    for (const entry of entries.values()) stopEntry(entry);
    entries.clear();
  }

  async function ensure(document) {
    if (disposed || !vscode.workspace.isTrusted || document.languageId !== 'spp' || document.uri.scheme !== 'file') return;
    const folder = vscode.workspace.getWorkspaceFolder(document.uri);
    if (!folder || folder.uri.scheme !== 'file') return;
    const configuration = vscode.workspace.getConfiguration('spp', folder.uri);
    const sourceRoots = configuration.get('sourceRoots', ['src/main/spp']).map(root => path.resolve(folder.uri.fsPath, root));
    const inRoots = document => /\.(spp|enum|app|phy|rdb)$/.test(document.uri.fsPath)
      && !path.relative(folder.uri.fsPath, document.uri.fsPath).split(path.sep).some(part => ['.git', 'target', 'node_modules'].includes(part))
      && sourceRoots.some(root => {
      const relative = path.relative(root, document.uri.fsPath);
      return relative && relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative);
      });
    if (!inRoots(document)) return;
    const key = folder.uri.toString();
    if (entries.has(key)) return;
    if (entries.size >= 4) {
      output.appendLine('S++ language service supports up to four active workspace folders.');
      return;
    }
    const currentGeneration = generation;
    let release;
    const ready = new Promise(resolve => { release = resolve; });
    const entry = { release, client: null, watcher: null, workspace: folder.index + 1, state: 'starting', stage: 'jar' };
    const queuedOpens = new Map();
    entries.set(key, entry);
    const current = () => !disposed && vscode.workspace.isTrusted && generation === currentGeneration && entries.get(key) === entry;
    const belongs = document => document.uri.scheme === 'file'
      && vscode.workspace.getWorkspaceFolder(document.uri)?.uri.toString() === key && inRoots(document);
    const provide = async (document, ...argumentsList) => {
      const version = document.version;
      const next = argumentsList.pop();
      const cancellation = argumentsList.at(-1);
      if (await ready && current() && belongs(document) && document.version === version
          && !document.isClosed && !cancellation?.isCancellationRequested) {
        const result = await next(document, ...argumentsList);
        return current() && !document.isClosed && document.version === version && !cancellation?.isCancellationRequested
          ? result : undefined;
      }
      return undefined;
    };
    try {
      const jar = configuration.get('languageServerJar', '') || context.asAbsolutePath('server/spp-language-server.jar');
      if (!path.isAbsolute(jar) || !(await fs.stat(jar)).isFile()) throw new Error();
      entry.stage = 'runtime';
      const { LanguageClient, CloseAction, ErrorAction } = require('../client-dist/language-runtime');
      if (!current()) return;
      entry.watcher = vscode.workspace.createFileSystemWatcher(new vscode.RelativePattern(folder, '**/*.{spp,enum,app,phy,rdb}'));
      const quietTrace = { append() {}, appendLine() {}, clear() {}, show() {}, hide() {}, dispose() {}, name: 'S++ private protocol' };
      entry.stage = 'client';
      const client = new LanguageClient(`spp-language-${folder.index}`, 'S++ Language Service', {
        command: configuration.get('javaExecutable', 'java'), args: ['-Xmx512m', '-jar', jar],
        options: { cwd: folder.uri.fsPath, shell: false }
      }, {
        documentSelector: sourceRoots.map(root => ({ scheme: 'file', language: 'spp', pattern: new vscode.RelativePattern(root, '**/*') })),
        workspaceFolder: folder,
        initializationOptions: { sourceRoots: configuration.get('sourceRoots', ['src/main/spp']),
          physicalProfile: configuration.get('physicalProfile', '') },
        synchronize: { fileEvents: entry.watcher },
        outputChannel: output,
        traceOutputChannel: quietTrace,
        errorHandler: {
          error: () => { entry.state = 'failed'; return { action: ErrorAction.Shutdown }; },
          closed: () => {
            entry.state = 'stopped';
            entry.release(false);
            return { action: CloseAction.DoNotRestart };
          }
        },
        middleware: {
          didOpen: async (opened, next) => {
            if (!current() || !belongs(opened)) return;
            if (entry.state === 'ready') return next(opened);
            queuedOpens.set(opened.uri.toString(), { document: opened, next });
          },
          didChange: async (changed, next) => {
            if (entry.state === 'ready' && current() && belongs(changed.document)) return next(changed);
          },
          didClose: async (closed, next) => {
            queuedOpens.delete(closed.uri.toString());
            if (entry.state === 'ready' && current() && belongs(closed)) return next(closed);
          },
          provideDefinition: provide,
          provideDeclaration: provide,
          provideHover: provide,
          provideDocumentSymbols: provide,
          provideWorkspaceSymbols: async (query, cancellation, next) => {
            if (await ready && current() && !cancellation.isCancellationRequested) {
              const result = await next(query, cancellation);
              return current() && !cancellation.isCancellationRequested ? result : undefined;
            }
            return undefined;
          }
        }
      });
      entry.client = client;
      entry.stage = 'initialize';
      await client.start();
      if (!current()) { stopEntry(entry); return; }
      async function synchronize() {
        if (!current()) return;
        const initialDocuments = [];
        while (queuedOpens.size && current()) {
          const pending = [...queuedOpens.values()];
          queuedOpens.clear();
          for (const opened of pending) {
            if (!current()) return;
            initialDocuments.push(opened);
            if (!opened.document.isClosed) await opened.next(opened.document);
          }
        }
        entry.state = 'ready';
        entry.stage = 'ready';
        const synchronized = new Set();
        for (const { document: opened } of initialDocuments) {
          if (!current()) return;
          if (opened.isClosed) await client.sendNotification('textDocument/didClose', { textDocument: { uri: opened.uri.toString() } });
          else await client.sendNotification('textDocument/didChange', {
            textDocument: { uri: opened.uri.toString(), version: opened.version },
            contentChanges: [{ text: opened.getText() }]
          });
          synchronized.add(opened.uri.toString());
        }
        for (const opened of vscode.workspace.textDocuments) {
          if (!current()) return;
          if (opened.languageId === 'spp' && belongs(opened) && !opened.isClosed && !synchronized.has(opened.uri.toString()))
            await client.sendNotification('textDocument/didChange', {
              textDocument: { uri: opened.uri.toString(), version: opened.version }, contentChanges: [{ text: opened.getText() }]
            });
        }
        entry.release(true);
      }
      await synchronize();
      if (current()) output.appendLine(`S++ language features ready for workspace folder ${folder.index + 1}.`);
    } catch (error) {
      if (current()) {
        stopEntry(entry);
        entry.state = 'failed';
        entry.failure = ['MODULE_NOT_FOUND', 'TypeError', 'RangeError'].includes(error.code || error.name) ? error.code || error.name : 'UNAVAILABLE';
        output.appendLine('S++ language service unavailable. Check workspace trust, Java 17+, trusted language server JAR and source roots. Basic highlighting remains available.');
      } else stopEntry(entry);
    }
  }

  async function refresh() {
    stop();
    await Promise.all(vscode.workspace.textDocuments.map(document => ensure(document)));
  }

  const subscriptions = [
    vscode.workspace.onDidOpenTextDocument(document => { ensure(document).catch(() => undefined); }),
    vscode.workspace.onDidGrantWorkspaceTrust(() => { refresh().catch(() => undefined); }),
    vscode.workspace.onDidChangeWorkspaceFolders(() => { refresh().catch(() => undefined); }),
    vscode.workspace.onDidChangeConfiguration(event => {
      if (['spp.javaExecutable', 'spp.languageServerJar', 'spp.sourceRoots', 'spp.physicalProfile'].some(key => event.affectsConfiguration(key)))
        refresh().catch(() => undefined);
    })
  ];
  const controller = { refresh, stop, status: () => [...entries.values()].map(entry => ({ workspace: entry.workspace,
    state: entry.state, stage: entry.stage, failure: entry.failure })), dispose() {
    disposed = true;
    stop();
    for (const subscription of subscriptions) subscription.dispose();
  } };
  context.subscriptions.push(controller);
  refresh().catch(() => undefined);
  return controller;
}

module.exports = { createLanguageController };
