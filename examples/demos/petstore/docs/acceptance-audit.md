# Petstore acceptance record

2026-10-09. The independent petstore-demo project described business/data/acceptance in SPP, APP, and PHY, with a thin presentation/request web layer.

This record predates the independent-scenario refactoring. The subsequent source-refactoring phase deliberately did not run regressions at the user's request. Historical PASS results below do not prove that refactored version's runtime behavior. Later regression records provide separate evidence.

## Build and automated acceptance

`mvn -pl :petstore-demo -am clean verify` generated and compiled project Java from an empty target. Public transaction-boundary regression and final source-business verification followed:

- `mvn -pl :petstore-demo -am verify`: 956 public tests passed—lang 307, appframe 544, generator 105—and all 28 APP entries passed. Log: `/tmp/spp-petstore-completion-verify.log`, completed 10:43:58.
- After adding source request-identity validation, `mvn -pl :petstore-demo -am verify -DskipTests` reran all 28 APP entries successfully. Public code was unchanged, so public tests were not rerun. Log: `/tmp/spp-petstore-final-business-verification.log`, completed 10:45:39.
- Zero handwritten demo Java files; all 806 Java files under target/generated-sources came from generation.

Thirty JSON and thirty Markdown files under target/spp-examples/evidence reported PASS. Restart groups additionally exported resume and parent-group evidence, explaining the file count above 28 root entries.

| Business/guarantee | Entry | Queried evidence |
| --- | --- | --- |
| Complete settlement/fulfillment | happyPathExample | Two staple-food bags 67.80; wallet 4932.20/income 67.80 after payment; final available 38/sold 2, received |
| VIP formula | vipPricingExample | 119.60 − 11.96 + 0 = 107.64 |
| Multiple products | multiItemExample | Two lines 72.15 + 8 = 80.15; available 37/reserved 3 |
| Cancellation/refund/return | unpaidCancellationExample, paidCancellationExample, returnRefundExample | Stock restored to 40, wallet 5000/income 0; original order retained |
| Later order preserved | laterOrderPreservedExample | After canceling the first, second remains awaiting payment; available 39/reserved 1 |
| Business validation | oversell/empty/fractional/insufficientFunds/shippedCancellation/duplicateCustomer/profileValidationScope, etc. | Corresponding inputs/states rejected; failing scope preserves stored facts |
| Read-only quotes/cart operations | quoteWithoutCommitExample, cartRemovalExample | Quote creates no order/reservation; removal leaves empty cart |
| Business idempotency | idempotencyExample, requestCollisionExample | Repeated payment does not deduct again; same identity with different facts rejected |
| Atomic multiline rejection | multiItemAtomicRejectionExample | Earlier available line followed by insufficient stock leaves no stock/order settlement |
| Actual concurrency | concurrentCheckoutExample | Barrier forces two attempts to buy seven; only one succeeds; available 33/reserved 7 |
| Mid-write failure | rollbackRetryExample | Fault after insertOrder rolls back order/stock; original identity retryable |
| Mid-transfer failure | paymentRollbackRetryExample | saveMerchant fault after transfer restores wallet/shop/order to prepayment; ROLLED_BACK; retry succeeds |
| Unknown commit | uncertainCommitQueryExample | commitAfter simulates lost confirmation; UNKNOWN; queries actual receipt and 38/2 stock; retry returns original receipt |
| Actual quantum storage | quantumLedgerExample | Three inventory plus two amount logs and five current values; summed delta zero; quantum values agree with business records |
| Independent-process persistence | persistenceSetupExample → persistenceResumeExample | Two independent JVMs/empty participants; second reads stock 38/2 and amounts 4932.20/67.80 |

## Chinese-language UI acceptance

The public GeneratedWebServer actually ran. APP exposed three scenarios and invoked one source startup scenario. There was no handwritten demo startup class or Controller business implementation.

Chrome/Playwright clicked registration, products/cart, quote, checkout, payment, replay, shipping, receipt, return, and refund: PASS, zero page errors, no horizontal overflow at 390px mobile width. Evidence: target/ui-evidence/browser.json, desktop.png, mobile.png. The temporary technical test script was not business implementation. The APP entries and UI could reverify source behavior.

## Additional boundaries and compatibility

pricingBoundaryExample independently checked four cases: ordinary threshold 100, below-threshold 99.99, VIP post-discount threshold, and rounding 12.345. clientPriceIgnoredExample proved forged client prices/amounts did not replace persisted catalog prices. cartAdditionExample checked repeated product additions accumulate quantity. Cancellation/return checked final states. Missing requestId was rejected by source validation before a physical transaction, yielding UNREPORTED.

