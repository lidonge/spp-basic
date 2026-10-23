# Basic SDK source ownership — BASIC-DEPENDENCY-1

The public `spp-basic` repository is the authoritative source for the basic
compiler, generator, runtime, extension interfaces, local CLI, language server and
shared editor implementation. Fixes to those components belong here first.
The basic reactor and its Apache-2.0 license remain independent of commercial
implementations, accounts and downstream deployment systems.

Downstream integrations consume a fixed Git commit or versioned Maven artifacts.
They may add commercial implementations through the public extension interfaces;
they must not maintain a second copy of the basic implementation. Updating a
pinned revision is an explicit integration change with compatibility checks.
A push here does not silently change a downstream production build.

Build this repository independently with `mvn -Pbasic install`. The existing
`com.servpp` artifact identifiers and `1.0-SNAPSHOT` source version are unchanged.
A downstream private aggregator must use a different artifact identifier from
this repository's `com.servpp:spplang` parent POM.

`SOURCE-MANIFEST.json` retains the hashes of the initial reviewed source export.
It is historical provenance, not an inventory of subsequent Git commits. Git
commits identify maintained source revisions. Do not overwrite this repository
with later snapshots exported from a downstream private workspace.

The tutorial and SDK manuals are maintained under `docs/tutorial/` and
`sdk-tools/docs/`. Their validation note distinguishes upstream compiler/runtime
runs from downstream integration checks. Community scheduling and maintenance
containers remain a separate maintainer task; this change enables none.

## Migration verification

Implemented with Codex; exact model build identifier is unavailable. Baseline:
`51f7d0da04d733e28eabc8e355bf5cdff41db1b0`. The eight new tutorial/manual inputs
and updated public contribution documents were imported from the reviewed basic
allowlist at documentation revision `462b63b1`; no private history or commercial
implementation was imported. Existing third-party and book licensing is unchanged.

Actual local checks for BASIC-DEPENDENCY-1:

- `mvn -B -o -Dmaven.repo.local=/tmp/spp-sdk-m2 -Pbasic,basic-example clean install`:
  all 13 modules passed, 1,238 tests, zero failures/errors/skips. The existing
  dependency cache was used; this is not an empty-cache build claim.
- `node sdk-tools/vscode/tools/build-client.js` and
  `node --test sdk-tools/vscode/test/*.test.js`: 56 passed, two commercial-JAR
  packaging cases skipped because this independent tree contains no member CLI.
  Real basic CLI/language-server bundles and the external-manifest source-reuse
  case were exercised. No production VSIX or signed release was published.
- `git diff --check` passed.

Downstream platform and commercial integration validation is recorded in the
private integration workspace; it does not introduce a reverse build dependency
or certify distributed recovery, production accounts or hosted CI execution.

The shared VSIX verifier also exposes its CLI entry as a callable function with
an explicit downstream default-public-key path. This preserves existing trusted
key locations when only the implementation moves. Default keys still require
local-test opt-in and cannot authenticate production releases. Three independent
verifier tests passed, including these trust-boundary checks; no private signing
key or production trust material is added.

Non-basic editor assemblies now carry the shared editor's original Apache-2.0
license and notice under `licenses/`, with a component-scope explanation. A
member descriptor's own terms do not replace those source-component notices.
The original basic package layout and its commercial-component rejection checks
are preserved. The editor suite was rerun after this packaging change: 56 passed,
two member-JAR-specific skips, zero failures.
