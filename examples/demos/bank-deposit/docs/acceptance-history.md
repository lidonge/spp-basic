> Historical document: this describes pre-refactoring implementation and acceptance, not current source/test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Historical acceptance records

These records preserve evidence and limitations at each stage, not today's completion state. See [acceptance audit](acceptance-audit.md) for the later assessment.

## Checkpoint awaiting final clean acceptance

At this checkpoint, all handwritten demo Java had been deleted—main 0, tests 0—but final clean acceptance was still running. Complete deposit/actual-reversal reports had passed two-JVM execution and exported-fact checks. Final acceptance still needed the latest per-case provenance enhancements, all 86 source entries, public JUnit tests, and absence of every old/nested class. Log: `/tmp/spp-source-only-final-clean-validation.log`. The historical goal was not complete before that run finished.

The table and subsequent earlier-stage records preserve their original evidence. Old test names do not mean those files should remain. See [coverage responsibilities](source-example-coverage.md), [refactoring history](source-only-refactoring.md), and [report migration](source-report-migration.md). Final source-only acceptance required zero handwritten Java, no old bytecode, and every source entry passing new clean verify.

The table follows the first three transaction categories and P1–P4 in requirements-and-functions.md. Component completion is not complete transaction acceptance; test counts cannot replace the specific evidence below.

| Requirement | Checkable evidence at the recorded stage | Assessment and gaps |
| --- | --- | --- |
| Independent subproject; documentation before implementation | Root/module POMs, requirements/functions/path/comparison/decisions/source map, run-demo.sh | Established; retain stage history without treating early missing features as later status |
| Ordinary/non-performing/written-off repayment and balance relationships | DepositKernelTest, DepositReversalAmountsTest, actual generated cboddeposit services/models | Selected calculations verified; source formulas and golden runtime values distinct; original COBOL not executed |
| Numeric layering and three-decimal amounts | PHY double as BigDecimal, three-decimal mathematics/JDBC settlement, explicit interest/fee rounding | Selected representation/input verified; yen legacy checks and PIC truncation uncertainties recorded; no all-currency compatibility claim |
| Shared admission/product/authorization/warnings | bankAdmissionRulesExampleRun / cashAccountControlExampleRun; bankQualificationRulesExampleRun / bankWarningCashExampleRun / bankRejectedCashExampleRun replacing deleted old tests; NormalCashDepositTest | Selected source subset executed; real bank queries/permissions and complete priority among multiple invalid-message conditions unavailable or unimplemented |
| Ordinary/agreement/overdraft accrual bases | bankCalendarExampleRun, agreementProductExampleRun, datedAgreementExampleRun, depositAccrualRulesExampleRun / depositAccrualCashExampleRun / depositAccrualInvalidExampleRun / depositAccrualWriteFailureExampleRun replacing old tests; CashAccountRecord/CashDepositTrade | Saved with deposit accounting; agreement lookup/disable and recorded source-call differences still uncovered |
| Fees and business entries | DepositFeeTest, DepositPostingTest, actual deposit_ledger, independent CLI expectations | Demo rules verified; ART/actual rates explicitly unavailable-configuration fixtures, not an added requirement to deploy production |
| Three deposit categories from query to commit | NormalCashDeposit, ten DepositDemoMain cases, DepositDemoReportTest | General, ordinary/non-performing debt and exact amounts executed, including admission/authorization/fees/accrual/accounting/current facts |
| Deposit failures, retries, concurrency | JdbcDepositStoreTest, NormalCashDepositTest; account/fact/entry/audit assertions | Local shared JDBC commit verified; graceful reopen and in-process faults are not actual crash/network tests |
| Original queries, core reversal eligibility, inverse components | DepositReversalPreparationTest, DepositReversalEligibilityTest; generated atomics/scenarios | Actual original settlement/current eligibility verified; ordinary cross-branch deposit and reversal paths treated separately; versions do not replace eligibility |
| Original-entry-driven inverse accounting and refund input | prepareRecordedDepositReversalPostings, actual original ledger, inverse-candidate tests | Six core inverse categories and explicit refund bounds verified; earlier component inputs retained; formal cash entry refunds original XBOD KBN actual fees |
| Financial reversal component commit, repetition, concurrency | JdbcDepositReversalStoreTest; deposit_reversal/ledger, original association, file reopen | Actual component settlement/fault evidence; fixture lacked formal cash accrual/qualification facts, so not complete cash reversal |
| Complete three-category cash reversal | CashDepositReversal, CashDepositReversalTest, CashReversalDemo, actual report | Selected account model joins eligibility, daily totals/details/dates, accrual, and inverse entries in one commit; peripheral limits/print fields excluded |
| Cross-day reversal | Cash end-to-end, latest/subsequent detail dates, 24-hour mode, daily reset, EN097, actual report | Selected model follows effective XBOD source: ordinary accrual subtracts original retrospective component without recomputing later history; overdraft days retained |
| Complete reversal refund policy | selectXbodCashReversalFee, original KSAFEE0 5500, actual fee entries | Counter cash path refunds original actual charge without querying rates again |
| Quantum inverses preserve later effects | Financial inverse/submission tests and independent later-deposit Saga case | Selected components verified; not arbitrary numeric fields or all non-quantum updates |
| Non-quantum dependencies and compensation | default-reversal-design.md, rollback-reversal-compensation.md | Classification agreed; generic tracking/compensation unimplemented. Account-opening inverse outside first-batch scope, but capability limits remain explicit |
| Transaction modes/recovery comparison | JDBC rollback, native Saga financial binding/persistent events, comparison documentation | Local joint commit and explicit recovery evidenced; quantumStore/generated SPP transaction/cash and counterparty quantum records share one JDBC connection, not cross-store quantum deployment. Cross-process Saga reconstruction/automatic scanning not evidenced |
| Original COBOL equivalence and limits | source-map.md, source documents, cobolExecuted=false/bankConfigurationObtained=false | Source derivation, mathematical targets, fixtures, legacy differences explicit; no original runtime or golden equivalence claim |
| Traditional comparison | Stage-by-stage same-contract comparison in spp-vs-cobol-java.md | Responsibilities, benefits, and costs described; no measured performance, labor, or production-cost conclusion |

