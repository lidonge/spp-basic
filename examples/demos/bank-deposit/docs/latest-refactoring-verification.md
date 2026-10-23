# Latest refactoring verification record

On 2026-10-13, refactoring remained under an active goal. The 1,135 cases below record the combined regression before additional limited-input entry checks. This report establishes only the executed scope, not completion of all historical acceptance or overdraft funding models.

## Combined regression

```
mvn -pl :petstore-demo,xbod-deposit-demo -am test -Dspp.environment=develop
```

BUILD SUCCESS, with zero failures, errors or skips:

| Module | JUnit test count |
| --- | ---: |
| lang | 362 |
| appframe | 617 |
| generator | 106 |
| Petstore | 39 |
| XBOD | 11 |
| Total | 1135 |

The 11 XBOD tests include 92 historical input vectors: core repayment 15, agreement accrual 12, eligibility/authorization 49, and fees 16. Vector counts are separate from JUnit methods; they are not presented as 92 independent tests. See the refactoring document for changed missing-rate and authorization-evaluation protocols.

## Split-database verification

```
mvn -pl :xbod-deposit-demo -am test \
  -Dtest=XbodCalculationTest,XbodRuleTest,XbodTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing
```

BUILD SUCCESS. Coverage includes zero-funded opening, negative cash-counterparty balances, post-settlement hydration, same-day reversal, cross-day preservation of later deposits, Mode2, quantum recovery after ordinary-save faults, and the actual result that committed ordinary Saga stages still require compensation. Testing uses two separate H2 data sources inside one JVM; this does not establish actual network-distributed deployment, XA, Seata AT or TCC acceptance.

## Verification limits

- Quantum conservation contributions for debt/exposure still require a modeling decision; passing core calculation vectors does not prove persisted overdraft repayment.
- Split-database ordinary Saga compensation was not invented unilaterally. Fail records retain facts requiring investigation and RECOVERY_REQUIRED.
- Technical fault, restart, communication and concurrency coverage from the former 86 APP acceptance entries had not all migrated.
- This round had not been committed/uploaded. Business source remains exclusively SPP/APP/PHY; Java source is acceptance-only.

## Additional limited-input account-opening acceptance

A new test bypasses test-helper zero initialization, supplying only account number, opening date and empty instances of declared participants. The generated opening scenario must establish ordinary business initial state itself, while default repositories establish zero quantum facts, allowing immediate cash deposit. Additional generic repository tests cover missing initial values in local/separate quantum stores. Existing nonzero initialization restrictions, post-commit creation and recovery tests remain.

On 2026-10-13, focused develop acceptance ended BUILD SUCCESS: QuantumOpeningRecoveryTest 9, DatabaseInitializationTest 2, bank acceptance 12; 23 total, zero failures/errors/skips. This does not replace the combined regression above; new runtime changes still required inclusion in final full regression.

Focused testing acceptance also passed all 23 cases with zero failures/errors/skips, including immediate cash deposit after limited-input opening. Both databases are isolated to avoid cross-test interference from repeated account numbers sharing old quantum facts.

Additional full runtime regression, `mvn -pl appframe -am test`: BUILD SUCCESS, 618 tests, zero failures/errors/skips. Default quantum-zero creation changes are included in this full runtime validation.

## Additional branch-rule and account-ownership acceptance

Added 54 vectors for branch scope, teller-number boundaries/exceptions, and card/passbook warnings, reusing recorded historical inputs/expectations. The new bank business suite contains 146 vectors.

Actual storage acceptance covers ownership: mixing eligibility facts from another account rejects deposit before settlement; using another account's original agreement rejects reversal before settlement. Both verify funds remain unchanged.

On 2026-10-13, all three bank test classes ran in develop and testing, each BUILD SUCCESS with 15 JUnit tests and zero failures/errors/skips. These vectors/ownership checks do not resolve KI-R01 debt-conservation modeling or replace unmigrated technical recovery cases.

## Additional cash reversal entry acceptance

Completed original-entry lookup, eight-slot evidence validation, opposite debit/credit calculation and reversal-entry persistence. APP foreign-key relations infer original-entry queries keyed by the original agreement and reverse-entry queries keyed by the reversal agreement; no redundant repository configuration was added.

A real stored-rate path registers a personalized rate: depositing 10,000 incurs an original fee of 6; reversal refunds all 6. Database requery verifies the eight reverse entries' slots, accounts, directions and amounts, and the hydrated net balance of zero. Details receive agreement ownership after automatic agreement numbering and before insertion, avoiding empty voucher references.

On 2026-10-13, develop and testing bank acceptance each passed 16 JUnit tests with zero failures/errors/skips. Historical rule vectors remain 146. Debt quantum-conservation modeling and ordinary Saga compensation remain known issues; this does not declare the entire goal complete.

## Reversal-entry persistence fault acceptance

A new fault before reverse-entry insertion rereads databases to check funds, the original settlement marker, reversal agreement and entries. Develop LOCAL restores original funds/unreversed state, leaves no reversal agreement, and permits a successful retry. Testing Saga successfully reverses the quantum transaction, but committed original markers/reversal agreements remain and entries are empty. Fail returns RECOVERY_REQUIRED, handled=false and records RECONCILIATION_REQUIRED; it does not claim whole-transaction rollback.

On 2026-10-13, XbodTransactionTest passed all 9 tests in each configuration with zero failures/errors/skips. Including previous calculation/rule tests, the bank total became 17 methods; this focused run did not rerun all 17.

## Independent-JVM file-database restart acceptance

New XbodRestartTest runs three independent JVMs against the same file databases: open/deposit; restart, query original settlement and reverse across days; restart again, query original settlement marker/eight reverse entries, and verify zero account/cash-settlement balances. Later JVMs neither reopen accounts nor reset quantum facts. Test Java only calls generated services and asserts.

