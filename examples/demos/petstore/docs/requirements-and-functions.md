# Petstore requirements and functions

Demonstrate SPP business expressiveness through a complete pet-supply shop.
The web UI (originally Chinese) and Java acceptance invoke the same generated
services. Production business uses only SPP/APP/PHY, without handwritten Java.
The earlier petstore, bank and business-showcase examples were outside this
original migration phase. Historical compile-only statements below describe
revision `417a41f`, not every later verification run.

## Business scope

| Function | Rules | Acceptance targets |
| --- | --- | --- |
| Products | Browse categories, available products, stock | Category filter, no match, withdrawal |
| Membership | Registration/profile/NORMAL or VIP; zero opening, independent demo funding from settlement account | Duplicate registration, invalid details |
| Cart | Add/set/remove/clear positive integer quantities | Multiple products, empty cart, withdrawn/insufficient stock |
| Quote | Line amounts; VIP 10% discount; free shipping at discounted subtotal 100, otherwise 8; explicit rounding to cents | NORMAL/VIP, fractions, boundaries |
| Order | Server prices, stock reservation, settled snapshots, cart clearing | All lines succeed/fail together; reject forged prices |
| Payment | Full wallet-to-merchant transfer | Insufficient balance, conserved funds |
| Fulfillment | Ship after payment, receive after shipment | State eligibility, reserved-to-sold stock |
| Cancellation | Unpaid releases stock; paid/unshipped releases/refunds | Preserve later trades; shipped orders cannot directly cancel |
| Return | Request after receipt; confirmed return restores available stock and refunds fully | Explicit reason, state transitions |
| Credentials | Receipts/order history query committed facts | Query after uncertain commit, read after restart |
| Web UI | Click through lifecycle, display server amounts/states | Real interfaces and interaction |

Wallet funds are demonstrations, not bank-card integration. Authentication,
payment gateways, taxes, multiple warehouses/currencies, external fulfillment
and production deployment are excluded. Cancellation/return are new business
trades; rollback is APP/PHY technical undo of a failed transaction.

## Modeling requirements

Customer owns Person (individual/organization); product owns Product.
Product plays Goods; Person plays Buyer/Merchant. Merchant adds shop/trading name
and received balance through take. Goods plays CartLine/OrderLine/StockLine;
Buyer plays Cart. Whole-participant mappings retain relationships.
Receipt is an actual credential. Inventory independently owns StockLine without
inventing an entity; sales organizes shopping/orders. There is no StockBook
aggregate state; totals are calculated when needed.

Each Contract corresponds to a specific scenario. ShopOrder directly contains
Buyer and Goods lines, not customerId/itemId substitutes. Later contracts retain
original order, parties and current agreement. Ordering uses ShopOrder without
PurchaseAgreement; quote hydrates an unsettled ShopOrder without QuotationAgreement.
Queries use explicit participant collections, not wrapper/result contracts.

SPP has no meaningless id/storeId/version/requestId fields. Membership/product/
order/receipt numbers need business-purpose descriptions. APP/PHY own platform
keys, foreign projections, revisions, duplicate protection and database location.
See [business model boundaries](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-business-model-boundary.md).

## Services and scenarios

Every shop change, quote, read and opening has its own declaration; providers
have their own opening/revision scenarios. Scenarios cannot call scenarios.
Inputs include only participating roles/entities/contracts/collections.
Scenarios cannot construct participants; queries hydrate input instances, atomics
create dynamic details. Services have no business return values.

Eligibility checks are explicit in scenarios. Default failure ends the current
block without rollback; explicit checkXXX,breakTransction requests rollback.
Calculations hide no checks. Calculate may compose calculate only; ordinary
atomics cannot call each other. Quantum services declare increments/decrements,
with caller bounds defining settlement limits. Generated calls pass limits and
settlement enforces them, rolling back the bound transaction on violation.
Finite business states and membership tiers use enums.

Every database-changing scenario explicitly declares transaction, default REQUIRED.
REQUIRES_NEW follows the supported matrix; NESTED/unsupported combinations are
compile errors for the selected unsupported configuration. SPP does not select
realization mode. Stock available/reserved/sold and wallet/income are quantum;
quantities default to int, monetary representation to BigDecimal specifications,
overridable in PHY. Business supplies formulas and explicit rounding.

Standard CRUD is invoked as find/insert/update/delete + type, without empty
atomic declarations. Reference and a unique explicit take resolve cross-domain
standard calls: insertPerson(customer)/updatePerson(customer) maintain subjects,
insertBuyer/updateBuyer separately maintain roles. Complex queries/business
services remain explicit.

## Application and acceptance boundaries

APP declares repositories, platform revisions, optional duplicate protection,
web entries and four-domain same-JVM deployment; customer/product/shop define
startup services. PHY binds records/quantum resources to H2/JDBC.
Quantum stock reads do not lock ordinary resources; quantum realization handles
concurrent settlement. Frontend parallel reads are not a consistent snapshot.

Java tests under src/test/java only prepare input, compose generated scenarios,
assert and inject faults. Coverage targets lifecycle, rejection, boundaries,
duplicates, concurrency, failures and restart. The original instruction was
generation/compilation only, not regression execution. Coverage targets and old
PASS counts were not execution evidence for that phase.

