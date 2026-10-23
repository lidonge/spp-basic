> Historical document: this describes implementation and acceptance before the
> refactoring, not current source or test results. See the [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Audit and failure outcomes

## Attempt records and completed trades

`deposit_trade` stores successful business trades. `deposit_audit` appends events
for each attempt with increasing sequence numbers. Neither replaces the other.
Each attempt has its own `attemptId`; one business request can pass through
pending authorization, elevated authorization, settlement and repeat queries.
Audit queries use the original request identifier. Records retain account
identity, event, error code, relevant input or plan payload, and database time.
A later result does not overwrite an earlier failure.

`deposit_request` retains the first request contents for resuming authorization
and preventing request-identifier reuse. It is request identity/audit metadata,
not a settled account, debt or posting. This technical metadata may survive
rejection or rollback without thereby restoring a business trade.

## Events and commit boundaries

| Event | Meaning | Persistence boundary |
| --- | --- | --- |
| PendingAuthorization | Business authorization threshold not met; no business updates executed | APP appends audit separately |
| Rejected | Current request rejected; code and that attempt's input retained | APP or conflict handling appends audit separately |
| PreparationFailed | Exception during query, input conversion or preparation | APP appends audit separately |
| Committed | New trade, postings and account state committed successfully | Success audit shares the business records' local transaction |
| Replayed | Same request returns an existing trade without updating the account again | Query/replay attempt appends audit |
| RolledBack | Attempt failed before commit and local rollback is confirmed | Independent audit through a new connection after rollback |
| CommitOutcomeUnknown | Commit threw, or local rollback cannot be confirmed | Preserve the exception; query trade evidence instead of assuming no trade occurred |
| CommittedResponseFailed | Commit returned successfully, then response handling failed | Preserve success audit and append this response-failure event |

Every failure event describes its own `attemptId`. It neither cancels nor
rewrites an earlier successful `deposit_trade`. In particular, a failed later
attempt does not mean the original trade was rolled back. Business reversal
still requires a new business identity and logic linking the original trade.

Before commit, the store inserts the Committed audit inside the same JDBC
transaction. Account, debt, qualification, postings, trade and audit all commit
or roll back together. Failure to write the success audit prevents the new trade
from committing, avoiding a trade without success evidence at the same boundary.

After failure, the store handles rollback first, then persists failure audit
independently. It does not commit the original connection merely to retain audit.
If failure audit also cannot be written, preserve the original exception and
attach the audit exception as suppressed. Do not swallow the original error or
turn failure into success. When audit storage is unavailable, persistence of a
failure event cannot be promised; the caller can observe the exception.
Preparation failures follow the same original-exception preservation rule.

## Historical verification scope

`JdbcDepositStoreTest` used real H2 business transactions and a separate audit
connection to cover audit without business results after partial posting failure,
independent retry history, response failure after success, a throwing commit,
and an unavailable audit table. `NormalCashDepositTest` checked audit contents
for business rejection, fee-query failure, pending authorization and replay.

Commit exceptions were injected through a JDBC proxy; response failure through
an `afterCommit` callback. These are explicit in-process fault models, not real
network-disconnection or database-crash tests. Technical rollback evidence is
limited to the local JDBC transaction. Cross-resource transactions, quantum
coordination, Saga and business compensation need separate verification; their
implementation cannot be inferred from audit-event names.

## Saga financial-component recovery events

At this historical stage, the financial component bound explicit business
recovery through the native Saga strategy. `SagaLogRecorder`/`JdbcSagaLogStore`
persisted events, queried separately from local business audit in `deposit_audit`.
`LOCAL_COMMITTED`, `RECOVERY_REQUESTED/DONE/FAILED` and
`RECOVERY_RESULT_QUERY_REQUIRED` do not replace queries of original trade and
reversal records. Completion logging may fail after R1 has settled; that failure
must not cause a new reversal to be generated. See [Saga recovery](saga-recovery.md)
for scope, stable identities and recovery tests. There was no evidence of an
automatic pending scanner or a complete banking Saga.

This English edition preserves the historical claims from revision `417a41f`;
translation is not a new execution of those tests.