On 2026-10-13, develop and testing restart checks each passed 1 test, BUILD SUCCESS. Testing uses two separate file databases. This proves persistence/continued operation after normal JVM exit, not forced termination, unknown commit outcomes or network-partition recovery.

## Cross-day accrual and scenario separation acceptance

XbodAccrualTest adds two actual posting paths: hold 100 for one day then deposit 50, persisting ordinary accrual 100; use balance 650 across ten ordinary days, a four-day agreement window and five threshold tiers, persisting ordinary accrual 4,300 and agreement accrual 2,200. Generated queries reread storage rather than merely checking calculation plans.

On 2026-10-13, both focused develop accrual cases passed. After all 13 scenarios were separated into individual files, testing Xbod*Test passed all 20 cases with zero failures/errors/skips. Historical rule vectors remained 146; debt quantum modeling and compensation policy remained unresolved.

The same round's develop Xbod*Test also passed all 20 cases with zero failures/errors/skips, covering split-scenario generation, local joint commit, normal-exit restart and actual accrual persistence.

## APP technical idempotency and current combined regression

Trusted RemoteServiceIngress verifies deposit/reversal replay for the same caller/serial number. Requeries prove funds, agreement numbers and entries do not duplicate; different callers with the same serial settle independently. Local calls with identical technical request headers still settle separately, without remote idempotency suppression. Four focused testing cases passed; this does not establish real network-fault recovery.

On 2026-10-13, `mvn -pl :petstore-demo,xbod-deposit-demo -am test -Dspp.environment=develop` ended BUILD SUCCESS: lang 362, appframe 618, generator 106, XBOD 24, Petstore 39, total **1,149**, zero failures/errors/skips. This includes default zero quantum facts, single-domain deployment generation, actual reversal entries, cross-day accrual, restart and idempotency.

See [completion-audit.md](completion-audit.md): a passing combined regression does not automatically complete debt quantum modeling, ordinary Saga compensation or historical entries not individually migrated.

## Additional concurrent-business acceptance

XbodConcurrencyTest covers two paths. Different accounts synchronize after advance calculation, deposit 100/200, then verify individual funds, settlement counterparties, agreement ownership and detail counts in storage. Two reversals of the same original transaction both pass advance checks before racing; only one settles, the other reports ROLLED_BACK, final funds are zero and exactly eight settled reverse entries remain.

On 2026-10-13, develop/testing each passed both focused cases with zero failures/errors/skips. No test barrier was placed inside a transaction; no account calculation or business mutex implementation was handwritten. This verifies a controlled two-request race, not high load/real network concurrency, nor every former retry entry for different request identities acting on one account.

## In-progress remote duplicates and request-content conflicts

Concurrent identical caller/serial requests do not reexecute business. The current API returns REQUEST_IN_PROGRESS for an in-progress duplicate. After the original finishes, technical replay returns the same agreement number/result; persisted balance increases only by 100. This was not silently changed to synchronous waiting. Reusing the serial with a changed amount returns REQUEST_ID_REUSED and funds stay at 100.

On 2026-10-13, develop XbodIdempotencyTest passed 6 cases; testing passed 5 concurrent-replay-related cases plus the new content-conflict case separately. All had zero failures/errors/skips. Unresolved in-progress response policy is recorded in KI-R05; new technical return policy is not hidden in business language.

## Local HTTP and UI duplicate protection

Actual loopback HTTP calls the GeneratedWebServer APP scenario catalogue, covering deposit/reversal, repeated-click replay, server override of forged call-mode/caller headers, and 404 for unexposed services. Browser Origin.USER uses APP's five-second duplicate guard; system Origin.SERVICE uses caller-serial idempotency; LOCAL is suppressed by neither. Acceptance initially misclassified browser requests as system RPC and was corrected to the runtime protocol with matching APP configuration.

On 2026-10-13, develop/testing HTTP plus idempotency checks each passed 7 tests with zero failures/errors/skips. Servers use random local ports and close afterward. This does not verify public deployment, packet loss or a complete bank frontend.

The bank then contained 29 methods: calculation 5, rules 3, transactions 9, restart 1, accrual 2, idempotency 6, concurrency 2, HTTP 1. The earlier 1,149-test combined run predates concurrency/HTTP additions and is not the latest full combined result.

## Multiple-participant duplicate checks and latest combined regression

On 2026-10-13, `mvn -pl :petstore-demo,xbod-deposit-demo -am test -Dspp.environment=develop` passed. Surefire XML confirmed lang 364, appframe 620, generator 106, XBOD 29, Petstore 39: **1,158 total**, zero failures/errors/skips. Log: /tmp/xbod-multi-facts-joint.log.

APP facts(path1, path2, ...) compares multiple participants, supports nullable optional facts and remains compatible with single-path metadata. New remote-proxy metadata-query tests ensure these queries do not invoke business adapters. Actual HTTP rejects replay when fee participant facts change while amount stays unchanged; balance remains at the original settlement value. Develop/testing focused checks and develop combined regression passed; KI-R06 closed.

This does not complete overdraft quantum conservation, ordinary Saga compensation policy or all historical recovery entries; limits remain in known-issues-latest.md.

## Repayment posting acceptance for fund counterparties

