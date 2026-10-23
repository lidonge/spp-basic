# Transaction boundaries and ordinary/quantum fact implementation plan

> Historical plan, preserved from revision `417a41f`. The 2026-10-11 transaction/
> storage update superseded earlier joint-commit, automatic player-save and
> CustomerSubject descriptions below. See the [implementation contract](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> That update selected AT ordinary updates, quantum settlement first and Saga
> recovery between modes; REQUIRED does not change this. Person/Product are
> explicitly maintained in their owning domains. Later LOCAL deployment rules
> and current environment configuration must also be consulted.

The requirement was for business source to declare consistency boundaries and
propagation only. Admission queries finish outside transactions; later buyer
deactivation does not re-reject in-flight business. APP's default optimistic
protection covers ordinary facts read/calculated outside and modified inside a
transaction. The selected quantum implementation protects quantum facts.

## Design

Ordinary facts and technical revision markers must be read together; writes use
atomic database conditional updates. Revision markers are persistence metadata,
not SPP fields. Stable participant instances and cross-domain model projections
carry the same read baseline. Only participants whose ordinary facts actually
change enter ordinary version comparisons. Admission-only reads do not become
commit conditions. Quantum balances/stock hydrate from the logical quantum
domain, not ordinary role snapshot copies. Quantum settlement must not advance
ordinary-fact revisions.

APP duplicate protection may register requests then retain completed results,
but cannot wrap an entire scenario in a hidden database transaction. Failed or
commit-uncertain requests retain recovery state; already committed stages cannot
be rerun automatically. This describes the optional coordination path, not a
basic-SDK replay guarantee.

Reservation first commits acceptance, then attempts stock reservation; failed
reservation retains acceptance. The historical payment design used nested
REQUIRED for funds within the order/receipt joint boundary. These demonstrate
independent stages versus composition of consistency units; actual guarantees
remain subject to the updated resource rules above.

## Implementation path and acceptance

1. Add reliable repository read baselines, atomic conditional updates and
   cross-domain baseline propagation.
2. Separate ordinary snapshots from authoritative quantum facts; remove ordinary
   role saves used solely to copy quantum settlement results.
3. Remove hidden scenario-deduplication transactions; record recovery limits.
4. After these protections exist, narrow every scenario boundary and update its audit.
5. Add reservation acceptance/stock scenarios, APP/PHY persistence and the then-
   Chinese web entry; retain payment's nested REQUIRED.
6. Generate/compile production and test sources and inspect statically only.
   Do not run tests, services or databases for that phase. Compilation is not
   measured runtime concurrency evidence.

## Comparison with traditional implementations

Traditional Java often puts queries, validation, calculations and updates in one
method-level transaction, or requires handwritten version checks. SPP puts
business boundaries in scenarios and lets APP generate technical comparisons,
shortening ordinary-resource lock duration and exposing independent stages.
The cost is reliable framework baseline management, cross-domain propagation and
failure recovery. Optimistic conflicts remain possible and are not business
validation rules. Independent transactions have no overall rollback; an earlier
success must remain a queryable business fact.

At the recorded milestone all six implementation steps existed in source/docs,
with generation, compilation and static review complete but no runtime test
execution. The historical inventory was 30 scenarios and 837 generated
production Java files; see [acceptance audit](acceptance-audit.md). Old-database
migration and automatic unknown-request recovery were not implemented. These
counts and test restrictions describe that phase, not current acceptance results.
