# S++ basic SDK

## Start here

- [S++ tutorial](docs/tutorial/spp-tutorial.md): check, generate, build and run your first scenario.
- [SDK user guide](sdk-tools/docs/user-guide.md): editions, installation, CLI, VS Code and integration.
- [Built-in technical services](sdk-tools/docs/builtin-technical-services.md): paid-edition configuration, replacement and transaction boundaries.

Public documentation and release notes use English as their primary language.
The technical-services manual describes optional components; it does not include
or relicense their implementation source.

S++ separates business behavior (SPP), application realization (APP) and physical
deployment (PHY). This source package contains the language tools, business
generator, basic JDBC runtime, offline command line, local language server and
VS Code extension source. Its own code is licensed under Apache-2.0; see
[third-party notices](THIRD-PARTY.md) for the exceptions and external dependencies.

Java 17 and Maven 3.6.3 or later are required. Maven downloads external dependencies
from public repositories. The included `vendor/antlrmustache` modules build the
template engine from source; a sibling checkout or preinstalled S++ JAR is not needed.

```sh
mvn -Pbasic,basic-example clean package
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar --version
```

Before building generated Maven applications outside this source reactor, install
the basic compiler/runtime artifacts into your local Maven repository:

```sh
mvn -Pbasic -DskipTests install
```

The basic compiler requires no account and imposes no scenario quota or
deployment-topology license limit. Generated business code does not acquire a
license merely because this compiler produced it. Application authors choose
their own distribution terms and dependencies.

The [custom services example](sdk-tools/samples/custom-services/README.md)
demonstrates generated persistence, complete-field updates, optimistic version
conflict protection and an explicitly configured technical service. Official
request coordination, property tracking, partial-update selection, automatic
update scheduling and Seata adapters are supplied separately. Public interfaces
allow other implementations. Basic concurrency protection remains active without
those optional implementations.

The versioned [extension requirements](EXTENSION-CONTRACTS.md) describe the public
contracts for custom request handling, update field selection, model tracking,
transaction stages and scheduling, with links to interfaces and acceptance cases.

For local editing, build the language-server and basic CLI JARs first, then:

```sh
cd sdk-tools/vscode
npm ci --ignore-scripts
npm run build:client
node tools/package-vsix.js --basic --vsce /path/to/@vscode/vsce/vsce \
  --cli-jar ../basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar \
  --language-server-jar ../language-server/target/spp-language-server-1.0-SNAPSHOT.jar
```

This source tree contains no Git history or commercial implementation. A
`PUBLICATION_BLOCKED.md` file, when present, identifies a review snapshot whose
release prerequisites are still incomplete. Do not present such a snapshot as a
complete public SDK distribution.

Signed basic VSIX releases can be checked from an independently trusted copy of
the verifier. Select the basic extension identity and expected version explicitly:

```sh
node sdk-tools/release/verify-vsix.mjs --artifact spp-tools-basic-0.1.2.vsix \
  --manifest manifest.json --signature manifest.sig --public-key /trusted/release-public.pem \
  --expected-version 0.1.2 --expected-extension servpp.spp-tools-basic
```

An ephemeral `local-test` signature requires `--allow-local-test` and establishes
only the local test chain. It does not turn a review build into an official release.

## Source ownership and downstream integrations

See [repository responsibilities](docs/repository-role.md). Basic fixes are
maintained here; downstream commercial workspaces consume a pinned revision.
