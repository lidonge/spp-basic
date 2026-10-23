> Historical document: this records pre-refactoring implementation/acceptance, not current source or test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Declarative business evidence and report migration

Initial checkpoint: generic evidence export was verified; cash aggregate report source was implemented and undergoing compilation/two-JVM validation, while the complete bank report remained unfinished. DepositDemoReportTest/CashDepositReversalTest responsibilities had to remain until replacement evidence passed. This phase prohibited handwritten Java reporting and moving bank templates/formulas into the public framework. Later sections record actual progress, failures, and final evidence.

## Layers

SPP defines report participants, case facts, independent expectations, and acceptance conditions. Actual results come from generated-service execution and persistent queries, never copied from expectations. APP/PHY supplies deployment, databases, and faults. The generic runner exports results/participant facts/readable formats; it neither judges bank outcomes nor calculates inverse transactions or bank-specific report contents.

The original public framework only printed PASS/FAIL. Generic export added JSON/Markdown for post-execution participant facts, execution state, and errors, with technically configured output directories. Chinese-language titles, explanations, business states, and scope flags were explicit SPP String fields, not framework Java constants.

## Original deposit-report obligations

Retain independent evidence for ten cases and six actual settlements: cash 600/fee 2; ordinary-debt interest 30/collection interest 20; non-performing principal 50; exact-business-mathematics principal 0.001; pending authorization without changes then authorized settlement; original-identity replay without updates; next-day daily total 100/accrual 2200/final balance 2300; control rejection EN432 and date rejection E1043. Query freezes, authorization, accrual, and original bank-qualification facts independently from storage.

Five financial/entry inverse candidates remain explicitly unexecuted, not cash reversals or settled transactions. Retain expectations including net balance 1700, zero/original fee 2, and entry total 602. Reports state original COBOL was not executed, production configuration was unavailable, and production replacement was unverified. That business-evidence scope is not the coding goal's execution status.

Declarative two-stage restart entries use two independent JVMs to close/reopen file stores. Resume independently queries six original settlements, 48 entries, cash version 3/balance 2300/accrual 2200, original bank qualification/replay audit, and persisted report facts. JSON export or recreating objects in one process cannot replace this evidence.

## Actual cash-reversal report obligations

Separately list original settlement, later activity, refunds, account deltas, entries, and persistent quantum logs for four actual reversals. Candidate versus actual execution must differ explicitly in fact types/states. Existing cash/ordinary-debt/non-performing/fractional reversal scenarios had run; aggregate reports still needed queried outcomes, actual transaction counts, and matching quantum records, not existing PASS text.

## Implementation and retirement gates

1. Add non-bank runtime tests for generic JSON/Markdown export: exact numbers, arrays/nested participants, failures, and output paths.
2. Define report Roles, bank cases, query/persistence bindings, and independent expectations, reusing SPP business services.
3. Run two-JVM file-store cases and four actual reversals; compare exported files with real queries.
4. Only after those obligations pass, remove both old report tests/dependent main Java and run clean verify with other migrations, checking source and old bytecode removal.

The design itself proved no implementation. Evidence had to identify actual builds, entries, exports, and queried outcomes.

Initially ExampleEvidenceWriter and three non-bank tests were written but neither compiled nor integrated into GeneratedExampleRunner. Only @JsonProperty-declared fields were captured, excluding helper getters. JSON/Markdown preserved BigDecimal/BigInteger precision; cycle detection protected secondary-failure extraction. The running bank 81-entry plugin JAR predated this code, so its results could not validate the exporter.

After integration, spp.examples.evidenceDirectory enabled export; absent configuration preserved prior execution. Each independent JVM exported its own facts; the parent used __restart for group results without overwriting stage evidence. Eleven focused JUnit tests passed—exporter 3, runner 6, physical participants 2—log: `/tmp/spp-source-scenario-evidence-runner-corrected-validation.log`. The first integration had overwritten setup evidence when the parent failed; a failed-second-stage test now protected the fix. Bank POM selected target/spp-examples/evidence. Generic export tests did not discharge the still-pending ten-case/six-settlement/five-candidate/four-reversal bank report obligations.

## Planned source layout

Four smaller groups: report Roles/expectations; ten deposit cases/six settlement queries; five unexecuted candidates/four actual reversals; independent restart readback. Fixed-slot Role arrays and source atomics copy each case's then-current facts so later operations cannot rewrite earlier evidence. Query actual settlements/entries separately; neither working memory nor expectations substitute for persisted evidence. Resume begins with empty participants, reads the report by stable identity, and requeries six transactions, 48 original entries, and accounts.