Real customer/product/inventory/petshop domains bind same-kind atomics through APP.
Shared PHY backend/resource/connection supports joint-commit intent; one-click
registration registers subject and opens Buyer without calling a provider
scenario. See [boundaries](domain-boundaries.md) and [implementation](multidomain-implementation.md).
Source/generation had completed at that checkpoint, runtime acceptance had not.

Later refinements lock ordinary in-transaction reads, including mixed participants
when ordinary attributes change, not quantum-only work. findStockLine does not
lock quantum facts; lockStock was removed. addCartItem admission moved outside
the write transaction. APP chooses database/TCC/dedicated quantum services,
PHY integrates them. Shared-JDBC evidence is not evidence for every mode.
See [transaction reads](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

Changing/read-only scenarios narrow transactions using reliable optimistic
baselines: queries/checks/calculation before, atomic conditional writes inside.
Admission-only reads are not commit conditions; quantum settlement does not
advance ordinary revisions. Reservation retains acceptance after second-stage
stock failure; payment illustrates nested REQUIRED logical boundaries.
Non-LOCAL commits quantum work first and recovers across modes with Saga.
The recorded develop setup uses LOCAL joint ordinary/quantum commit.
See [audit](transaction-boundary-audit.md) and [plan](transaction-evolution-plan.md).

### Simplified queries and business identifiers

Order listing names key Buyer as the condition participant, with a supplied
order collection. The unique APP order→buyer foreign key and Buyer primary key
derive the query without positional repository configuration. Complex queries
still demonstrate APP customization. Customer/order/line/reservation/receipt
number generation uses auto ... prefix ... in owning APP tables; the business
model retains these meaningful numbers.

## Environments, numbers and source presentation

- APP environment selects deployed domains; develop uses readable LOCAL policy.
  PHY selects environment parameters and rechecks resource identity after runtime
  parameter resolution. LOCAL jointly commits ordinary/quantum work; unsupported
  distributed capabilities reject configuration.
- Quantum attributes/modified properties are collected automatically, without
  repeated APP lists. XA requires a real Seata XA datasource proxy, not AT labeled
  XA. External coordinator/database execution requires separate acceptance.
- Model numeric types are defaults. PHY demonstrates global exact representations
  and local field overrides. Business rounding is explicit; storage capacity does
  not redefine mathematical rules.
- Localized text is split by language, referenced by PHY resource directory.
  Business constant names remain stable.
- Default participant names/argument-free calls use full type matching, including
  Goods versus Goods[]. Repeated full types retain aliases. SPP collection
  references stay Goods[]; Java uses valid internal identifiers.
- SPP/APP/PHY/enum comments were removed in favor of separate explanations.
  That phase generated/compiled/statically checked without running regression.

Compared with traditional implementations, APP/PHY centralize deployment and
representation without repeated DTO aliases, quantum lists or localization
loading in scenarios. Compiler/runtime must validate resource/protocol capability;
parsable configuration does not prove a working distributed deployment.

## Zero opening and conserved external transfers

Opening creates a zero wallet. Product stock, settlement and supply accounts also
start at zero. Funding/receipt are separate scenarios with two-sided quantum
changes. Source bounds have no lower limit; recipient bounds require nonnegative
values. UI/tests compose generated scenarios, not handwritten business Java.
See [design and comparison](conserved-opening-and-transfers.md).

## Product facts versus settled order lines

Product name/price may change, but old lines retain settled name, unit price and
amount. Payment/fulfillment cannot overwrite them from current catalogue facts;
new orders use latest facts.

OrderLine is played by Goods through take. APP declares
snapshot(OrderLine.name, OrderLine.unitPrice) on line storage and current(Goods.name)
for product name. Other ordinary mapped attributes default current. Snapshots
never reverse-save into goods; roles still directly relate to product participants.

Product's reviseProduct changes names; shop's reviseShopGoodsPrice changes sales
price. Each has a business contract/minimal transaction. Acceptance composes
order, rename, reprice, reread old order, payment and new order, checking old/new
facts. See [role-mapped storage](https://github.com/lidonge/spp-examples/blob/main/book/references/role-mapped-storage.md).

## Reservation failure disposition

Accept first, reserve second. Insufficient stock retains the request and records
rejection. Framework handles quantum undo; fail saves business disposition in
an independent transaction. Built-in ResultInfo is scenario-only, not an input.
Unknown commit, failed recovery or failed disposition cannot be called success.
See [reservation failure handling](reservation-failure-handling.md).

## Technical-service demonstration

APP matches all services, scenarios or named services; PHY binds implementation/
parameters. Business SPP adds no logging or technical participants.
The example observes shop scenario/query/check/calculate/ordinary-update/quantum
calls and timings. Interceptors see technical metadata only; after is not commit,
and ordinary business rejection is not a technical exception.
See [technical services](technical-services.md) and
[syntax design](https://github.com/lidonge/spp-examples/blob/main/book/references/app-technical-services.md).

The basic SDK warns when optional official tracing/request coordination is absent.
That absence does not disable persistence, business checks or basic optimistic
concurrency; it also must not be described as equivalent replay protection.
