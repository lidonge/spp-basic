> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Source acceptance replacing legacy reversal-store tests

Historical status: all source responsibilities below passed incremental verify
(978 JUnit tests and 74 APP root entries; log
`/tmp/spp-prepared-reversal-corrected-source-validation.log`).
The 16 old methods in JdbcDepositReversalStoreTest were deleted. Subsequent clean
verify passed 962 JUnit tests and 74 entries, removing old test/nested classes
(log `/tmp/spp-reversal-store-retirement-clean-validation.log`).
Other legacy tests still used the old Store production Java, which awaited
retirement with those remaining responsibilities.

| Old responsibility | Source evidence |
| --- | --- |
| Joint commit of new inverse trade, account/postings/link/audit; retain original and later deposits | 99j cash reversal and 99zm/99zma commit points; independent queries of original/later trades and postings |
| Completed replay does not reverse again, even with later business | 99zn cross-JVM restart; 99zub prepared-result replay after an actual later deposit of 25, retaining current 225/original trade 200 |
| Different reversal identities cannot reverse the same original twice | 99j rejection; 99zo/99zoa/99zob actual different-identity concurrency |
| Account becomes stale after preparation: no failed link/postings; retry original identity from new state | 99zua actually deposits another 5 and rejects the stale candidate; fresh preparation yields 205 while retaining later trade/postings |
| Rollback of link/account/partial-posting/pre-commit faults | 99zm/99zmb actual service/JDBC faults; 99zt fourth-posting collision confirms removal of first three |
| Same/different identity concurrency commits one reversal | 99zo/99zoa/99zob release both execution threads to commit only after both prepared |
| Commit succeeds but response fails: query original result/replay | 99zmb afterCommit actual connection fault, query/retry |
| Original document changes after preparation: no settlement | 99zu declaratively persists original settled.version=99; locked revalidation inside transaction preserves balance/link/postings/quantum facts, then fixture is restored for retry |
| Changed request cannot reuse a settled identity | 99zub independently changes overnightMode2 and original document; five audit entries, one trade, two replays |
| Primary/failure audit unavailable: preserve primary exception and roll back inverse | 99zr dual faults; source failure facts retain additional error code |
| Replay-audit failure does not deny known settlement or reverse again | 99zr enables dual audit faults only after actual trade; query and stable-identity retry |
| File close/restart preserves trade, actual postings and identity | 99zn two independent JVMs read original/later/reversal trades and history from file H2 |
| Same identity with different original document is identity rejection | 99zub changes prepared original fact to 99, returns DEPOSIT_REQUEST_ID_REUSED, preserves original trade and later business |
| Exception before actual commit: unknown result, query then retry original identity | 99zmb commitBefore at actual connection boundary, no durable trade, unknown audit and retry |
| Exception after actual commit: unknown result, query existing trade | 99zmb commitAfter at actual connection boundary, complete durable trade, query/replay without another deduction |

Stale-plan source commits return PERSISTENCE_VERSION_CONFLICT; old Java components
returned DEPOSIT_VERSION_CONFLICT. Both reject stale plans and guarantee complete
rollback within the verified boundary. Source concurrency/query paths explicitly
use the former; do not alter generic exception messages to simulate bank-specific
errors. New source cash entries follow XBOD eligibility and cross-date rules.
Fixtures from old unconfigured financial components do not define new cash policy.

Source scenarios and old Java classes use different storage layouts. Declarations,
queries and version updates jointly implement original-fact checks and unique
reversal links inside full source transactions. What replaces the old tests is
the listed behavioral responsibility, not replication of private Java SQL table
names, callbacks or wrappers.

Evidence is preserved from revision `417a41f`, not rerun for translation.