Cash chronology uses StoredPreparation dates: accrual date 20261007; same-day 600 and 1500 add no accrual; next-day 100 accrues one day on prior balance 2200, ending version 3/balance 2300/accrual 2200. Authorization wait precedes the second actual settlement and retains version 1/700. Original replay returns settled balance 700 while current account remains version 2/2200. EN432/E1043 each use independent version-0 accounts outside the six settlements.

Five candidates cover cash 600 with zero/original fee and ordinary/non-performing/fractional debt. Copy complete read-only plans/original entries without submitting, and query original accounts/associations/audit for no changes. Four actual reversals reuse executeCashReversal and independently query settlements, associations, entries, original/later activity, and quantum logs. Cash 2300 minus original 600 leaves 1700, preserving later 1500/100; debt inverses restore original components while retaining current accrual. Six deposits plus four reversals yield 20 quantum records; candidate counts cannot imply actual records.

Original-report and actual-reversal phases use separate two-stage restart groups/file stores to prove both original balance 2300 and later 1700, without confusing them as simultaneous state. SPP Strings supply titles/scope/unexecuted-candidate explanation/expected net balance. Production-replacement flags remain false and distinct from coding-goal status. At the planning checkpoint, these aggregate files were not yet created/run, and two tests/13 main Java files could not be removed.

## First aggregate source implementation: initially unverified

Added cboddeposit-99zya-report-model.spp and cboddeposit-99zyb-report-cash-scenarios.spp, with explicit APP persistence/two-JVM entries. Cash report executes 600, pending 1500, authorized settlement, replay, next-day 100; queries three settlements/24 entries/audit; copies two unexecuted candidates. A persisted Role is reread from empty participants on resume, then compared with three re-queried transactions using sameFacts. Report persistence has explicit SPP transaction quantum; titles/scope flags remain in SPP.

This initially uncompiled/unrun subset did not cover debt categories/rejections/full six settlements/48 entries/five candidates/four reversals/20 logs. Old tests/main Java remained. First compilation failed because an atomic check invoked another check, invalid under existing syntax. Calls moved to the resume Scenario, without syntax extension or bank Java workaround. Corrected-build log: `/tmp/spp-source-cash-report-corrected-validation.log`; runtime results were still pending.

## Complete report source: generation before runtime acceptance

Added 99zyc models, 99zyd ten cases, 99zye four actual reversals, 99zyf cross-JVM recovery; APP bound two new file-store restart groups. Ordinary/non-performing/fractional debt used same-branch zero-fee configuration with independent 30/20, 50, and 0.001 expectations. Actual EN432/E1043 cases query zero settlements/entries, version 0, and one Rejected audit. Three cash plus three debt settlements total six/48 entries; five candidates stay typed/unexecuted, actual reversals use independent records.

Deposit reports require 12 committed quantum rows. Reversal reports require 20, zero summed deltas, and persisted values for four accounts/four counterparties. Cash reversal leaves 1700/version 4/accrual 2200, preserving 1500/100 transactions; debt restores original components and retains post-settlement accrual. Report-save quantum boundaries are explicit SPP; source services perform all business/query/check work.

The two new entries were not yet compiled/run. Earlier corrected-cash logs covered generated 84 entries, not the now-86 source count. Source count is not passing-entry count.

Selected cash report then passed: GeneratedExampleRunner ran depositCashReportSetupExampleRun in two fresh JVMs for setup/resume. Log: `/tmp/spp-cash-report-selected-restart-validation.log`; exports: `/tmp/spp-source-cash-report-evidence`. Exact JSON/Chinese-language Markdown checks matched three settlements/24 entries, 2300/version 3/accrual 2200, two unexecuted candidates, and debit totals 600/602. This used the 84-entry build. Further bank-qualification, first F0554, and fee 2/2.5/2 expectations were added but not yet executed.

The 84-entry incremental run passed `mvn -pl :xbod-deposit-demo -am verify -Dmaven.test.skip=true`, including the cash two-JVM group; log: `/tmp/spp-source-cash-report-corrected-validation.log`. It skipped JUnit/test compilation and did not replace prior clean 948-test/83-entry evidence. Enhanced cash checks and two complete-report entries were added afterward; 86-entry package generation ran at `/tmp/spp-source-complete-report-generation.log`.

That package succeeded, compiling 4,212 Java files, but skipped JUnit and did not execute APP entries. Three selected JVM groups—enhanced cash, complete deposit, actual reversal—started with log `/tmp/spp-source-complete-report-selected-validation.log` and exports `/tmp/spp-source-complete-report-evidence`; terminal outcomes were initially unknown.

## First complete-report run and generic registration fix

The three groups produced one pass/two failures: enhanced cash passed; complete deposit/reversal exceeded the runner's fixed 60-second stage0 limit with empty stage logs, not business success. Diagnostic stacks showed LocalGeneratedServiceRuntime refreshing the entire idempotency catalog after each remembered service, repeating metadata traversal. Another diagnostic stack showed repeated H2 directory scans because its file store was directly in a large /tmp directory. That diagnostic process was terminated and did not count as restart evidence.

