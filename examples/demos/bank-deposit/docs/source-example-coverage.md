> Historical document: this records pre-refactoring implementation/acceptance, not current source/test results. See [current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Source-language example coverage

This ledger tracks responsibilities of old handwritten acceptance tests. One passing source example does not mean every old branch migrated. APP declares entries, SPP provides data/transactions/assertions, and PHY binds actual repositories/quantum guarantees.

> Evidence correction: four entries containing empty catch generated empty methods before the fix—replay, missingFee, pendingCurrentAccount, crossDayCashReversal. Their earlier passing claims were withdrawn. Corrected verify actually executed all 17 entries successfully. Evidence below uses that rerun; see source-only-refactoring.md.

## Actual deposit lifecycle

| Old NormalCashDepositTest responsibility | Source entry | Evidence and remaining scope |
| --- | --- | --- |
| Ordinary deposit, fee, balance, daily totals, entries | ordinaryCashExample / crossBranchFeeCashExample | Both actually passed; same-branch exemption, stored cross-branch minimum fee, account version/eight entries checked; all pre-settlement fields still needed individual review |
| Ordinary/bad/written-off debt priority | normalDebtCashExample / badDebtCashExample / writtenOffCashExample | Three passed; independent expectations check priorities, remaining debt, actual balances/entries |
| Pending without updates, policy recheck, authorized commit | authorizationCashExample | Passed; audits reread after three attempts, two waits have no settlement/entries, only final attempt updates |
| Account changed while authorization pending | pendingCurrentAccountCashExample | Passed; later transaction commits first, resumed original rejects under latest balance, preserves later result and saves failure audit |
| Retry original after later updates/stricter next-day policy | replayCashExample | Passed; original result/current state separately checked, no repeated entries, Replayed audit, changed amount/identity reuse rejected; cross-process and numeric-text variants initially pending |
| Daily reset/current-total warnings | bankAdmissionRulesExampleRun / dailyCashLifecycleExampleRun | 101+400 warns; next-day old 900 resets to 400 without warning; actual 300/300/next-day 100 checks settlements/current-original account facts/entries/audit |
| Business rejection/missing rate retains identity only | missingFeeCashExample / replayCashExample | Missing-rate/identity-reuse rejections passed with unchanged account/no new settlement/entries and independent audit; full identity queries/other rejection branches pending |
| Pending identity across instances; changed amount rejection | pendingRestartSetupExampleRun → pendingRestartResumeExampleRun | Two independent JVMs passed: persisted 1500 identity rejects 1501; authorized original then alternate-format replay retains v1; post-retirement clean verify regenerated/executed |
| Equivalent concurrent requests settle once | concurrentCashLifecycleExampleRun | SPP parallel/APP barrier makes both see no settlement; actual 600.00/600 race, one definite rollback then original query/replay, one account/entry update, Committed plus Replayed audit; full verify passed |

## Remaining complete migration at this checkpoint

Cash-reversal boundaries, calendars/accrual, agreement windows, eligibility, numeric boundaries, real faults/conflicts/concurrency, definite/unknown outcomes, Saga recovery, and declarative reports continued. At the recorded 61-entry checkpoint, 14 main/seven test Java files remained to remove. Their source acceptance obligations had to pass first; old Java regression success did not meet source-only requirements.

Maven verify executes source entries automatically. Report JUnit and entry counts separately: one entry may execute multiple transactions/assertion groups; entry counts cannot be added to JUnit methods or treated as business coverage percentages.

## Complete cash reversal

crossDayCashReversalExample actually passed using source deposit/reversal lifecycles: original settlement with bank qualification/accrual/stored rates, next-day later settlement, joint reversal commit, account/original/reversal rereads, original-fee refund, eight inverse entries, unique association, same-reversal retry, and rejection audit for another identity reversing again. Actual balance 100 → 700 → 800 → 200; original settlement and later accrual 800 retained.

99k added sameDayCashReversalExample/mode2CashReversalExample, checking same-day institution-limit restoration marker and overnight forced dates. Both passed. Changing the pre-original date to 20261006 and rerunning twelve entries proved actual same-day date restoration versus Mode2 current-day selection. Debt/fractional reversals, concurrency on unreversed associations, mid-reversal faults, and uncertain commits were then pending.

## Debt, fractions, and real version conflicts

normalDebtCashReversalExample, badDebtCashReversalExample, and fractionalCashReversalExample passed checks of original principal/interest/collection-interest/exposure restoration, fee refund, retained current accrual, inverse entries, version, association, and audit. Fractional expectations follow agreed exact business semantics, not bit-exact legacy PIC truncation.

cashVersionConflictExampleRun prepared a candidate, executed another deposit, then submitted stale preparation. Submission wrote settlement first and failed revision protection second. Actual queries proved candidate settlement rolled back, no entry/link residue, and account retained the intervening deposit. Failure audit used an independently declared SPP quantum transaction. Additional successful deposit after failure passed at balance 950/version 3/daily total 850, with consistent quantum balance in actual joint commit. This was controlled interleaving, not multithreaded/fault-injected/unknown-commit evidence.

## Fault after entry writes

cashWriteFailureExampleRun declares a one-shot APP fault after successful storeCashPostingRecords; the runner must prove it was hit. Independent SPP queries show account/settlement/eight entries/association rolled back while failure audit persists. Same business identity/new attempt then commits: balance 700/version 1, debit/credit totals 600. This does not prove commit-point faults, unknown outcomes, concurrency, or reversal failures.

## Calendar and interest units

bankCalendarExampleRun passed 30 date expectations—eight ACT, seven each for two 360 conventions, eight rejections—and two explicit quantization cases. Sources/inputs/expectations reside in 99o and invoke generated date services. This replaced all four BankCalendarTest methods and deleted the file. Clean verify passed 1,011 regressions/18 entries and found no old bytecode. Accrual/agreement/other rules still needed migration.

## Deposit kernel and five-tier agreement accrual

depositKernelExampleRun passed sixteen independent cases: fifteen nine-field plans and one negative rejection, with eight-field account snapshots checked per case. PHY/javap separately confirmed BigDecimal binding. agreementProductExampleRun passed seventeen mode/threshold/unit/disabled/rejection cases, checking five result fields and per-slot products; rejection preserves all products at 999. Clean rerun strengthened mode-1→mode-2 reuse of the same five slots, proving the actual 2000 product was cleared.

These replaced DepositKernelTest's sixteen parameter groups and AgreementProductTest's six methods; both Java files were deleted. Fixtures split by repayment/boundary and agreement mode/precision/rejection, with sequencing/assertions retained in entries. Post-deletion clean verify passed 989 regressions/20 entries; three retired test classes were absent.

## Agreement windows, controls, 49 admission groups

datedAgreementExampleRun passed eight facts: seven effective/empty intersections, both accrual classes, actual clipped dates; invalid effective date EE374 before product generation. cashAccountControlExampleRun passed six rejections, normal account, and distinction between freeze status/amount. bankAdmissionRulesExampleRun passed 49 inputs replacing all twelve BankRulesTest methods: date/amount, temporary accounts, opening bounds/disabled settings, balance ceiling, warnings/daily reset, strict B/A authorization thresholds, policy rechecks, six transaction/two channel exceptions, USD thresholds, quantization, control precedence.

DatedAgreementTest five methods, CashAccountControlTest three, and BankRulesTest twelve were deleted. Source/expectations are 99r/99s/99t, with 49-group setup/checks in 99ta/b/c. Clean verify passed 969 regressions/23 entries; six retired classes absent. Bank queries, concurrency, commit faults/unknown outcomes, reports, and other tests remained.

## Bank qualification, original notices, cash policy

bankQualificationRulesExampleRun passed 54 independent facts: institution relationships/null errors, restricted-subtype scope, teller family/number/permissions, transaction/business exceptions, and separate loss warnings. bankWarningCashExampleRun jointly committed real fees/accrual/entries, then read original WN200/WN201 and SA01102 teller facts. Replay after changing agent-signing status still returned original notices/teller, retaining version 1/balance 700/eight entries with totals 602 and Replayed audit. bankRejectedCashExampleRun checked EN134/teller-context mismatch: both no settlement/entries, version 0/balance 100, independent Rejected audit.

xbodCashPolicyExampleRun passed four metadata plans, insufficient accrual, and original fees 0/2/1.005. Post-deletion clean strengthened independent refund constants and unchanged original fees. Plans check dates/detail counts/daily cash/ordinary-actual accrual/institution-limit marker/input immutability. A marker is not a complete institution-limit ledger update.

These replaced DepositBankQualificationTest eight/XbodCashReversalPolicyTest five methods. Clean verify passed 956 regressions/27 entries; eight retired classes absent. Fourteen main/13 test Java files remained, including cross-process/full concurrency/commit faults/unknown outcomes/remaining rules/reports.

## Stored rates and current-state financial inverses

depositFeeRulesExampleRun passed sixteen expectations using unique customer/fee-code repository records seeded inside explicit SPP quantum transactions. Full prepareDepositFee checks region before institution, percentage/discount/half-up/fixed fee/bounds including zero maximum, lookup-free Mobile, unsupported account kind, absent rate, wrong code, and negative amount. Wrong-code rejection also preserves prior fee.

financialInverseRulesExampleRun passed ten original/current-account cases. Actual deposit kernel/description services record original components; seven inverse fields compare against independent constants, eight current-account fields against copied snapshots. Coverage includes later spending/deposits, freeze/authorization, original/current loan-state changes, clamped exposure delta, fractional repayment, capacity/result eligibility, and forged originals. Early rejection must precede plan creation.

DepositFeeTest five/DepositReversalAmountsTest seven methods were replaced/deleted. Clean verify passed 944 regressions/29 entries; ten retired classes absent. Posting tests still served as fixtures for whole-deposit/repository tests and required coordinated retirement. Fourteen main/eleven test Java files remained.

## Accrual responsibility migration

Six DepositAccrualTest methods map to four 99z/99za APP entries: window accrual/input immutability; separate 360/ACT; units/collection-interest days; actual original periods/idempotency/same-day second settlement; invalid-unit whole-operation rejection; post-account/accrual fault with independent rollback audit/original-identity retry. APP fault before storeCashPostingRecords occurs after account/accrual updates but before entries. Generic framework outcome tests cover old driver-fault lifecycle; source cases check bank data.

Initially two rule/rejection entries and old 29 passed; two actual-settlement entries failed additional entry expectations. Independent funding facts corrected the expectations before rerun. The old test remained until clean evidence.

Corrected full verify passed 33 entries, then DepositAccrualTest was deleted and clean verification started. 99zb added seven paired deposit/reversal audit policies: unreported, definite rollback, unknown, post-settlement return failure, replay-audit failure, precommit rejection, and no work context. These are classification fixtures, not physical evidence. Generic tests measure actual commit/rollback/cleanup; bank commit-point/recovery source checks remained pending.

Final clean passed 951 JUnit/34 APP entries; eleven retired classes absent. Four accrual entries replaced six methods; seven audit pairs passed separately. Fourteen main/ten test files remained. Initial 50-versus-170 entry error remains in history: final independent slots use cash 50, prior-deposit reduction 120, principal 100/interest 30/collection interest 40.

## Core reversal eligibility and original preparation

99ca supplies original/current queries, financial/core-eligibility candidates, and explicit-refund inverse entries, with no implicit transaction/submission. 99zc/99zd began replacing four DepositReversalEligibilityTest responsibilities: closed/canceled/frozen priority, SA01116 exception, document warnings without ordinary deposit-range gating, separate front institution controls, actual later freeze/loss/cancellation, input copies, and no changes to account/audit/entries/association.

Initial generation failed cross-file field-order requirements; source ordering was fixed but full execution was pending at this checkpoint. Eight financial/entry-preparation responsibilities were not yet all migrated, so these two eligibility entries did not justify deleting DepositReversalPreparationTest.


Core eligibility full verify then passed both new entries plus the original 34. Candidates used the current frozen account at version 3/balance-accrual 800; reversing original 600 produced candidate 200 without submission. Copied SA01116/capacity 1000 resisted later caller mutation; current cancellation advanced version 4 and rejected EN113. Original eight entries with debit/credit totals 602, one Committed audit, and version-0 unreversed association remained. Four old eligibility tests were deleted; clean verification followed. Eight preparation obligations still required real missing-entry evidence via generic APP deletion, not fake query returns.

Final core clean: `mvn -pl :xbod-deposit-demo -am clean verify`, 947 JUnit—lang 290, appframe 494, generator 101, old demo 62—with no skips, plus all 36 entries. Twelve retired test classes/nested classes absent. Cases regenerated from empty target. Fourteen main/nine test files remained; preparation, actual deletion/corruption, commit outcomes, concurrency, Saga/compensation, and reports continued. No bank query/eligibility logic moved into public Java and no demo Java was added.

## Original settlement and actual-entry preparation

Eight 99ze/99zf/99zg entries covered old preparation: original financial inverse at current version 3/balance 2300/accrual 2200 without commit and with copied capacity; explicit refunds 0/1.25/2 preserving directions/accounts/six components; actual ordinary/bad/fractional settlements; unknown/rejected originals and ES020; absent/negative/excessive refund; settled basis corrupted to 999; account corrupted to DEMO.WRONG_CASH; real APP delete of entry 8 with query proving seven remain before rejection. Fixture seeding/corruption uses explicit SPP quantum, not bank Java or replaced query returns.

Generic String + String/deletion were added, with separate framework coverage for exact-decimal native deletion keys. Old DepositReversalPreparationTest stayed until execution passed. After aligning dates with the old fixture, all 44 entries passed, including debt/fractional/preparation/refund/rejection/settlement-ledger evidence. Its eight-method Java file was removed. Actual original-account tampering requiring INVALID_ORIGINAL_DEPOSIT_ACCOUNT was added before clean verification.

Final preparation clean: 948 JUnit—lang 292, appframe 501, generator 101, old demo 54—with zero failures/errors/skips, plus 44 entries. Account tampering rejected while preserving account/accrual/audit/original entries. Thirteen retired classes/nested classes absent. Fourteen main/eight test files remained; restart/concurrency/commit outcomes/Saga/reports still pending.

## Daily totals and synchronized concurrency

99zh dailyCashLifecycleExampleRun uses three settlements to prove same-day warnings/next-day reset, checking original/settled/account facts, eight entries, and audit each time. 99zi concurrentCashLifecycleExampleRun uses independent lifecycles, same business identity, exact 600.00/600, SPP parallel/APP barrier after both read no settlement. Both must return version 1/balance 700 with one actual account update, eight entries, and Committed plus Replayed audit. Before execution, the clean baseline remained 44 entries; old nine-method NormalCashDepositTest stayed and cross-process pending identity lacked evidence.

Final incremental verify passed 952 JUnit—lang 293, appframe 504, generator 101, old demo 54—with zero failures/errors/skips, plus 46 entries. Daily progression Ready→Warning→Ready and persisted totals passed. Concurrency proved exact numeric variants, actual barrier arrival, exactly one PERSISTENCE_ALREADY_EXISTS/ROLLED_BACK, lookup returning the same settlement, one account/entry update, and one Committed/one Replayed audit. Normal business does not enable barriers; generic semantics/generation/runtime had framework coverage. Fourteen main/eight tests remained; cross-instance pending identity prevented retirement of NormalCashDepositTest.

Cross-JVM pending-identity clean then passed 947 JUnit—lang 294, appframe 507, generator 101, old demo 45—with no failures/errors/skips, plus 48 entries. Independent setup/resume logs: target/spp-examples/restart-5696792022188799453/stage-0.log and stage-1.log. Three authorization-history records (two pending, one settled), rejected/missing-rate identity rereads, original fees, definite rollback of unsettled collision, and preserved primary exception all ran from clean generation. Fourteen retired classes/nested classes absent. Remaining main/test inventory: 14/7.

## Deposit JDBC commit points and query recovery

All four 99zl entries—beforeCommitCashFaultExampleRun, commitBeforeCashFaultExampleRun, commitAfterCashFaultExampleRun, afterCommitCashFaultExampleRun—passed full incremental verify. The anchor is final storeCashAudit in the main quantum transaction. Expected outcomes: ROLLED_BACK/UNKNOWN/UNKNOWN/COMMITTED. Real account/settlement/eight entries/unique association/attempt history distinguish the two outwardly identical UNKNOWN errors as physically uncommitted versus committed. SPP then retries by stable identity and replays exact 600.00, with only one account update.

APP audit key (attemptId,event) allows two events for a committed operation with lost confirmation/failed response. Collection queries check multiple events, never arbitrary scalar selection. Confirmed post-commit return failure does not invoke rollback. Every fault must actually fire.

Incremental verify: 954 JUnit—lang 295, appframe 513, generator 101, old demo 45—zero failures/errors/skips, plus 52 root entries. This was not clean; prior clean 947/48 remains its baseline. Old tests stayed; reversal faults/concurrency/audit failures/quantum logs/Saga/reports still needed migration.

## Reversal faults and cross-JVM replay

Six 99zm/99zma/99zmb roots passed: beforeCommitReversalFaultExampleRun, commitBeforeReversalFaultExampleRun, commitAfterReversalFaultExampleRun, afterCommitReversalFaultExampleRun, recordReversalFaultExampleRun, linkReversalFaultExampleRun. Queries distinguish ROLLED_BACK/UNKNOWN/UNKNOWN/COMMITTED and definite rollback after first reversal-record/link writes. Uncommitted preserves original/later settlements and v2/800; committed yields v3/200, accrual 800, refund 2, link v1, eight entries with debit/credit totals 602. Recovery/next-day replay never creates a second reversal; original/later entry side/subject/amount remain consistent.

99zn setup/resume also passed in two independent JVMs sharing a PHY file store. Empty participants read settlements/entries without reseeding. A new 25 deposit in the second JVM yields v4/225/accrual 1000; reversal replay returns original v3/200 without changing current state. Changed original transaction or overnightMode2 under the same identity rejects DEPOSIT_REQUEST_ID_REUSED; another reversal identity rejects ORIGINAL_DEPOSIT_ALREADY_REVERSED. Four original-reversal audits (settled, replayed, two identity rejections) and separate duplicate-reversal rejection are actual repository facts.

Incremental verify passed 954 JUnit/59 roots with no failures/errors/skips; compound restart stages have separate PASS records at target/spp-examples/restart-4430443081500016374/stage-0.log and stage-1.log. Java inventory remained 14/7. Actual concurrency, changed/stale preparation, partial batches/audit unavailability, quantum persistence, Saga, reports remained.

## Actual concurrent reversal

99zo/99zoa/99zob sameIdentityConcurrentReversalExampleRun and differentIdentityConcurrentReversalExampleRun passed full verify. APP barriers synchronize both after preparation; source assertions prove exactly one physical conflict with definite rollback. Same identity returns one v3/200 settlement, unique link/eight entries, one settled/one replay audit. Different identities yield one success and ORIGINAL_DEPOSIT_ALREADY_REVERSED, no failed-candidate residue, eight total entries with debit/credit totals 602, one settled/one rejected audit. Original/later deposits, entries, and accrual remain.

Recovery queries actual settlement/link inside catch, preserving the original error if lookup fails. Identity checks, replay, and evidence-based business rejection occur after catch, avoiding generic original-error preservation treating the new rejection as secondary. Without settlement/reversed link, rethrow the original failure; conflicts alone prove no business result.

Incremental verify passed 954 JUnit/61 roots, no failures/errors/skips, with old faults/two cross-JVM schemes intact. Java remained 14/7. Tiny-value physical storage, partial batches/audit failures, Saga, reports were pending.

## Exact quantum storage and native journal queries

99zp quantumEvidenceExampleRun creates account net 100/counterparty zero, commits 1e-25, then queries actual journal/current-value tables through APP. Independent assertions require 100.0000000000000000000000001 and -1e-25, two exact COMMITTED before/delta/after records, summed delta zero. PHY selects BigDecimal and jdbcQuantum text; APP numeric-to-text mapping does not change business numeric semantics.

Incremental verify passed 960 JUnit—lang 296, appframe 517, generator 102, old demo 45—with no failures/errors/skips, plus 62 roots/two cross-JVM stages. Not clean; Java 14/7. Isolation, failed-write log preservation, stale-value source responsibilities still prevented deleting CashQuantumLedgerTest.

## Quantum lifecycle handover

All three 99zpa entries passed. quantumIsolationExampleRun writes independent A/B deltas 1e-25/25, queries both logs/current values, and retains exact A. quantumRollbackExampleRun rejects business after an update inside explicit quantum: ROLLED_BACK, no persisted logs/values, then legitimate 110/-10 commit. quantumStaleValueExampleRun settles 100→110, then submits old plan 100→120/delta20. Locking current 110 gives 130, mismatching plan 120; source rejects QUANTUM_CASH_BALANCE_MISMATCH/ROLLED_BACK. Queries retain 110/-10/two logs; valid delta5 yields 115/-15/four logs. Runtime still applies deltas to latest state, never replacement writes.

Incremental verify passed 960 JUnit/65 roots with original 62 intact. Four quantum entries replaced three CashQuantumLedgerTest methods; file removed, Java inventory 14/6, expected remaining old JUnit 42. Source cases use visible explicit boundaries/persistent outcomes/valid post-failure commits rather than old handwritten JDBC outer-transaction/internal-scope assertions. Caller-owned connection rollback/scope cleanup remains generic framework coverage, not new demo Java. Clean acceptance was not claimed before the next run.

Final quantum clean: lang 296/appframe 517/generator 102/old demo 42 = 957 JUnit, no failures/errors/skips; all 65 entries. Fifteen retired classes/nested classes absent. Java 14/6 remained. This baseline did not include subsequent consecutive-fault/suppressed-code extensions. Log: `/tmp/spp-quantum-lifecycle-clean-validation.log`.

## Primary and failure audit unavailable together

Four 99zq/99zr entries passed:
- depositAuditUnavailableExampleRun injects two consecutive faults before primary/failure audits: primary error/ROLLED_BACK, exactly one secondary error, no account/settlement/entry residue; same identity later succeeds.
- rejectedDepositAuditUnavailableExampleRun preserves DEPOSIT_FEE_RATE_NOT_FOUND plus audit failure and unchanged account.
- reversalAuditUnavailableExampleRun enables dual audit faults only after original/later deposits: complete reversal rolls back, both deposits remain, retry/replay produces one reversal.
- replayedReversalAuditUnavailableExampleRun enables replay/failure-audit faults after settled reversal: known settlement remains, six actual quantum logs do not increase, valid retry adds only one replay audit.

Secondary errors come from the original exception's direct suppressed chain, independently counted in source facts without replacing the primary error. Incremental verify: lang 298/appframe 522/generator 102/old demo 42 = 964 JUnit, no failures/errors/skips, all 69 entries, original 65 intact. Not clean; Java 14/6 unchanged. Bank audit faults no longer used table deletion/handwritten JDBC injectors. Changed receipts/stale candidates/partial batches/Saga/reports remained.

Partial-batch incremental verify then passed the same 964 JUnit and 71 roots. 99zs/99zt actually collide at slot4, prove prior inserts roll back while fixture remains, remove the fixture, and succeed with same identity without duplicate quantum logs. Log: `/tmp/spp-partial-posting-source-validation.log`. Not clean, Java 14/6; original-fact recheck, preparation/submission separation, Saga/reports still pending.

Original-evidence binding correction passed lang 300/appframe 528/generator 105/old demo 42 = 975 JUnit, no failures/errors/skips, plus 71 roots including two cross-JVM groups and actual same/different-identity reversal races. Source 93 locks/rechecks original deposit via sameFacts inside complete SPP quantum; 91 archives original snapshot; 99 separates prepare/submit. Six missing APP bindings were added; generated Java was not hand-edited. Log: `/tmp/spp-original-evidence-binding-source-validation.log`. Incremental only, Java 14/6; the three independent changed/stale/already-settled preparation cases still needed evidence.

Prepared-result correction passed lang 303/appframe 528/generator 105/old demo 42 = 978 JUnit, no failures/errors/skips, all 74 roots. New 99zu changed receipt, 99zua stale preparation/same-day accrual 800, and 99zub settled-result replay/two identity mismatch rejections passed, preserving cross-JVM/concurrent coverage. Log: `/tmp/spp-prepared-reversal-corrected-source-validation.log`. JdbcDepositReversalStoreTest's sixteen methods were retired per reversal-store-retirement.md, leaving Java 14/5 and expected old JUnit 26; no clean success was assumed in advance.

Post-retirement clean passed lang 303/appframe 528/generator 105/old demo 26 = 962 JUnit, no failures/errors/skips, all 74 roots. That test/nested classes absent, sixteen retired test files total, Java 14/5. Log: `/tmp/spp-reversal-store-retirement-clean-validation.log`. Later 99zv six Saga cases/99zw posting rules were outside this baseline.

Scenario physical-binding/evidence-export verify passed lang 303/appframe 533/generator 105/old demo 26 = 967 JUnit, no failures/errors/skips, all 81 roots. Six Saga cases/posting rules, original 74, two independent-JVM groups, and actual concurrency passed. Exports contained Done/NotCommitted/Failed/Confirmed, three recovery attempts/two confirmation attempts after completion-record failure; seven new JSON/Markdown files under target/spp-examples/evidence reported PASS. Log: `/tmp/spp-source-saga-runtime-evidence-validation.log`. DepositPostingTest/DepositSagaRecoveryTest, six methods each, were deleted: eighteen retired files, Java 14/3, expected old JUnit 14. Clean/old-class removal remained unproven; reports/account-snapshot rechecks/different-identity cash races/main-Java removal continued.

Account-boundary corrected verification used `mvn -pl :xbod-deposit-demo -am verify -Dmaven.test.skip=true`: all 83 roots, no FAIL, existing 81/two cross-JVM/concurrency retained. Log: `/tmp/spp-cash-account-boundary-corrected-source-validation.log`. It skipped JUnit/test compilation, not full test regression; the public recovery catalog separately passed all 536 appframe tests. 91/92/93 locked account rechecks and 99zx tampering/different-cash-identity contention were generated/compiled/run. JdbcDepositStoreTest's ten methods were retired per deposit-store-retirement.md, removing the last dependency on retired posting fixtures. Nineteen test files retired, Java 13/2 with four remaining report/reversal tests. Complete clean was then started; no advance success claim.

Clean evidence at 2026-10-08 23:16: `mvn -pl :xbod-deposit-demo -am clean verify`, `/tmp/spp-account-boundary-retirement-clean-validation.log`. All 948 JUnit—lang 303, appframe 536, generator 105, old demo 4—with zero failures/skips, plus 83 entries including concurrency/two JVM groups. Old/nested classes for JdbcDepositStoreTest, DepositPostingTest, DepositSagaRecoveryTest, JdbcDepositReversalStoreTest, ExplicitSagaStep absent. This did not include later cash-report entry 84. Java 13/2 remained.

The 84-entry incremental `verify -Dmaven.test.skip=true` passed, including new cash-report two-JVM group; log: `/tmp/spp-source-cash-report-corrected-validation.log`. Skipping JUnit/test compilation did not replace clean 948/83 evidence. Enhanced cash checks/two full-report entries were added afterward; 86-entry package generation was running at `/tmp/spp-source-complete-report-generation.log`, not proof of 86 passed entries.

Complete deposit-report two-JVM plus JSON/Chinese-language Markdown facts passed; DepositDemoReportTest was deleted, leaving 13 main/one test file (three CashDepositReversalTest methods). Old report bytecode awaited clean removal. Generic batch registration passed 537 framework tests. Actual reversal report failed audit ambiguity due to duplicate attempt identity; source fixture identity was corrected before regeneration. See source-report-migration.md.

At the 2026-10-09 final migration gate, default parent-run cash-reversal report passed two new JVMs; log: `/tmp/spp-report-default-lifetime-runtime.log`. Matching exports verified four actual reversals, 32 inverse entries, 20 quantum rows, cash 1700/version 4/accrual 2200. Last 13 main/one test Java files were removed, leaving zero. Per-case request/authorization/provenance and actual replay-return enhancements still needed fresh generation in the running `/tmp/spp-source-only-final-clean-validation.log`; the goal was not yet complete then.

## Final historical status — 2026-10-09

Source-only refactoring completed: zero handwritten main/test Java, no residual classes from 14 old main classes/21 test families, clean generation/compilation, 945 public JUnit and all 86 APP entries passed. Five two-JVM groups, per-case input/authorization/provenance, six settlements/48 entries/five unexecuted candidates, and four reversals/32 inverse entries/20 quantum rows were checked.

See [final verification](source-only-final-verification.md) for authoritative phase evidence. Earlier sections preserve intermediate status and scope limits; none are fresh results from this translation.
