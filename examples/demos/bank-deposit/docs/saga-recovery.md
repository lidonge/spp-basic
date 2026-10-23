> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Explicit business recovery for Saga stages

## Verifying the agreed concepts

A successful Saga stage has finished its local transaction. Later failure cannot
make that database commit disappear. Recovery invokes explicitly bound business
reversal or compensation. This demo used a committed financial-component deposit
and an independent inverse trade, not snapshot restoration as a default Saga action.

Historical APP binding ExplicitSagaStep implemented Service and TransactionalService
against native DefaultTransactionManager/SagaTransactionStrategy in SAGA mode.
Local execution, successful-trade lookup and business recovery had to be supplied
explicitly. The framework interface was still named TransactionParticipant.rollback;
this binding invoked business recovery through it, not global technical rollback.
Construction rejected a missing recovery action; there was no default compensation.

Local execution used generated SPP amount/posting plans and JdbcDepositStore.
Recovery queried original settlement, generated an inverse plan and used
JdbcDepositReversalStore. The new inverse trade had its own identity, postings,
audit and original-trade link. Business formulas remained in SPP. APP bound
execution/query/recovery without guessing settlement from a single exception.

## Native execution and failure boundaries

Local actions require queryable evidence of successful settlement. Success logs
LOCAL_COMMITTED; execution failure logs LOCAL_EXECUTION_FAILED, which does not
prove the business failed to commit. The native manager visits executed
participants in reverse order. The binding queries actual trade evidence again:
no trade produces LOCAL_NOT_COMMITTED without an inverse; an existing trade
invokes bound business recovery.

Recovery writes RECOVERY_REQUESTED, then RECOVERY_DONE on completion or
RECOVERY_FAILED on failure, propagating the recovery exception to the manager.
The manager preserves it as suppressed on the original failure, not success.
If recovery returns successfully but completion logging fails, record
RECOVERY_RESULT_QUERY_REQUIRED. Do not execute a new inverse or infer that
original business remains unreversed from that logging failure.

SagaLogRecorder writes JdbcSagaLogStore with business identity, step name,
native transaction identity, state and explicit recoveryPayload. Log persistence
is not the same transaction as local business commit. Query actual trade/reversal
records rather than treating log state as business outcome. Logging failure
preserves the primary exception and adds the log exception.

## Historical executed cases

DepositSagaRecoveryTest used the native manager, actual financial components
and H2:

- Successful stage: deposit 10 into 200 produces 210; original trade remains and
  no recovery executes.
- Later failure with independent business: original 10 settled, then an
  independent deposit of 20. Reverse only original 10, leaving 220. Original and
  later records remain; new inverse R1 is recorded, not restoration to 200.
- Unsettled stage: execution fails and no successful record exists; no default
  inverse is invoked.
- Business-rejected recovery: frozen amount violates ES020 capacity. The original
  deposit stays committed and recovery failure is recorded; the framework does
  not invent compensation.
- Explicit recovery retry: after fixture unfreezing, recoverCommitted uses stable
  R1. Repetition neither deducts again nor reruns the original deposit.
- Inverse committed but completion log fails: actual R1 remains queryable and
  RECOVERY_RESULT_QUERY_REQUIRED is recorded. Later stable-identity recovery
  repairs logging without another monetary inverse.

Original fee and explicit refund were zero, with no product/accrual facts.
These are financial-component Saga recovery cases, not selection of full cash
cross-date/refund/non-quantum policy. Versioned fixture updates simulated freeze/
unfreeze; no real bank freeze transaction was claimed migrated.

## Limits at that stage

The binding verified native execution strategy, explicit recovery invocation and
durable events. It did not provide automatic pending scanning, cross-process
callback reconstruction or reliable recovery across disconnection. Payloads
retained example original/inverse identities, but registration/reconstruction of
recovery services still required a formal execution contract. The binding still
owned stable identities and queries of business database results.

These cases do not establish complete banking Saga migration, default compensation
for arbitrary actions or quantum integration in deposit scenarios. Technical
rollback, quantum coordination and business reversal require separate acceptance.
This English edition preserves claims from revision `417a41f`, not new test runs.
