> Historical document: this describes implementation and acceptance before the
> refactoring, not current source or test results. See the [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Retirement responsibilities for legacy deposit-store tests

Historical status: the ten old `JdbcDepositStoreTest` methods were migrated and
deleted. Clean verification without skipped tests passed: 948 JUnit tests and
83 source entries. Old test classes, including nested classes, were absent.
Legacy fixture dependencies exposed by deleting posting tests were removed
without adding banking Java implementations.

| Old responsibility | Source evidence / remaining work at that stage |
| --- | --- |
| Joint commit of debt, fractions, original facts, fees and eight postings | Debt cash, StoredFractionalPreparation, actual postings and query entries passed; PHY determines source precision |
| Replay does not update; different requests cannot reuse identity | replayCashExample, identity rejection and cross-JVM pending authorization/continuation passed |
| Stale plans and forged original snapshots cannot settle | cashVersionConflictExampleRun checks stale versions; forgedCashSnapshotExampleRun passed same-version original-snapshot tampering to 999, rejection/full rollback and fresh preparation settling 700; 91/92/93 revalidate under locks inside the full quantum transaction |
| Rollback after account, partial posting and pre-commit faults | cashWriteFailure, partialCashPosting and actual JDBC beforeCommit source faults passed |
| Two trade identities racing on one version produce one trade | Same-identity cash and different reversal identities passed; differentIdentityConcurrentCashExampleRun used two real threads and a post-preparation barrier, yielding exactly one trade and one version conflict; fresh preparation allowed both identities to settle, balance 800 and four quantum records |
| Same identity concurrently produces one trade and one replay | concurrentCashLifecycle passed with real threads and a preparation barrier |
| Durable rollback audit and separate attempts for stable-identity retry | Cash fault entries and independent history queries passed |
| Response failure after commit must be queried/replayed | afterCommitCashFault passed with an actual JDBC connection fault |
| Exceptions before/after the actual commit call are classified as unknown | commitBefore/commitAfterCashFault passed durable-evidence and stable-identity continuation checks |
| Audit failure cannot hide primary failure; no trade without primary audit | depositAuditUnavailable/rejectedDepositAuditUnavailable passed actual dual-fault checks |

Account revalidation belongs inside SPP's full cash-commit transaction, reusing
APP locked reads and `sameFacts` where appropriate. Do not add pre-commit business
checks in handwritten Java. A version CAS alone does not establish original-fact
revalidation. Quantum increments and ordinary account snapshots have separate
responsibilities: this demo still version-updates full business records.
Overwriting a stale snapshot cannot replace a quantum increment.

Old tests were deleted only after their replacement source evidence passed.
The final clean build checked absence of old classes. At this stage 13 main and
two test Java files remained; the overall historical goal was not complete.

## Recorded clean-build evidence

On 2026-10-08 at 23:16, `mvn -pl :xbod-deposit-demo -am clean verify` succeeded.
Log: `/tmp/spp-account-boundary-retirement-clean-validation.log`.
All 948 JUnit tests passed with no failures or skips: lang 303, appframe 536,
generator 105 and old demo 4. All 83 APP entries passed, including actual
concurrency and two independent-JVM restart groups.

Old classes and nested classes for JdbcDepositStoreTest, DepositPostingTest,
DepositSagaRecoveryTest, JdbcDepositReversalStoreTest and ExplicitSagaStep were
confirmed absent. This evidence covers 83 entries, not the subsequently added
cash-report source and 84th restart entry. The remaining 13 main and two test
Java files were still migration obligations under the rule in force then;
the historical goal remained active.

These are recorded claims from revision `417a41f`, not tests rerun for this
translation. Later contribution rules permit technical/input/assertion Java
tests; they still prohibit handwritten production banking Java.