Generic batch registration now remembers the whole batch then refreshes once, while individual registration still refreshes immediately. Bank rules, boundaries, assertions, and 60-second limits were unchanged. Non-bank BulkGeneratedServiceRegistrationTest passed: eight services required eight metadata reads, and replacing one still refreshed. Log: `/tmp/spp-bulk-registration-validation.log`. Framework and real file-store groups reran in `/tmp/spp-bulk-registration-framework-validation.log` and `/tmp/spp-source-complete-report-fast-validation.log`; old bank Java remained pending results.

## Complete deposit-report responsibility replaced

After the fix, enhanced cash/complete deposit two-JVM groups passed, as did 537 framework JUnit tests without failures/skips. Resume JSON/Chinese-language Markdown checks verified ten cases, six settlements/48 entries, five candidates, 30/20/50/0.001 allocation, EN432/E1043 version-0 rejections, 2300/version 3/accrual 2200, and 12 actual quantum rows. Evidence: `/tmp/spp-source-complete-report-fast-evidence/depositCompleteReportResumeExampleRun.json` and matching .md; run log: `/tmp/spp-source-complete-report-fast-validation.log`. DepositDemoReportTest was removed, leaving 13 main Java files and CashDepositReversalTest with three tests.

Actual reversal failed with REPOSITORY_SCALAR_QUERY_AMBIGUOUS. Its fixture reused SOURCE-REPORT-CASH-REPLAY as reversal attemptId, colliding with deposit replay. Two distinct trade audits actually existed; scalar lookup correctly rejected ambiguity. No query/assertion was weakened. Reversal identity changed to SOURCE-REPORT-CASH-REVERSAL-REPLAY, requiring regeneration/execution before deleting the remaining test/main implementation.

## Per-case provenance gate

Following requirements-and-functions, cash reports added actual pending/authorized/replay commands, current replay-time account 2200/version 2, and returned original account 700/version 1. Complete cases added request, businessDay, authorizationLevel, sourceProgram, sourceParagraph, expectedOrigin. SPP copies facts at execution and requires all ten cases to carry inputs/provenance. Fractional cases explicitly use business-mathematics expectations; others combine source derivation/demo configuration, never bank golden or executed COBOL claims.

These additions occurred after the unique-attempt 86-entry generation had read models. The current package validated the previous identity-corrected build, not these additions. Final clean verify after Java removal had to regenerate/run them; code presence was not acceptance.

## Reversal persistence and file-store lifecycle

After identity correction, the default file-store report timed out again. With unchanged generated source/assertions, keeping H2 open within each individual JVM allowed independent stage0/stage1 processes to pass in 17.64/9.90 seconds. Logs: spp-report-phase-runtime and spp-report-phase-resume-runtime. Each JVM closed its store on exit; the next reopened it. JSON/Chinese-language Markdown checks verified four actual reversals, 32 inverse entries, 20 quantum records, 14 rows after six deposits/one reversal plus replay/duplicate rejection, cash 1700/version 4/accrual 2200, refund 2, and three debt restorations. Exports: `/tmp/spp-report-phase-evidence`.

Generic ExampleProcesses adds DB_CLOSE_DELAY=-1 only to default generated H2 file URLs. Ordinary JDBC connections still close normally; the database closes before JVM termination. User-supplied URLs are unchanged. The 60-second limit/assertions remain. Six GeneratedExampleRunnerTest cases passed, including distinct PIDs, empty participants, and actual persistent readback, proving no cross-JVM memory reuse or obstruction of the next stage. The default parent-run reversal group then reran at `/tmp/spp-report-default-lifetime-runtime.log`; final clean still had to cover provenance additions.

On 2026-10-09 the default parent's two-fresh-JVM reversal group passed. Matching JSON/Markdown verified four reversals/32 inverse entries/20 quantum rows and cash 1700/version 4/accrual 2200. The last 13 main Java files and one test file were deleted, leaving zero source Java. New per-case requests/authorization/provenance/replay returns still awaited regeneration in the running `/tmp/spp-source-only-final-clean-validation.log`; the goal was not yet complete at that checkpoint.

## Final historical status — 2026-10-09

The source-only refactoring completed: zero handwritten main/test Java, no residual classes from 14 old main classes/21 test families, clean generation/compilation, 945 public JUnit tests and all 86 APP entries passed. Five two-JVM groups, per-case input/authorization/provenance, six settlements/48 entries/five unexecuted candidates, and four reversals/32 inverse entries/20 quantum rows were checked.

See [final verification](source-only-final-verification.md) for authoritative phase evidence. Earlier sections retain intermediate failures/status/scope limits, not fresh test results from English translation.