On 2026-10-13, KI-R01 was revised following user clarification: interest repayment must increase an internal bank account; reducing interest owed is not a substitute for incoming funds. New repaymentPostingsIncludeBankInterestAndPrincipalCounterparts supplies deposit 100, interest owed 10, collection interest 5 and principal owed 20. Actual generated calculation/check/posting services assert customer credit 65, principal repayment 20, interest 10, collection interest 5, and debit/credit totals of 100 each.

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodCalculationTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop` passed all 6 tests with zero failures/errors/skips. Log: /tmp/xbod-repayment-postings.log. This validates allocation/posting calculation; internal-bank quantum fund persistence and reversal remained to implement. Posting assertions are not account-credit acceptance.

## Actual receiving internal accounts and quantum-state combined acceptance

On 2026-10-13, three internal-bank account types gained SPP roles, separate opening scenarios, APP tables, original-settlement references and deposit/reversal updates. After depositing 100: customer balance 65, bank interest 10, collection interest 5, principal recovery 20, cash settlement -100. Interest/principal owed and exposure are quantum states updated in the same operation, not counted again in the fund sum.

Three new XbodRepaymentTest cases cover real internal-account requery/cross-day reversal after mixed repayment; restoration of bank funds and debt after later ordinary-entry persistence failure; and reversal rejection when internal-account funds are insufficient, preserving the unreversed original transaction and not restoring debt. Generic QuantumStateWithConservationTest verifies conservation, rollback of all funds on state-boundary failure, persistence and no duplicate inverse execution on retry. Language tests ensure quantum writes excluded from fund groups remain in complete metadata.

Review of archived sources found that old core JSON extraction omitted decimals without a leading zero. Twenty-two input/expected fields were restored. Corrected vectors exposed insufficient two-decimal PHY capacity; after switching to BigDecimal(25, 6), all original 15 exact-calculation vectors passed. Earlier passes do not establish coverage for those previously omitted decimal branches.

`mvn -pl :petstore-demo,xbod-deposit-demo -am test -Dspp.environment=develop` passed: lang 365, appframe 623, generator 106, XBOD 33, Petstore 39, **1,166 total**, zero failures/errors/skips. Log: /tmp/xbod-internal-accounts-joint.log. Full testing acceptance for final six-decimal configuration was run separately, not inferred from develop.

With final configuration, `mvn -pl :xbod-deposit-demo -am test '-Dtest=Xbod*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 33 tests with zero failures/errors/skips. Log: /tmp/xbod-internal-accounts-testing-final.log. KI-R01 closed; ordinary Saga compensation and historical recovery cases not individually checked retain their own statuses.

## Historical debt branches and actual process termination

Four historical persisted paths were added to XbodRepaymentTest: ordinary debt interest-first, nonperforming loans principal-first, written-off loans principal-first, and decimal repayments 0.005/0.004/0.001. They check actual internal accounts, restored debt, pre-settlement accrual, original fees, eight reversal entries, detail counts and original transaction date. Physical-storage fixtures prepare existing account facts; business handling calls generated SPP only. Both APP tables and current object-repository facts must be prepared; editing tables alone does not establish that queries read new state.

New XbodReversalRuleTest covers closed/revoked/frozen accounts, the SA01116 exception, check precedence, separate advance business control/core reversal checks, WN201 warning and EN134 rejection. All 10 focused develop cases passed.

New abruptExitAfterConfirmedCommitPreservesDepositForReversal halts a child JVM with Runtime.halt(73) after confirmed settlement and recording the original transaction number. Second/third JVMs query, reverse, and inspect actual entries/balances. The first clean run revealed that H2's default 500 ms file-write delay can lose recently confirmed results on immediate exit. After setting WRITE_DELAY=0, both restart tests passed. Default file connections in run-demo.sh and PHY were aligned; custom file URLs should retain synchronous writes. This verifies process exit after confirmed settlement, not unknown commit timing, operating-system power failure or mid-Saga recovery.

Logs: /tmp/xbod-debt-history-develop-v2.log and /tmp/xbod-crash-durability.log. Final clean-build/split-database results are recorded separately.

## Final clean-build results

On 2026-10-13, `mvn -pl :petstore-demo,xbod-deposit-demo -am clean package -Dspp.environment=develop` passed: lang 365, appframe 623, generator 106, XBOD 41, Petstore 39, **1,174 total**, zero failures/errors/skips. Log: /tmp/xbod-clean-joint-final.log. Old generated directories were removed first; domain service catalogues and APP/PHY metadata were regenerated. Generated catalogues no longer depend on old root app-ddl.properties. Production catalogues contain no obsolete ExampleRun/Verification entries.

New XBOD coverage includes four persisted debt branches, three core reversal-rule checks and restart after forced exit following confirmed settlement. This does not automatically complete migration of former technical lifecycle/authorization-pending flows. See the historical entry ledger and KI-R02/KI-R04/KI-R05/KI-R07 for scope/differences.

The final testing command that day, `mvn -pl :xbod-deposit-demo -am test '-Dtest=Xbod*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing`, passed **41 XBOD tests**, zero failures/errors/skips. Log: /tmp/xbod-history-testing-final.log. Split-database bad-debt/write-off/decimal repayment and forced-exit durability checks passed.

## Complete historical calendar-entry acceptance

Thirty original input/expected vectors were individually extracted from archived cboddeposit-99o-calendar-examples.spp into bank-calendar-cases.json. Two original interest-unit vectors remain: 199.999 with unit 100 gives 100; 10 with unit 3 gives 9; both accrue for two days. XbodCalendarTest calls generated scenarios/asserts only; Java does not implement date or interest algorithms. Coverage includes century leap years, ordinary years, year boundaries, same-day intervals, ACT/360365/360360, invalid/reversed dates and unsupported day-count conventions. Error codes match the archive individually.

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodCalendarTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed: 2 JUnit methods, 32 vectors, zero failures/errors/skips. Log: /tmp/xbod-calendar-testing.log. This fills calendar-entry coverage only, not full transaction regression or other historical-entry checks.

## Historical accounting-entry acceptance

XbodPostingRulesTest and posting-rules-cases.json migrate six allocations from archived cboddeposit-99zw-posting-rules-examples.spp: ordinary deposit with fees, ordinary debt interest-first, nonperforming debt principal-first, zero deposit repaying from prior balance, combined deposit/prior-balance repayment, and decimals. Each slot's amount/account/direction/sequence and debit/credit totals are checked. Four original rejection codes cover wrong direction, null account, forged totals and missing allocation. The first two directly compare generated request objects before/after, proving rejected participants remain unchanged.

