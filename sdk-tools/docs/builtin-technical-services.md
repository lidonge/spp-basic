# Built-in technical services and paid-edition extensions

This manual describes the official implementations in the current
`1.0-SNAPSHOT` source tree. It covers configuration and integration contracts;
it does not distribute their implementation source or change their licenses.
Use it alongside the [SDK user guide](user-guide.md).

S++ keeps business behavior in SPP. APP selects technical policies and PHY binds
concrete implementations. The open basic SDK exposes extension interfaces and
retains basic optimistic concurrency. The official implementations below are
separate paid-edition components; customers may supply their own implementations
through the public interfaces.

## Capability map

| Facility | Official component | Activation/control | Basic edition |
| --- | --- | --- | --- |
| Remote update request idempotency | `appframe-enhanced` | `idempotency` plus eligible remote context | Public request-coordination interface |
| User duplicate submission protection | `appframe-enhanced` | `duplicateGuard` plus APP scenario `duplicate` declaration | Custom provider possible |
| Invocation logging | `appframe-enhanced` | Explicit APP/PHY interceptor binding | Custom `TechnicalService` possible |
| Property change tracking | `generator-enhanced` + `appframe-enhanced` | `propertyTracking`; regenerate models | Public model-extension contracts |
| Partial-update field selection | `appframe-enhanced` | `partialUpdate`; update `before` interceptor | Full-field updates; custom selection possible |
| Native MyBatis writes | `appframe-mybatis` | PHY `nativeUpdateExecutor` | Basic JDBC native writer |
| Automatic update scheduling | `appframe-enhanced` | APP `ordinaryUpdates: PARALLEL` | Serial fallback; explicit SPP parallel remains available |
| Seata transaction integration | `appframe-seata` | Deployment/resource configuration | Public transaction interfaces and local runtime |
| Basic optimistic conflict protection | Public `appframe` | `optimisticConcurrency`, enabled by default | Included; independent of paid optimizations |

“Built-in” means supplied by the selected official distribution. A policy flag
does not download, license or instantiate an implementation that is absent from
the classpath. A paid subscription alone does not configure a database or Seata
server. Check the actual generated dependency list and runtime assembly.

## Installation and extension points

Commercial Maven coordinates use group `com.servpp` and the matching SDK version:
`appframe-enhanced`, `generator-enhanced`, `appframe-mybatis` and
`appframe-seata`. Obtain them through the authorized commercial distribution or
build them in the private commercial workspace. They are not modules of the
public basic source export.

The member CLI assembles its enhanced generator and records the selected runtime
extensions in the generated POM and `spp-generation.json`. For a custom Maven
pipeline, add the required runtime dependencies to the application, for example:

```xml
<dependency>
  <groupId>com.servpp</groupId>
  <artifactId>appframe-enhanced</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

Tracking generation additionally requires the extension in the **generator
plugin's** dependencies. Merge this fragment into the generator plugin already
configured by your project; it does not replace its execution or output settings:

```xml
<plugin>
  <groupId>com.servpp</groupId>
  <artifactId>generator</artifactId>
  <version>1.0-SNAPSHOT</version>
  <dependencies>
    <dependency>
      <groupId>com.servpp</groupId>
      <artifactId>generator-enhanced</artifactId>
      <version>1.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
</plugin>
```

Regenerate the models and package the matching runtime. Merely adding
`generator-enhanced` to an application's ordinary dependencies does not install
it in Maven's plugin classloader.

Not every facility is a method interceptor. Logging and field selection use
`TechnicalService`; request coordination, model generation, update scheduling,
transaction adapters and native writes use their corresponding public contracts.
ServiceLoader providers must be registered under their interface names in
`META-INF/services`. Avoid installing competing providers for the same extension
point; provider discovery is not a priority-selection mechanism.

To substitute your implementation, disable the relevant official policy or
remove the optional official binding/component, then configure your provider.
Preserve its public protocol, including failure handling and transaction
ownership. The basic SDK's
[custom-service example](../samples/custom-services/README.md) demonstrates a
complete explicit APP/PHY binding without any official enhanced dependency.

## APP system-service switches

Place this block at domain level in APP:

```app
domain ledger;

