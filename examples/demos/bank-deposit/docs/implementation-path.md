> Historical document: this describes pre-refactoring implementation/acceptance, not current source/test results. See [current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Implementation path

## Path revision — 2026-10-08

The refactoring goal began; see [source-only implementation history](source-only-refactoring.md). That phase first required [source-only migration](source-language-only.md): explicit complete SPP transaction boundaries/modes, scenario control and query/update generation, then generated execution/acceptance and removal of handwritten demo Java. Earlier stages remain behavior evidence, not compliance with that new constraint. The later refactoring allows technical Java tests but no handwritten business implementation.

## Sequence and deliverables

1. Establish requirements, decisions, provenance, comparisons, and an independent Maven module. Upgrade the calculation prototype to complete generated services/scenarios, preserving prototype evidence.
2. P1: turn source boundaries into cases, extend three-decimal interest/currency inputs. Confirm migration targets for implicit truncation/uncertain branches before explicit rules or physical compatibility; add independent reference outcomes.
3. P2: trace eligibility, product limits, authorization, calendars/interest, and fees individually. Define Role/result contracts first, then implement selected subsets with provenance/pending configuration. If authorization continuation/check scope is inexpressible, design and implement support rather than hiding business decisions in APP.
4. P3: trace classification/accounting, define funding and entries, then queries/updates, persistence, logs, idempotency, conflicts. Verify scenarios, conservation, and faults; a two-balance technical transfer is not bank accounting.
5. P4: trace original KBN logs/reversal sections. Use actual original components and verify later changes, duplicate reversal, cross-day accrual, original state, and inverse entries. Test framework rollback separately.
6. Summarize executable cases, evidence strength, traditional comparisons, and limits; regress changed shared compiler/runtime. Commit/push when requested.

## Layout and execution

In this historical source-only phase, src/main/spp held business/APP/PHY; handwritten src/main/java and src/test/java were migration violations. docs held requirements/path/comparison/decisions; target held generated source, Surefire, and case reports.

Historical reactor entry: `mvn -pl :xbod-deposit-demo -am test`. Each phase adds success/rejection/boundary evidence with actual commands/results. P3/P4 add CLI transactions and machine-readable reports. See current running-demo instructions for today's repository layout.

## Initial progress checkpoint

- Goal, requirements, and roadmap established.
- Complete P1 generation baseline; P2 admission/control/authorization/calendar/agreement-tier/fee subsets; P3 ordinary cash and ordinary/agreement/overdraft accrual through JDBC joint commit, authorization recheck, original retry/audit, file-store CLI/reports. Full eligibility, remaining agreement/accrual, and P4 incomplete then. Later sections retain each phase's state.
- Fourteen source-derived cases ran actual OpenAPI models/generated atomics/serial scenarios, checking nine outputs, unchanged input snapshots, and negative rejection before plan creation. Original COBOL still unexecuted.
- User confirmed business semantics first and initial cash/overdraft deposits plus corresponding reversals. Unconfirmed policies are recorded, not guessed.

## Overall acceptance

All four priorities need executable evidence/explicit limits; selected transactions must run from request through persistence/reversal. Rejection, pending authorization, retry, recovery, and concurrency need assertions. Business policy stays SPP/shared declared business libraries; deployment/storage APP/PHY. Every rule has provenance without claiming unverified equivalence. Missing decisions/material do not prevent independent work, but fixtures cannot masquerade as migrated bank configuration.

## Acceptance — 2026-10-07

`mvn -pl :xbod-deposit-demo,business-showcase -am test` passed: lang 255, runtime 445, generator 91, existing showcase 24, deposit 14; total 829, zero failures/errors/skips. Log: `/tmp/spp-xbod-demo-regression.log`.

Complete generation exposed/fixed an incorrect model-package prefix on local boolean types, also covering local String with semantic regression. Earlier statement-only arithmetic execution could not reveal this complete-generation defect, motivating generated/scenario tests.

Next: expand P1 numeric boundaries/P2 contracts; P3/P4 remained pending and the historical goal active. Reversal automation direction/existing quantum-inverse interfaces were recorded in decisions.

## Admission/authorization increment — 2026-10-07

Same module test command passed lang 255/runtime 445/generator 91/demo 24 = 815, zero failures/errors/skips. Log: `/tmp/spp-xbod-rules.log`. Ten new tests contain multiple boundaries; numeric thresholds are demo fixtures, not actual bank parameters.

Services cover front cash positive amount/current value date, temporary expiry, closed accounts, first/initial deposit bounds, ordinary balance ceiling, daily warnings, general A/B authorization, selected USD/channel/transaction exceptions. Assessment returns PendingAuthorization/Warning/Ready; warnings may continue and execution has a separate check. Authorization retry reevaluates current thresholds; DB-version binding/persistent continuation were not yet verified.

Interest-unit service checks explicit rounding/accrual. Date validity, calendars, full CSAQRY1, tiering, and fees were still missing, so P2 was not complete. At the user's request, new reversal/rollback work awaited conceptual discussion; the historical goal remained active.

## Calendars and numeric boundaries — 2026-10-07

Module tests passed lang 255/runtime 445/generator 91/demo 30 = 821, zero failures/errors/skips. Log: `/tmp/spp-xbod-calendar.log`.

Two three-decimal-interest cases and four multivector calendar tests use actual generated scenarios: APP decodes dates, SPP selects ACT/360365/360360. Checks include leap/century years, month/year ends, zero periods, invalid/reversed dates, and date-driven unit accrual. unit=3 verifies explicit business rounding handles a nonterminating intermediate quotient; exact BigDecimal representation of that quotient is not required.

C-CM-DT-GT-PRDH is O, start-inclusive/end-exclusive, not H excluding both. Errors follow active constants, not old headers. Original COBOL was not run; expectations remain source-derived or explicit business mathematics.

Adapter inverse evidence/gaps entered default-reversal-design.md. Default restoration for ordinary assignment/state remained under discussion, not implemented. Full P2 eligibility/tiering/fees and P3/P4 remained incomplete.

## Agreement-tier interest — 2026-10-07

Tests passed lang 255/runtime 445/generator 91/demo 36 = 827, zero failures/errors/skips. Log: `/tmp/spp-xbod-agreement.log`.

Generated calculateAgreementProduct and six tests retain five slots traversed 5→1; mode2 distributes excess by tier, mode1 assigns whole amount to selected tier, unspecified mode follows currency. Coverage: strict/non-strict thresholds, explicit mode override, disabled/zero-overlap, below-start, invalid contracts, and non-combinable per-tier quantization. Input amount snapshot stays unchanged; remainder/tier accrual are outputs.

Inputs provide ordinary/overlap days; effective/cancellation clipping was not yet migrated. Five nonnegative thresholds form the current contract; absent slots/original negative-threshold disable markers need explicit lookup/mapping. Separate rounding can make tier-plus-ordinary accrual smaller than original total; retain source behavior without confusing accrual differences with monetary conservation. No golden COBOL equivalence.

User confirmed quantum inverses preserve later business, while non-quantum inverses require dependency checks. Drafts replaced version-only restoration; concrete inverse/dependency mechanisms remained undecided.

## Ordinary cash fees — 2026-10-07

Tests passed lang 255/runtime 445/generator 91/demo 41 = 832, zero failures/errors/skips. Log: `/tmp/spp-xbod-fee.log`.

Generated prepareDepositFee invokes input checks, SPP region/institution classification, APP customer-rate lookup, and SPP fee math. Five tests cover scope/codes, discount, fixed fee after rounding, bounds/zero maximum, teller channel, missing/wrong rates, and negative amounts rejected before query. It formed fee plans, not deductions/accounting.

Only ordinary recipient-account cash with empty payer migrated. Multicurrency, dual-sided fees, and KCMFEE0 remain unsupported; parameters are fixtures. Provenance is in source-map, original COBOL unexecuted. Full eligibility/date clipping/P3/P4 remained pending.

## Agreement dates to accrual — 2026-10-07

Tests passed lang 255/runtime 445/generator 91/demo 46 = 837, zero failures/errors/skips. Log: `/tmp/spp-xbod-dated-agreement.log`.

prepareAgreementWindow/calculateDatedAgreement compose original date decoding, ordinary period, overlap window, nested calendar, and tiers. Five tests cover effective/cancellation clipping, no cancellation, future/absent effective date, early cancellation, zero window, invalid dates. Zero represents original blank dates. No overlap creates a zero period from valid original dates rather than letting APP decide whether business calculation runs. AgreementPolicy.enabled becomes request-specific applicability, not reusable persistent product configuration.

This date/accrual subset ran; bank agreement lookup, negative-threshold disabling, and complete eligibility remained. Original COBOL was not run. Next: selected eligibility, accounting/configuration tracing, persistent plans; P3/P4 still incomplete.

## Ordinary cash account controls — 2026-10-07

Tests passed lang 255/runtime 445/generator 91/demo 51 = 842, zero failures/errors/skips. Log: `/tmp/spp-xbod-account-control.log`.

Front account controls/core closed-product checks were added to generated assessNormalCashDeposit, composing input, front controls, product eligibility, core facts, and authorization. Five tests cover institution controls, pay-only, locked, freeze-status versus frozen-amount, closed records, the housing-related product's inapplicability to ordinary cash, and no authorization result after rejection. Input/product services remain reusable.

Cross-institution CSAQRY1, card/passbook loss warnings, and other exceptions remained. Single-cause rejection tests did not prove original COBOL message priority for arbitrary multiple-invalid states. Accounting tracing found GCCBAAR depends on runtime ART/SMT/SMP; classification/configuration limits are in posting-plan-source.md. Accounts/ledgers were not yet persisted; P3/P4 remained incomplete.

## Posting plans — 2026-10-07

Module tests passed lang 255/runtime 445/generator 91/demo 57 = 848, zero failures/errors/skips; `/tmp/spp-xbod-posting-plan.log`.

Added classifyDepositAccounting/checkDepositAccounting and calculateDepositPostings/checkDepositPostings, composed with kernel calculation by generated prepareDepositPostings. Six tests cover cash/separate fees, ordinary/bad-debt allocation, existing-deposit repayment, core zero amount, three decimals, invalid configuration, and forged totals. DEMO accounts are illustrative; original ART/runtime values unavailable. Plans passed, not yet persistence or full admission-to-settlement.

## JDBC joint commit — 2026-10-07

Tests passed 255/445/91/63 = 854, zero failures/errors/skips; `/tmp/spp-xbod-jdbc.log`.

SPP prepareDepositAccountUpdate converts validated plans into independent snapshots, retaining repayment in business logic. JdbcDepositStore writes account/debt, original settlement, and eight entries in one local JDBC transaction. Versions/original snapshots prevent stale plans overwriting new settlement. Same identity/complete Prepared facts replay original results; changed facts under reused identity reject.

Six real H2 tests cover three-decimal persistence, joint commit, adapter rereads, repetition, stale versions/forged snapshots, faults after account/partial entries/before commit, competing transactions, concurrent identical commands. JSON/amount strings avoid DB decimal truncation. This was demo schema/in-memory H2, not DB-process restart, disk failure, cross-resource, quantum-coordinator, or Saga acceptance.

The store accepted generated validated plans, not complete original requests. Request-only retry, eligibility/authorization revalidation, daily/accrual updates, failure audit, and CLI reports remained. P4 implementation still awaited discussion.

## Ordinary cash request through settlement — 2026-10-07

Tests passed 255/445/91/72 = 863, zero failures/errors/skips; `/tmp/spp-xbod-full-entry.log`.

Added generated prepareNormalCashDeposit, business prepareDepositEligibilityUpdate, and NormalCashDeposit APP entry. JDBC jointly reads eligibility/controls/product policy/fee identity; new eligibility/amount state share version/snapshot conditions and commit. Business forms daily totals/net balance/configured detail count; physical storage preserves request identity/original results. Generated constructors' dependency lookup required dependency-ordered demo registration; no generic generator change.

Nine tests cover ordinary/bad/written-off deposits, warning continuation, fees/eight entries, pending without updates, upgraded authorization/current-threshold-account rechecks, replay after later transactions/day changes, normalized amount text, daily reset, control/rate lookup failure, continuation after adapter reconstruction, and whole-entry concurrent duplication.

See cash-execution-contract.md. Identity persistence is not complete approval/audit. Authorization issuance and concurrent external-configuration changes were not implemented/jointly versioned. No actual process restart/lost response injection yet. Accrual/full bank eligibility/configuration/failure audit/reports/P4 remained.

## Audit and failure outcomes — 2026-10-07

Tests passed 255/445/91/76 = 867, zero failures/errors/skips; `/tmp/spp-xbod-audit.log`.

deposit_audit adds attempt/event/code/payload queries. Success audit commits with business; failure first rolls back then independently appends audit through another connection. APP records rejection/pending/replay. Errors before commit, during commit, and after successful return classify as definite rollback, unknown outcome, and committed-return failure. Preserve business and audit exceptions; do not automatically reverse/compensate.

Four store tests cover partial-entry failure audit/retry history, post-success return failure, proxy commit exceptions, unavailable audit. Nine existing entry tests gained rejection/rate/pending/replay audit assertions. These are in-process callbacks/JDBC proxies, not real network/DB crashes. Audit-store failure is observable, not a guarantee that audit persisted. See audit-and-failure.md. Accrual/full eligibility/configuration/reports/P4 remained.

## File database and reports — 2026-10-07

`./demos/bank-deposit/run-demo.sh` ran reactor package: 255/445/91/77 = 868 tests, zero failures/errors/skips, then Java main completed ten cases/six settlements/48 entries. Log: `/tmp/spp-xbod-report.log`.

Output: target/demo-report/run-e0d3bbed-b1ef-45ba-adb6-39b5896ffb9c/, containing report.json, report.md, deposit.mv.db. Each run creates a fresh directory/store. Reports retain independent expectations, actual snapshots, original facts, entries, attempt audit; main checks states/errors/net balances/versions, entry-plan consistency, conservation. Replay references old settlement and separately records current state.

DepositDemoReportTest checks exports, ordinary/bad/three-decimal expectations, pending/replay/rejection, and graceful reopen reading accounts/originals/audit/48 entries. This is not a crash test. running-demo.md lists use. Reports explicitly retained missing accrual/full selected eligibility/reversal/recovery and unavailable bank configuration/golden outputs; goalComplete=false.

## Joint accrual commit — 2026-10-07

Tests passed 255/445/91/83 = 874, zero failures/errors/skips; `/tmp/spp-xbod-accrual.log`.

Generated prepareDepositAccrual/prepareCashDepositWithAccrual use pretransaction amounts/independent accrual dates for ordinary/five-tier agreement, overdraft principal/interest, then cumulative snapshots. AccountFacts.accrual commits with amounts/eligibility/entries. Prepared.accrual stores original dates/days/actual components for cross-day reversal. Formulas did not move into JDBC/Java model allocators.

Six tests cover calendars/units/nonterminating quotients, original counting conditions, unchanged inputs, in-period/same-day/replay, invalid configuration, and joint rollback after amount/accrual writes followed by ledger failure. File-report tests add accrual reopen checks.

The current main reran at target/demo-report/run-dbbecdf1-8ea4-4b08-a4bd-a84b58a02244/, log `/tmp/spp-xbod-accrual-demo.log`. Next-day accrual uses prior 2200 × 1, not new balance 2300, over 20261007–20261008. All CLI accounts carry accrual facts.

See accrual-execution.md for source/call differences. Agreement cursors/negative-threshold disable, interest-flag combinations, mode1 high-tier save conditions, and retrospective value dating remained unverified. No full-bank equivalence; reversal eligibility/cross-day adjustment still pending.

## Financial inverse plans — 2026-10-07

Tests passed 255/445/91/90 = 881, zero failures/errors/skips; `/tmp/spp-xbod-reversal-amounts.log`.

describeOriginalDepositMovement/prepareDepositReversalAmounts use actual original components on current accounts, rederive availability from current freeze/authorization, and neither restore old snapshots nor reallocate by current loan status. Shared eligibility includes capacity ES020, new interest-bearing amount EN249, restored-debt EN177. Actual-exposure-delta inversion differs from original principal-addition and is separately documented.

Seven generated-scenario tests preserve later spending/deposits, ordinary/bad original components, exact three decimals, original exposure clamping, eligibility/forged-component rejection. Only plans, no new reversal settlement. Cross-day/refund policy questions did not block plan verification. Original lookup/new identity/exclusion/cross-day/fees/entries/audit joint commit remained; see reversal-financial-plan.md.

## Cross-institution and loss warnings — 2026-10-07

Tests passed 255/445/91/98 = 889, zero failures/errors/skips; `/tmp/spp-xbod-bank-qualification.log`, Surefire XML checked.

Account/operator Roles, cross-institution checks, independent WN200/WN201, and two complete qualification scenarios added. SPP owns conditions; APP decodes/persists original handling facts; replay retains notices. Eight tests cover ordinary/restricted exception scopes, relationship/teller bounds, missing facts, actual cash/accrual/entries joint commit/rejection. See bank-qualification.md for source/custom-error/priority limits.

Ten demo cases ran at target/demo-report/run-00460a5d-a192-4c67-9f5a-e303421585a5/, `/tmp/spp-xbod-bank-demo.log`. Original payload now includes bank/operator/notices; accrual still jointly persists. Real queries/configuration/full eligibility/agreement branches/reversal remained.

Confirmed classification: quantum inversion applies negative original delta to current state; non-quantum inversion requires subsequent-dependency checks, not merely version protection. Generic non-quantum mechanisms/cross-day/refund policies remained unimplemented.

Additional report-export/reopen assertions passed `mvn -pl :xbod-deposit-demo -am -Dtest=DepositDemoReportTest -Dsurefire.failIfNoSpecifiedTests=false test`: one test, zero failures/errors/skips; `/tmp/spp-xbod-bank-report.log`. Graceful reopen only, not crash recovery.

## Actual settlement to inverse preparation — 2026-10-07

Tests passed 255/445/91/102 = 893, zero failures/errors/skips; `/tmp/spp-xbod-recorded-reversal.log`, XML checked.

APP DepositReversalPreparation reads original successful settlement/current account and calls prepareRecordedDepositReversal. checkOriginalDepositSettlement matches stored before/after against original plan, then extracts components/forms inverse candidates. Lookup uses only original account identity, not supplied replacement components; results include current version/capacity snapshots.

Four actual JDBC/generated tests cover preserving later cash settlements, ordinary/bad/three-decimal amounts, absent successful original/insufficient capacity/inconsistent originals. Preparation leaves accounts/originals/entries/requests/audit unchanged. This is a demo fact contract, not KBN decoding, tamper-proof storage, or complete reversal.

New identity/association/exclusion, full eligibility/non-quantum dependencies, cross-day/refund policy, entries/audit joint commit remained. Read-only cross-day preparation neither permits whole reversal nor refunds fees/changes accrual.

## Inverse entries and explicit refund — 2026-10-07

Tests passed 255/445/91/106 = 897, zero failures/errors/skips; `/tmp/spp-xbod-reversal-postings.log`.

Added original-ledger query, checkRecordedDepositLedger, assessOriginalDepositPostingLines, checkDepositFeeRefund, prepareRecordedDepositReversalPostings. Validate original/actual entries, classification/fees; reverse six core directions; require explicit refund within zero/original fee for two fee slots. SPP owns conditions/math/totals; APP/PHY query/allocate. Initial compilation exposed a total-calculation service wrongly declared check; changed to calculate, retaining checks' no-business-write rule.

Four tests cover zero/partial/full illustrative refunds, directions/exact components, inconsistent/incomplete ledger, absent/negative/excess refund. Preparation changes neither accounting nor audit; fixture choices are not bank-policy decisions.

Reports added five reversalCandidates with executed=false: cash zero/original refund and ordinary/bad/three-decimal repayment. Focused `-Dtest=DepositDemoReportTest,DepositReversalPreparationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed nine, zero failures/errors/skips; `/tmp/spp-xbod-reversal-report.log`. Original ten cases/six settlements/48 entries unchanged. Complete submission/exclusion/daily-cross-day/non-quantum dependencies/recovery remained; no technical default refund.

Current main passed at target/demo-report/run-42989940-e59f-43c9-8907-a1a670107680/, `/tmp/spp-xbod-reversal-demo.log`: six actual settlements/48 entries, five unexecuted candidates each with eight balanced inverse entries. Cash candidate totals 600/602, both net 1700, without changing current 2300.

## Actual financial-component reversal commit — 2026-10-07

Initial tests passed 255/445/91/115 = 906, zero failures/errors/skips; `/tmp/spp-xbod-reversal-store.log`. After audit/identity additions, focused JdbcDepositReversalStoreTest passed twelve, zero failures/errors/skips; `/tmp/spp-xbod-reversal-store-audit.log`. Current Surefire total 909, demo 118.

prepareDepositReversalAccountUpdate/JdbcDepositReversalStore preserve original logs and uniquely associate new reversal, jointly committing inverse entries/new account/success audit. Original-settlement locking/uniqueness handles contention; current-version/snapshot conditions prevent overwriting intervening ordinary settlements. Stable identity supports replay without requiring account still at reversal version.

Twelve H2/generated tests cover 200→deposit10→deposit20→230→reverse10→220, preserving later activity; repetition/concurrency, revision retry, link/account/partial-entry/precommit faults, committed-return failure, changed Receipt/identity, unavailable audit. Fixtures explicitly omit product/accrual and use zero fees/refunds, not formal cash-policy inference.

See reversal-submission.md for contracts/two ledgers. Complete cash entry/eligibility/non-quantum dependencies/daily-accrual/cross-day/refunds/Quantum-Saga acceptance remained. Cross-day/refund decisions were requested; independent technical commit was not relabeled complete P4.

## Core eligibility and file persistence — 2026-10-07

Tests passed 255/445/91/124 = 915, zero failures/errors/skips; `/tmp/spp-xbod-reversal-eligibility.log`, XML checked.

Added checkCoreDepositReversalEligibility/assessCoreDepositReversalEligibility/prepareCoreQualified: KSADEP0:603 EO082; CSAQRY1:5000/9751/9753 EN113/EN111 and SA01116 freeze exception; shared loss warnings. Reversal does not call ordinary 9709; front institution controls have separate scope, not wholesale normal-admission reuse.

Four eligibility tests cover errors/exceptions, warnings versus ordinary scope, current post-settlement controls, front/core division. Physical tests gracefully reopen H2 and query original/inverse settlement, unique association, eight slots, success audit, identity, then replay after later deposit. Changed original Receipt classifies as identity rejection, not audit failure. ledger(reversalId) reads actual entries, not regenerated payloads.

See bank-qualification.md/reversal-submission.md. Full cash updates/cross-day/refund policy/Quantum-Saga were still pending; core eligibility alone does not authorize the whole operation.

## Native Saga and explicit financial recovery — 2026-10-07

Tests passed 255/445/91/129 = 920, zero failures/errors/skips; `/tmp/spp-xbod-saga-recovery.log`. After completion-log-failure coverage, focused DepositSagaRecoveryTest passed six, zero failures/errors/skips; `/tmp/spp-xbod-saga-log-outcome.log`. Surefire total 921/demo130.

APP ExplicitSagaStep binds local execution, successful-settlement lookup, and explicit business recovery to DefaultTransactionManager/SagaTransactionStrategy, using SagaLogRecorder/JdbcSagaLogStore. getTransactionParticipant.rollback means business recovery in this binding, not default technical rollback/compensation.

Six H2/generated-plan cases cover local success, later failure inversing only original components while preserving independent settlement, no inverse without commit, ES020 recovery rejection, stable-identity retry, and R1 settled but completion-log failure followed by lookup. Original/inverse ledgers remain; framework preserves primary/suppressed recovery failures rather than declaring success.

Zero fees/refunds and absent product/accrual remain explicit component fixtures. Journals/callback binding do not share the local business transaction; no automatic scanning/cross-process reconstruction. Complete bank Saga/quantum deposit were not connected. See saga-recovery.md/spp-vs-cobol-java.md.

## Unknown reversal commits and complete command — 2026-10-07

Two proxy faults throw before actual commit or after DB commit. Both record CommitOutcomeUnknown, then query independent reversal identity. Uncommitted retries once with the same identity; committed replays original Receipt without another deduction. Sixteen focused JdbcDepositReversalStoreTest tests passed; `/tmp/spp-xbod-reversal-unknown.log`.

run-demo.sh packaged/tested/ran file-store demo: lang255/runtime445/generator91/demo132 = 923, zero failures/errors/skips; `/tmp/spp-xbod-current-demo.log`. Output target/demo-report/run-84ce53fa-b681-4f4a-9a2c-9821dafdc5b1/ verified ten cases/six actual settlements/48 entries/five unexecuted balanced candidates. goalComplete/cobolExecuted/bankConfigurationObtained remained false.

See acceptance-audit.md. Complete P4 cash/cross-day still lacked end-to-end evidence; policy decisions could not be inferred from components or ordinary next-day tests. Scope did not expand indefinitely to all products/backends/production.

## P4 cash policy confirmed

User confirmed original XBOD rules for cash reversal including cross-day. Earlier alternative-policy choices ceased blocking; previous sections remain historical. Traced KSADEP0 4600/4630/4640 and KSAFEE0 5500; active/comment boundaries in cash-reversal-source.md. Added SPP metadata plans/original-fee selection before complete entry integration; plans were not end-to-end settlement.

## Scenario splitting and explicit quantum transactions

Following feedback about oversized source/invisible quantum boundaries, split same-domain scenarios and added amount quantum declarations, APP cashLedger, PHY jdbcQuantum. Net cash balance/signed counterparty conserve; generated SPP transaction uses the actual JDBC commit connection. Acceptance covers real quantum logs, object isolation, repetition, joint rollback; see quantum-and-transactions.md.

## Scenario, cash reversal, quantum joint-commit evidence

Full regression passed 934: lang255/appframe445/generator91/demo143, zero failures/errors/skips. run-demo.sh executed deposits/four XBOD reversals, each replay without repeated changes, exporting 32 inverse entries/20 quantum records. Output target/demo-report/run-a7aa3ad3-df28-4f64-b5be-95d8dba12f77; log `/tmp/spp-xbod-final-cash-demo.log`. Quantum tests cover outer rollback, object isolation, wrong-before rejection, and exact values beyond generic SQL default scale.

Selected model/fixture evidence is in acceptance-audit.md. Golden equivalence, peripheral fields, generic non-quantum lifecycle recovery, cross-store deployment remain explicitly limited.

At the 2026-10-09 final migration gate, the default parent's two-new-JVM reversal-report group passed; `/tmp/spp-report-default-lifetime-runtime.log`. Matching JSON/Markdown checked four actual reversals/32 inverse entries/20 quantum rows/cash1700/version4/accrual2200. Last 13 main/one test Java files were removed, leaving zero. Per-case request/authorization/provenance/replay-return enhancements still awaited fresh clean generation in `/tmp/spp-source-only-final-clean-validation.log`; the historical goal was not complete then.

## Final historical status — 2026-10-09

Source-only refactoring completed: zero handwritten main/test Java, no residual classes from 14 old main classes/21 test families, clean generation/compilation, 945 public JUnit and all 86 APP entries passed. Five two-JVM groups, per-case input/authorization/provenance, six settlements/48 entries/five candidates, four reversals/32 inverse entries/20 quantum rows checked.

See [final verification](source-only-final-verification.md). Earlier sections preserve intermediate status/limits, not fresh results from translation or current implementation claims.