Current rules require explicit scenario checks, so tests invoke checkPostingRules before calculateDepositPostings. Old calculation-internal checks and try/catch are not restored. Production depositCash classification, accounting checks, posting-rule checks, posting calculation and debit/credit validation all precede the transaction.

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodPostingRulesTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 3 methods, zero failures/errors/skips; /tmp/xbod-posting-rules-testing.log. This is focused historical rule-entry evidence, not acceptance for mid-persistence faults or other recovery entries.

## Failure midway through batch entry insertion

New XbodPartialPostingTest confines technical fault injection to test source. An H2 AFTER INSERT trigger permits three writes and throws SQLException on the fourth; trigger count 4 proves failure occurs mid-batch, not before service execution. Generated deposit/reversal scenarios each run; actual table queries confirm no failed-batch residue, and customer/cash-settlement requeries confirm restored funds. Failed reversal also preserves all eight original deposit entries.

LOCAL rolls back the whole operation; after removing the trigger, retry succeeds with exactly eight target entries. In split-database testing, quantum changes are inverted but prior ordinary steps remain committed, including reversal=true. It therefore must report RECOVERY_REQUIRED, not whole-transaction rollback; tests do not implement business compensation.

Testing command `mvn -pl :xbod-deposit-demo -am test -Dtest=XbodPartialPostingTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` and its develop counterpart each passed 2 tests, zero failures/errors/skips. Logs: /tmp/xbod-partial-posting-testing.log and /tmp/xbod-partial-posting-develop.log. This fault does not equivalently cover old preexisting-primary-key collisions, audit events or exact quantum-journal counts; ledger differences remain explicit.

## Entry-fact hydration and original-settlement participants

New XbodSourceIntegrityTest forges customer balance/debt/closed state/detail count, cash balance, allocation plan and agreement amount at entry. Queries restore zero-opening facts and a real deposit of 100 still succeeds. Reversal input forges original amount 999 and reversed=true; default queries restore the actual original 100 transaction and reverse correctly. Separately replacing cash settlement, interest income, collection-interest income or principal-recovery participants with other opened same-type roles is rejected with ORIGINAL_SETTLEMENT_ACCOUNTS_MISMATCH. The original remains unreversed and customer/substitute-account funds remain unchanged.

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodSourceIntegrityTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop` and the testing counterpart each passed 3 tests, zero failures/errors/skips. Logs: /tmp/xbod-source-integrity-develop.log and /tmp/xbod-source-integrity-testing.log. Historical post-query tampering with prepared work objects differs from entry-fact hydration coverage; the ledger preserves that distinction. This also does not resolve KI-R03 trusted external eligibility sources.

## Different requests concurrently targeting one account

New XbodConcurrencyTest.concurrentSameAccountRejectsStaleFactsAndAllowsFreshRetry synchronizes two threads before prepareDepositAgreement so both read the same old account facts, then deposits 100/200. Exactly one succeeds; the loser has handled=true. Requeries check funds, cash counterparty, ordinary display balance and detail count, retaining only the winner. A fresh scenario call requeries and retries the losing amount; final balance is 300 with two details. No SPP revision field or handwritten Java bank implementation was introduced.

`mvn -pl :xbod-deposit-demo -am test '-Dtest=XbodConcurrencyTest#concurrentSameAccountRejectsStaleFactsAndAllowsFreshRetry' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop` and the testing counterpart each passed 1 test. Both logs show PERSISTENCE_VERSION_CONFLICT: /tmp/xbod-same-account-develop.log and /tmp/xbod-same-account-testing.log. The conflict occurs in ordinary update; this does not prove automatic recovery for all partially committed Saga failures. KI-R04 remains. Controlled concurrency does not replace high-load/real-network tests.

## Corrupt original entries and additional original-cash reversal policy

XbodOriginalPostingIntegrityTest corrupts the same entry in both APP tables and object storage, changing an amount or deleting one entry, then invokes generated reversal. Rejection must preserve customer/settlement funds, original transaction marker and reverse entries. Initial amount-tampering acceptance passed, but deletion failed before reversal because strict repository delete lacked a declared transaction. The fixture switched to direct SQL to create missing facts without weakening production repository constraints.

XbodCashPolicyTest individually migrates eight archived cash-policy vectors: four date/detail/daily-total/accrual expectations; EN097 when accrual 2200 is below reversal amount 2200.001; full refund of original fees 0, 2 and 1.005. Tests invoke explicit generated check/calculation services and verify input participants remain unchanged, without restoring checks inside calculations. Final results follow.

`mvn -pl :xbod-deposit-demo -am test '-Dtest=XbodOriginalPostingIntegrityTest,XbodCashPolicyTest' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 5 methods, zero failures/errors/skips. Log: /tmp/xbod-original-policy-testing.log. Corrupt originals are rejected before updates; all eight historical cash-policy expectations remain.

## Account-control and fee-entry boundaries

New XbodAdmissionBoundaryTest migrates ordinary acceptance and six cash-control rejections: controlling institution, payments-only/no receipts, locked, frozen, revoked and real-estate category. It also checks available balance 1 after depositing 1 into balance 10 with frozen funds 10. Current checkCashControl combines former advance/core account checks; calculation remains separate. Complete deposit scenarios additionally reject missing rates and locked accounts, requery funds/details and directly verify no settlement/entry records.

For cross-branch rate 0.001, fixed fee 1, minimum 2 and maximum 50, a deposit of 600 incurs fee 2. Eight persisted entries verify both fee slots and debit/credit totals 602. New accounts start at zero rather than copying historical fixtures injecting unexplained funds of 100. Initial acceptance found that missing rates recorded PARTICIPANT_NOT_IDENTIFIED but still posted: identify skipped only its enclosing if block, not the scenario. The fix performs queries inside if and calls checkRequiredFeeIdentified at the main level, using participant confirmation to return the original DEPOSIT_FEE_RATE_NOT_FOUND before any transaction. Both depositCash and quoteDepositFee were fixed; pure rate-calculation previews do not require queried identity. Old business request/attemptId and rejection-audit structures were not migrated. Final results follow.

