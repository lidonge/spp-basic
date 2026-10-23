# SPP compared with traditional Java

> Historical comparison from revision `417a41f`. The 2026-10-11 transaction/storage
> update superseded earlier joint-commit, automatic player-save and CustomerSubject
> descriptions. See [implementation rules](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> It selected AT ordinary updates, quantum first and Saga across modes; REQUIRED
> does not change that. Person/Product are maintained in their own domains.
> Later LOCAL configurations are distinguished below.

Compare the same Petstore requirements, not an assumption that Java cannot model
domains. Java/ORM can use roles, value objects, domain services and external
technical metadata. SPP aims to make business boundaries enforceable language rules.

| Dimension | SPP | Traditional Java | Benefit/cost |
| --- | --- | --- | --- |
| Entities/roles | Entity/Role/take; Person plays Merchant with added role facts | Interfaces, composition, explicit mappings | Visible business identity; mapping/entity lifecycle still costs effort |
| Participants | Actual inputs only, stable instances, no business returns | Parameters, returned objects, rebinding | More definite scope/hydration; in-place collection and alias rules required |
| Contracts | Each Contract represents a real scenario | Domain agreement objects or DTOs | Avoids universal commands; names do not prove business meaning |
| Relationships | ShopOrder contains Buyer; APP projects foreign keys | Object associations or customerId | Storage simplification stays out of business; hydration/identity are more complex |
| Numbers | Formulas, PHY representation, explicit rounding | BigDecimal chains/helpers | Direct formulas; generator must preserve adaptation/conversion |
| Checks | Explicit scenario checks with lexical scope/rollback marker | if, validation, exceptions/return values | Reviewable control scope; reusable group syntax still pending |
| Atomic composition | Ordinary atomics do not call peers; calculate composes calculate | Free method calls | Controlled dependencies at cost of reuse/expression freedom |
| Transactions | Business boundary/propagation; APP/PHY realization | Annotations/templates/configuration | Visible commit relationships; syntax cannot create resource capabilities |
| Quantum changes | Deltas/settlement constraints over complete resources | SQL deltas, locks, ledgers/checks | Cancellation/refund preserves later changes; runtime/audit complexity |
| Platform identity/revisions | APP columns, player mapping, query baselines | ORM mappings/optimistic versions | No technical facts in business model; metadata must be preserved correctly |
| Duplicate protection | APP request keys/facts | Interceptors, unique keys, result tables | Less business boilerplate; joint commit/result recovery still need verification |
| Independent scenarios | Separate actions, frontend composition | Controller routing/service orchestration | Clear actions, more scenarios/compatibility cost |
| Tests | Java composes generated scenarios | Java tests domain services | No embedded test syntax; faults/concurrency still must actually run |

Return requests directly declare Buyer, original ShopOrder and ReturnRequestAgreement.
Eligibility is explicitly checked, reason belongs to the agreement, and updates
persist transactionally. There is no ShopCommand/switch action. ShopOrder itself
is the ordering contract; using it for payment/return does not call ordering again.

Quantum realization protects stock settlement; ordinary in-transaction reads lock
where required. Physical concurrency cost depends on realization. Parallel UI
reads are not a snapshot, same-JDBC guarantees do not extend to cross-database
strong consistency, and take mapping alone is not implemented entity synchronization.

Generation/compilation verify syntax/interfaces/types. The original phase ran no
regression and claimed no performance, concurrency or recovery superiority to Java.
Earlier runtime evidence used an older model; comparisons require equal data
volumes and fault conditions.

Customer/product are real independent domains. APP selects same-JVM qualified
atomic bindings; PHY shares declared resources. Java modules/interfaces can do
the same. SPP adds compile checks for projections, collection identities, service
kinds and binding cycles, at the cost of generation, catalog aggregation and
baseline management. Compiling four domains alone does not compare reliability
or performance.

Quantities default to int, amounts to precision/scale BigDecimal; both are number
representations globally/locally replaceable in PHY, with explicit business
rounding. Java can likewise unify interface, serialization and arithmetic rules;
SPP generates that representation code from unified declarations.

Bounds keep quantum services focused on increments/decrements while callers
choose negative/dynamic/null limits. Settlement enforces them. Java can use
policy objects/transaction templates too. SPP reduces repetitive caller checks,
but each quantum implementation must bind limits to settlement and rollback.

Goods retains its whole relationship when playing line roles; Buyer plays Cart.
This suits multiple roles better than simple inheritance, though Java composition
can express it. SPP does not replace business analysis or establish advantages
merely by compiling.

## Transaction refinements

Ordinary in-transaction queries lock; mixed participants lock if ordinary fields
also change, not for quantum-only work. findStockLine does not lock quantum facts;
lockStock was removed and addCartItem admission moved outside writes.
APP selects database/TCC/dedicated quantum realization, PHY integrates it.
Shared-JDBC evidence is not every mode's evidence. See
[transaction reads](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

The recorded four-domain set had 30 scenarios (22 changing/eight read-only).
Reliable optimistic baselines move queries/checks/calculation before transactions,
with atomic conditional writes inside. Admission-only reads are not commit
conditions; quantum settlement does not advance ordinary revisions.
Reservation retains acceptance after second-stage failure; payment has nested
REQUIRED. Non-LOCAL settles quantum first and uses Saga across modes; recorded
develop uses LOCAL joint commit. See [audit](transaction-boundary-audit.md) and
[plan](transaction-evolution-plan.md).

Optimistic commit reduces long ordinary locks and handwritten version predicates,
not conflicts themselves. Separating ordinary revisions from quantum facts avoids
unnecessary profile-update conflicts from balance changes. Sequential transactions
retain stage results but require recovery protocols. Request-result recording and
last business commit are not one transaction; unknown outcomes require business
credential queries and technical-registration recovery, not automatic replay.

## Generated persistence rules

Traditional Java explicitly separates inherited role DTO facts, role-table writes
and entity-domain calls, often copying quantum balances after updates. SPP retains
facts/playing in the model while APP/PHY drive split tables, hydration and quantum
readback. Joint cart-line identity and independent order-line credentials illustrate
composed-role storage ownership.

One role query may trigger role/player/member/quantum-source queries. Per-member
hydration prioritized semantics, not optimized batch querying. Non-LOCAL quantum
commit followed by ordinary failure requires inverse recovery; LOCAL shared-store
work can truly roll back. These are different guarantees. The original comparison
had generation/compile evidence, not measured performance/concurrency/recovery.

## Zero opening and conserved external transfers

Wallets, product stock, settlement and supply accounts start at zero.
Funding/stock receipt are separate two-sided quantum scenarios. Source bounds
have no lower limit; recipients must remain nonnegative. UI exposes separate
entries; tests compose generated scenarios rather than implement business Java.
See [design and initialization comparison](conserved-opening-and-transfers.md).

Optional official coordination/tracing is not part of basic guarantees. Missing
providers produce warnings; basic persistence and optimistic protection remain.
This comparison's historical enhanced behavior must not be attributed to an
unconfigured basic build.
