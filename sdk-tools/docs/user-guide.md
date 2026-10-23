# S++ SDK user guide

This guide covers the current `1.0-SNAPSHOT` SDK source layout and the 0.1.2
extension configuration. It is the English user-facing entry point. Start with
the [tutorial](../../docs/tutorial/spp-tutorial.md) for a complete executable
example; use the [technical services manual](builtin-technical-services.md) for
paid-edition runtime facilities. Version numbers in commands identify this
checkout, not a claim that these artifacts are already published.

## Choose an edition

| Capability | Open basic SDK | Official member SDK |
| --- | --- | --- |
| Local language analysis and business generation | Included | Included |
| Account required for compiler use | No | Yes; signed authorization required |
| Commercial scenario/extension quota | None | Determined by the issued entitlement |
| Basic persistence and optimistic conflict protection | Included | Included |
| Public custom technical extension interfaces | Included | Included |
| Official enhanced implementations | Excluded | Explicit commercial dependencies |
| Language-server navigation and outline | Offline, no login | Offline, no login |

The Apache-2.0 grant covers the allowlisted basic source distribution, not this
entire private development workspace. It imposes no centralized-deployment-only
restriction. Generated business code does not become Apache-2.0 merely because
the compiler produced it; application authors must still respect the licenses of
runtime dependencies. Paid services have their own distribution terms. Third-party
libraries retain their upstream licenses.

## Install and build

Use Java 17+ and Maven 3.6.3+. Node.js 20+ is needed to build/test the VS Code
extension, not to run an installed extension. First-time Maven/npm builds need
their dependency registries. From the SDK source root:

```sh
mvn -Pbasic -pl :spp-basic-cli,:spp-language-server -am install -DskipTests
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar --help
```

The vendored template modules build in the reactor. A separate `antlrmustache`
checkout is unnecessary. `install` also prepares dependencies for generated
Maven projects built outside the reactor. If the default Maven cache is not
writable, pass `-Dmaven.repo.local=/absolute/writable/repository` consistently to
all Maven commands, including the generated project's build.

In an authorized commercial source workspace, the member build is:

```sh
mvn -Pcommercial -pl :spp-cli -am install -DskipTests
java -jar sdk-tools/cli/target/spp-cli-1.0-SNAPSHOT.jar --help
```

The public source package excludes that module. A member CLI built from an
unconfigured `sdk-release.json` rejects conversion with `AUTH_NOT_CONFIGURED`.
Building the source is not a substitute for a trusted release and entitlement.

For a downloaded binary, obtain the artifact and signed manifest through the
release channel and check the signature with an independently trusted release
key and verifier. A SHA-256 printed beside an untrusted download alone does not
authenticate its publisher. For example, a basic VSIX can be verified with:

```sh
node sdk-tools/release/verify-vsix.mjs \
  --artifact spp-tools-basic-0.1.2.vsix \
  --manifest manifest.json --signature manifest.sig \
  --public-key /trusted/release-public.pem \
  --expected-version 0.1.2 --expected-extension servpp.spp-tools-basic
```

Substitute the actual release's version and extension identity. A `local-test`
signature is development evidence only; do not treat it as an official release.
A ZIP is a distribution container: unpack it, verify its contents according to
the accompanying release instructions, and install the `.vsix` inside it. Do not
rename a ZIP to `.vsix` or assume every ZIP is a VS Code extension.

## CLI reference

Both CLIs accept these commands:

| Command | Purpose |
| --- | --- |
| `--help` | List commands and options |
| `--version` | Report the selected compiler's version/capabilities |
| `status` | Inspect the basic edition or member authorization status |
| `check` | Compile/check input without publishing a generated project |
| `generate` | Publish a new generated Java project directory |

The member CLI additionally supports `login`, `logout` and `unregister`.
The basic CLI does not need or implement an account workflow.

For `check` and `generate`:

| Option | Meaning | Default |
| --- | --- | --- |
| `--source DIR` | Source root containing S++ inputs | `src/main/spp` |
| `--output DIR` | New generated directory (`generate`) | Basic: `target/spp-basic`; member: `target/spp-sdk` |
| `--package NAME` | Java base package, such as `example.generated` | `com.servpp.generated` |
| `--environment NAME` | APP/PHY environment selection | `develop`, unless set by `spp.environment` |
| `--profile NAME` | Physical source profile selection | No explicit selection |
| `--timeout-seconds N` | Local execution deadline, 1–120 seconds | `120` |

