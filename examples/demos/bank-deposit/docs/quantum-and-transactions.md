> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Quantum attributes and cash transactions

The first part retains the audit of an old handwritten implementation; it is not
proof of completed source-only migration. The later source path used full quantum
boundaries in SPP 92/93, APP repository/resource bindings and PHY backends.
CashQuantumLedger, JdbcDepositStore and JdbcDepositReversalStore mentioned below
were old implementations awaiting deletion at the recorded stage.

Business source was split into cboddeposit-00 through cboddeposit-95 under one
cboddeposit domain; root cboddeposit.spp served as navigation. APP and PHY remained
separate.

Interest-bearing balance, frozen/authorized amounts, overdraft principal/interest/
exposure, eligibility net balance/daily totals, request amount and posting amounts
were explicitly `quantum double`. APP cashLedger listed owned attributes; PHY
used jdbcQuantum for journal/value tables. The cash adapter created decimal-string
columns to avoid implicit truncation by generic quantum tables' default scale.
Database capacity overflow was rejected, not silently rounded. PHY still selected
BigDecimal representation: quantum semantics and numeric storage are distinct.

The actual conserved cash path was in cboddeposit-95-cash-transaction.spp.
Customer net balance and signed cash-counterparty amount formed cashSettlement.
SPP transaction invoked quantum update moveCashQuantumValue; APP described
balance increase/counterparty decrease. Deposit net change was the original
deposit amount; reversal net change its negative. Business calculations still
determined principal/interest allocation. Pairing only interest-bearing balance
with raw cash would not conserve funds during overdraft repayment.

CashQuantumLedger distinguished quantum objects by actual account IDs, read
current counterparty amount and ran the generated transaction scenario.
JdbcDepositStore/JdbcDepositReversalStore invoked it before their joint commit.
Quantum journal/value updates joined the same account/trade/posting/audit JDBC
connection through JdbcTransactionScope.withConnection. The outer scope owned
commit, rollback and close. Completed replay did not repeat quantum updates.

This physical binding ran on a shared transaction manager in one JVM. It
synchronized binding and restored the previous adapter to avoid concurrent demo
requests overwriting each other's bindings. It was not cross-database quantum
deployment. Earlier financial-component fixtures without banking facts separately
verified JDBC and were not claimed to produce quantum logs.

Declaring quantum attributes does not make every amount independently reversible.
Original requests/postings remain settled facts. Cash reversal eligibility, dates,
fees and special exposure formulas follow XBOD. Non-quantum account facts, dates,
notices and original-record links jointly commit with quantum amounts locally.
A net monetary inverse cannot replace the whole banking reversal.

Complete bank reversal required CashDepositReversal. Generic
reverseCommittedQuantumBoundary handles only its quantum boundary, not this
demo's account JSON, eligibility, fees or original linkage. It was not the
whole-bank-reversal API. The executable guarantee was a single-database joint
transaction, not arbitrary distributed quantum integration.

## Exact storage during source migration

PHY cashLedger explicitly added `config decimalStorage = "text";`.
Public JDBC quantum storage retains numeric text, preserving very small PHY
BigDecimal amounts; default decimal mode remains compatible with existing
projects. Text mode checks existing column type/capacity and rejects incompatible
startup without dropping tables or changing data.

Source 99zp queries actual journals/current values through native APP table
mapping and verifies 1e-25 through source quantum calls. At the initial checkpoint,
full source execution was still being accepted and old CashQuantumLedgerTest
awaited replacement coverage for account isolation/failure branches. It was not
yet deleted and overall source-only completion could not be claimed.

The subsequent 99zp stage passed all 960 JUnit tests and 62 APP root entries.
Actual text journals/current-value queries retained 1e-25 and verified before,
delta and settled values for two COMMITTED records plus conserved increments/
decrements. The next 99zpa round tested two-object isolation, explicit quantum
failure leaving no durable residue followed by successful commit, wrong before
value rejection preserving original records, and a valid new-value commit.
That round was still running when recorded; the old quantum test remained.

Business formulas always use business numbers. PHY/APP explicitly choose exact
representation/storage capacity. These are historical claims from revision
`417a41f`, not freshly executed acceptance of the current source.
