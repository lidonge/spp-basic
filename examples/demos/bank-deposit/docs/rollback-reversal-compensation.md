> Historical document: this records the pre-refactoring design or acceptance, not current source or test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Rollback, reversal, and compensation

2026-10-07. This records the agreed conceptual responsibilities and implementation questions still open at that time. It does not claim that the demo implemented these capabilities.

## Agreed concepts

### Rollback

Rollback is technical: when a transaction fails, undo its effects and restore its pre-transaction state, retaining audit records. This applies only to that transaction's state and effects; it must not overwrite successful results of independent transactions.

The guarantee depends on the transaction mode:

- A quantum transaction provides deterministic rollback through its quantum services. Every participating state must fall within that guarantee. APP/PHY owns resource release, retries, and recovery protocols.
- A successfully committed Saga node has already completed its local transaction. Local rollback cannot undo that committed business result. An uncommitted failing node can still roll back locally. Recovery of the overall Saga requires business reversal or compensation, not the same rollback guarantee as a quantum transaction.

Automating Saga coordination, retries, and progress recording does not automatically provide business inverse operations.

### Reversal

Reversal is business behavior: after an original operation completes, a business reason requires undoing it. The reversal is a new transaction. Business rules determine eligibility, scope, and exceptions, independently of the original transaction mode.

Many reversals can use a default inverse-transaction rule. The system generates and executes inverse updates from actual settlement facts, without requiring business authors to repeat mathematically determined inverse formulas. This does not turn reversal into technical rollback.

Inverse transactions may be quantum or non-quantum. Quantum inverses have a deterministic relationship that preserves subsequent operations. Non-quantum inverses first require confirmation that they will not overwrite other business activity. An account cannot be canceled after receiving deposits merely because its opening-status field version is unchanged. See [default reversal design](default-reversal-design.md).

A failed reversal follows its own transaction mode: a quantum transaction can roll back that reversal; a Saga must handle its already committed nodes. Whether the original operation has been reversed depends on actual settlement state, not merely an error returned by a call.

### Compensation

Compensation is business behavior for cases where reversal cannot complete or business rules determine that an operation cannot be undone. The system cannot infer compensation rules or substitute a universal technical mechanism for a business decision.

It can invoke explicitly bound compensation services, record outcomes, and retry as declared. Business rules must define their contents, applicability, and completion criteria.

## Proposed default inverse implementation

Describe the normal business operation once. Record its actual settlement components and participants, then generate an inverse plan from those facts when reversal is requested. Eligibility and exceptional handling remain business responsibilities; transaction mode, storage, execution, and technical recovery belong in APP/PHY.

Use the original actual amount delta. If a balance of 100 increases by 50 and another transaction then raises it to 170, reversing the first transaction subtracts 50, leaving 120. Restoring the old snapshot of 100 would destroy subsequent business. Similarly, reverse the original settled principal and interest components, rather than reallocating them under today's repayment policy.

Assignments, state transitions, collection changes, and external effects do not always have safe business inverses derivable from old values. Establish each inverse contract, subsequent dependencies, and eligibility separately. Require explicit reversal logic or business compensation when inference is impossible; do not force an undo using old snapshots.

The committed quantum-boundary export and inverse-execution interfaces of `DefaultTransactionManager` were candidate execution foundations. Their connection to settlement records, eligibility, duplicate handling, and cross-day rules still required verification. An interface's existence did not prove complete bank reversal support.

## Questions remaining before implementation

1. Which update types have default inverses, which require explicit rules, and how are subsequent business dependencies identified?
2. Which components and rule facts must settlement records retain, and how does reversal cover accounts, details, accrual bases, and journal entries?
3. How are transient failures, unknown outcomes, eligibility rejection, and confirmed irreversibility distinguished? A timeout alone must not trigger compensation; business-defined conditions must prevent duplicate processing.
4. How does a Saga bind each node's reversal or compensation service? The coordinator executes explicit business bindings rather than inventing rules.
5. Which explicit policies and evidence govern XBOD cross-day interest, fees, and subsequent transactions?

At the time of this discussion, the conceptual division was agreed, but these contracts remained open. New reversal/recovery mechanisms were deferred; independent deposit-rule verification could continue and the implementation goal remained active. This is a historical status, not a statement about the current goal.