Public tests verified synchronous local serial subscenarios inherit AT/QUANTUM boundaries, reject incompatible modes, implicit TCC inheritance, and cross-thread groups inside transactions. Settled Saga nodes were not treated as quantum technical rollback.

Bank compatibility: `mvn -pl :xbod-deposit-demo -am verify -DskipTests` passed all 86 APP entries, including five restart groups. Log: `/tmp/spp-petstore-bank-regression.log`, completed 10:34:46 in 17 minutes. This skipped public unit tests, covered by the Petstore build above.

## Public scope limits

Local same-store H2, one shop's inventory-ledger lock, demonstration wallet. No production authentication or real payment provider, no multi-resource Saga/TCC deployment. This path did not prove Entity-to-multiple-Role take mappings. Original bank business modules were unchanged; Petstore tests were not represented as bank tests.

## Participant-driven migration evidence boundary

Source then migrated to Buyer/Merchant Roles, in-place queries, no business return values, default role names, implicit bindings, identify, lexical check scope, explicit cancellation, quantum constraints, and compound calculations. Only generation and main/test compilation ran, not regressions. Historical cases/counts above describe older modeling, not runtime acceptance of that source. See [implementation path](implementation-path.md).

## 2026-10-10: four domains and bounds compilation

The phase completed customer/product/inventory/petshop source, Roles/integer stock, actual local bindings, constant localization, and call-site bounds. After isolating old artifacts, `test-compile -DskipTests` passed for 781 main Java and three test sources. Log: `/tmp/spp-petstore-final-four-domain-build.log`. Only compilation/static review occurred: no tests, servers, browsers, or old-database operations. Prior PASS records remained historical-model evidence.

## 2026-10-10: automatic transactional reads and quantum-domain contract

lockStock and explicit APP for update were removed; addCartItem qualification queries moved outside transactions. Generated metadata covered ordinary Cart locking, Buyer profile ordinary-write protection, payment Buyer/Merchant quantum changes without ordinary read locks, lock-free quantum findStock reads, and cross-domain projection intent. After isolating old artifacts, 773 main Java and three test sources compiled. Log: `/tmp/spp-automatic-transaction-reads-final-build.log`. Static review/web-script syntax passed; no regressions, services, or database changes. Logical quantum domains and APP database/TCC/dedicated-provider choices were documented without claiming runtime acceptance of every physical strategy or store split.

Additional quantum settlement bounds regenerated and passed `mvn -pl :petstore-demo -am test-compile -DskipTests`: 773 main Java and three test sources; log: `/tmp/spp-provider-quantum-bounds-final-build.log`. Static inspection found call-site getQuantumBounds metadata, no post-call settlement-value query, JDBC upper/lower conditions, and provider-side projected bounds. No tests/database operations ran; actual SQL, concurrency, and joint rollback on bounds violation remained pending.

Whole-scenario boundary review generated/compiled 785 main Java and three test sources using the same test-compile command. Log: `/tmp/spp-minimal-scenario-boundaries-build.log`. Static checks covered pretransaction queries, renewed order protection, quantum-read metadata, and retained bounds. No regression ran. Default optimistic protection was then a design direction; atomic reads and automatic generation were still pending implementation.

## Transaction evolution: compilation/static evidence

Default ordinary-snapshot revision protection, cross-domain baselines, separation of ordinary/quantum facts, scenario duplicate boundaries, sequential reservation transactions, and nested REQUIRED payment entered source. There were 30 scenarios—22 mutating, eight read-only—with ordinary queries outside source transactions and conditional writes inside. Roles changing only quantum facts did not call ordinary save.

`mvn -pl :petstore-demo -am test-compile -DskipTests` ended BUILD SUCCESS; log: `/tmp/spp-transaction-evolution-final-build.log`. It compiled 837 Petstore main Java, three test sources with 31 methods, and 49 appframe test sources. Static review: `/tmp/spp-transaction-evolution-static.txt`; web `node --check` and `git diff --check` passed. No tests, services, database operations, faults, concurrency, restart checks, commit, or push occurred in that phase.

Tests existed for accepted-request persistence after reservation rejection, nested-payment rollback, quantum settlement not advancing ordinary revision, and stale-baseline rejection. Compilation did not prove runtime success. Old databases had no automatic migration. With request registration separated from business commit, unknown/partial commits required technical recovery coordination; automatic original-receipt recovery was unsupported at that checkpoint.

All commands, counts, and limitations in this page describe their stated historical phase, not a new execution during English translation.
