# Implementation path and evidence

> Transaction/storage rules changed on 2026-10-11. Earlier joint-commit, automatic actor persistence, and CustomerSubject descriptions below are historical. See the [implementation contract](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md). Under the AT configuration, quantum settlement precedes ordinary updates and cross-mode recovery uses Saga; REQUIRED does not change that. Person/Product are maintained explicitly by their owning domains. The develop LOCAL configuration described later is a separate common-resource path.

Requirements, business modeling, generic language/toolchain changes, source, and UI followed the [multidomain implementation path](multidomain-implementation.md). Domains: customer, product, inventory, petshop. Production business uses only SPP/APP/PHY; the three Java test files at that checkpoint prepare inputs and organize acceptance under src/test/java. Counts and commands below record successive historical checkpoints, not a fresh execution or a claim that every intermediate design remains current.

## Implementation results

1. Independent providers own Person/Product; consumers explicitly Reference them. Person plays Buyer/Merchant; take adds Role-specific facts, retaining complete Goods/Buyer actor mappings.
2. Cart, CartLine, OrderLine, and StockLine became Roles. Per-product quantum stock and business quantities default to int. StockBook aggregation and the fixed total-40 check were removed.
3. APP provides same-JVM deployment, domain-qualified local containers, and fact projection. Provider query instances, collection confirmation, platform identities, and version baselines remain stable. Inventory enriches/saves by identity and records quantum changes.
4. PHY declares common database/schema, resources, and transaction connections. One-step registration updates customer and sales domains without scenarios calling scenarios. Call-site bounds declare settlement limits, enforced by providers, without handwritten quantum-service range checks or APP business formulas.
5. Named SPP constants and APP/PHY localization synchronize the Chinese-language UI, default participant inputs, Java test inputs, and model comparison.

## Compilation/static evidence

Historical commands from the earlier reactor layout:

```bash
mvn -pl :petstore-demo -am test-compile -DskipTests
node --check demos/petstore/src/main/resources/web/app.js
git diff --check
```

After isolating old generated sources/classes/test-classes, generation/compilation ended BUILD SUCCESS: 773 main Java files and three test sources. Log: `/tmp/spp-automatic-transaction-reads-final-build.log`; artifact backup: `/tmp/spp-automatic-reads-fresh-1qu35z1c`. The manifest contained four domains, removed old StockBook types, constrained exact integer deserialization, generated/passed call-site bounds, and emitted actual provider inventory deltas/per-product records.

No tests, servers, or browsers ran; existing databases were neither cleaned nor modified. Compilation proved types/call relationships only. Older runtime results belong to [historical acceptance](acceptance-audit.md). Transaction-read/bounds additions were not yet committed at this checkpoint.

## Subsequent runtime acceptance scope

Once regression resumed, planned checks covered lifecycles, collection identities, dynamic/negative/null bounds and per-element violations, cross-domain joint undo, concurrency, duplicate protection, recovery, and browsers. Old databases had no automatic upgrade; use isolated databases and plan migration separately.

This checkpoint did not demonstrate complex multi-Entity composition, remote calls, cross-store strong consistency, Saga/TCC deployment, or production authorization. REQUIRES_NEW/NESTED were generic syntax; payment demonstrated nested REQUIRED and other scenarios used default REQUIRED. Shared validation-group reuse syntax remained undecided.

