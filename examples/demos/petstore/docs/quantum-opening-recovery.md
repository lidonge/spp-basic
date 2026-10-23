# Technical recovery for cross-database zero-record creation

The business entry is one `insertBuyer`. Quantum attributes belong to Buyer, but
APP/PHY determine where their zero records live. Business scenarios do not split
ordinary insertion from quantum-record creation. Ordinary maintenance creates
only zero records; nonzero fund changes still use conserved quantum services.

## Implementation

Generated CRUD uniformly uses JdbcObjectRepository. The generic runtime handles
this behavior without Petstore business Java or rewritten SPP scenarios.

Ordinary and quantum tables sharing one DataSource retain a joint local
transaction. For different DataSources, insert the local ordinary snapshot, write
the APP role table and enqueue zero-record creation in
`<snapshot_table>_quantum_openings`. All three commit in one local transaction.
Rollback removes the task before any remote creation request; savepoint rollback
also prevents dispatch of rolled-back tasks.

JdbcTransactionScope runs technical callbacks after actual commit, unbinding and
closing the local connection. A callback reads committed tasks, confirms the
local owner exists, creates remote zero records with idempotent missing-only
operations in an independent technical transaction, then removes the task. It
does not borrow a querying caller's quantum transaction, whose later rollback
could otherwise remove a zero record after its task had been deleted.

Each quantum attribute has a separate task, allowing safely replayable partial
completion. Before completion, the owning transaction may read its newly created
participant's pending facts as zero. Reads outside that transaction first finish
technical recovery; missing records are not disguised as complete query success.

If the quantum database is unavailable after commit, the callback throws
QUANTUM_OPENING_RECOVERY_PENDING, retaining local role and durable task. APP/PHY
technical handling owns this error. Completing zero records inside this service
does not require an SPP business inverse; other already committed scenario
services still follow existing transaction recovery rules. Recovery retries when
startup connects the repository, a participant is queried, or
JdbcObjectRepository.recoverQuantumOpenings() is called. It does not depend on
in-memory jobs, timers or automatically created background threads.

Full service success requires callback completion. A technical error does not
mean every local write rolled back. If remote creation succeeds but its response
is lost, or task deletion fails, retry never resets an existing quantum value.
Zero creation produces no financial transaction entries. Even with a lost local
commit response, role and task either both remain absent or both persist.
Restart queries real database facts rather than guessing the commit outcome.

## Scope and cost

The built-in ordinary JDBC repository and JDBC quantum store support this
recoverable cross-database creation, not simultaneous cross-database commit.
Damaged persistent media, orphan-zero cleanup from older versions and other
provider protocols are outside automatic repair scope. An externally owned JDBC
connection requires its caller to drive technical recovery after committing;
the framework cannot infer that owner's commit result.

Traditional approaches often assign this failure to business compensation or
use only in-memory callbacks. Here the task persists with the local role and
recovery stays in APP/PHY. The cost is a technical table and remote creation
round trip; interruption requires recovery before a complete participant can
be supplied.

## Historical verification

QuantumOpeningRecoveryTest checks local rollback without remote records, remote
creation only after local commit, restart recovery after quantum-store failure,
lost remote response without overwriting settled values, lost local commit
response followed by restart, same-database joint rollback, savepoint rollback
without dispatch, post-commit recovery for external connections, and technical
replay isolated from unrelated quantum-transaction rollback.
DatabaseInitializationTest preserves startup missing-only compatibility.

The recorded full framework regression passed 589 tests with zero failures,
errors or skips, including eight new recovery tests; log
`/tmp/spp-opening-appframe.log`. The first testing three-JVM full web/restart flow
passed wallet, per-SKU stock and per-transaction conservation checks; evidence
`/tmp/spp-bootstrap-testing-8jone6s4/`.

After the final technical-transaction isolation change, packaging succeeded
(`/tmp/spp-opening-testing-package.log`). The testing three-JVM full web flow and
shop restart passed again with browser exit 0; final evidence
`/tmp/spp-bootstrap-testing-64239ev0/`. Test processes were stopped without
changing existing resident services/databases. A final read-only ledger audit
after restart also passed total funds, per-SKU totals and each transaction's
conserved deltas: `/tmp/spp-bootstrap-testing-64239ev0/ledger-audit.log`.

These are historical results preserved from revision `417a41f`, not tests rerun
for this English translation.
