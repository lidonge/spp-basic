# Capability and acceptance matrix

This matrix distinguishes source demonstrations from runtime acceptance. The
recorded stage generated, compiled and statically checked the model; the
[historical audit](acceptance-audit.md) does not prove a later model's behavior.
This is an English translation of revision `417a41f`, not a new acceptance run.

| Capability | Recorded source | Business use and limits |
| --- | --- | --- |
| Entities and real domains | customer.spp, product.spp | Independent Person/Product lifecycles; inventory need not define an entity |
| Roles and take | 00-model, inventory.spp | Roles add business facts; Goods/Buyer whole-performer relations; complex compositions are not demonstrated |
| Scenario agreements and relations | Scene files and ShopOrder | Each action is independent; orders include Buyer, lines include Goods; no technical-ID routing |
| Default participant names | Empty cart, return request | Agreement Buyer and entry Buyer bind uniquely without arguments; other scenarios retain aliases |
| Stable instances and collections | Queries, single-participant save, StockLine[] | Populate in place, match business identity, create dynamic lines in atomic services |
| Mathematics and logic | 20-pricing, 05-checks | Shared formulas, explicit business rounding, if/for and calculate composition; no rule agenda |
| Explicit checks | Scenario check calls | Failure exits its block by default; breakTransction explicitly rolls back; guard skips only its inner block |
| Quantum services | Inventory/fund updates and call-site bounds | Actual increments/decrements and per-product conservation; callers choose bounds, checked automatically |
| Transactions and resources | transaction and PHY declarations | Default REQUIRED; same-JVM services and a shared JDBC connection configuration |
| Platform metadata | APP tables, versions, duplicate protection and numbering | Technical information stays outside SPP; read baselines and atomic revision protection |
| Numeric representation | BigDecimal model specifications; exact PHY substitutions and field overrides | Physical capacity checks and explicit business rounding; no representation-method chains in business expressions |
| Localization | Product constants and APP/PHY locales | Translate product text by constant name; vouchers and numbers do not change with locale |
| Multi-domain deployment | APP application/bind | Real providers and domain-qualified local containers; no mocks or direct cross-domain table reads |
| UI/test separation | web, src/test/java | Frontend composes scenarios; the recorded stage only compiled the three Java test sources |

The example scenarios are serial. Parallel frontend reads are not an SPP
`parallel` demonstration or a consistent snapshot. REQUIRES_NEW/NESTED are
generic syntax; this example defaults to REQUIRED. The recorded example did not
verify Saga/TCC, cross-database execution, remote deployment or production
authorization. See [language gaps](language-gaps.md) and the
[implementation record](multidomain-implementation.md).

The recorded refinement automatically generates locked reads for ordinary
participants inside transactions. Mixed participants lock when ordinary facts
also change; quantum-only changes do not take that ordinary read lock.
`findStockLine` does not lock quantum facts, `lockStock` was removed, and
`addCartItem` eligibility queries moved outside the write transaction.
Quantum facts belong logically to a quantum domain; APP selects database/TCC/
specialized quantum strategies and PHY binds implementations. A shared-JDBC
example is not runtime proof of all strategies. See
[transaction reads and quantum domains](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

The recorded four-domain model has 30 scenarios: 22 changing state and eight
read-only. It narrows transactions around reliable optimistic baselines:
ordinary queries, checks and calculations precede the transaction; atomic
conditional writes occur inside it. Read-only eligibility is not a commit
condition; quantum settlement does not advance ordinary revision markers.
Reservation demonstrates two sequential transactions, retaining an accepted
agreement if reservation fails. Payment demonstrates nested REQUIRED logical
boundaries. Under non-LOCAL policies the quantum phase settles first and
cross-mode failure uses Saga recovery; `develop` uses LOCAL joint commitment of
quantum and ordinary updates. See the [transaction audit](transaction-boundary-audit.md)
and [implementation plan](transaction-evolution-plan.md).
