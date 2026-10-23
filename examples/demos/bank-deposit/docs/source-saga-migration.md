> Historical document: this describes pre-refactoring implementation and acceptance, not current source/test results. See [the current refactoring](latest-petstore-refactoring.md); retired source is under `legacy-source/`.

# Saga source-language migration

Historical status at this checkpoint: generic scenario-recovery semantics and invocation metadata passed 248 focused tests and all 303 public lang tests. The six bank source cases in 99zv passed complete verify with 967 JUnit tests and 81 entries. Corresponding old tests were deleted, but post-deletion clean verify remained pending. The six responsibilities of DepositSagaRecoveryTest are recorded below; this checkpoint alone was not final bank Saga acceptance. This phase still prohibited handwritten demo Java.

## Business and transaction boundaries

SPP `transaction saga` expresses a business process composed of local transactions that finish independently. Each deposit node invokes the existing deposit scenario with its explicit whole-operation `transaction quantum`. A later node's failure cannot undo an earlier local commit. Explicit business recovery is required, using existing XBOD cash-reversal eligibility, dates, fees, and inverse calculations.

Recovery first queries original settlement evidence. If absent, create no reversal. If present, query the stable reversal identity and confirm whether reversal already completed; only incomplete work proceeds through eligibility checks. SPP participant facts explicitly identify original, reversal, and later independent transactions. The public framework must not infer bank eligibility; retaining original settlement is not a technical rollback failure.

If recovery eligibility is rejected, preserve original and later business and record recovery failure. Retrying recovery invokes only recovery services, never deposits again. If recovery settled but its completion record could not be written, query the actual reversal and confirm it. Do not invent another identity and deduct twice.

## Confirmed capabilities and gaps

Existing facilities included explicit Saga/Quantum modes, scenario calls, participant construction/copy, conditions, failure-fact capture, explicit rethrow, and query response binding. APP already provided `compensate(target)`; generic participants invoked the target with source-call arguments. Bank deposit and cash reversal already had complete source implementations.

Before migration, semantic checks accepted only local atomic-update recovery targets with local transaction mode or resources. They were extended to complete local scenarios, provided the target itself declared an SPP local transaction or delegated to a source scenario with one. Parameter compatibility and prohibition of nested recovery chains remained. Complete cash reversal queries/checks facts, builds an inverse plan, then enters explicit Quantum submission. A meaningless atomic wrapper must not bypass target validation.

Generic scenario-target semantics, argument compatibility, generation, and actual propagation of original participants required verification. Recovery's local transaction belongs explicitly in SPP, not hidden by an APP binding. Missing targets, self-recovery, duplicate targets, incompatible arguments, and invalid nested chains remain errors. Any recovery-catalog, journal-read, or journal-fault extensions were to be determined from generated/runtime evidence.

The default generic participant invokes recovery for nodes that started execution. Therefore business recovery must query whether settlement actually happened; a Java callback boolean cannot decide this. Generic journal success/failure describes call results, not bank settlement evidence. Persistent recovery facts use declared Roles, query/update services, and APP/PHY storage.

## Implementation order and independent acceptance

1. Verify complete-scenario recovery bindings, parameters, nested local transactions, and primary-failure preservation in generic compiler/runtime tests using a non-bank model; introduce no bank names/formulas.
2. Declare deposit nodes, later nodes, business recovery, and stable identities in SPP. APP/PHY binds only implementations, resources, and storage.
3. No later failure: one deposit settlement, no reversal.
4. Later failure: original settlement remains queryable; inverse removes only its increment and preserves a later independent deposit.
5. No local settlement: no inverse transaction or inverse entries.
6. Recovery eligibility rejection: original settlement remains, failure is recorded, no fabricated successful compensation.
7. Explicit retry after eligibility is restored: reuse the same reversal identity and confirm outcomes; do not reexecute deposit or duplicate balance/entry changes.
8. Recovery settled but completion recording failed: query actual reversal, then retry with only one settlement/entry set.
9. Delete corresponding old tests only after source execution passes. Ultimately remove main Java with other migrated responsibilities and inspect a clean generation directory for residual bytecode.

These are business-recovery checks in a single-database reference demo. Cross-database/service production deployments require each PHY implementation's guarantees; local results do not prove distributed guarantees.

## Actual responsibility handover evidence

| Old responsibility | Source entry and independent facts |
| --- | --- |
| No recovery after success | cashSagaSuccessExampleRun: version 1/balance 700, original eight entries, no reversal/recovery record |
| Later failure preserves independent activity | cashSagaLaterFailureExampleRun: original 600 and later 100 remain queryable; reversal leaves balance 200/version 3; three separate balanced eight-entry sets; original fee refund 2; recovery Done |
| No inverse without settlement | cashSagaAbsentCommitExampleRun: E1155, version 0/balance 100; no original/inverse transaction or entries; NotCommitted |
| Rejected recovery preserves settlement | cashSagaRejectedRecoveryExampleRun: actual freeze 700, ES020; original balance 700/version 2 retained; no reversal entries; primary later failure retained with ES020 appended exactly once; persisted Failed |
| Independent recovery retry | cashSagaRetryExampleRun: after explicit unfreeze, invoke the same recovery source; final version 4/balance 100; third call only queries/confirms; eight inverse entries and original transaction retained; Confirmed/attempts3 |
| Completion-record failure after settlement | cashSagaCompletionFaultExampleRun: actual storeCashSagaRecoveryDone fault; reversal settled at version 2/balance 100; QueryRequired and secondary failure retained; next query confirms with no second reversal; Confirmed/attempts2 |

Historical full log: `/tmp/spp-source-saga-runtime-evidence-validation.log`; JSON/Markdown files use each entry's name. Cash recovery uses complete XBOD eligibility, dates, fees, inverse calculations, and explicit Quantum submission, replacing the six responsibilities formerly wrapped by Java financial components. CashSagaRecoveryRecord declares persistent recovery states; it does not claim to reproduce old ExplicitSagaStep internal state names or callback interfaces.

The generic recovery catalog was aligned: registered local services and generated transaction-call catalogs establish a scenario's local SPP boundary. A valid local recovery binding reports BUSINESS-owned SPP_DECLARED_SCENARIO recovery without requiring object snapshots or claiming automatic adapter quantum inversion. A missing registered recovery target retains RECOVERY_OPEN.

Three new catalog cases and all 536 appframe JUnit tests passed; log: `/tmp/spp-scenario-recovery-catalog-framework-validation.log`. All 83 bank entries also passed at that later checkpoint. These remain single-database reference results, not cross-service production guarantees or fresh results from this translation.