The three focused testing cases then passed, /tmp/xbod-admission-boundary-fixed.log. Full Xbod*Test ran 60 cases: 59 passed; only fee vector 14 failed because copied resources still expected the old generic code while implementation returned the original business code. After correcting only that original expectation, `mvn -pl :xbod-deposit-demo -am test '-Dtest=XbodRuleTest,XbodAdmissionBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed all 6 cases, covering 49/54/16 rule vectors and new rejection/actual-posting checks; /tmp/xbod-fee-regression-final.log. The first full log, /tmp/xbod-expanded-testing.log, retains failure evidence and is not presented as a single all-green build. The other 59 cases were not repeated for that one expected-value correction.

## Agreement dates and reuse of tier results

XbodDatedAgreementTest migrates seven original agreement windows: partial overlap, full coverage, future start, expired, no effective date, same-day activation/cancellation and related cases. It checks clipped dates, overlap days, the ten-day ordinary period and original ordinary/agreement accrual. An eighth invalid-date vector returns EE374 through explicit checkAccrualDates without modifying inputs. Both testing methods passed; /tmp/xbod-dated-agreement-testing.log.

Review of all 17 original tier cases found JSON covered 01, 02 and 04–13, twelve vectors. Missing were case 03 cross-call result reuse and rejection cases 14–17. New XbodAgreementReuseTest executes mode 1 on a generated request, changes inputs, then executes mode 2. Old tier accrual 2000 must clear and new tiers become 0/400/400/400/400. Missing tiers, unordered tiers, negative thresholds and excessive overlap are rejected before calculation with all participants unchanged. Final results follow.

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodAgreementReuseTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 2 methods covering those five missing vectors, zero failures/errors/skips; /tmp/xbod-agreement-reuse-testing.log. Together with the existing twelve, all 17 agreementProductExampleRun rules now have corresponding acceptance.

## Reversal capacity and exposure-restoration policy

XbodReversalCapacityTest migrates cash-shared rejection rules from generic inverse cases 07–10: insufficient available capacity ES020, permitted overdraft but negative base EN249, positive base conflicting with restored principal EN177, and unbalanced original amount. Checks leave every input unchanged. XbodRepaymentTest adds an actual opening fixture where exposure is below repaid principal: principal owed 20/exposure 5; repayment 10 yields exposure 0 and original exposureDelta -5. Under original XBOD cash reversal rules, reversal restores principal owed 20/exposure 10 and clears the principal-recovery account. Differences between original cash policy and generic actual-delta inverse behavior are documented in the design/migration ledger.

`mvn -pl :xbod-deposit-demo -am test '-Dtest=XbodReversalCapacityTest,XbodRepaymentTest#cashReversalKeepsXbodPrincipalBasedExposureRule' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 3 tests, zero failures/errors/skips; /tmp/xbod-reversal-capacity-testing.log. This preserves confirmed cash business semantics, not a change to quantum inverse algorithms for technical failures.

## Persisting settlement notices and handling information

Review of bankWarningCashExampleRun showed original settlement records retain card/passbook notices, admission warnings and handling information, while new agreements previously retained only amounts/reversal components. Four explicit business facts—notices/operator/bankProfile/admission—were added to CashDepositAgreement through SPP prepareDepositAgreement.

After a real deposit of 600, XbodBankNoticeTest uses default queries to recover WN200, WN201, WARNING/F0554, SA01102, non-agent handling and original account series 49927. A second deposit uses different handler/card data; requerying the first confirms its facts remain unchanged. Test Java prepares requests/asserts only. Original transaction/reversal/technical-idempotency checks run alongside it; results follow. This neither resolves KI-R03 trusted external eligibility nor treats a new request as replay of the original.

`mvn -pl :xbod-deposit-demo -am test '-Dtest=XbodBankNoticeTest,XbodTransactionTest,XbodIdempotencyTest' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 16 tests, zero failures/errors/skips; /tmp/xbod-bank-notice-testing.log. Notice retention, existing cash reversal and remote replay all passed.

## Whole-scenario rejection for branch limits and teller context

New full-scenario XbodBankNoticeTest coverage rejects EN134 when an original-branch-only account is handled elsewhere, and DEPOSIT_OPERATOR_CONTEXT_MISMATCH when teller region conflicts with fee-handling context. After both rejections, customer/settlement funds and details are unchanged; CASH_DEPOSITS/CASH_POSTINGS remain empty.

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodBankNoticeTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop` passed 2 tests, zero failures/errors/skips; /tmp/xbod-bank-notice-develop.log. This also confirms notice retention under LOCAL. The preceding section records 16 testing notice/reversal/idempotency passes.

Original assertions for ordinaryCashExample, crossDayCashReversalExample, sameDayCashReversalExample and mode2CashReversalExample were reviewed individually. Ledger entries now map explicit new tests and unmigrated technical structures rather than generic unchecked placeholders. Multiple focused tests are not exaggerated as literal equivalence to old entries.

## Failure-disposition record query gap

Two XbodTransactionTest persistence-fault cases were strengthened to requery findCashFailureRecord, checking disposition status and full failure fields in the associated failed agreement. Testing command `mvn -pl :xbod-deposit-demo -am test '-Dtest=XbodTransactionTest#storageFailureRollsBackFundsAndRunsSourceFailureDisposition+reversalPostingFailureRestoresFundsAndExposesOrdinaryStageOutcome' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` **failed**, with 1 failure and 1 error; /tmp/xbod-persisted-failure-testing.log.

Default association hydration in the deposit case queried an uncommitted agreement and raised PARTICIPANT_ASSOCIATION_NOT_FOUND. The reversal case read the live agreement, losing the original failure code. Assertions were retained rather than weakened. Causes were indiscriminate ParticipantRelationshipQueries recursion and APP snapshot support restricted to scalar role properties. KI-R08 records the proposed fix and incomplete status.

### KI-R08 progress: association snapshots and physical capacity

