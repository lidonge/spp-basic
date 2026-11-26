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
| `--strict` (basic CLI) | Treat conciseness warnings as compilation errors | Off |
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

The basic compiler reports redundancy on stderr with a source-relative path,
one-based line and column, a stable `SPP_REDUNDANT_*` code and a suggested S++
construct. The language server publishes the same warnings for open documents,
including unsaved changes. Use `spp-basic check --strict` to enforce them in CI
or add `--strict` to `generate` to reject a redundant source before publication.

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

For an SPP enum that declares an `other` member, the generated Java value
class exposes `kind()`. Use `switch (value.kind())` in Java 17 to branch on its
nested Java enum constants. The constants use the declared member names,
including `OTHER` when that is the name of the fallback member. An unrecognized
wire code has the fallback member's kind and retains its original code in
`getValue()` for serialization and storage. The nested enum is named `Kind`
(`$Kind` if the class or a member already uses `Kind`).

An APP play can apply this same constructor when reading a provider string into a
consumer enum field. See [APP cross-domain role bindings](#app-cross-domain-role-bindings).

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


## Decimal declarations and rounding

Use `BigDecimal(precision, scale, mode)` for Entity, Role and Contract fields,
quantum attributes and calculation locals. The supported modes are `DOWN`,
`UP`, `HALF_UP`, `HALF_DOWN`, `HALF_EVEN`, `CEILING` and `FLOOR`.
`DOWN` truncates toward zero, `UP` rounds away from zero, `CEILING` rounds
toward positive infinity and `FLOOR` rounds toward negative infinity.

Write division as `a / b`. Repeating decimal quotients use 34 significant
digits in intermediate calculations. Ordinary and compound assignments
apply the declared mode once at the target's scale. PHY can select the
representation, which must be able to store the rounded result exactly.

For a target with no declared mode, the compiler warns on division and when
a sum or product may exceed the declared scale. When rounding is needed,
assignment uses `DOWN` at the target's effective scale. An existing PHY
replacement of the precision or scale can still preserve an exact value
for a declaration without a mode. A decimal with no effective scale keeps
the calculated quotient. Explicit functions such as `roundDown(value, places)` and
`roundHalfUp(value, places)` remain available.

## SPP operators

The ordering operators `<`, `<=`, `>`, and `>=` accept two numbers,
two scalar Strings, or two members of the same enum. Implied spaces are
added to the right of the shorter String, so both `"AB" <= "AB "` and
`"AB" >= "AB "` are true. `==` and `!=` keep their existing equality
semantics, including the original String length and spaces.

An explicit `BLANK` represents spaces in ordering. When the other operand
is null, its APP blank read policy determines whether it represents
`BLANK`. A null String in ordinary ordering or a null excluded by the
blank read policy raises a runtime error.

Enum ordering follows the member declaration. If `LOW` precedes `HIGH`,
then `Priority.LOW < Priority.HIGH` is true for any wire codes declared for
those members. The compiler emits informational diagnostic
`SPP_ENUM_ORDER_COMPARE` at the operator. Mixed operands and different enum
types fail compilation.

## Legacy system interop

### Blank String fields

Use `BLANK` when business logic asks whether a String field is empty. String
equality compares the original value, including leading and trailing spaces. A scenario can also supply the canonical blank as a read key.

```spp
if (voucher.reference != BLANK) { voucher.previous = voucher.reference; }
voucher.reference = BLANK;
// In a scenario with Voucher as a participant and reference as its key
findVoucher by Voucher.reference = BLANK;
```

Without a declaration, `field == BLANK` accepts null and the empty string, and
`field = BLANK` writes the empty string. Declare the deployment default once in
APP or PHY. For example, a fixed-width deployment can accept three incoming
forms and always write one form.

```app
blank { read: null, "", spaces; write: spaces; }
blank RECEIPTS { read: null; write: null; }
blank RECEIPTS.reference { read: null, ""; write: ""; }
```

A field declaration overrides a table declaration, which overrides the
deployment default. Use the APP table name and SPP field name. A participant
field such as `blank Voucher.reference` can also be configured directly,
including for wire messages and provider roles. An APP `play` field with no
consumer override uses the mapped provider field’s representation. An explicit
`find … by` constant supplies the canonical form for the key. A SQL primary key
cannot match a canonical null. The `spaces` read form matches
one or more ASCII spaces (U+0020). Tabs, mixed text and other whitespace keep
their original meaning. Assigning `BLANK` with `write: spaces` requires a
declared width such as `String(30)` and writes exactly that many spaces.

### APP collation

Place the collation declaration alongside the APP blank policy at module
level. For example, use IBM-037 ordering for legacy codes in the scenarios
and services deployed by the application.

```app
collation ebcdic;
```

The default, `collation binary;`, compares Unicode code points. With
`ebcdic`, each character is ordered by its unsigned IBM-037 byte value.
Characters outside IBM-037 follow all mapped characters, in Unicode
code-point order. Both sequences use the same right-padding rule. For
example, `"1" < "A"` is true with `binary` and false with `ebcdic`.
Deployments sharing generated domains must agree on any explicitly
declared collation.

### Fixed-width forms

An APP binding lets a legacy endpoint exchange a fixed-width record with a
generated participant. The scenario continues to take the same business role
when the endpoint retires. The custom-services sample declares this binding in
[reserves.app](../samples/custom-services/src/main/spp/reserves.app).

```app
formHeader SlipHeader {
    length width 2;
    code width 8;
}
form ReserveSlip as "PZZZ7602" length 63 {
    header SlipHeader;
    referenceCode at 11 width 12;
    amount width 17 decimal(15,2);
    postingDate width 8 date;
    entries occurs 2 { label width 5; units width 3 decimal(2,0); }
}
```

Call `ReserveSlipForm.write(slip)` to send bytes or `ReserveSlipForm.read(bytes)`
to construct the role. Bindings live in the domain’s generated `forms` package.
Use `form BindingName for Participant as ...` to name a second representation.
The header is optional. Within a shared header, `length` occupies two unsigned
big-endian bytes and `code` contains the declared form code. Readers verify
both when present. Ordinary header fields bind to participant properties.

Widths and one-based positions count bytes. Each omitted position follows
the highest occupied byte in its group. Gaps contain ASCII spaces. The last
occupied byte must equal the declared length. Text uses strict UTF-8 with
right-side ASCII space padding. Reads remove trailing padding. A field made
entirely of padding decodes to the empty string by default. A BLANK policy
accepting `spaces` decodes it to the canonical write value. Null and empty
text encode as padding. Fixed-width text cannot distinguish trailing data
spaces from padding.

Decimals use right-aligned signed text with a decimal point when scale is
positive. Width must accommodate the sign and all digits. Conversion rejects
overflow and values requiring rounding. A date maps an ISO calendar date
string (`2024-02-29`) to eight digits (`20240229`). A group’s positions are
relative to its start. Use `labels occurs 3 width 8;` for a scalar array or a
braced group for an array of participants. Both directions use the exact
declared count.

## Technical values: serial numbers, context identifiers and interceptors

A transaction serial number, a log number or a generated reference is a technical
value, not business. SPP never allocates or computes it. APP declares what the value
is and which field receives it; PHY binds the technical service that produces it.
Voucher or document numbers that carry business meaning stay in SPP.

### Serial rules

```app
technical services { movementSerialEngine; }

serial movementSerial using movementSerialEngine {
    scope operatorBranch, businessDate;   // one counter per scope value
    range 1..9999 wrap;                   // without wrap, the end is a SERIAL_EXHAUSTED rejection
    format "{operatorBranch}{businessDate:yyyyMMdd}-{seq:4}";   // optional
    gapless;                              // optional
}

tables contracts {
    table MOVEMENTS(CashMovement) {
        primarykey(CashMovement.movementCode);
        auto CashMovement.serialNumber serial movementSerial;
        auto CashMovement.logNumber context transactionLogNumber;
    }
}
```

* `scope` lists fields of the entity. A counter exists per combination of values; a
  date or date-time value counts as its calendar date, so a business-date scope resets daily.
  An empty scope is one global counter. A scope value that is blank is rejected
  (`SERIAL_SCOPE_VALUE_REQUIRED`).
* `range a..b` (default `1..Long.MAX_VALUE`) and `wrap` are applied by S++ to the number
  the engine returns. `format` placeholders: `{seq}`, `{seq:N}` (zero padded), `{scopeField}` and
  `{dateScopeField:yyyyMMdd}`. A rule with a format fills a `String` field; without one it
  fills `short`, `int`, `long` or `BigInteger`.
* `auto Entity.field serial rule;` assigns the field on insert when it is still empty. SPP
  never mentions it and may read it afterwards. An SPP assignment to the field is a compile error.
* `gapless` allocates inside the business transaction, so a rollback leaves no gap. The rule's
  engine must implement `GaplessSerialEngine`, otherwise compilation fails. Without `gapless`
  an allocation is allowed to leave gaps.

### Context identifiers

`auto Entity.field context name;` fills the field from the trusted invocation context, never
from a business request. The names are `transactionLogNumber`, `auditLogNumber`,
`nonAccountingLogNumber` and `triggerSequence`. The ingress establishes them:

```java
RemoteServiceIngress.execute(service, request, callerId, requestSerial,
        Map.of("transactionLogNumber", "TL-0001", "triggerSequence", "42"));
```

They are available to every call under that ingress. A missing name is rejected with
`CONTEXT_IDENTIFIER_UNAVAILABLE`; the browser ingress supplies none. In tests, set them with
`InvocationContext.withIdentifiers(Map.of(...), () -> ...)`.

### The serial engine is a technical service

Which library or table produces the raw numbers is a PHY choice. The serial rule names its
engine, an ordinary technical service. Every implementation is bound the same way; there are no
engine-specific keywords:

```phy
phy {
    technical service movementSerialEngine {
        implementation: "com.servpp.technical.serial.CosIdSerialEngine";
        config mode = "segment"; config step = "100";
        config datasourceUrl = "jdbc:postgresql://db/app"; config driverClassName = "org.postgresql.Driver";
    }
}
```

| Implementation | Module | Gapless | Configuration (validated by the class) |
| --- | --- | --- | --- |
| `com.servpp.technical.serial.CosIdSerialEngine` | `appframe-serial-cosid` (optional; brings CosId 4.1.1, Apache-2.0) | no | `mode` `segment` or `snowflake`; segment: `step`, `namespace`, `initTable`, `datasourceUrl`, `driverClassName`, `username`, `password`; snowflake: `machine` |
| `com.servpp.service.serial.DbSequenceEngine` | `appframe` | no | `sequence`, `datasourceUrl`, `driverClassName`, `username`, `password`; one counter for all scopes |
| `com.servpp.service.serial.GaplessCounterEngine` | `appframe` | yes | optional `table`, `keyColumn`, `valueColumn` (default `SPP_SERIAL_COUNTER`, `COUNTER_KEY`, `COUNTER_VALUE`) |
| `com.servpp.service.serial.InMemorySerialEngine` | `appframe` | no | `store`; deterministic, for tests |

`GaplessCounterEngine` runs a locked `UPDATE ... SET v = v + 1` on the repository's business
transaction connection. It needs the counter table to exist:
`create table SPP_SERIAL_COUNTER (COUNTER_KEY varchar(200) primary key, COUNTER_VALUE bigint not null)`.
It can point at a legacy counter table; the stored value is the last number issued, and rows are keyed
by the counter key `rule/scope1/scope2` (`SerialValueService.counterKey`).
`CosIdSerialEngine` keeps one CosId segment generator per counter; a restart skips the unused rest of a
segment. Add the module to the application's dependencies only when you use it.

For deterministic tests and difftests bind `InMemorySerialEngine` and seed it from the same fixture as
the legacy counter: `InMemorySerialEngine.seed("store", SerialValueService.counterKey("movementSerial", "B01"), 41)`
makes the next number 42; `InMemorySerialEngine.reset()` clears every store.

### Plug in your own engine, or replace the whole serial

*Engine swap, keeping the S++ serial semantics.* Implement `SerialEngine` and bind it as above. The
engine returns the raw next number of one counter (starting at 1); S++ still applies range, wrap and format.

```java
public class MyEngine implements com.servpp.service.serial.SerialEngine {
    public void configure(java.util.Map<String, String> configuration) { /* validate your keys */ }
    public long next(com.servpp.service.serial.SerialRequest request) { /* request.counter(), request.scope() ... */ }
}
```

`SerialRequest` carries the rule name, the counter key, the scope values, the business date if the scope has
one, the range, whether the rule is `gapless`, and the business transaction connection.

*Full replacement.* Attach any technical service that implements `FieldValueInterceptor` to an entity insert:

```app
technical services { bankSerial; }
intercept CashMovement {
    insert: bankSerial -> serialNumber;
}
```

`produce(FieldValueContext)` receives the entity and field names, the target field's Java type, the record's
field values (`context.value("operatorBranch")`), the service's PHY configuration, the trusted invocation
context and the business transaction's connection (`context.connection()`). The returned value is converted to
the field type and stored when the field is still empty. The compiler rejects an unknown technical service, a field
SPP also assigns, a field of an unsupported type (use `String`, `int`, `long`, `BigInteger` or `BigDecimal`),
an implementation that does not implement `FieldValueInterceptor`, and a field with two producers.

`auto ... serial` and `auto ... context` are sugar over this mechanism: they attach the built-in services
`SerialValueService` and `ContextValueService` as insert interceptors, so built-in and custom services share one runtime
path. The built-in serial is a technical service you can replace. `update` interception is reserved and
rejected for now.

Limits: values are produced when an insert service runs, inside the business transaction of the repository
resource; a gapless allocation outside one fails with `SERIAL_GAPLESS_REQUIRES_TRANSACTION`. The compiler checks
the engine's contract only when its class is on the generator's classpath; otherwise the same check runs at startup of the
first allocation.

## Scenario read bindings and transaction rejection

Use `readonly Scenario serial preview(Entry, Facts)` for a front-end inquiry.
It generates a normal scenario API entry and may call queries, checks and
calculations. The compiler rejects insert, update, delete and quantum calls
and explicit transaction blocks, including inside guarded branches.

Use `findBook by Selection, Publication;` in a scenario when `Selection`
and `Publication` each take the target role `Book`. Each listed participant
supplies the APP primary-key fields covered by its take mapping. For example,
`take Selection as Book { String code = requestedCode; }` binds `Book.code`
from `Selection.requestedCode`.

Bind a literal or named constant through a performer field with
`Publication.edition = CURRENT_EDITION`, or bind the target key directly
with `Book.edition = 3`. Every key field needs exactly one source. The compiler
rejects missing or duplicate sources, constants outside the mapped key,
participants without a take relation, and incompatible types or declared
widths. APP key order has no effect on which value supplies a field.

A declared keyed query, including a list query, binds its scalar condition
participant in the same way. The generated scenario sets the key fields
immediately before the read. The target keeps the bound keys, and list
queries fill the destination collection.

Use `unique findBook by Selection else CatalogError.DUPLICATE;` when a lookup
may match several books and the scenario expects at most one. The target must
be a scalar participant. Supply one or more take-mapped participants or field
constants, for example `unique findBook by Book.title = REQUESTED_TITLE;`.
Zero matches leave the participant absent. Use `require(Book, CODE)` in a
check if the scenario needs a book. One match identifies the participant.
A second match rejects the scenario with the wire value of the enum member in
`else`, or with `PERSISTENCE_NOT_UNIQUE` when the clause is omitted.

A non-primary-key lookup uses mapped APP columns and bounds the JDBC result
to two rows. Only the first row is hydrated. When the predicates cover the
primary key, the compiler warns that `unique` is redundant and uses the
single-row identity lookup.

A failing plain check or `identify` ends its enclosing bare block. Only an
explicit `serial { }`/`parallel { }` block, a `transaction { }` block or a
failure handler is a bare block; the bodies of `if`/`else` and of a guarded
check are transparent. When the enclosing bare block is the `transaction { }`
itself, the failure rolls the whole transaction back, exactly as
`,breakTransaction` does. A check inside an `if` body that sits directly in the
transaction therefore also rolls it back. To leave only part of the logic, wrap
it in an extra `serial { }`: a failing check there exits only that block and
the transaction continues and commits. Outside a transaction a failing check
only ends its block.

Use `,breakTransaction` to roll back the whole transaction from any depth,
including from inside a nested bare block.

## APP cross-domain role bindings

Declare `play ConsumerRole by provider.ProviderType { consumerField = providerField; }`
in the APP for the consumer domain. The SPP role owns the business interface.
For a role with its own APP table, a binding of ordinary fields uses a local
SPP `Reference` to declare the provider facts. The consumer keeps its own
table and quantum storage. Petstore uses this form for Buyer and Merchant.

A scalar consumer enum with declared wire codes can bind to a provider
`String(n)`. For example, given `Enum ProviderOutcome { NOT_FOUND:"E1214","OLD", SUCCESS:"", UNRECOGNIZED:other }`,
map the fields directly in the consumer APP:

```spp
play ProviderResult by providerdomain.Admission {
    accountNumber = account;
    code = returnCode;
}
```

Reading `returnCode` constructs `ProviderOutcome(returnCode)`. Both `E1214` and
`OLD` resolve to `NOT_FOUND`. An unknown code such as `E9999` selects
`UNRECOGNIZED` and retains `E9999` for rejection, write-back and lookup.
Without an explicit `other` fallback, an unknown non-null code raises a runtime
error. Here a null provider code selects `UNRECOGNIZED`. A declared `null`
fallback takes precedence for null input. Writes and lookup
keys use the member's `getValue()`, so an alias writes the canonical code.

When another provider uses different codes, add a table to the affected field
in its APP binding. The SPP role and its business check stay the same.

```app
play ProviderResult by othercore.AdmissionResult {
    accountNumber = account;
    code = status { NOT_FOUND: "404"; SUCCESS: "OK", "200"; };
}
```

Both `OK` and `200` select `SUCCESS`. A write or provider lookup key uses
`OK`. A rejected `404` selects `NOT_FOUND`, whose business rejection code
is still `E1214`. An unmatched provider code selects the explicit `other`
member and keeps its original value, even if it matches a replaced SPP code.
Without `other`, an unmatched code raises a runtime error.

Each listed member replaces its entire alias list for this binding. An
omitted member keeps its declared SPP aliases, so a provider that changes
only success needs just `SUCCESS: "OK", "200";`. Each effective code must
belong to exactly one member, including inherited aliases.

You can also declare `Enum Outcome { NOT_FOUND, SUCCESS, UNRECOGNIZED:other }`.
When an ordinary member lacks declared codes, every play binding of that enum
field requires a table. An omitted ordinary member without aliases has no
provider representation. It cannot be read as that member, and writing it
raises an error. The `other` and `null` markers need no table entries.

For provider-owned quantum storage, each `play` maps all quantum fields and all parts of the
provider's primary key. The compiler uses the provider's APP primary-key order
and checks field types, quantum modifiers and numeric scale. Each mapped
quantum attribute uses the provider's storage, bounds, journal and automatic
reverse transaction. The played role has no quantum storage of its own.

Several `play` declarations for the same role are alternatives. For each
lookup, exactly one provider row must match. With zero matches, a role query
returns zero for the quantum fields and transfers fail with
`QUANTUM_PLAY_POSITION_NOT_FOUND`. Several matches fail with
`QUANTUM_PLAY_AMBIGUOUS`. Each provider's default primary-key query supplies
the role's read data. Switch the APP declaration to change providers between
deployments.

The consumer and providers must be deployed in one unified LOCAL JDBC store.
The compiler reports distributed deployments as
`distributed cross-domain quantum transfer not yet supported (APP TCC pending)`.
The `play ... by` spelling is provisional. Same-domain SPP `take` continues to
define local role composition.

### Invoking a local atomic service in a test

Install the SDK and add the appframe testing artifact to the test module.

```xml
<dependency>
    <groupId>com.servpp</groupId>
    <artifactId>appframe</artifactId>
    <version>1.0-SNAPSHOT</version>
    <classifier>testing</classifier>
    <scope>test</scope>
</dependency>
```

Install the generated deployment on the local runtime and pass the generated request
to the test entry. The call uses the registered service and its configured wrappers.

```java
import com.servpp.service.runtime.AtomicServiceTesting;

// In src/test/java
AtomicServiceTesting.invokeAtomic(runtime, "orders.calculateTotal", request);
```

Production bridges invoke a scenario through `runtime.executeGeneratedService`.
The scenario owns the business checks and transaction boundary. Use a scenario
test to verify those guarantees across multiple atomic calls.

## Batch review and repair

Run `spp-basic check --source src/main/spp --format json` for JSON Lines on standard output. Each line contains one diagnostic. A successful check with no diagnostics produces an empty stream. Compiler logs go to standard error. Errors appear first, followed by warnings ordered by code, path and position. Each object's `count` is the number of diagnostics with its code.

The fields `code`, `severity`, `file`, `line`, `column`, `message`, `cause`, `fixHint` and `relatedLocations` describe the finding. Paths are relative to the checked source directory. Source locations are one-based. Project-level errors use an empty file and zero positions. Each safe edit contains a path, inclusive start and exclusive end in UTF-16 offsets, and replacement text. Apply the edits for a diagnostic together.

The [diagnostic reference](../../lang/src/main/resources/diagnostics) contains the rule, review rationale and examples for each code. Run `spp-basic explain CODE` to read the same page offline. Run `spp-basic fix --source src/main/spp --code CODE` to apply a single rule or `--all-safe` to apply all eligible edits. The command compares the source with the checked snapshot and recompiles after applying a batch. A warning may require human review even when other occurrences of the same code carry edits.

Set Java system properties `spp.lint.maxParticipants` (8), `spp.lint.maxCalculateStatements` (40) and `spp.lint.maxNesting` (4) to positive integers to configure the complexity warnings.
