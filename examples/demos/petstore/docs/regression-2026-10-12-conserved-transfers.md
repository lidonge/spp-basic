# Conserved opening and transfer regression — 2026-10-12

This is a historical implementation/verification record, not a fresh test run. KI-003 was subsequently addressed by [cross-store zero-record technical recovery](quantum-opening-recovery.md). References to residual zero addresses below describe the state at that time.

The regression goal first corrected the business model: account opening and inventory start at zero; third-party settlement accounts and stock-supply sources also start at zero. Separate funding and stock-receipt scenarios settle conserved two-sided changes, with no lower bound on source balances. No handwritten business Java was added.

Old KI-001 had an inaccurate diagnosis: directly granting 5000 at registration and 10 stock at shop creation mixed nonzero funding into opening. The new model uses ordinary services to establish zero participants and quantum services for nonzero transfers, rather than disguising registration as technical startup initialization. Framework zero-fact creation is missing-only, creates no quantum transaction entries, and never overwrites existing values. Residual zero-address lifecycle after cross-store failure was tracked separately; cross-store atomic opening was not promised.

Implementation evidence: SPP added SettlementAccount, StockSupply, WalletFundingAgreement, StockReceiptAgreement, fundShopWallet, and receiveShopStock. APP declared primary keys, web exposure, and duplicate protection; PHY retained the existing unified quantum system. Browser and Java tests composed independently generated scenarios without automatic registration grants. Compiler default shared resources no longer required identical property sets: inferred conservation groups were separated from explicit fixed-property contracts, with explicit-conflict tests retained.

Acceptance covered develop generation and all Petstore scenarios, including zero opening, negative sources, and per-transaction conservation; framework/language/generator/Seata regressions; and the Chinese-language UI. Testing was regenerated and checked with three JVMs, independent databases, the complete UI, and restart. All used isolated stores, leaving resident demo services/databases untouched. Open issues were recorded while independent verification continued.

At completion, implementation and develop/testing regressions passed. Technical-lifecycle and compensation questions remained in known issues without blocking this acceptance.

## Evidence obtained

The new zeroOpeningAndExternalTransfersAreConserved test passed: zero account/inventory opening; independent funding leaves wallet 5000 and settlement source -5000; independent receipt leaves available stock 10 and supply source -10; quantum deltas grouped by transaction_id sum to zero; invalid funding/receipt preserves settlement facts. Log: `/tmp/spp-conserved-transfer-test.log`, BUILD SUCCESS.

The existing cross-domain facade declaration `atomic update receiveInventory(...)` automatically bound the inventory domain's real `quantum update` implementation, avoiding a wrongly declared empty local quantum implementation. The real develop Chrome flow passed: explicit zero opening → funding → stock receipt → shopping/payment/fulfillment/refund/cancellation/reservation, plus the English catalog. pageErrors=[]; log: `/tmp/spp-bootstrap-develop-8dq0k4hk/browser.log`. Test services stopped. Early generation and initial UI failure logs were diagnostic only, not passing evidence.

Combined regression `mvn -fae -pl petstore-demo,appframe-seata -am test -Dspp.environment=develop` ended BUILD SUCCESS: lang 340, appframe 579, generator 105, appframe-seata 20, Petstore 34; total 1,078 passed, no failures/errors/skips. Log: `/tmp/spp-conserved-reactor.log`. This included existing scenarios and new zero-opening/external two-sided-transfer acceptance.

The latest develop Chrome UI passed again with additional assertions: registered wallet 0, settlement source -5000, each received product's available stock 10, and supply source -10, followed by the original shopping/refund/reservation flow. pageErrors=[]; log: `/tmp/spp-bootstrap-develop-mhlx8i3_/browser.log`. Test services stopped.

## Testing-profile separate stores

`mvn -pl :petstore-demo -am package -DskipTests -Dspp.environment=testing` ended BUILD SUCCESS, proving generation/compilation/packaging only. Log: `/tmp/spp-conserved-testing-package.log`. The first testing run, `/tmp/spp-bootstrap-testing-ol4gdd43`, started three JVMs and restarted successfully. Zero opening, settlement funding, and stock receipt passed, resolving old KI-001. Ordering failed with `Undefined repository property lineNumber on java.util.LinkedHashMap`. Read-only quantum-store inspection after shutdown showed wallet 5000, settlement -5000, cash 0; inventory plus source summed to zero for every product, and each transaction's quantum deltas summed to zero. Evidence: ledger-audit.log.

The order error came from generic repository property access lacking JSON-snapshot Map support, not a new SPP business rule. Property access gained nested Map and omitted nullable-field support, while ordinary Java objects still reject nonexistent properties. New ObjectPropertyAccessTest covered this behavior; all 580 appframe tests passed. Log: `/tmp/spp-conserved-map-appframe.log`. The later complete testing UI passed as recorded below.

A second testing run's identifier-setter error exposed the deeper cause: cross-domain property backfill used raw List types, degrading ShopOrder.lines elements to LinkedHashMap. Develop saved details before inventory updates; testing executed quantum changes first and exposed the problem. LocalDomainServices now derives JavaType from setter generics, including deferred quantum-backfill callbacks. New LocalDomainCollectionFactsTest and all 581 appframe tests passed. Log: `/tmp/spp-conserved-generic-appframe.log`. No business identifier Map-setter workaround was introduced.

Final testing evidence: `/tmp/spp-bootstrap-testing-l5oqppgs`. Three independent JVMs—customerService/productService/petstore—and four isolated H2 file stores—customer/product/shop/ledger—passed the complete Chrome flow, including source-balance and zero-opening assertions, with pageErrors=[]. Shop restart reached readiness=true. All test processes stopped. Old KI-001 was resolved with no dependent steps skipped; technical-lifecycle and compensation questions remained documented.

## Final combined regression and conservation audit

The final combined regression, including Map reads and generic collection backfill fixes, used `mvn -fae -pl petstore-demo,appframe-seata -am test -Dspp.environment=develop` and ended BUILD SUCCESS: lang 340, appframe 581, generator 105, appframe-seata 20, Petstore 34; total 1,080 passed with zero failures/errors/skips. Log: `/tmp/spp-conserved-final-reactor.log`.

After testing shutdown/restart, read-only isolated quantum-store checks passed: wallet + operator cash + third-party settlement balances sum to zero; available + reserved + sold + supply-source stock sums to zero for each SKU; deltas sum to zero for each transaction_id. Evidence: `/tmp/spp-bootstrap-testing-l5oqppgs/ledger-audit.log`.

A final complete Chrome flow against regenerated develop artifacts also passed with browser exit=0. Log: `/tmp/spp-bootstrap-develop-14zgz50m/browser.log`; test services stopped. The docs/writing worktree received a Markdown snapshot before final acceptance. Subsequent regression records were updated only in the main directory to avoid overwriting the user's documentation edits.

Commands and paths above are historical evidence from the earlier repository layout, not current build instructions.
