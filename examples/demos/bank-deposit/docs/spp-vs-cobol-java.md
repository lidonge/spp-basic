> Historical document: this compares pre-refactoring implementation/acceptance, not current source or fresh test results. See [current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# SPP versus traditional COBOL/Java

Compare identical business inputs/policies, adding actual examples at each stage. Lines of code are not complexity measurements. No performance or development-cost advantage is assumed.

| Dimension | COBOL / conventional Java | SPP approach | Cost and verification |
| --- | --- | --- | --- |
| Repayment | Work areas/assignment semantics or numeric APIs mixed with rules | Unified participant mathematics; PHY representation | Identify PIC truncation; verify generated arithmetic |
| Shared rules | Subprograms/methods, often message/concrete-object interfaces | Role-contract checks/calculations; APP deployment | Reconstruct contracts; verify remote fact writeback/errors |
| Orchestration | Explicit paragraph/method calls/branches | Scenarios/groups share participants; checks bound execution | Verify authorization continuation/scope |
| Data/consistency | Framework/database transactions, logs, retries | Business plans/conservation separated from APP/PHY execution | Idempotency, conflicts, commit, recovery remain necessary |
| Reversal | Original logs drive inverse processing, often storage-coupled | Original-transaction Roles drive business reversal | Migrate actual components/accrual; framework rollback is not reversal |
| Tools/operations | Mature compilers/debugging/ecosystem | Readable declarations, inspectable generated models/metadata | Multilayer generation/provenance adds tooling costs |

## Initial evidence and comparison method

The early calculation prototype expressed ordinary/non-performing allocation and balance relationships. Fourteen source-derived cases passed statement generation/execution, proving mathematical expressiveness, not complete transactions, original-system equivalence, performance, or labor savings.

P1 compares same-input results/reference formulas; P2 shared checks/authorization/contracts; P3 failure recovery/repetition/concurrency; P4 business reversal versus technical rollback. Compare equal contracts, explicitly label missing behavior, and do not use an artificially simplified Java implementation to flatter SPP.

## Incremental rule comparison

**Agreement tiers.** The original uses five work slots and mutable amounts. SPP uses five participants, enhanced for, explicit quantization, and separate input snapshots/remainders. Six generated-service tests cover both modes/boundaries, not complete retrieval/date clipping at that stage.

**Fees.** Original institution queries, code selection, customer rates, COMP-3 work areas, rounding, and limits are separated into SPP classification/formulas and APP rate lookup. Generated scenarios prove queried facts reach calculations without business deployment addresses. Java can use the same layering; SPP supplies Role contracts, generated calls, and numeric bindings. Generated code/metadata/source mappings still need checks; no speed/labor measurement.

**Posting and updates.** SPP determines principal/two-interest repayments, deposit deltas, and entries, then builds independent account snapshots. JDBC applies plans/version conditions/joint commits. Six DB tests cover local failure/repetition/concurrency—the same local guarantees Java can provide, not a consequence of mathematical syntax or added distributed safety. Separation removes JDBC formulas but adds plan/settlement/generated-contract maintenance. Request idempotency/audit/full eligibility-to-update were initially pending.

**Ordinary cash entry.** Generated scenarios compose selected eligibility, authorization, fees, and posting; APP handles request identity, queries, and joint commit. Nine complete-operation tests cover pending rechecks, replay after later settlement, and daily accumulation. Java can do the same; SPP keeps thresholds/amounts in business declarations. A real cost emerged: generated constructors locate child services, requiring dependency-ordered assembly. Compilation/atomic tests cannot replace scenario execution. Approval permissions, failure audit, full eligibility, and cross-day reversal were not yet complete.

**Audit.** Success audit commits jointly; failure audit independently; unknown commit differs from failure returning after committed success. APP/PHY's explicit transaction/error handling supplies these guarantees, equally available in Java. Four added DB fault tests show business declarations alone cannot prove recovery; audit-store failure and outcome lookup need verification. Permissions/full eligibility/cross-day work remained.

**CLI reports.** Actual generated scenarios write file H2, then query entries/settlements for reports; graceful reopen is tested. Reports distinguish source-formula expectations, exact business math, and demo configuration. Persistence/recovery advantages cannot be inferred from choosing SPP. Its separation of business expression/physical numbers still requires generation, assembly, and fact-contract verification.

**Accrual.** SPP expresses ordinary/agreement and overdraft periods, jointly saving state/original components/accounting. Six tests prove product 360 cannot replace overdraft ACT, pretransaction amounts cannot be replaced by updated values, and rollback must include accrual. Java needs the same checks; SPP composes Roles/math without APP BigDecimal formulas. Explicit units, implicit PIC truncation, call conditions, and original first-tier save thresholds must still be audited.

**Inverse amounts.** Original-settlement Roles express inverse components with current eligibility; seven tests preserve subsequent operations. Java can also apply correct negative deltas. Unbound numeric syntax cannot choose refunds, retrospective interest, or non-quantum lifecycle eligibility; default formulas do not automatically undo arbitrary business.

**Cross-institution/loss warnings.** Original 9704/9709 account/institution/teller/exceptions become shared Role conditions. Separate warnings compose with eligibility/authorization/fees/accrual; the Java entry passes facts/persists settlement. Eight tests prove an ordinary-scope exception does not exempt an independently restricted subtype. Decode contracts, exception scope, and generation require joint checks; syntax/types do not prove bank-policy correctness or performance.

**Settlement lookup.** APP reads committed originals/current accounts; generated scenarios check amount relationships and form candidates. Both approaches need reliable records/current state. Versions after reading are only execution evidence: preparation is not submission-time lock/version validation, and repeatable read-only preparation is not reversal idempotency. Complete reversal still needs original association/new identity/entries/audit in one commit.

**Inverse entries.** SPP reverses actual recorded accounts/amounts, accepts explicit refund decisions, and checks balance. Java also must compare payload/ledger, separate core/fees, and preserve originals. Composable Roles do not eliminate ledger-format/refund/transaction contracts or solve cross-day/non-quantum dependencies.

**Financial-component commit.** SPP builds a new account from current facts/inverse amounts; JDBC saves independent reversal, unique original association, entries, current-version protection, and success audit. No extra DB guarantee arises. Separate business journals add lookup/audit-aggregation costs. Component fault/concurrency success does not prove complete bank reversal/accrual/Saga recovery.

**Core eligibility.** Shared loss checks combine with separate closed/canceled/frozen rules, not the entire ordinary-deposit group. Both implementations must distinguish those paths. SPP makes composition/contracts explicit, but effective source call sections must still be checked. File readback/replay identity come from JDBC, not SPP crash/performance superiority.

**Saga.** Native strategy invokes explicitly bound business inversion, preserving original/later deposits. Recovery failure does not invent compensation. SPP supplies financial/entry plans; APP binds local transactions, settlement queries, and recovery journals. Java Saga likewise needs identities/eligibility/retries. An interface named rollback does not imply global technical rollback. Persisted evidence helps queries; cross-process callback reconstruction/scanning still costs implementation. Only explicit in-process retry was then verified.

**Cash reversal/quantum transactions.** Effective XBOD dates/daily totals/accrual/fees enter independent scenario source. SPP declares quantum net customer balance, signed counterparty, and transaction. Accounts/facts/entries/audit/quantum records share JDBC and roll back together. Java can provide identical single-store guarantees. Conservation/math/boundaries are inspectable, but object identity, ordinary-state joint commit, and representation must be implemented—not just a quantum keyword. During overdraft repayment, interest-bearing balance alone does not conserve with cash; net balance closes the counterpart relationship. Generic boundary inversion is not complete cash reversal. No cross-store/performance/cost measurements were claimed.

## Source lifecycle comparison

After fixing empty-catch generation, 29 APP entries actually reran successfully, covering ordinary deposits, three debt states, authorization continuation, retries, stored rates, rejection audit, current-account rechecks, cash reversal, conflicts, post-write faults, calendars, kernel, and agreement boundaries. Earlier false positives were withdrawn; corrections are in source-only-refactoring.md.

| Aspect | Then-current SPP/APP/PHY | Old Java driver | Limitation |
| --- | --- | --- | --- |
| Order/state | Pending, settlement, retry, rejection, source catch visible | Java selects scenarios/assembles results | Migrate every branch; generation alone is insufficient |
| Boundaries | Separate SPP quantum deposit/failure-audit boundaries | JDBC store/entry wrapper chooses scope | Generic outcome evidence/fault injection needed |
| Queries/persistence | APP optional queries, composite identity, inserts, revisions; PHY resources | Bank Java lookup/save/register/commit | Less bank technical code, greater compiler/runtime responsibility |
| Numbers | Business arithmetic/comparison; PHY BigDecimal | Concrete Java numeric APIs | Numeric extension/rounding responsibilities remain explicit |
| Acceptance | SPP fixtures/steps/independent expectations; APP entries run during verify | Java tests/manual assembly | Verbose nested initialization; do not hide defaults merely to shorten fixtures |

At the 29-entry checkpoint, handwritten Java remained. Advantages were limited to actual covered scope. Mature Java debugging/testing was more direct; source-only tooling still needed equivalent generation/fault/concurrency support.

Generation introduced a specific risk: swallowed template rendering exceptions made empty methods appear successful. Pre-generation checks and generic regression prevented scenario output on error; fault entries also proved the target was reached. Direct Java compilation lacks this template risk but still needs execution evidence. Inspectable declarations cannot replace compiler/template/runner verification.

**Calendar migration:** 32 SPP input/expectation groups replaced Java fixtures/registration/exception assertions and four old tests. Shared business language reduced numeric-API/assembly work but lengthened fixture/call setup. Test-method counts are not coverage or measured labor/performance benefits.

**Kernel/agreement:** two more old test files removed; SPP checks amounts, input immutability, per-slot quantization, and no changes before rejection. Collection construction/enhanced for/switch handle fixed-slot rules; APP lists local services/entries. Java parameterized tables may be more concise. Twenty clean-generated entries passed, not concurrency/commit-fault acceptance.

**Window/admission:** eight date groups and 49 admission groups invoke source services, replacing three Java tests. Participant facts/SPP conditions express clipping, warning scope, strict thresholds, and channel exceptions; APP does not choose business branches. Fixed cases remain verbose despite rule-based file organization.

**Bank qualification:** 54 relationship/permission/warning groups plus settlement/retry/rejection use source acceptance. Original warnings/teller/account evidence persist and replay reads originals rather than recalculating changed warning conditions. Eight cash metadata/fee groups additionally prove expressiveness, not every account reversal, complete institution limits, or distributed guarantees.

**Rates/inverses:** real repository lookup replaces Java Map fixtures; classification/discount/half-up/bounds stay SPP. Current-state inverses use original components, not new loan-status allocation. Sixteen fee/ten inverse groups prove those responsibilities only, not whole reversal/distributed recovery. Generation/provenance and storage/recovery design remain costs.

**Accrual/outcomes:** windows, calendars, quantization, original periods, and retries move to SPP; generic APP faults replace bank JDBC proxies. Physical runtime supplies measured outcome evidence; syntax cannot eliminate lost commit confirmation or turn compensation into rollback. No fully remote/multi-DB atomic guarantee was verified. Final clean passed four accrual entries/seven audit-policy groups; six old accrual tests were removed, leaving 14 main/10 test Java files. An initial independent entry expectation incorrectly said total 50; actual funding was cash 50 plus deposit reduction 120, yielding debits/credits 170. Correcting that business expectation preceded success. Unified syntax does not eliminate analysis errors or increase database consistency.

**Core preparation:** source scenarios replace Java query wrappers/mutable-operator copies; APP binds repositories. Candidates versus commits/core eligibility versus front-end thresholds remain distinct. Cross-file field-definition order adds sorting/debugging costs. Clean evidence later verified current frozen exception at version 3/balance-accrual 800, original 600 candidate net 200, cancellation advancing version 4 then rejection, and no preparation writes. Original eight entries/success audit/unreversed link stayed unchanged. Four old eligibility tests removed; 947 JUnit/36 entries passed. This did not authorize cash reversal wherever core eligibility passed.

**Original entries:** SPP atomics/APP persist replace test JDBC DELETE/UPDATE, comparing stored facts with immutable settlement evidence. Generic deletion, native-mapping consistency, and string concatenation still required implementation. After 44 entries passed, the old eight-method preparation test was deleted. Final clean passed 948 JUnit/44 entries and found no classes for 13 retired tests. Query/evidence/capacity-refund copies/inverse math and fixture quantum/update/delete boundaries were traceable; preparation remained read-only. Fourteen main/eight test Java files remained. Large generated models/services added compile time, not measured production inferiority/superiority.

**Concurrency:** generic APP barriers replace bank CyclicBarrier tests; SPP parallel invokes independent participants while the framework proves arrival. Bank source still asserts unique settlement/audit. Forcing both paths to read absent originals exposed a real key conflict; source 98 added failure-time original lookup/replay. Corrected verify passed 952 regressions/46 entries, proving one failed transaction actually rolled back and subsequent lookup added only replay audit, preserving unique accounts/entries. This covers declared common storage, not all backends/topologies/conflicts. Pending cross-instance authorization, Saga/recovery, and commit outcomes remained; 14 main/eight test files remained.

**Cross-JVM pending identity/unsettled collision:** clean verify passed 947 regressions/48 entries; old nine-method cash test removed. Two fresh JVMs/shared PHY file store/empty participants strengthen process-isolation evidence over recreating a repository object. SPP describes pending/rejection/authorization/replay; the runner only arranges processes/storage. Startup, configuration/classpath, logs/timeouts add costs, not production-performance proof. Existing-entry errors cannot imply replay without actual original settlement; the absent-original counterexample passed. Fourteen main/seven test files remained; Saga/commit/report migration incomplete.

**Deposit/reversal commit points:** 59 entries passed using generic APP jdbcFault instead of bank DataSource/Connection proxies. Four measured outcomes, source queries, stable identities, and separate quantum-versus-business-reversal semantics remained. It generated/compiled 3,104 Java files; full reactor verify took 9 minutes 38 seconds—project evidence, not a fair traditional-code benchmark. Scope was local H2/process isolation, not throughput/arbitrary backends/all Saga faults. Fourteen main/seven test files remained.

**Concurrent reversal:** 61 root entries passed using SPP parallel, APP barriers, and source-query recovery. Stable identities/actual storage remained essential. Initial failure exposed catch preservation of original exceptions: handling failure differs from a new business rejection based on known facts. The latter moved after catch while retaining failed-transaction evidence. Sequential replay alone cannot prove this. Tiny-value storage, Saga, and reports still required migration.

## Aggregate reports and source migration — 2026-10-09

SPP defines business facts, independent expectations, and candidate/settlement distinction; APP defines queries/persistence/two-JVM groups; the runner exports generic JSON/Markdown. Java could provide identical results. Traceability avoids separate bank Java report templates but adds Role initialization/fixed-slot/snapshot-copy verbosity, generated files, and cross-file dependency ordering.

Complete deposit reports verified ten cases, six settlements/48 entries, five unexecuted candidates, and file-store readback. Actual reversal exposed two costs: per-service full-catalog refresh caused timeout, fixed by generic batching verified with 537 framework tests; reused deposit/reversal attempt identities caused audit-query ambiguity, requiring SPP identity correction, not weaker queries/assertions. Unified syntax does not solve identity design automatically.

Identity-corrected incremental package succeeded in 10 minutes 53 seconds, log spp-source-report-unique-attempt-generation. Again this was a project build, not comparative performance. Complete reversal, per-case provenance, Java removal, and final clean were still pending at that checkpoint; existing report success did not prove production replacement, original COBOL equivalence, or every database.

The complete reversal report subsequently passed: four reversals/32 inverse entries/20 quantum rows and source-query recovery checked. JDBC connections close normally; default file stores remain open within each child JVM, close on process exit, and reopen in the next. The 60-second limit remained. All original bank Java was deleted; final clean with enhanced report assertions was running. Public-runtime searches found no bank class names, KSADEP0/GSA01102, or EN432/F0554 hardcoding: business was not moved from Java into the framework.

## Final historical status — 2026-10-09

Source-only refactoring completed: handwritten main/test Java zero, no residual classes from 14 old main classes/21 test families, clean generation/compilation, 945 public JUnit tests and all 86 APP entries passed. Five two-JVM groups, per-case input/authorization/provenance, six settlements/48 entries/five candidates, and four reversals/32 inverse entries/20 quantum rows were checked.

See [final verification](source-only-final-verification.md). Earlier paragraphs preserve stage-specific evidence/limitations, not current test results or a continuing prohibition on later technical Java acceptance tests.
