# S++ Tools (basic)

## Current account sign-in (0.1.3)

A trusted internally configured preview with `accountProtocol: 2` supports device-code
sign-in, account status, credential refresh and revocation for the current free evaluation
membership. Sign-in no longer requires an old compiler lease. Membership has no expiry;
short-lived login credentials do not limit the runtime of generated programs.
Official enhanced generation is still unavailable and is rejected by the platform and CLI.
The open-source basic compiler and all free editing features remain independent of sign-in.
Unconfigured development builds still fail closed with `AUTH_NOT_CONFIGURED`.
The legacy compiler-lease workflows described below remain for compatibility tests only;
upgrading the extension alone does not configure its bundled SDK for a production service.

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

Downstream packages can reuse these sources and supply their own extension
metadata with `tools/package-vsix.js --manifest /path/to/package.json`. The
compiler requirements and basic-edition component checks still apply.