Ordinary-participant queries inside transactions generated locks. Mixed participants were locked when ordinary facts changed, not for quantum-only changes. Inventory findStockLine did not lock quantum facts; lockStock was removed and addCartItem qualification queries moved outside the write transaction. Quantum properties belong logically to a quantum domain, with database/TCC/dedicated implementation selected by APP and connected by PHY. Shared JDBC evidence is not evidence for every mode. See [transaction reads and quantum domains](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

## Provider-side quantum bounds

Call sites generate metadata only; providers pass bounds to the selected quantum implementation. JDBC uses conditional UPDATE for existing quantum values and conditional INSERT SELECT for initial values. Unsatisfied bounds reject the operation and undo the bound transaction. In-memory journals, TCC, and remote protocol versions without dynamic-bound support explicitly reject constrained calls; post-call queries cannot replace settlement-time enforcement.

The bounds addition regenerated/compiled with `mvn -pl :petstore-demo -am test-compile -DskipTests`: 773 main Java and three test sources. Log: `/tmp/spp-provider-quantum-bounds-final-build.log`. Static checks found getQuantumBounds metadata, no post-call settlement-value query, JDBC upper/lower conditions, and cross-domain provider projection of bounds. No tests/database operations ran; actual SQL, concurrency, and joint rollback remained pending.

addCartItem's pretransaction buyer query and product chain became parallel branches, with the product chain internally serial. Validation remained in the main block so failure cannot enter the write transaction. Generation/compilation passed with 773 main Java and three test sources; log: `/tmp/spp-add-cart-parallel-build.log`. Static checks confirmed branch ordering, validation, and transaction placement; concurrent execution was not yet tested.

All 30 scenarios—22 mutating, eight read-only—then narrowed transactions around reliable optimistic baselines: ordinary queries/checks/calculations before transactions, atomic conditional writes inside. Read-only eligibility is not a commit condition; quantum settlement does not advance ordinary revision. Reservation demonstrates two sequential transactions, preserving its accepted Contract if reservation fails. Payment demonstrates nested REQUIRED. Non-LOCAL quantum stages settle first and cross-mode failures recover via Saga; develop LOCAL jointly commits quantum/ordinary updates. See [transaction audit](transaction-boundary-audit.md) and [evolution plan](transaction-evolution-plan.md).

The earlier conservative-boundary compile log is `/tmp/spp-minimal-scenario-boundaries-build.log`. Default atomic optimistic updates, cross-domain baselines, and mixed quantum facts replaced the old “reprotect order” design. Final evolution compilation: `/tmp/spp-transaction-evolution-final-build.log`, BUILD SUCCESS, 837 main Java and three test sources; 31 test methods were not executed. Static review passed for all 30 boundaries, sequential/nested relationships, and default protection. No database migration, UI launch, tests, commit, or push occurred in that phase.

## Remote idempotency versus UI duplicate protection

APP requests retained global `3 days` retention and `5 seconds` UI window. Petstore changed idempotency key to duplicate and removed the idempotency business list. Four-domain technical headers added callerId, requestSerialNumber, and callMode. Production scenarios remained generated; web startup/exposure remained. Generic support covered trusted remote entry, replaceable protocols, and expiry of memory/JDBC completed records; local execution did not cache. Java acceptance simulated remote calls and added UI duplicate checks. See [request protocol](https://github.com/lidonge/spp-examples/blob/main/book/references/app-call-idempotency-and-duplicate-guard.md).

Final generation/compilation: `/tmp/spp-call-protocol-final-build.log`, BUILD SUCCESS, 837 generated main Java and three Petstore test sources with 32 methods, plus new compiler/protocol test sources. Static evidence `/tmp/spp-call-protocol-static.txt` checked 30 entries and global retention/window coverage; JavaScript syntax and whitespace passed. No runtime regression/services/database changes. These coordination features require enhanced providers; current basic builds warn rather than promise replay.

### Default repositories and Role enrichment

Redundant standard-primary-key repository, ordinary persist, and cross-domain inventory bind configuration was removed. Standard creation became atomic insert; modification remained atomic update, with atomic delete recognized. Role queries automatically enrich external Entities, removing describeBuyer/describeGoods/describeCatalogue and repeated scenario calls. Explicit repository demonstrates non-key filtering; explicit bind demonstrates custom cross-domain projection. APP SHOP_MERCHANTS demonstrates an added technical key.

Static generated evidence: SimpleFindBuyer infers membershipNumber and invokes customer.findSubject; SimpleFindGoods infers sku and invokes product.findCatalogProduct without projecting selling price/listing. SimpleInsertProducts infers INSERT/sku. SimpleInsertMerchant infers INSERT/unique Person actor identity using APP merchantRowId; this column does not enter Merchant's business model. Old describe artifacts disappeared after clean. Verification was generation/test compilation only, with no database operations or runtime acceptance.

### Queries from identity and actor relationships

Six custom cross-domain binds became target-only declarations. Goods explicitly plays product CatalogProduct; Buyer/Merchant explicitly play customer CustomerSubject. Strict parameter-identity, collection-dimension, and take checks replaced field-shape inference. Compiler test sources were added but not executed. Local filtering uses repository directly, not another local query-service binding.

Default Role queries recursively follow actor relationships to root Entities. Customer/product domains generate root-key queries and declare root tables in APP. The framework synchronously saves local table-backed Entities; external Entity updates still require explicit cross-domain services. Missing keys or unmatched primary-key queries fail rather than falling back to field filters. A Role view without its own table can enrich directly through actor relationships.

Traditional code receives results, builds DTOs, copies fields, and invokes external services manually. SPP places keys/conditions in APP, parameter mappings in take, and implementation in generation, preserving participant/entry instances. It requires explicit mappings and a unique key provider. No complete MyBatis SQL/XML dialect was added, complex SQL was not claimed, and old root-Entity data was not migrated.

Final role-query compilation: `mvn -pl :petstore-demo -am test-compile -DskipTests`, `/tmp/spp-role-query-final-build.log`, BUILD SUCCESS; 848 main Java, three Petstore test sources, and new compiler tests compiled. Static recursive queries, catalog actor adaptation, collection keys, and local-root persistence passed. No runtime tests/services/database operations or commit/push.

## 2026-10-11 transaction and Role storage

APP selects AT for ordinary services. Source boundaries generate Saga stages: quantum first, ordinary AT afterward. Central-store inversion applies negative original settled deltas to current values and backfills participants. Customer/product services explicitly maintain Person/Product; Role saves do not cascade. CartLine uses Buyer+Goods joint identity; OrderLine has a business receipt identity. Parent collections store identities and query services enrich them.

The first version allowed new-plan quantum settlement/independent nesting only with centralized localJdbc. Homogeneous AT outer REQUIRES_NEW was supported; NESTED and unsupported combinations were compile errors. External quantum/TCC plan protocols remained future work; compensation remained a separate discussion. Test sources were added, but only generation/compilation/static checks ran. Old snapshots required detail migration or fresh demo stores; existing databases were untouched.

## Standard CRUD simplification

Standard key queries/single-table changes no longer require empty SPP atomic declarations. Scenarios call `find/insert/update/delete + type name`, retaining the name for collections. APP can configure transaction mode, numbering, or custom bindings for these built-ins. The compiler resolves participant signatures and emits generic bridges; complex queries retain explicit declarations. See [implicit participant CRUD](https://github.com/lidonge/spp-examples/blob/main/book/references/implicit-participant-crud.md).

### Readable configuration, relation queries, table numbering

Request retention accepts units such as `3 days`/`5 seconds`, retaining old ISO-string compatibility. Order listing became `atomic query listOrders(key Buyer, ShopOrder[]);`, deriving queries from APP associations; web/tests now supply Buyer. Five business identifiers moved to owning-table `auto property prefix "prefix";` declarations instead of repeated service-argument positions. Participants retain business meaning; storage/numbering stay in APP/generation. Inference requires a unique association; complex/ambiguous queries need explicit APP implementations.

`mvn -pl :petstore-demo -am clean test-compile -DskipTests` passed; log: `/tmp/spp-table-auto-key-query-final-compile.log`. Static checks covered all five numbering declarations and Buyer→order queries; JavaScript syntax/whitespace passed. No regressions, servers, or databases were exercised.

## Environments, numbers, and source presentation

- APP selects deployed domains by environment; develop uses readable LOCAL policy. PHY parameters vary by environment, with resource identities rechecked after runtime parameter resolution. LOCAL jointly commits quantum/ordinary updates and rejects unsupported distributed configurations.
- Quantum/service-modified properties are collected automatically rather than repeated in APP. XA selects an actual Seata XA datasource proxy, never an AT proxy labeled XA. Real coordinators/databases require separate acceptance.
- Model numeric types are defaults; PHY demonstrates global exact-spec replacement and field overrides. Business rounding is explicit; capacity does not redefine mathematics.
- Text resources split by language and PHY references their directory; business constant names stay stable.
- Default participant names and argument-free calls apply consistently. Goods and Goods[] match by complete type; only duplicate complete types require aliases. SPP retains Goods[] references; Java alone uses legal internal identifiers.
- Source comments were removed from SPP/APP/PHY/enums; explanation lives in separate documents. This phase performed only generation/compilation/static review.

Compared with traditional code, APP/PHY centralize deployment/representation without repeated DTO aliases, quantum lists, or localization loaders. The compiler/runtime must validate resource/protocol capability; parseable configuration is not runnable distributed evidence.

### Multiprocess integration checkpoint

Testing added customerService, productService, and petstore applications, PHY domain routes, and an internal atomic-service protocol. Participants enrich in place; technical revision evidence crosses processes. Consumers compare environment, coordination group, quantum identity, atomic signatures, then provider readiness. LOCAL remains single-process joint commit. Testing co-locates quantum execution domains for the default unified quantum store. Unsupported remote quantum recovery or transaction-locked queries are rejected, never silently downgraded.

Startup supports SPP_ENVIRONMENT, application name, isolated ports, and shared internal credentials. Both environments generated/compiled main/tests, but no JUnit, UI, database, or external coordinator ran. Runtime protocol acceptance awaited permission to resume regressions.

Environment/physical defaults were implemented; see the [requirements/evidence document](https://github.com/lidonge/spp-examples/blob/main/book/references/deployment-environments-and-physical-defaults.md). Develop uses one JVM/common LOCAL commit; testing generates three applications, remote default-CRUD routes, and a unified quantum store. Numeric global/field overrides, language resources, and default participants were demonstrated. Real coordinator/network-fault acceptance remained outside that compile-only phase.

## Zero opening and conserved external transfers

Opening creates only a zero wallet. Inventory, third-party settlement, and supply sources start at zero. Funding/stock receipt are separate scenarios with conserved two-sided quantum changes: no lower bounds on sources, nonnegative recipients. UI entries are separate; tests compose generated scenarios for demo balances/stock without business Java. See [design and comparison](conserved-opening-and-transfers.md).

## Automatic ordinary-update concurrency

APP `ordinaryUpdates: SERIAL | PARALLEL` supports domain defaults/environment overrides. Generation preserves boundary scheduling policy, marks fully prepared ordinary updates eligible, and traces local cross-domain proxies' quantum identity/mode. After quantum stages, the executor can save concurrently; calculations/checks/queries/independent transactions are barriers. Shared JDBC commits jointly; failures join before rollback. Settled Saga nodes recover under existing rules. Automatic receipt identifiers are allocated before concurrency begins. See [design/comparison/acceptance scope](https://github.com/lidonge/spp-examples/blob/main/book/references/automatic-transaction-update-concurrency.md). Automatic parallel scheduling is optional enhanced behavior; basic fallback is serial without disabling ordinary revision checks.

## Business-oriented shop source layout

Goal: keep models, Contracts, checks, calculations, and scenarios for a feature together instead of mixing features.

petshop/ splits membership, goods, cart, orders, payment, fulfillment, returns, and reservations. Mixed files were split only at complete top-level declarations; shared definitions moved to shared/. Domain declarations/APP/PHY remain at the root. Recursive scanning preserves domain identity, deployment, and transaction boundaries. README/source-guide links were updated.

Acceptance compared all 173 top-level declarations before/after: identical content/count. All shop source links passed. `mvn -pl :petstore-demo -am compile -DskipTests -Dspp.environment=develop` recursively generated/compiled successfully; log: `/tmp/spp-petshop-functional-layout-build.log`. No regression ran and no formulas/eligibility/transaction definitions changed.

Unlike controller/service/repository layering, this layout concentrates one business operation's definitions. Shared definitions still need deliberate reuse management; directories do not imply service isolation.

## Scenario failure handling

Define built-in ResultInfo/FailureInfo and Contract association contracts first, then implement fail parsing, scenario-specific participant checks, and generated post-recovery control. Reservation acceptance → stock reservation → failure disposition demonstrates independent commits with SPP-only business. Java tests invoke generated services, inject technical faults, and assert outcomes. See [failure handling](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-failure-handling.md) and [reservation example](reservation-failure-handling.md).

## APP technical services and interception

[Requirements/syntax/path](https://github.com/lidonge/spp-examples/blob/main/book/references/app-technical-services.md) preceded APP target matching, PHY technical-class bindings, generated metadata, and unified runtime interception. Scenario types remain intact; both typed atomic calls and execute entries are covered. Petstore adds only APP/PHY configuration; generic logging lives in the framework. The [example](technical-services.md) covers queries, calculations, quantum updates, and scenarios. Tests capture logs and verify enhanced replay/fault propagation without implementing business in Java. Basic builds omit unavailable optional official tracing with a warning.