An environment and a physical source profile are separate selectors. Make them
explicit in CI when the project has several deployment targets. The execution
deadline, source validation and path restrictions are resource protection, not a
basic-edition commercial quota.

```sh
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar check \
  --source sdk-tools/samples/tutorial/src/main/spp --environment develop
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar generate \
  --source sdk-tools/samples/tutorial/src/main/spp --output target/first-app \
  --package example.generated --environment develop --timeout-seconds 120
```

Generation refuses an existing output directory and source symlinks. It takes a
local input snapshot and publishes the completed output atomically. Check uses
temporary output and cleans it up. Source-directory scripts are not executed.
Compiler work remains local; optional member usage events do not upload source.

### Automation and diagnostics

Stdout is a single JSON object. Success has `ok: true` and a `result`; failure has
`ok: false` and an `error` with `code` and `message`. Read diagnostics from stderr.
Do not parse human-readable compiler logs as the result protocol.

```sh
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar check \
  --source sdk-tools/samples/tutorial/src/main/spp \
  > check-result.json 2> check-diagnostics.log
```

Both CLIs return zero on success and nonzero on failure. Their detailed exit-code
mappings differ: use the JSON error code for classification rather than assuming
a member authorization exit code also applies to the basic CLI.

### Member sign-in and credentials

```sh
java -jar spp-cli.jar login
java -jar spp-cli.jar status
java -jar spp-cli.jar generate --source src/main/spp --output target/member-app
java -jar spp-cli.jar logout
```

`login` displays a fixed confirmation page and a one-time code. Complete the
browser workflow for that installation. The CLI does not ask for your account
password. `logout` clears local CLI credentials and cached authorization;
`unregister` revokes the current installation online and clears its local state.
Plugin credentials and CLI credentials are separate stores.

On POSIX, CLI credentials use a private directory and files (0700/0600).
`SPP_HOME` changes their location, not the trusted authorization endpoint or key.
The extension uses VS Code SecretStorage and passes credentials through stdin.
For controlled automation the member CLI accepts `--token-stdin`; provide a JSON
object containing `accessToken` through a protected input stream and close that
stream. Do not put live tokens in arguments, repository files or shared logs.
A signed lease determines offline validity; network failure does not extend its
hard deadline. A source build lacking production trust material is expected to
refuse authorization.

## VS Code

Install a verified `.vsix` using **Extensions → … → Install from VSIX…**, or:

```sh
code --install-extension /path/to/spp-tools-basic-0.1.2.vsix
```

Open the project **folder**, trust it if appropriate, and ensure Java 17+ is
available on the extension host. With Remote SSH, install the extension and Java
on the remote host; `/home/...` paths are resolved there. A local-machine JAR path
does not identify a remote JAR.

Use `.vscode/settings.json` for project settings:

```json
{
  "spp.sourceRoots": ["src/main/spp"],
  "spp.basePackage": "example.generated",
  "spp.environment": "develop"
}
```

If opening this SDK repository instead, the tutorial root is
`["sdk-tools/samples/tutorial/src/main/spp"]`. Source roots must be a nonempty
list of directories inside the workspace (at most 16); avoid overlapping roots.
`[]` selects no valid source-root configuration. Include the related SPP, APP and
PHY files in the project analysis roots.

Machine-level settings belong in **User Settings**, or **Remote Settings** for
SSH workspaces:

| Setting | Use |
| --- | --- |
| `spp.javaExecutable` | Java executable; defaults to `java` |
| `spp.cliJar` | Trusted CLI override; empty selects the bundled JAR |
| `spp.languageServerJar` | Trusted language-server override; empty selects the bundled JAR |
| `spp.shareUsageEvents` | Optional member usage reporting; defaults to `false` |

Opening the workspace settings file with
`workbench.action.openWorkspaceSettingsFile` is a command, not a setting value.
Put the actual JSON settings in that file. Do not use it as the value of a source
root or JAR path.

