# Multidomain Petstore requirements and implementation path

> Historical plan from revision `417a41f`. The 2026-10-11 transaction/storage
> update superseded earlier joint-commit, automatic player-save and CustomerSubject
> descriptions below. See the [implementation contract](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> That update selected AT ordinary updates, quantum settlement first and Saga
> recovery between modes; REQUIRED does not change that. Person/Product are
> explicitly maintained in their owning domains. Consult later LOCAL deployment
> rules and actual environment configuration before interpreting these guarantees.

This phase implemented business models and real multidomain deployment
declarations: SPP business, APP deployment, PHY numeric representation/resource
guarantees, all production Java generated. The phase's instruction was generation,
compilation and static review only, preserving the old database, followed by a
local commit after the historical goal completed. That is a historical workflow,
not permission to commit unrelated current changes.

## Requirements and artifacts

| Requirement | Implementation |
| --- | --- |
| Independent subject/product domains | customer owns Person; product owns Product; consumer References name their source |
| Roles reflect business identity | Person → Buyer/Merchant; Product → Goods; Buyer → Cart; Goods → StockLine/CartLine/OrderLine |
| Role-specific facts | take adds Merchant.shopName/tradingName/cash; whole-participant mappings retain Goods/Buyer relationships |
| Integer stock per product | quantum int available/reserved/sold; business quantities default to int; StockBook and aggregate state removed |
| Explicit scenarios/contracts | Actual input participants, hydration in place, no returned object or scenario calls; default names and explicit aliases coexist |
| Real centralized multidomain deployment | APP deploys customer/product/inventory/petshop; generic local container dispatches atomic services by domain |
| Joint transactions in the original design | PHY same backend/schema/resource/connection; explicit SPP transaction defaults REQUIRED, subject to later resource-rule updates |
| Caller quantum policy | Inclusive bounds with nullable limits; settlement enforces bounds and rolls back the bound transaction |
| Constants/localization | Named SPP constants, APP language, PHY translations; English and Chinese product text |
| UI/automated acceptance sources | Frontend composes independent scenarios; Java tests prepare inputs/assert/inject faults, not business implementations |

See [domain boundaries](domain-boundaries.md) and [caller bounds](quantum-caller-limits.md).
This example did not verify complex multi-entity composition, cross-database strong
consistency or remote deployment. Conceptual inheritance compatibility is not a
new entity-inheritance syntax.

## Recorded implementation stages

1. Define subject/product ownership; remove Operator/Shop wrappers; role/contract
   relationships directly reference participants. APP retains technical keys,
   revisions, duplicate protection and foreign-key projections.
2. Complete take role-specific attributes, whole-participant mapping, external
   entity/role/contract References and compile checks.
3. Establish customer, product, inventory and sales domains with explicit local
   APP bindings. Generate models, services, roles, storage, constants and deployment
   catalogs by domain.
4. Migrate integer quantities/per-product quantum resources, identity-based
   collection hydration, ordinary read/revision protection and per-element quantum
   records. A fixed whole-book total does not replace per-product conservation.
5. Retain one-click cross-domain registration updates while migrating shopping,
   payment, fulfillment, cancellation and returns. Call sites declare bounds;
   services describe increments/decrements. APP contains no business formulas.
6. Synchronize the then-Chinese UI, test inputs and comparison documents.
   Generate/compile after isolating old artifacts; statically review types,
   call chains and resource declarations.

## Generation, compilation and acceptance boundaries

From the examples root:

```bash
mvn -pl :petstore-demo -am test-compile -DskipTests
node --check demos/petstore/src/main/resources/web/app.js
git diff --check
```

The historical compile evidence is in [implementation history](implementation-path.md).
Only generated source and production/test classes under target were isolated;
the database was neither cleaned, deleted nor migrated, and services were not
started. Compilation establishes type/call consistency, not transaction-fault,
concurrency, deduplication, restart or browser behavior. Earlier runtime records
belonged to an older model.

Resumed regression must cover full lifecycle, multiproduct concurrency,
cross-domain write-failure rollback, negative/null/dynamic limits, per-element
bound violations and recovery. Use an independent database; old-data migration
requires a separate plan.

## Comparison with Java

Java modules, interfaces, composition objects and transaction containers can
implement this architecture too. SPP expresses domain ownership, playing,
participants, formulas and transaction intent as checkable declarations,
reducing representation code. Generator/runtime own fact projection, collection
identity, revisions and resource merging. Business suitability of roles still
requires human judgment. See [SPP versus Java](spp-vs-java.md).

A later addition in this phase generated locked reads for ordinary participants
queried inside transactions. Mixed participants lock when ordinary attributes
also change, not for quantum-only changes. Inventory findStockLine does not lock
quantum facts; lockStock was removed. addCartItem eligibility queries moved
outside the write transaction. Quantum attributes logically belong to the quantum
domain; APP selects database/TCC/dedicated quantum service and PHY integrates it.
A joint-JDBC example is not runtime proof for every mode. See
[transaction reads and quantum domains](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).
