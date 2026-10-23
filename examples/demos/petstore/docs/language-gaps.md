# Generic capabilities, limits and open questions

This inventory records the stage captured in revision `417a41f`. Its historical
test claims are not new evidence from the basic-distribution migration.

## Capabilities added in that phase

- Business fields may declare `business("purpose")`. Clearly technical fields
  without declared business meaning are rejected. Natural-language truthfulness
  still needs review.
- APP defines keys and versions outside business models. Runtime owns platform
  metadata; table `auto` declarations generate business credential numbers.
- APP expands direct Buyer/Goods/ShopOrder relationships into storage projections.
  Identity comparison uses actual business credentials, version comparison uses
  the query baseline, and saves receive only current participants.
- One-dimensional participant collection inputs generate mutable Lists. Queries
  hydrate Goods[]/ShopOrder[] directly without collection-wrapper contracts.
  Scalars cannot be rebound; collection containers stay stable. Item-by-item
  hydration by each participant's business identity supports optional and locked
  reads; general filtered collection queries support neither.
- Quantum services may change only part of a complete conserved resource;
  unchanged attributes still participate in its snapshot. Ordinary checks are
  not subject to quantum-entry restrictions.
- Take mappers demonstrate entity/role playing, role-specific attributes and
  whole-participant relationships. Multi-entity composition is a language
  capability but not demonstrated here. Atomic execution binds/hydrates entity
  facts; this does not replace entity repository lifecycle management.

## Existing foundation

`append` returns a new sequence, `copy` copies business facts, and atomic services
create dynamic detail participants. Scenarios cannot construct participants,
call scenarios or route through generic action strings. Ordinary atomic services
cannot call each other; calculate composes only calculate, without hidden checks.

SPP provides transaction, REQUIRED, REQUIRES_NEW and NESTED. APP/PHY determine
actual resource capabilities. Missing suspension, savepoint or consistency
support must be rejected, not treated as cross-database strong consistency.
Parallel work within transactions and TCC/Saga conditional updates still have
limits; consult the transaction documentation.

APP web start/expose generates entry allowlists. Public GeneratedWebServer
accepts JSON and serves static pages; it contains no shop rules and supplies no
production authentication, authorization or tenant isolation. Tests live only
in Java test directories and are not registered as business entries.

## Questions and verification gaps

Named reusable validation execution groups had no confirmed syntax. Scenarios
explicitly order checks; mutually calling check services cannot simulate support.
Field completeness, proof of contract business meaning, and entity synchronization/
persistence across roles or processes were not fully covered. Old data keys,
columns and snapshots require an upgrade plan; the old database was preserved.

The business model was corrected to zero opening plus independent, two-sided
fund/stock transactions. Full develop business/web flows and testing three-JVM
web, registration/funding/refund/reservation/restart flows passed at that stage;
KI-001 was resolved. Framework zero-fact creation does not overwrite settled
values. Arbitrary old-database repair, concurrent initialization and cross-database
residual-zero lifecycle issues are tracked in [known issues](known-issues.md),
not grounds to stop other regression work. Later lifecycle resolution is recorded
there. See the [regression record](regression-2026-10-12-conserved-transfers.md).

The phase also added centralized four-domain deployment, external entity/role/
contract References, domain-local bindings, integer PHY and localized constants.
Callers declare quantum bounds; range checks are automatic and services express
only business increments/decrements. This is not new exception-control syntax.
See the [capability matrix](capability-matrix.md) for runtime boundaries and gaps.
