> Historical document: this file describes implementation and acceptance before
> later refactoring. It does not establish current source behavior or test results.
> See [the later refactoring](latest-petstore-refactoring.md); old sources are
> retained under `legacy-source/`.

# Final source-language-only acceptance checklist

Historical status: final `clean verify` and artifact-by-artifact checks passed.
The record reports zero handwritten Java files, 945 shared JUnit tests passing
and all 86 source entry points passing. See
[final verification](source-only-final-verification.md). These are historical
results, not tests rerun during translation. Later rules permit Java acceptance
tests and supersede the historical zero-handwritten-test-Java target.

The scope follows the [requirements](requirements-and-functions.md),
[language constraints](source-language-only.md) and AGENTS: ordinary cash deposits,
normal and impaired overdraft repayment, decimal-math cases and corresponding cash
reversals, including P1–P4, recovery and the original acceptance obligations.
Completing this refactoring is not equivalent to replacing a production bank system.

| Requirement | Authoritative historical evidence | Final acceptance criterion |
| --- | --- | --- |
| Business in SPP, deployment in APP, physical realization in PHY | All `src/main/spp` sources; `cboddeposit.app`/`.phy`; Java inventory | No handwritten main, test, startup, repository or helper Java; generated Java only in `target` |
| Complete transaction boundaries and modes | 92 commitCashDepositRecords, 93 commitCashReversalRecords; identity/audit boundaries in 98/99b; Saga in 99zv | Accounts, deals, eight postings, associations, audit and quantum values commit together; the source exposes the mode |
| P1 numbers and components | Kernel 00; 99p/99pa/99pb, 99y; PHY numeric double as BigDecimal | Independent assertions for interest-before-principal on normal debt, principal-first on impaired debt, holds/authorization, principal bounds, decimals and balance relationships |
| P2 shared business rules | 20/30/40/50/60; 99o/99q/99r/99s/99t/99u/99v/99x | Dates, tiers, acceptance, refusal, warnings, authorization rechecks, charges and bank qualification pass |
| P3 end-to-end deposits | 96/97/98; 99d/e/f/h/i/zh and complete reports | Cash, debt and decimal cases run from queries to actual persistent updates; six deals and 48 postings; pending authorization and replay do not update twice |
| P4 original-deal-driven cash reversal | 70/90/99/99b; 99j/k/l/w/zu and actual reports | XBOD same-day/cross-day and overnight Mode2; driven by original amount, refunds and postings; preserves later deposits and current accumulated balances |
| Original evidence and plan revalidation | Locked queries inside 91/93 commits; 99ze/zf/zg/zu/zx | Rejects original-voucher tampering, stale versions, same-version snapshot tampering and identity reuse without partial writes |
| Concurrency and identity | 99zi/zo/zx and APP barriers | Two competing executions actually arrive; one update and real rollback/query recovery; sequential calls are not substitutes |
| Transaction failure and audit | 99n/zl/zm/zq/zr/zs; 99c/zb | Four actual JDBC commit points, unknown-outcome queries, mid-posting failure, unavailable audit and preservation of the original exception |
| Quantum guarantees | 95, 99zp/zpa; native journal/value queries | No residual quantum changes after complete transaction failure; account isolation; rejection of incorrect original values; exact tiny amounts; conserved differences |
| Saga business recovery | Six root entry points in 99zv; APP compensate; PHY saga | Actual query evidence separately covers success, failure after commit, no deal, qualification refusal, retry and completion-record failure |
| Reporting and cross-JVM behavior | 99zya/b/c/d/da/e/f; five APP restart groups | Two fresh JVMs, empty participants and persistent file-database queries; ten cases, six deals and five unexecuted candidates; four actual reversals, 32 reverse postings and 20 quantum rows; per-case inputs, authorization, origins and actual replay returns |
| Migration of all original responsibilities | source-example-coverage.md and source-report-migration.md | The original 14 main classes, 21 test families and nested classes are absent; bank Java is not relocated into the framework or templates |
| Clean generation and regression | Final Maven state, Surefire XML, APP summary of 86 entries and JSON/Markdown evidence | Generate and compile from an empty target; no failures or skips among 945 shared JUnit tests and 86 entries; inspect actual artifacts individually |
| Documentation and comparison | Requirements, implementation path, decisions, source map and SPP/COBOL/Java comparison | Keep layering, implementation, language gaps, generic capabilities, benefits/costs and acceptance evidence aligned |

## Capability limits

The evidence covers only the selected paths and explicit fixtures. The original
COBOL was not executed; bank golden results and real ART fee rates were not obtained.
Yen and PIC-truncation differences remain; no whole-product or bit-for-bit
equivalence is claimed. Cross-database quantum deployment, production disaster
recovery, automatic cross-process Saga scanning, transfers, collections and
reversing account-opening lifecycle operations are outside the confirmed first batch.

The report's `productionReplacementVerified=false` describes production replacement
scope, not the completion state of the historical source-only refactoring goal.
Ordinary-debt, impaired-debt and decimal candidates were not committed. An actual
cash reversal is a separate new business transaction; Saga recovery is not the
same as a quantum technical rollback.

Earlier failures, corrections and build records are in
[historical acceptance](acceptance-history.md). Source responsibilities and original
logs are described in [coverage responsibilities](source-example-coverage.md) and
the [refactoring record](source-only-refactoring.md).

English translation of the document at examples revision `417a41f`. Numeric
results, source identifiers and limitations are retained as historical claims;
translation does not independently revalidate them.
