# Composing generated scenarios in Java tests

Tests moved out of SPP. The six 90–95-series SPP files, test roles/contracts,
185 test-service APP bindings and test-only log-query mappings were removed.
The compiler no longer recognizes Verification syntax; runtime no longer defines
a VERIFICATION identity.

Business Java still comes from SPP/APP/PHY. Handwritten Java under
`src/test/java` only prepares input, invokes generated services, asserts results,
injects faults and orchestrates tests. It does not implement price formulas,
eligibility, state transitions, refunds or stock updates.

## File responsibilities

- [PetstoreScenarioTest](../src/test/java/com/servpp/petdemo/PetstoreScenarioTest.java):
  composes independent scenarios and asserts business/physical facts. Its original
  28-method inventory below predates later additions; consult current test reports
  for the executed count.
- [PetstoreTestSupport](../src/test/java/com/servpp/petdemo/PetstoreTestSupport.java):
  generated request/response invocation helpers and test-only Session inputs.
  Production scenarios never receive this fixture.
- [PetstoreRestartProbe](../src/test/java/com/servpp/petdemo/PetstoreRestartProbe.java):
  test-only child-JVM entry; creates/pays first, then a different process reads
  the original database and checks durable facts.

For example, the full lifecycle test invokes order, pay, ship and receive
scenarios in sequence. Java supplies that sequence; atomic services in each SPP
scenario still update business state. Price-boundary tests may invoke generated
quoteOrder directly with fixed inputs and independent expected values, without
rewriting the formula in Java.

## Coverage targets

| Area | Test methods |
| --- | --- |
| Lifecycle, VIP, cancellation/refund, return | happyPathExample, vipPricingExample, unpaidCancellationExample, paidCancellationExample, returnRefundExample |
| Cart, multiple products, read-only quote | multiItemExample, cartAdditionExample, cartRemovalExample, quoteWithoutCommitExample |
| Eligibility/scope rejection | insufficientFundsExample, shippedCancellationRejectedExample, duplicateCustomerExample, profileValidationScopeExample |
| Stock/quantity boundaries | oversellExample, multiItemAtomicRejectionExample, emptyCartExample, fractionalQuantityExample |
| Preserve later trades | laterOrderPreservedExample |
| Duplicate/identity behavior | idempotencyExample, requestCollisionExample |
| Rollback, retry, unknown commit | rollbackRetryExample, paymentRollbackRetryExample, uncertainCommitQueryExample |
| Concurrency/physical quantum evidence | concurrentCheckoutExample, quantumLedgerExample |
| Math boundaries/client-input isolation | pricingBoundaryExample, clientPriceIgnoredExample |
| Independent-JVM persistence restart | persistenceSetupExample, internally running setup and resume processes |

Concurrency uses a Java CyclicBarrier to start two order calls, not a test
synchronization service inside business scenarios. Fault injection uses generic
runtime test tools. JDBC test queries read physical quantum logs/current values
directly instead of adding test-only SPP services/models. Restart tests retain
durable quantum evidence rather than clearing storage at exit.

Each test uses an independent H2 in-memory database; restart tests use independent
temporary file databases. Tests neither touch the web target/petstore database,
register test services nor ship in the production application JAR.

## Build and execute

From the examples root, generate/compile business and tests without execution:

```bash
mvn -pl :petstore-demo -am test-compile -DskipTests
```

To execute regression tests:

```bash
./demos/petstore/run-demo.sh examples
./demos/petstore/run-demo.sh examples happyPathExample concurrentCheckoutExample
```

The retained `examples` script name runs JUnit, not APP examples.
Reports are under `target/surefire-reports`. This migration did not remove APP
examples support used elsewhere.

## Historical migration checkpoints

The original phase checked generation, compilation and production-directory
isolation without executing tests. Old APP PASS evidence did not prove the new
Java tests passed. Transactions, separated validation/calculation, duplicate
protection and platform revisions had migrated; inputs used explicit participants,
independent contracts and stable collections. Java fixture labels such as
customerId/orderId map to business membership/order numbers, not SPP technical
fields. Compiled sources alone do not prove runtime behavior.

The multidomain fixture installs generated application deployment catalogs and
real customer/product startup services, not production mocks. Stock inputs are
StockLine[] located by product relationships; aggregation exists only in assertion
helpers. fractionalQuantityExample checks APP/PHY rejection of noninteger JSON
rather than silent truncation; it is not an SPP floating-quantity eligibility
rule. Quantum evidence is read by product identity, not aggregate stock state.
The three source files were compiled but not executed at that checkpoint.

Three later test sources added successful reservation, retained acceptance after
reservation rejection with protection against blind replay, and payment leaving
the buyer's ordinary revision unchanged. Payment fault injection moved to
payOrderFunds. Unknown-commit checks examine recovery state and business queries,
not promised automatic receipt recovery. Generic repositories separately test
stale/missing revision baselines. These additions were initially compile-only too.

Java business acceptance then used RemoteServiceIngress to simulate external
system requests; uiDuplicateGuardExample separately simulated consecutive UI
clicks. Both invoke generated scenarios; SPP has no test entries. See the
[idempotency/duplicate-guard specification](https://github.com/lidonge/spp-examples/blob/main/book/references/app-call-idempotency-and-duplicate-guard.md).
That checkpoint likewise recorded compilation, not execution.

These historical checkpoints are preserved from revision `417a41f`. Current
basic tests explicitly assert no optional replay or trace when providers are
absent, while retaining business checks, real persistence and concurrency
protection. Enhanced-profile tests retain coordinated expectations.
SdkProfileTest verifies provider availability matches the requested Maven profile;
a missing enhanced provider must not silently count as enhanced acceptance.