APP parsing/deployment validation now permit snapshot participant associations/collections for entities, roles and contracts while preserving scalar-mapping constraints. Generated metadata contains table.CASH_FAILURES.snapshotFacts=deposit,reversal. Default association queries skip snapshot subtrees; storage projection no longer reduces snapshots to identity keys. New language/runtime checks passed 6 and 2 cases respectively; an additional projection test awaited execution.

End-to-end acceptance still failed. The v3 query fix removed the missing-agreement exception but exposed identity-only persistence. After v4 saved complete snapshots, H2 reported insufficient DEPOSIT_RESULTINFO_FAILURE_SUPPRESSEDCODES VARCHAR(50) capacity (REVERSAL prefix for reversal), causing failure-record persistence itself to fail. Logs: /tmp/xbod-failure-snapshot-v3.log and /tmp/xbod-failure-snapshot-v4.log; each XBOD run failed 2 of 9 tests. KI-R08 could not close. Participant snapshot representation/system-failure capacity, the new projection test and end-to-end revalidation remained.

### KI-R08: snapshot CLOB and null-overwrite revalidation

The testing combined check in /tmp/xbod-snapshot-clob-testing.log passed: language 6, runtime 3, XBOD transaction 9, total 18. Requerying failure records preserved complete failed-agreement fields. Generated CASH_FAILURES uses deposit/reversal CLOB columns for whole snapshots.

Later direct JDBC acceptance found that setting an existing snapshot to null caused generic column fallback to retrieve an old JdbcClob and JSON-encode it, raising PARTICIPANT_SNAPSHOT_WRITE_FAILED. Snapshot columns now save the participant's complete current value, including null, without fallback to old database objects. `mvn -pl generator -am test '-Dtest=ParticipantSnapshotJdbcTest,DDLGeneratorTest' -Dsurefire.failIfNoSpecifiedTests=false` passed; /tmp/participant-snapshot-jdbc-ddl-fixed.log covers long failure reasons, typed collections, null overwrite and CLOB DDL. The goal remained active, not blocked; broader regression still had to continue.

## Current full regression and historical accrual checks

On 2026-10-13, `mvn -pl :xbod-deposit-demo,petstore-demo -am clean test -Dspp.environment=develop` ran, logged in /tmp/xbod-petstore-current-joint.log. At this checkpoint, lang 366, runtime 627, generator 107 and XBOD 70 had passed; Petstore was still running. The final outcome follows. New XbodAccrualRuleTest uses four original historical expectations for accrual, interest-calendar differences, truncation units and collection-interest days; Java tests do not reimplement calculation.

During that run, QuantumRepositoryFacts snapshot-subtree boundaries were fixed and QuantumParticipantSnapshotTest added. The running full suite did not include these later changes; separate revalidation was required.

The develop clean combined run ultimately passed: lang 366, runtime 627, generator 107, XBOD 70, Petstore 39, **1,209 total**, zero failures/errors/skips, ending 2026-10-13 17:04:34. Testing quantum snapshots, JDBC snapshots, association projection and XBOD transaction/accrual checks then started, /tmp/xbod-quantum-snapshot-final.log, with results pending inspection.

In the first quantum-snapshot follow-up, a fixture incorrectly used seedCurrentValue's three-argument overload, whose first argument is groupId, not an attribute; live accounts were therefore uninitialized. Explicit groupId/attributeId/objectId fixed the fixture. /tmp/xbod-quantum-snapshot-final-v2.log passed: runtime 5 and testing XBOD 10, **15 total**. Quantum projection preserves historical balances in explicit snapshots; quantum queries do not enter snapshot subtrees, while ordinary live associations still hydrate current balances. Nine XBOD transaction tests and four historical accrual vectors passed.

## Full testing regression and original-entry account evidence

