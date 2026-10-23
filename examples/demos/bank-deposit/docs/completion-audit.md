# Refactoring completion audit

This is the recorded refactoring audit from revision `417a41f`, not a report of
the current basic-SDK migration's test runs. It evaluated the working tree and
executions at that stage. Historical batches, fault investigation and repairs
remain in the [verification record](latest-refactoring-verification.md).
Historical pass counts must not substitute for a current regression.

| Requirement | Recorded implementation and evidence | Status at that stage |
| --- | --- | --- |
| Document requirements, functions, implementation path and comparison first | latest-petstore-refactoring.md: business constraints, six implementation steps and traditional-implementation comparison; legacy-source preserves original rules | Implemented |
| Business only in SPP/APP/PHY | src/main/spp; no handwritten production Java; Java tests limited to input, invocation, assertions and technical faults; generated output under target | Clean develop generation and regression passed |
| Participant/module modeling | BankAccount entity, account roles and explicit contracts; eight capability directories, 15 single-scenario files; stable participants hydrated in place | Migrated |
| Default CRUD, APP/PHY and storage | Default services, APP tables and automatic identifiers; develop LOCAL, testing shared quantum store plus ordinary Saga; APP-declared associated trade snapshots | Generation and actual JDBC persistence verified |
| Ordinary/quantum queries and settlement hydration | Default account queries, real-fund queries, reversal net-balance recomputation; historical snapshots not overwritten by current quantum values | Focused tests and clean develop combined regression passed |
| Explicit minimal transactions and call-site bounds | Queries/checks/calculations before the update boundary; quantum bounds at call sites; financial account opening separate from fund changes | Whole-scenario evidence in both environments |
| Cash and overdraft repayment | Normal/bad-debt/written-off/fractional cases; three internal bank receiving accounts; debt/exposure remain quantum without double-counting fund conservation | Both environments passed; KI-R01 closed |
| Preserve cash reversal rules | Same-day/cross-date/Mode2, fees, accrual products, details, limits, current eligibility; original eight-slot postings and inverses; reject corrupted ownership/amounts in original contracts | Actual persistence and rejection verified |
| Mathematics and logic | Independent historical vectors; focused calendar, bands, date-window, accrual, fee and posting checks | Verified, not equivalent to migrating every old entry |
| Failure information/disposition | fail/ResultInfo; deposit/reversal contract, posting and accrual faults; secondary disposition-record failure preserves primary cause; CLOB snapshot requery | Focused tests passed in both environments; ordinary Saga compensation remains KI-R04 |
| Technical services, idempotency and HTTP | APP invocationTrace; trusted remote serials; concurrent same-serial deposit/reversal; no local suppression; loopback HTTP and multi-participant UI duplicate guard | Accepted within recorded scope, excluding real network faults |
| Concurrency/restart | Independent/same-account races; stale reversal versus later trade; original-contract optimistic conflicts; queries/reversals across three JVMs; forced exit after confirmed commit | Specific paths verified; mid-commit crashes/unknown-commit automatic recovery remain KI-R02 |
| Per-entry historical reconciliation | All 86 entries in legacy-entry-migration.md have concrete evidence, differences or unmigrated explanations | Reconciled, not 86 equivalent implementations |
| Generation/regression | Public modules, XBOD and Petstore clean develop, then full XBOD testing | All 1,226 develop tests passed; testing 86 tests had zero failures and two LOCAL-only skips; later preview additions have separate focused evidence |

## Known issues and non-equivalence retained by agreement

- Independent read-only `previewCashReversal` was added, with two focused tests
  passing in each LOCAL/testing environment. Old generic partial-refund candidates
  are not the confirmed cash rules; arbitrary partial refunds remain unsupported.
  Cash preview is not equivalent migration of every generic candidate.
- Pending-authorization persistence and cross-process continuation await the
  authorization-domain boundary decision, KI-R07. Existing authorization checks
  are not a complete continuation workflow.
- Business compensation for committed ordinary Saga stages remains KI-R04.
  Restored funds do not imply restored ordinary facts. Preserve RECOVERY_REQUIRED;
  do not conceal residual state or automatically overwrite later operations.
- Automatic unknown-commit reconciliation, crashes during commit, abnormal
  pre-existing orphan-data collisions and real-network/high-load testing remain
  KI-R02. Four LOCAL commit-fault points and restart after confirmed commit cover
  only part of this scope.
- Durable technical audit for every call and bounded waiting for RUNNING requests
  remain KI-R09 and KI-R05 respectively. Trace logs and REQUEST_IN_PROGRESS are
  not substitutes.

## Recorded completion determination

The requested refactoring aligned syntax, participants, default CRUD, APP/PHY,
transactions, quantum bounds, failure disposition and technical services with the
then-current Petstore. Confirmed cash-deposit/reversal rules had source-language
implementation and persistence verification, with no Java in production business
directories. New internal receiving accounts supplied actual fund counterparties
for principal/interest repayment. Framework fixes had public tests and combined
Petstore regression evidence.

There were 15 independent scenarios. Clean develop combined regression, full
testing execution and focused preview checks in both environments passed.
Packaging and actual HTTP account opening through `run-demo.sh` passed.
Commands, logs, skip reasons and scope are in the verification record.

The user required unresolved issues to be recorded while implementation continued.
Accordingly, completion meant the requested refactoring, gap analysis and
regression, not an unagreed authorization domain, complete production bank
configuration, real-network recovery or equivalence of every historical
exploratory example. All 86 historical entries trace to specific evidence or
differences; test counts do not establish business equivalence.

Optional tracing, request replay and duplicate-guard claims in this historical
audit must not be interpreted as guarantees of the basic SDK without their
providers. This translation does not rerun the cited historical tests.