## Completion thresholds retained at that stage

Selected-model cash reversal, repetition, and cross-day flows were completed, including quantum properties, generated transactions, same-connection quantum records, and rollback. A full regression passed 934 tests: lang 255, appframe 445, generator 91, demo 143; zero failures/errors/skips. The CLI produced four actual cash reversals, 32 inverse entries, and 20 total deposit/reversal quantum records. See cash-reversal-report and quantum-report.

This covered the selected model/fixtures only. Original COBOL remained unexecuted, real ART/rates unavailable, peripheral institution limits/printing unintegrated, cross-process Saga recovery and cross-store quantum services unimplemented. Generic quantum-boundary inversion was not a substitute for bank CashDepositReversal. The deposit-phase report.json goalComplete=false was a phase snapshot, not proof of equivalence to the whole original system.

Production deployment, all bank products, transfers/collections, every transaction backend, and generic lifecycle inverses were outside the first batch. Preserve gaps honestly without endlessly expanding the historical goal. Explain and verify Quantum/Saga within selected resources; generic runtime tests cannot replace bank integration evidence.

## Incremental source-only evidence

After fixing empty-catch generation, all 18 APP source entries actually executed and passed. Earlier false-positive conclusions for four entries were withdrawn and rerun. Source 99n verified actual rollback after entry-write failure, independent failure audit, and original-request retry. Source 99o verified 30 date groups and two interest-unit calculations, replacing the deleted calendar Java test. Fourteen main Java and 20 test Java files remained; source-only completion was not claimed. Final clean-generation responsibilities remained in source-language-only.md.

Kernel/agreement increment: source entries 99p/99q passed and replaced two Java tests, leaving 14 main/18 test files. Kernel coverage included all original 16 parameter groups and input immutability; agreement covered 17 independent datasets and per-slot outputs from all six old methods. Post-deletion clean build passed 989 regressions and all 20 source entries, with three migrated old test classes absent. Old test-method counts were not source acceptance metrics.

Date-window/control/admission increment deleted three more Java tests. Independent expectations in 99r/99s/99t passed; clean verify passed 969 regressions and 23 source entries. Six migrated test classes were absent. Remaining inventory: 14 main/15 test Java files; source-example-coverage.md tracked remaining responsibilities.

Bank qualification/cash policy deleted two more tests after 54 qualification-fact groups, original-warning replay, two no-update rejections, and eight XBOD date/fee-policy groups passed. Clean verify passed 956 regressions and 27 source entries; eight replaced test classes were absent. Fourteen main/13 test files remained; overall acceptance incomplete.

Fees/financial inverses replaced two further tests. Sixteen fee groups using real repository rates and ten inverse-amount expectations on current balances passed. Clean verify passed 944 regressions and 29 source entries; ten replaced test classes were absent. Fourteen main/11 test files remained.

## Accrual and transaction-outcome source acceptance

The 2026-10-08 clean verify passed all 951 JUnit tests—lang 290, appframe 494, generator 101, remaining old demo 66—with no skips. All 34 APP entries separately passed. Four new accrual entries replaced six responsibilities of DepositAccrualTest; that file was deleted and all eleven retired test classes were absent.

Generic outcome evidence covered successful rollback before commit, both actual effects around failed commit confirmation, rollback failure, post-commit cleanup failure, partial multi-resource commit, joining the same resource, recovery failure, and original-exception preservation. Source 99c determined bank audit events; seven paired facts in 99zb checked classification. Constructed facts did not replace actual physical fault injection. Complete bank commit-point/outcome checks, remaining reversal/recovery/reports, and removal of all Java were still pending: 14 main/10 test files remained.

Core reversal eligibility's final clean evidence: `mvn -pl :xbod-deposit-demo -am clean verify` succeeded with 947 JUnit tests—lang 290, appframe 494, generator 101, remaining demo 62—and no skips. All 36 APP entries separately passed. All twelve replaced test classes, including nested classes, were absent. New core-eligibility/current-fact cases were generated/executed after clearing target. Fourteen main/nine test files remained; the historical goal stayed active.

The remaining eight read-only financial/entry-preparation responsibilities, actual deletion/evidence corruption, commit-point/outcome checks, concurrency, Saga/compensation, and reports continued. No new demo Java was introduced and no bank queries/eligibility logic moved into public runtime Java. All counts here remain historical; later final evidence is linked from the acceptance audit.
