# Public extension requirements

Revision 1, 2026-10-22. These requirements describe the basic SDK and replaceable
technical facilities. They are part of the Apache-2.0 source package. Publishing
these contracts does not publish the separately supplied official enhancements.
Changes should identify the requirement below and the tested implementation.

## CORE-1: business behavior and persistence

SPP owns business checks, calculations, updates and transaction boundaries. APP
selects realization, mappings and service policies; PHY selects implementations
and physical resources. Switching a compatible technical implementation must
preserve the business result without adding technical version fields to SPP.

The basic repository retains read identity and revision evidence. An ordinary
update must atomically check that revision and write the mapped ordinary facts.
An explicit null is a write. Conflicting updates cannot silently overwrite one
another. Primary keys, read-only facts and independently stored quantum facts
remain outside an ordinary update's writable field set. Disabling an optimization
must not disable this protection. An explicit APP opt-out of the system version
check is a separate application policy and is not the basic edition's default.

The [basic persistence example](sdk-tools/samples/custom-services/README.md)
executes generated services against H2 and checks actual table contents and
competing updates. It does not certify another database or distributed topology.

## EXT-1: technical service lifecycle

Implement [TechnicalService](appframe/src/main/java/com/servpp/service/technical/TechnicalService.java)
with a public no-argument constructor and bind it explicitly in APP/PHY, as in
the example. Instances must be thread-safe. `configure` receives physical
configuration; `handle` receives before, after and failure events. Technical
services do not implement or mutate business behavior.

[TechnicalCall](appframe/src/main/java/com/servpp/service/technical/TechnicalCall.java)
exposes detached facts and technical controls. A before handler can complete a
call with a response, including null, and an explicit business result. A normal
after event means the call returned; a business refusal may also return normally.
An atomic service can return while its owner's transaction is still open.
Therefore a handler must not infer transaction commit from after alone or commit
a transaction owned by its caller.

Observer failures are isolated by default. A coordination service that must stop
the operation declares `critical()`. Its error must reach the owner; a failure
after an actual commit cannot turn that committed business work into a rollback.
See [lifecycle acceptance](appframe/src/test/java/com/servpp/service/technical/TechnicalCoordinationTest.java).

## EXT-2: requests and transaction responsibility

The basic SDK supplies no official request registration, replay or request-store
implementation. [RequestCoordinationProvider](appframe/src/main/java/com/servpp/service/idempotency/RequestCoordinationProvider.java)
and [RequestClaimStore](appframe/src/main/java/com/servpp/service/idempotency/RequestClaimStore.java)
expose the replacement contracts. A provider must distinguish trusted call
context from business input:

- Independently committed remote service updates can require request idempotency.
  Local calls, checks, queries and calculations do not gain it merely by executing.
- User duplicate submission protection belongs to declared scenario entries,
  not ordinary update services. It has a separate APP policy from idempotency.
- Managed XA/AT branch retries belong to the transaction/call adapter protocol.
  An application replay cache cannot substitute for the transaction controller.

Reservation and completion must be atomic. An old claim owner must not complete
or release a newer owner's claim. Replay must preserve the business response and
outcome and reject conflicting input. Release for retry requires confirmed
rollback; timeout, failure callbacks or missing responses alone are insufficient.
Already committed work with an uncertain receipt must remain available for
reconciliation, rather than being executed again. Provider absence is verified by
[BasicRequestBoundaryTest](appframe/src/test/java/com/servpp/service/idempotency/BasicRequestBoundaryTest.java).

## EXT-3: field selection, tracking and storage

A custom update.before handler can compare a participant's current facts with
its original query baseline, or consume compatible change tracking, and call
`TechnicalCall.Participant.selectUpdateFields`. The selection is scoped to that
call and participant. A fresh read of the latest database row must not replace
the original revision evidence. An empty field selection still requires version
validation; changes to null must not disappear from the selected update.

The repository rechecks field ownership and mapping. A custom
[NativeUpdateExecutor](appframe/src/main/java/com/servpp/service/repository/NativeUpdateExecutor.java)
borrows the repository connection and must not commit, roll it back or close it.
The revision check and native table update must have one transaction outcome.
SQL identifiers must come from validated mappings and values must be bound.

Model tracking uses [PropertyChangeProvider](appframe/src/main/java/com/servpp/service/runtime/PropertyChangeProvider.java)
and compatible generated models. Generator customization uses
[ModelGenerationExtension](generator-api/src/main/java/com/servpp/generator/spi/ModelGenerationExtension.java).
They are separate from a method interceptor and from basic optimistic protection.
Tracking must preserve load, projection, rollback and confirmed-save semantics;
ordinary update.after is not permission to discard uncommitted change evidence.
Without an optional provider, the basic generator emits ordinary models and
ordinary complete updates. See [BasicTrackingBoundaryTest](appframe/src/test/java/com/servpp/service/runtime/BasicTrackingBoundaryTest.java).

## EXT-4: transaction stages and optional scheduling

The source plan groups prepared local ordinary updates sharing a resource into
one SAGA stage; remote calls remain independent stages. Preparation barriers,
different resources and explicit independent boundaries must be respected.
A coordinator implements [SagaTransactionGroupCoordinator](appframe/src/main/java/com/servpp/service/transaction/SagaTransactionGroupCoordinator.java),
including its source-stage entry point. Compensation for a grouped local commit
must also be atomic; stage identity, prepared inputs and recovery evidence cannot
be replaced by mutable objects from a later call.

An optional [UpdateSchedulingProvider](appframe/src/main/java/com/servpp/service/transaction/UpdateSchedulingProvider.java)
may reorder only the supplied ready segment. It must prepare on the owner thread,
bind actions after preparation, execute each action once and join every started
action even when another fails. It cannot cross a preparation/transaction barrier
or let a writer outlive the owner's commit or recovery decision. Without a
provider, automatic update scheduling is serial; explicit SPP parallel behavior
and transaction grouping retain their semantics. See
[BasicUpdateSchedulingTest](appframe/src/test/java/com/servpp/service/transaction/BasicUpdateSchedulingTest.java).

Adapters need their own real commit, rollback and recovery evidence for the
databases and topologies they support. Interface conformance and local H2 tests
alone do not establish production XA/AT recovery or close a remote commit/receipt
failure window.