Files ending in `.spp`, `.enum`, `.app`, `.phy` and `.rdb` use the S++ language
mode. Syntax support is available without Java or login; trusted-workspace
semantic features need the local language server. Use F12 / Go to Definition,
Go to Declaration, hover, the Outline view and workspace symbols. Editor
analysis is offline and does not require a member account.

Useful Command Palette entries include **S++: Language Service Status**,
**S++: Restart Language Server**, **S++: Check Project** and
**S++: Generate Java Artifacts**. Member packages also provide sign-in,
sign-out and authorization commands. Use Language Service Status and the S++
output channel before changing unrelated editor settings.

## Generated Java and application integration

Generation writes business implementations, models, API handlers, OpenAPI
files, runtime catalogs, SQL-related resources, a Maven POM and
`spp-generation.json`. Build that directory with `mvn verify` using matching
installed runtime artifacts. Preserve its generated `META-INF/spp` resources
when packaging a host application; they describe services, deployments and
physical bindings.

The generated Maven project is an integration library. It does not automatically
supply a configured HTTP server, credentials, database migrations, a reverse
proxy or a browser UI. A host must install the generated deployment and runtime,
supply its selected database/resource adapters, and expose the APP-declared
entry points. Dependencies on Spring APIs alone do not start a server.

For a concrete local Java host/test, follow the complete
[custom-services example](../samples/custom-services/README.md). Its
[`BasicPersistenceTest`](../samples/custom-services/src/test/java/example/technical/BasicPersistenceTest.java)
creates a data source, constructs `LocalGeneratedServiceRuntime`, installs
`GeneratedApplicationDeployment`, and calls generated scenarios. This is also a
reference for checking database effects rather than just method-call counts.

For Maven-based source generation, the same example's POM configures
`com.servpp:generator` and its `sppcompile` goal with `sppSourceDirectory`,
`yamlOutputDirectory`, `javaOutputDirectory` and `basePackage`, followed by
OpenAPI Java generation. Keep its numeric configuration, templates and runtime
resources together. The CLI automates this assembly for a standalone output.

Use the [technical services manual](builtin-technical-services.md#installation-and-extension-points)
when adding official runtime or generator extensions. Adding a runtime library
alone does not retroactively regenerate tracked models.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| `ENOENT ... server/spp-cli.jar` | Check the installed package and the extension host. Reinstall the complete verified VSIX, or select an existing trusted JAR in the correct machine settings. Clear obsolete overrides. |
| No outline or definition navigation | Check workspace trust, Java, S++ language mode and nonempty source roots; inspect Language Service Status, then restart the server. |
| Built-in `find`/`update` shown as undefined | Ensure the current language server indexes the participant and its APP declarations; check the first source diagnostic and installed version before inventing a handwritten replacement. |
| Output directory already exists | Select a new destination. The CLI deliberately does not overwrite it. |
| `AUTH_NOT_CONFIGURED` | The selected member build lacks production authorization configuration; use an official configured release, or the independent basic CLI for basic features. |
| Maven cannot resolve `com.servpp` artifacts | Install the matching source reactor, and use the same Maven repository for the generated build. |
| `PARTICIPANT_NOT_IDENTIFIED` | Inspect APP identity/query mapping and whether the participant was actually loaded or inserted. Do not reuse an unrelated participant's identity. |
| `PERSISTENCE_QUERY_IDENTITY_AMBIGUOUS` | Check that the declared lookup resolves one intended identity; list queries and multiple rows need appropriate bindings. |
| `PERSISTENCE_VERSION_CONFLICT` | Reload current facts and re-evaluate the operation before retrying. |
| Transaction outcome pending | Preserve the request identity and inspect its business receipt/recovery evidence. A timeout is not proof of rollback. |
| Data survives restart | Inspect the effective datasource URL and process: file databases persist; an in-memory H2 database lasts while its owning JVM remains alive. |

## Verification and release scope

The tutorial exercises the basic compile/generate/build/run path and checks the
business result. A normal method return or the generic runner's PASS line alone
is not evidence that a business check accepted the operation. The custom
service sample tests generated persistence and optimistic conflicts on H2.
Neither establishes support for every database, operating system or production
distributed deployment. Exact validation for this documentation revision is
recorded in [the validation note](tutorial-validation.md).

New public documentation, example explanations and release notes are written in
English first. Existing historical design notes may remain in their original
language; they are not substitutes for the current user-facing guides.
