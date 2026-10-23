# Petstore known issues

Unresolved issues do not by themselves block independent regression work.
Continue verification while preserving failure evidence and scope. Skipped
verification must not be reported as passed. The evidence below records the
implementation stage captured in revision `417a41f`, not tests rerun for this
English translation.

## KI-001: Nonzero funding mixed into registration (resolved)

Originally registration directly assigned wallet 5000 and opening a shop assigned
stock 10, mixing maintenance with money/goods intake. Earlier attribution to a
quantum-initial-value resource-coordination gap was corrected.

Account/role preparation now starts at zero. Separate wallet funding transfers
from a zero-initialized SettlementAccount; separate stock receipt transfers from
a zero-initialized StockSupply. Source bounds have no lower limit; both-sided
quantum settlement conserves the total. The framework establishes zero facts
without overwriting existing values. The testing three-JVM registration, funding,
stock receipt, complete shopping/refund/reservation flow and restart passed with
`pageErrors=[]`. Evidence: `/tmp/spp-bootstrap-testing-l5oqppgs/browser.log`.
The separate technical lifecycle issue is KI-003, not nonzero business funding.

This stage also fixed a generic framework issue where lost collection generic
types during cross-domain hydration broke testing-environment order placement.
See the [regression record](regression-2026-10-12-conserved-transfers.md).

## KI-002: Initialization retry scope

Startup supports missing-only inserts and completion of quantum initial facts,
without overwriting existing ordinary facts or settled quantum values.
Retry after ordinary seeds committed but quantum initial facts were missing was
verified. Automatic repair of arbitrary damaged databases, concurrent
initialization of the same identity, and recovery when a query first encounters
missing quantum facts remained unverified. This is not a generic database-repair
protocol.

## KI-003: Technical recovery for cross-database zero-balance opening (implemented)

Previously, zero records could commit in the quantum database before the role
database committed. Later role-insert failure could leave orphan zero records.
The revised flow jointly commits the local role insert, APP table write and
technical pending task. Only after confirmed local commit does it create the
remote zero record. This is entirely generic runtime behavior used by generated
CRUD. SPP still invokes only `insertBuyer`; it neither splits opening into two
business steps nor requires business compensation.

Same-database deployment retains the local joint transaction. Cross-database
recovery persists a technical pending task: local rollback removes both role and
task before any remote creation; remote unavailability or a lost response leaves
the task for retry at runtime startup, participant query or explicit technical
recovery API. Remote success removes the task.

Replay creates only missing zero records, does not overwrite settled values and
creates no quantum transaction entries. Remote failure is a pending technical
recovery error. Zero-record recovery does not depend on business reversal; other
scenario services still follow their existing transaction recovery rules.
A local role with incomplete remote initialization is not a fully successful
opening. Local durable evidence supports recovery, not instantaneous atomic
commit across databases.

Scope is the built-in ordinary JDBC repository plus JDBC quantum store. This
does not retroactively clean orphan zero records from older versions, nor permit
nonzero business initial values through this mechanism. Other quantum providers
must implement the same technical creation/replay protocol; SPP compensation is
not a substitute. See [cross-database zero-record recovery](quantum-opening-recovery.md)
for mechanism and verification.