`mvn -pl :xbod-deposit-demo -am test '-Dtest=Xbod*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 70 tests with zero failures/errors/skips; /tmp/xbod-current-testing-full.log. This run includes the quantum snapshot-subtree fix.

Subsequent review of storedLedgerEvidenceExampleRun found that rejection of tampered original-entry accounts had not migrated. CashDepositAgreement.postingEvidence, SPP preserveDepositPostingEvidence and APP collection snapshots were added. Explicit checkOriginalDepositPostings compares actual entries with settlement snapshots. Java tests alter accounts through database fault injection only, not business-rule implementation. Focused log: /tmp/xbod-original-evidence-testing.log. The new rule was not counted in the preceding 70-test evidence before completion.

Focused testing original-entry evidence passed: XbodOriginalPostingIntegrityTest 3 and XbodTransactionTest 9, **12 total**. Changed accounts/amounts return ORIGINAL_DEPOSIT_LEDGER_EVIDENCE_MISMATCH; missing entries return INVALID_ORIGINAL_DEPOSIT_POSTINGS. Funds/original reversal marker remain unchanged and no reverse entries persist. Develop acceptance also added XbodRepaymentTest; /tmp/xbod-original-evidence-develop.log.

Focused develop acceptance passed: original-entry integrity 3, transactions 9, repayments 8, **20 total**, zero failures/errors/skips. It covers LOCAL rollback, same-day/cross-day/Mode2 cash reversal, actual internal-account repayment and historical decimal repayment; /tmp/xbod-original-evidence-develop.log.

## Failure of failure disposition itself

New XbodFailureDispositionTest adds an H2 trigger rejecting CASH_FAILURES insertion alongside the original business-service fault. Both cases initially failed: primary cause/rollback were correct but ResultInfo.failure.suppressedCodes was empty; /tmp/xbod-secondary-failure-before.log. Generic TransactionFailureHandling now updates returned failure facts after disposition exceptions while still throwing the primary exception. Failure to update metadata also cannot replace that primary exception. Existing framework tests gained return-info assertions.

`mvn -pl :xbod-deposit-demo -am test '-Dtest=TransactionFailureHandlingTest,XbodFailureDispositionTest' -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=develop` passed framework 7/bank 2, **9 total**; /tmp/xbod-secondary-failure-develop.log. The testing counterpart was logged in /tmp/xbod-secondary-failure-testing.log pending inspection.

All 9 testing framework/bank cases passed. After adding explicit recovery-status assertions, both full-scenario testing cases passed again; /tmp/xbod-secondary-outcome-testing.log. Secondary persistence failure neither replaces original RECOVERY_REQUIRED nor sets handled=true. LOCAL revalidation uses /tmp/xbod-secondary-outcome-develop.log.

Both LOCAL recovery-status cases passed: original ROLLED_BACK remains, secondary failure is visible, handled=false. This differs from testing RECOVERY_REQUIRED. All focused runs in this round had zero failures/errors/skips.

## Four-stage LOCAL commit-fault acceptance

New XbodCommitOutcomeTest injects technical faults into generated depositCash at JDBC beforeCommit, commitBefore, commitAfter and afterCommit. Expected outcomes are ROLLED_BACK, UNKNOWN, UNKNOWN and COMMITTED. Actual database queries establish no settlement/entries for the first two and one settlement/eight entries for the last two; exceptions alone do not determine commitment. This applies only to LOCAL; multistage testing coordination requires separate acceptance. Initial log: /tmp/xbod-commit-outcome.log, pending inspection at that checkpoint.

All eight deposit/reversal commit-fault combinations passed: two XbodCommitOutcomeTest methods, each containing four isolated-database cases, zero failures/errors/skips; /tmp/xbod-commit-outcome-both.log. Independent default queries/physical counts prove absent current agreements/entries in the first two stages and one agreement/eight entries in the latter two; funds match outcomes. This does not establish automatic unknown-commit recovery, remote replay disambiguation or split-database commit recovery.

## Daily-total lifecycle and invalid accrual configuration

`mvn -pl :xbod-deposit-demo -am test -Dtest=XbodDailyCashTest -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing` passed 2 cases with zero failures/errors/skips; /tmp/xbod-daily-testing.log. Three actual deposits/default requeries verify the warning as totals grow 300→600, next-day reset to 100, cumulative details and accrual 600. normalUnit=0 is rejected by the complete scenario with empty settlement/entry tables. Old unexplained opening balances were not copied.

The three historical report setups compile test evidence and are not restored as production scenarios under the rule moving tests out of SPP. Business-by-business evidence, unfinished authorization-resumption/persisted-audit scope and quantum-model differences are recorded in legacy-entry-migration. Separate focused checks are not claimed equivalent field-for-field to old reports.

## Prepared reversal with an intervening deposit

New XbodStaleReversalTest uses a test listener on APP technical invocation logs to pause a queried/checked reversal before prepareCashReversalPostings. It executes a real deposit of 5, then releases reversal. Stale ordinary facts must conflict, funds restore to 105, and the original remains unreversed. Fresh-query retry succeeds, leaving balance 5 and all eight intervening-deposit entries. Tests only schedule technical execution and call generated scenarios, without Java bank business. Initial testing log: /tmp/xbod-stale-reversal-testing.log, pending inspection.

XbodStaleReversalTest passed in testing/develop, 1 case each; /tmp/xbod-stale-reversal-testing.log and /tmp/xbod-stale-reversal-develop.log. Another generated deposit is precisely scheduled after query/before update, proving stale reversal rejection and fresh-query retry without sleep or handwritten business updates. This covers stale account facts, not concurrent changes to the original agreement itself.

## Original agreement changed after preparation

XbodStaleReversalTest adds a technical fault: after reversal queries complete, alter persisted original agreement fee and repository revision, then release execution. Initial testing confirmed PERSISTENCE_VERSION_CONFLICT prevents overwrite, but earlier ordinary Saga stages had committed, so the call throws rather than returning handled=true. The initial assumption of full recovery like the intervening-account case was invalid; /tmp/xbod-changed-receipt-testing.log.

Revised acceptance retains strict facts: fee=1 is not overwritten, the original remains unreversed, and funds restore to 100. Testing must report RECOVERY_REQUIRED/RECONCILIATION_REQUIRED/handled=false; direct retry cannot conceal ordinary residue. LOCAL requires complete rollback and successful requery/retry after fixture restoration. This is KI-R04, not a production change pretending rollback succeeded.

Both original-agreement/account-staleness cases passed in testing/develop, 2 each, zero failures/errors/skips; /tmp/xbod-changed-receipt-testing-v2.log and /tmp/xbod-changed-receipt-develop.log. LOCAL includes successful retry after restoring fixtures; testing explicitly retains compensation-required outcomes.

## Current reversal eligibility facts

New XbodCurrentEligibilityTest changes stored AccountEligibility.status to FROZEN after settlement, then calls generated reverseCash with ACTIVE input, expecting EN111. SA01116 cannot bypass the current cash-path restriction and must yield UNSUPPORTED_CASH_REVERSAL_PATH. Independent queries check funds, frozen status, original marker and absence of reversal records. This does not turn ordinary property reads into lifetime state locks or promise rejection of already-checked in-flight transactions. Testing log: /tmp/xbod-current-eligibility.log, pending inspection.

Testing XbodCurrentEligibilityTest passed 1 case, zero failures/errors/skips; /tmp/xbod-current-eligibility.log. Current eligibility comes from default database queries and cannot be overwritten by ACTIVE input. A core frozen-account exception does not expand the cash scenario's business scope.

## Fault after nonzero accrual persistence

XbodAccrualFailureTest first deposits 100, then requests 50 next day and injects failure before insertCashDepositAgreement, after accrual persistence. Focused testing passed: funds restore to 100/-100, committed accrual 100/next-day date remain, and RECOVERY_REQUIRED/handled=false is returned. Failure disposition is queryable; only the original agreement/eight entries remain. Ordinary accrual restoration is not portrayed as part of quantum inverse execution. Log: /tmp/xbod-nonzero-accrual-testing.log. LOCAL retry awaited /tmp/xbod-nonzero-accrual-develop.log.

Focused LOCAL acceptance passed 1 test, zero failures/errors/skips; /tmp/xbod-nonzero-accrual-develop.log. After failure, accrual stays zero/date stays original and funds are 100/-100. Retry yields balance 150/accrual 100. Actual persisted outcomes support both configurations' differing assertions.

## Concurrent same-serial reversal and persisted original-agreement integrity

XbodIdempotencyTest adds concurrent reverseCash for one remote caller/serial, using an actual deposit as original settlement. Final reversal voucher/result must match, funds become zero, and only one reversal agreement/eight entries remain. While RUNNING, current REQUEST_IN_PROGRESS is permitted; after original completion the same result is retrieved. No idempotency is added to SPP business. Full testing class passed 7 cases; /tmp/xbod-concurrent-reversal-idempotency.log.

New XbodOriginalAgreementIntegrityTest covers missing original voucher, persisted amount changed to 999, and original account changed to another opened account. Both physical tables/object storage are altered to check conservation/ownership validation after default queries. Funds remain unchanged and no reversal agreement/entries persist. Testing log: /tmp/xbod-original-agreement-integrity.log, pending inspection.

Testing original-agreement integrity passed 3 cases, zero failures/errors/skips. Missing agreement returns PARTICIPANT_NOT_IDENTIFIED; corrupted amount returns ORIGINAL_DEPOSIT_MOVEMENT_NOT_BALANCED; corrupted ownership returns ORIGINAL_DEPOSIT_ACCOUNT_MISMATCH. Log: /tmp/xbod-original-agreement-integrity.log.

## Fault before reversal agreement insertion

XbodTransactionTest adds technical failure before insertCashReversalAgreement, sharing fund/original-marker/failure-snapshot requery assertions with existing reverse-entry faults. Reversal tables must remain empty. LOCAL fully rolls back and permits a new-request retry; testing inverts funds but prior ordinary facts remain committed, requiring RECOVERY_REQUIRED. Full testing class log: /tmp/xbod-reversal-receipt-fault.log, pending inspection.

Completed acceptance: testing XbodTransactionTest passed all 10 cases and the corresponding LOCAL method passed 1; /tmp/xbod-reversal-receipt-fault.log and /tmp/xbod-reversal-receipt-local.log. LOCAL verifies successful new-request retry; testing retains the committed original marker despite inverse funds and explicitly reports RECOVERY_REQUIRED without immediate retry.

Final static checks: no handwritten Java under src/main; production SPP contains no try/catch/throw, Verification, context or transaction quantum/saga. PHY quantumStore is deployment configuration, not SPP business transaction syntax. git diff --check passed. README/design pages removed obsolete regression counts and pending-fund-model descriptions; historical logs remain in verification documents.

# Additional clean regression (2026-10-13)

This clean develop combined run failed in appframe: lang 366 passed; appframe had 7 failures/3 errors among 628 cases; later modules did not execute. Log: /tmp/xbod-final-joint-develop.log. New QuantumRepositoryFacts.snapshotProperties unconditionally accessed a nullable APP table catalogue, breaking quantum-opening recovery without a catalogue.

The fix returns empty snapshot configuration when no catalogue exists. QuantumOpeningRecoveryTest, DatabaseInitializationTest and QuantumParticipantSnapshotTest passed 12 cases, /tmp/xbod-snapshot-no-catalog.log, covering no-catalogue recovery and catalogue-backed historical snapshots. A full clean combined rerun started, /tmp/xbod-final-joint-develop-v2.log, but was unfinished at that checkpoint.

## Clean combined current-source regression passed (2026-10-13 18:36)

Command: `mvn -pl :xbod-deposit-demo,petstore-demo -am clean test -Dspp.environment=develop`.
Log: /tmp/xbod-final-joint-develop-v2.log. BUILD SUCCESS in 9 minutes 6 seconds: lang 366, appframe 628, generator 107, XBOD 86, Petstore 39, **1,226 total**, zero failures/errors/skips. This includes no-APP-catalogue compatibility, historical snapshots, receiving internal accounts, original-entry evidence and all fault/concurrency cases added by that point.

Full testing XBOD verification then started, /tmp/xbod-final-testing.log; no unfinished result was claimed. Independent preview and other explicitly unmigrated scope remain governed by completion-audit/known issues.

## Full testing regression passed (2026-10-13 18:41)

Command: `mvn -pl :xbod-deposit-demo -am test -Dtest=Xbod*Test -Dsurefire.failIfNoSpecifiedTests=false -Dspp.environment=testing`.
Log: /tmp/xbod-final-testing.log. 86 tests, zero failures/errors, **2 skipped**: the two LOCAL-only XbodCommitOutcomeTest methods. This predates previewCashReversal and does not establish preview acceptance; preview was separately generated/tested.

## Independent cash-reversal preview checks (2026-10-13)

New SPP previewCashReversal reuses formal reversal's queries/checks/calculations without transaction, quantum updates or ordinary persistence. XbodReversalPreviewTest passed 2 cases each in develop/testing; /tmp/xbod-preview-develop.log and /tmp/xbod-preview-testing.log. After deposits of 100 then 5, preview produces eight balanced candidate entries while database balance stays 105, original stays unreversed, and reversal agreement/entry tables remain empty. Formal reversal then preserves the later 5. Missing originals are rejected without persistence side effects. This does not cover historical generic partial-fee-refund candidates.

## Packaging and script startup acceptance

`mvn -pl :xbod-deposit-demo -am package -DskipTests -Dspp.environment=develop` succeeded; /tmp/xbod-final-package.log. Actual run-demo.sh web startup skipped duplicate build, used loopback port 18089 and an isolated H2 in-memory database. POST openDepositAccount created a zero-funded account and returned opening participants. Initially omitting entire participants was technically rejected; providing all six declared participant objects succeeded. README supplies complete JSON. This does not claim omitted participants are automatically constructed. The temporary server was stopped with no background service left.