system services {
    idempotency: true;
    duplicateGuard: true;
    propertyTracking: true;
    partialUpdate: true;
    optimisticConcurrency: true;
}
```

All five switches default to `true`, independently. Their effect still depends
on the implementation installed and the type of call. Invocation logging is
explicitly bound; automatic scheduling has its own transaction policy. Neither
is an extra member of this five-switch block.

| Switch set to `false` | Effect |
| --- | --- |
| `idempotency` | Disables official request coordination for otherwise eligible remote service update calls |
| `duplicateGuard` | Disables official user scenario duplicate submission protection |
| `propertyTracking` | Stops generated model change tracking for the domain; regenerate models |
| `partialUpdate` | Uses complete ordinary-fact updates instead of official field selection, including explicit nulls |
| `optimisticConcurrency` | Disables automatic optimistic revision protection; this is a correctness choice, not an optimization switch |

Disabling tracking does not automatically disable partial updates: when a read
baseline exists, the official partial-update service can compare the untracked
model against that baseline. Disabling both tracking and partial updates still
leaves basic optimistic protection enabled unless explicitly disabled.
Existing explicit version conditions and quantum constraints retain their own
semantics. The legacy APP `propertyTracking: false;` spelling remains an alias;
do not also declare the same setting in `system services`.

## Remote request idempotency

Request idempotency identifies an invocation by trusted caller identity, a
stable request serial number and target service. A completed retry replays the
original output and result. Reusing the identity with different input is a
protocol error (`REQUEST_ID_REUSED`). It does not identify two different business
operations merely because their JSON happens to be equal.

| Invocation | Official request behavior |
| --- | --- |
| JVM-local call | No separate remote request registration |
| Remote SERVICE ordinary update with independent commit | Request coordination when enabled |
| Remote SERVICE update scenario | Entry request coordination; its transaction stages retain their own rules |
| Remote SERVICE query, calculate, check or read-only scenario | No update-request idempotency |
| Update carrying a managed XA/AT transaction context | Handed to the transaction carrier's branch/call protocol |
| Remote USER scenario with a `duplicate` declaration | Scenario duplicate guard, described below |

An independently committed remote update may use a local transaction containing
several SQL statements. “Independent” does not require JDBC `autoCommit=true`.
Omitting APP transaction mode does not imply autocommit or SEATA_AT.

The ingress must establish the trusted LOCAL/REMOTE and USER/SERVICE context.
Business JSON cannot promote itself to a trusted service call. Internal calls
run as LOCAL so that every nested method does not register the same request
again. `RemoteServiceIngress` accepts a caller identity established by the
transport/gateway, rather than treating a self-reported HTTP header as proof of
identity. Keep the original serial and original input for transport retries;
a new serial represents a new invocation.

The official provider atomically claims a request, executes or replays it, then
records its result using transaction evidence. A claim token prevents an old
executor from completing a later claim. A running or uncertain request must not
be re-executed just because its clock-based retention period elapsed. If business
commit succeeded but saving the response failed, the outcome needs recovery;
the failed response write does not prove business rollback.

### Retention and storage

APP can declare application request defaults:

```app
requests {
    idempotency retention 3 days;
    duplicateWindow 5 seconds;
}
```

These are the defaults when omitted. Retention is measured from a **determinate
completion**. Running, partially committed and recovery-required work cannot be
released solely by TTL. After completed-result retention expires, do not blindly
retry an old financial or inventory operation as a new request; inspect its
business receipt.

In the PHY global configuration, `requestIdempotencyAdapter` selects `"memory"`,
`"jdbc"` or a custom `RequestIdempotencyStore` implementation class. Place it directly inside the selected PHY block, outside any backend or
repository declaration:

```phy
phy {
    config requestIdempotencyAdapter = "jdbc";
}
```

Memory storage lasts only for the owning JVM. JDBC uses durable technical tables
and short transactions for claims/results; these do not replace the SPP business
transaction. Custom stores must preserve atomic claims, replay, ownership and
uncertain-outcome protection. Absence of a provider is not evidence that a basic
application is automatically request-idempotent.

## Scenario duplicate submission protection

This guard applies only to **REMOTE + USER scenario entries** that have an APP
`duplicate` declaration. It does not apply to ordinary atomic updates, and
service-to-service calls use their own request identity protocol.

The following fragment can be used with the ledger persistence example's
`reviseRecord` scenario. Merge it with that domain's local scope rather than
adding a second ambiguous default scope:

```app
scope {
    local {
        duplicate key(Entry.code)
            facts(Revision.note, Revision.amount) window 5 seconds reviseRecord;
    }
}
```

The key identifies the short-lived operation slot; `facts` fingerprints the
business inputs that must agree for a duplicate. Include every input relevant to
the declared operation. A changed fact under the same protected slot is a
conflict, not permission to replay a different result. The per-entry window
above overrides `duplicateWindow`; the window begins at completion. After the
window, a new operation must still satisfy the SPP business checks.

Web ingress uses a browser identifier to scope duplicate handling and
`X-SPP-Request-Serial` for the click's serial. A browser cookie is not an
authenticated customer identity. Authentication and business authorization
remain the host application's responsibilities.

## Invocation logging and interceptor lifecycle

Bind the supplied logger explicitly:

```app
technical services { invocationTrace; }
intercept services {
    before: invocationTrace;
    after: invocationTrace;
    failure: invocationTrace;
}
```

```phy
phy {
    technical service invocationTrace {
        implementation: "com.servpp.service.technical.InvocationTraceService";
        config logger = "spp.invocations";
    }
}
```

The logger uses `java.util.logging`. It emits the phase, service, kind, call mode,
origin, caller, request serial, elapsed microseconds and business result. The
`logger` configuration is optional and defaults to `spp.invocations`; unknown
configuration keys are rejected. It does not serialize participant contents,
but failures can include exception messages/stack traces. Configure access and
retention for the application's actual log output.

Remove this binding to turn it off, or bind your own implementation. No extra
SPP logging statements are needed.

| Phase | Meaning |
| --- | --- |
| `before` | Before the business method; supported technical policies may reject, select fields or supply a controlled replay result |
| `after` | Normal method return; inspect the business result as well as transaction ownership |
| `failure` | A propagated technical exception; not an automatic declaration that all work rolled back |

An outer synchronous scenario that owns a transaction returns after its own
successful commit. An inner service's `after` may run while the caller's
transaction is still open. A multi-stage SAGA also does not turn every `after`
into a global atomic commit. Local updates must not commit an enclosing
transaction from an interceptor.

Observation failures are diagnostic by default. Critical technical services can
propagate failures; the official partial-update selector is critical. Custom
services must be safe for their runtime lifetime and concurrent invocations.
Use the public transaction protocols for commit/recovery work rather than
inferring it from logging callbacks.

## Property tracking, partial updates and optimistic concurrency

These are three separate responsibilities:

1. **Tracking** records changes to generated models without adding business
   properties to SPP, OpenAPI or serialized participant JSON.
2. **Partial-update selection** determines the ordinary stored fields to write.
3. **Optimistic protection** checks the read revision before accepting a write.

Official tracked models record setters and generated collection helpers and use
read baselines to cover nested/collection mutations and explicit nulls. Framework
loading establishes a clean baseline; technical assignment is not a business
modification. Rebuild generated models when changing the tracking policy.

The official `PartialUpdateService` runs before eligible local atomic updates.
It takes existing change evidence, or compares an untracked participant with its
original read baseline, and selects field paths. It does not take its first
“before” snapshot after the business has already changed the object and pretend
that snapshot represents the original database state. Inserts or arguments
without a read baseline do not acquire fabricated delta evidence.

The repository then filters selections through APP mappings and ownership rules.
Keys, unrelated participants and inherited facts do not become writable merely
because a technical service selected their names. Native-table writes can update
selected columns; a JSON snapshot store still writes its merged serialized
snapshot. Partial selection does not promise physical column updates on every
storage backend.

The read revision is compared atomically at persistence time. Two requests that
read the same revision still conflict even when they modify different fields;
this is not automatic conflict merging. With partial updates disabled, complete
ordinary facts, including nulls, are written under the same optimistic check.

After a successful write, the transaction's working baseline can advance for
subsequent writes. Final tracking cleanup follows actual commit; rollback and
savepoint rollback restore the appropriate technical baseline. A normal
interceptor `after` is not sufficient evidence for cleanup.

### MyBatis native update adapter

With `appframe-mybatis` on the runtime classpath, add these entries to the
project's existing `jdbcObjectRepository` PHY repository:

```phy
config appDdlTables = "true";
config nativeUpdateExecutor = "com.servpp.service.repository.mybatis.MyBatisNativeUpdateExecutor";
```

The repository validates fields and table/key mappings before calling the
adapter. The adapter uses bound values and borrows the already enlisted
connection. It must not commit, roll back or close that outer connection, and
requires an active transaction rather than autocommit.

In this path, revision CAS occurs in the authoritative snapshot table, followed
by the native-table write on the same connection and transaction. A conflict
prevents the native update; a native write failure rolls back the snapshot CAS.
An empty field set does not produce an invalid empty `SET` clause. This guarantee
does not automatically apply to arbitrary handwritten Mapper XML elsewhere.

## Automatic update scheduling

Select scheduling separately from transaction mode, for example in an existing
application deployment:

```app
application basicLedger {
    environment develop {
        deploy ledger;
        transactions {
            mode: LOCAL;
            resource: ledgerResource;
            ordinaryUpdates: PARALLEL;
        }
    }
}
```

`ledgerResource` must already identify a compatible PHY backend. `SERIAL` is the
default. Without an `UpdateSchedulingProvider`, the basic runtime falls back to
serial automatic scheduling. Explicit SPP parallel constructs retain their own
semantics; the optional optimizer is not their license gate.

The official scheduler operates on prepared built-in ordinary updates. Quantum
work completes first within the applicable prepared segment. Updates touching
the same participant remain ordered. Queries, calculations, checks, branches and
independent boundaries prevent arbitrary reordering across business preparation.
All started tasks must finish before the owner commits or begins recovery.
Sharing one JDBC connection still serializes access to that connection; enabling
parallel scheduling is not a promise of parallel SQL on it.

Grouping a SAGA's local work into one transaction and scheduling its writes
concurrently are separate decisions. A serial group can still commit once.

## LOCAL, SAGA, AT and XA

SPP defines the source transaction boundary. APP/PHY choose how compatible
resources realize it. Explicit APP deployment modes include `LOCAL`, `SEATA_AT`
and `XA`. With no explicit override, resource topology selects LOCAL where one
local transaction is possible, otherwise SAGA. Do not assume an omitted setting
means SEATA_AT, and do not invent an explicit `mode: SAGA` configuration from the
runtime strategy's name.

| Strategy | Current integration responsibility |
| --- | --- |
| LOCAL | One local commit/rollback for work enlisted in the same transaction resource |
| SAGA | Coordinated independently committed stages and compensation/recovery evidence |
| SEATA_AT | Managed branch participation and recovery through Seata AT facilities |
| XA | Managed XA branch lifecycle and coordinator/resource-manager recovery |

The official Seata adapter integrates Apache Seata 2.5.0. Seata itself retains
Apache-2.0; the paid component is the S++ integration and associated distribution
and support, not exclusive ownership of Seata.

A prepared SAGA segment can group local A and local B sharing the same actual
resource even when a remote C appears between them, subject to preparation and
barrier rules. It is not an unconditional “every local call in the whole scenario
is one transaction” rule. Resource-object identity and compatible execution
context matter; matching URL strings alone do not establish a shared connection.
The native Seata SAGA stage coordinator currently executes stages sequentially;
it does not promise optimized parallel stage execution.

Managed AT/XA updates carrying a transaction context are routed through
`DomainTransactionCarrier`, including stable same-branch call receipts. Missing
managed-call support fails explicitly (`DOMAIN_MANAGED_CALL_PROTOCOL_REQUIRED`).
Setting an enum without establishing the actual managed context does not bypass
normal remote request protection. The transaction controller handles its branch
retries; SPP does not add a duplicate business ledger for those retries.

A usable AT/XA deployment also needs a compatible coordinator, datasource/resource
adapter, transaction context propagation, persistence schema and recovery
operations. Do not treat adding the JAR or selecting the APP mode as a completed
production installation. Current local tests cover H2 effects and protocol
integration; real TC deployment, production database proxies, XA prepare/recovery
and multi-node failure recovery need environment-specific acceptance.

## Operational acceptance checklist

Before relying on a selected facility, verify its observable outcome:

- Retry an eligible remote update with the same serial/input: one business effect
  and a replayed determinate result. Change input under that identity: reject it.
- Double-submit a declared user scenario: protect it within the completion window;
  do not apply the user guard to an ordinary atomic update.
- Disable tracking/partial selection: verify complete writes and continued
  optimistic conflicts, including explicit nulls.
- Fail a native write: verify both snapshot and native data roll back together.
- Interrupt after commit but before response persistence: retain uncertain work
  and recover from evidence rather than blindly rerunning it.
- For distributed modes, verify actual database and coordinator states across
  process/network failures, not just mock call counts or success logs.

Use the application's business receipt and runtime recovery evidence when the
transaction outcome is pending. In particular,
`SOURCE_SAGA_COMMIT_EVIDENCE_REQUIRED` means evidence is missing; it is not an
instruction to clear the receipt and resubmit the business operation.

The [validation note](tutorial-validation.md) distinguishes the examples executed
for this manual from capabilities checked against implementation and existing
tests. No production distributed deployment is certified by this documentation.
