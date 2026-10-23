> Historical document: this describes pre-refactoring implementation and acceptance, not current source/test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`. The later refactoring permits technical Java acceptance tests, not handwritten business implementation.

# Source-only constraints and gap audit

On 2026-10-08, the user required this demo phase to use only SPP, APP, and PHY: all Java had to be generated, with no handwritten Java. SPP had to explicitly state transaction boundaries and modes. This superseded earlier choices allowing Java demo entry points, manual query implementations, or database commit wrappers.

## Historical acceptance conclusion

Final source-only acceptance passed: zero handwritten main/test Java files, 945 public JUnit tests and 86 source entries passed, and no residual classes, nested classes, or JAR entries from the old 14 main classes/21 test families. See [final verification](source-only-final-verification.md).

At the initial audit, SPP declared transactions only for conserved amount updates. Source files 92/93 subsequently declared complete deposit/cash-reversal quantum boundaries covering accounts, settlements, entries, associations, and audit. Identity registration, replay, and failure audit in 98/99b also declared independent SPP boundaries, generated and executed from an empty target directory. Old JdbcDepositStore/JdbcDepositReversalStore and their tests were deleted. SPP determined the new business boundaries; overall acceptance still required a current clean generation and all-entry execution.

## Handwritten implementation responsibilities

| Deleted original file | Original responsibility | Source migration requirement/check |
| --- | --- | --- |
| NormalCashDeposit | Queries, four business-path choices, pending authorization, replay, plan assembly, submission | Declarative queries/selection; SPP validation scope and results; APP/PHY execution bindings |
| CashDepositReversal | Original queries, reversal call order, fact checks, result assembly, commit | Complete cash-reversal scenario and boundary in SPP |
| DepositReversalPreparation | Original/current account reads, eligibility and financial-plan orchestration | Query bindings and generatable scenarios |
| JdbcDepositStore / JdbcDepositReversalStore | Table creation, request uniqueness, version updates, joint settlement/entry/audit commit | APP/PHY persistence/resource declarations; complete mode/boundary from SPP |
| CashQuantumLedger | Account identity, shared connections, quantum invocation, transaction-manager switching | Quantum identity/common-resource declarations and generic runtime support |
| ExplicitSagaStep | Saga steps, commit evidence, business recovery bindings | Explicit source mode and recovery services; no Java-defined business recovery strategy |
| BankDateDecoder | Date decoding, validity, ordinal calculation | Generic date/error bindings; business day-count rules remain in SPP |
| DepositFeeRateQuery | Rate lookup and inapplicable/missing outcomes | Query bindings; traceable business branches/results |
| DepositAccrualModels / DepositBankModels | Participant copies, initialization, model assembly | Generic participant construction/copy; no handwritten model wrappers |
| DepositDemoServices | Registration and replacement of generated services | APP/PHY/generated-catalog startup, no demo Java registrar |
| DepositDemoMain / CashReversalDemo | Cases, calls, independent expectations, reports | Declarative cases, generic runner/report support, no bank-specific Java entry |
| Original 21 src/test/java files, ultimately zero in this phase | Assertions, fixtures, fault/concurrency execution | Declarative acceptance or generic runner, no new demo Java tests in this phase |

## Source capabilities and acceptance locations

| Capability | Implementation/evidence location | Actual scope |
| --- | --- | --- |
| Roles, Contracts, mathematics, logic | Role/Contract, atomic check/calculate, unified expressions, Java-style branches/loops; 99p/99t/99x/99y | No bank Java formulas; production parameters and bit-exact COBOL golden output unavailable |
| Serial/parallel, exceptions, result orchestration | Scenario serial/parallel, if/else, try/catch, query return bindings; actual races in 99zi/99zo/99zx | APP barriers prove arrival; bank decisions in SPP; not arbitrary production scheduling topologies |
| Complete boundaries/modes | Whole quantum transactions in 92/93; independent identity/audit quantum in 98/99b; explicit saga/recovery in 99zv | Quantum rollback differs from Saga business recovery; this PHY uses shared local JDBC resources |
| Queries/updates | APP repository optional/for update, persist/compareVersion, PHY repositories; 91/92/93/99ca | Locked account/original-receipt rechecks at commit; original bank DB interfaces/ART configuration excluded |
| Faults, audit, idempotency, retry | Four actual JDBC commit points in 99zl/99zm; audit failure 99zq; mid-entry failure 99zr/99zs; preparation/replay 99zu | Do not infer rollback from exceptions; source queries establish unknown outcomes; not disaster-recovery acceptance |
| Reversal/Saga | Complete cash reversal 99b; six recovery classes and failed-recovery retry 99zv; four actual reversals in 99zy report | No generic account-lifecycle inverse or automatic business compensation |
| Numbers, dates, copies | PHY double as BigDecimal; calendars 20/30; new/copy/fixed-slot arrays and business rounding | Yen/PIC legacy differences retained; cross-file fields/helper scenarios require dependency order |
| Declarative acceptance/reports | 86 APP examples; independent SPP expectations; generic JSON/Markdown and two-JVM stages | Per-case input/source enhancements require clean regeneration; code presence, compilation, and business success are separate evidence |

Parsing, generated compilation, correct execution, and recoverability are distinct acceptance steps. The public runtime supplies only generic dates, numeric adapters, repositories, transactions, scheduling, and evidence export. Bank rules/report contents remain in SPP, never public libraries or templates.

## Implementation order and completion conditions

1. Define complete deposit/reversal scenarios, transaction modes, and pending/rejection scope in SPP; fill missing syntax/semantic checks.
2. Use or improve APP/PHY query/update, identity, idempotency, and common-resource bindings to generate persistence/startup. Reject incompatible modes/resources explicitly during generation.
3. Move handwritten Java business decisions/orchestration into SPP. Bind generic dates, participant initialization, and runtime behavior declaratively rather than adding demo implementations.
4. Provide declarative execution and independent expectations covering the initial three deposit categories and reversals, authorization, retry, later changes, cross-day behavior, version conflicts, and transaction faults.
5. Remove all handwritten demo Java for this phase; generate, compile, and run from SPP/APP/PHY in a clean directory, confirming boundaries/modes are readable directly in business source.

Before completion, old commands/results described old implementation only, not acceptance under the new constraint. Updated acceptance had to trace source declarations to generated artifacts, without manually writing or patching generated Java.

See [source-only implementation history](source-only-refactoring.md) and [APP repository writes](https://github.com/lidonge/spp-examples/blob/main/book/references/app-persistence.md).

## Intermediate historical evidence

At 2026-10-08 23:16, `mvn -pl :xbod-deposit-demo -am clean verify` succeeded. Log: `/tmp/spp-account-boundary-retirement-clean-validation.log`. All 948 JUnit tests passed—lang 303, appframe 536, generator 105, old demo 4—with zero failures/skips. All 83 APP entries passed, including actual concurrency and two independent JVM restart groups. Old/nested classes for JdbcDepositStoreTest, DepositPostingTest, DepositSagaRecoveryTest, JdbcDepositReversalStoreTest, and ExplicitSagaStep were absent. This evidence covered 83 entries, not the subsequently added cash-report source/84th restart entry. Remaining 13 main Java and two test Java files were still migration violations, so the historical goal remained active.

The complete deposit-report two-JVM run and JSON/Chinese-language Markdown fact checks then passed. DepositDemoReportTest was deleted, leaving 13 main Java files and one test file, CashDepositReversalTest with three methods. Its old class still awaited a clean build; removal was not yet claimed. Generic batch registration avoiding per-item full-catalog refresh passed 537 framework JUnit tests. The actual reversal report failed due to audit ambiguity from duplicate attempt identities; the source fixture identity was corrected and regeneration/verification was in progress. See source-report-migration.md.

## Final historical status — 2026-10-09

The source-only phase completed: zero handwritten main/test Java files, no residual classes from the old 14 main classes and 21 test families, clean generation/compilation, 945 public JUnit tests and all 86 APP entries passed. Five two-JVM groups, per-case input/authorization/source enhancements, six settlements/48 entries/five unexecuted candidates, and four reversals/32 inverse entries/20 quantum rows were checked.

The authoritative phase evidence is [final verification](source-only-final-verification.md). Earlier sections preserve intermediate history and scope limitations, not a claim that today's demo prohibits technical Java tests or has been rerun with these historical counts.
