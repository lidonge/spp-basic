# S++ Tools (basic)

Local S++ syntax highlighting, diagnostics, formatting, completion, outline and
definition navigation, plus an offline compiler with no account or scenario quota.
The extension runs on the workspace host and needs Java 17 or newer. Remote SSH
workspaces therefore need Java and the bundled JARs on the remote host.

Install the basic `.vsix` using **Extensions: Install from VSIX**. Open the
project folder, trust the workspace and set `spp.sourceRoots` if sources are not
under `src/main/spp`. **S++: Language Service Status** shows the active server;
**S++: Restart Language Server** restarts it after configuration changes.

The package bundles its matching basic CLI and language server under `server/`.
An empty `spp.cliJar` or `spp.languageServerJar` setting selects that bundled
artifact. Use **S++: Check Project**, **S++: Generate Java Artifacts** and
**S++: Compiler Edition** from the command palette.

The basic runtime includes complete-field persistence and optimistic concurrency
checks. Custom technical services can be explicitly bound through APP/PHY.
Official enhanced implementations are separate components and are not included
in this package. The extension's Apache-2.0 license does not change licenses for
user business sources or third-party dependencies.

Download artifacts through a trusted release channel and verify release
signatures against an independently trusted public key. A locally calculated
bundle hash identifies content; it does not establish who published it.
