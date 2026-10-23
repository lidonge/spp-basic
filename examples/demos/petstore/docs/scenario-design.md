# Petstore scenarios, contracts and transactions

One scenario expresses one business process by directly composing query, check,
calculate and update atomics. Scenarios neither call scenarios nor dispatch through
ShopCommand/action. Frontend code composes production interactions; Java tests
may compose generated scenarios. Historical counts and compile-only statements
below describe revision `417a41f`, not current execution evidence.

## Scenario/contract mapping

| Scenario | Contract | Other input participants | Agreement |
| --- | --- | --- | --- |
| initializeStore | StoreOpeningAgreement | Goods[], StockLine[], Merchant | Open demo shop and initial catalogue |
| registerShopCustomer | RegistrationAgreement | Buyer, Cart | Open membership |
| updateShopCustomer | ProfileAgreement | Buyer | Proposed name/contact/tier |
| addCartItem | CartAdditionAgreement | Buyer, Cart, Goods | Add product/quantity |
| setCartQuantity | CartQuantityAgreement | Buyer, Cart, Goods | Set selected quantity |
| removeCartItem | CartRemovalAgreement | Buyer, Cart, Goods | Remove selection |
| clearShopCart | CartClearingAgreement | Buyer, Cart | Clear selections |
| placeShopOrder | ShopOrder | Buyer, Cart, StockLine[], Goods selection, Goods[], Receipt | Settled goods/amount/address and stock reservation |
| payShopOrder | PaymentAgreement referencing original ShopOrder | Buyer, Merchant | Full payment |
| shipShopOrder | ShipmentAgreement referencing original ShopOrder | Buyer, StockLine[] | Dispatch quantities/tracking credential |
| deliverShopOrder | DeliveryAgreement referencing original ShopOrder | Buyer | Confirm receipt |
| cancelUnpaidShopOrder | UnpaidCancellationAgreement referencing original ShopOrder | Buyer, StockLine[] | Cancellation reason/released quantities |
| cancelPaidShopOrder | PaidCancellationAgreement referencing original ShopOrder | Buyer, Merchant, StockLine[] | Reason, release and refund |
| requestShopReturn | ReturnRequestAgreement referencing original ShopOrder | Buyer | Return reason/request |
| approveShopReturn | ReturnRefundAgreement referencing original ShopOrder | Buyer, Merchant, StockLine[] | Confirm returned goods/refund |
| reserveShopPet | PetReservationAgreement | Buyer, Goods, StockLine[] | Accept then reserve; retain request on reservation failure |

These are sales-domain opening/change contracts. Customer/product/inventory
providers have their own opening/revision scenarios and contracts. Original order
is a later scenario's participant, not a call to order placement. Quantum changes
belong to actual scenario contracts, not extra InventoryMovement/MoneyMovement
wrappers. Receipt is a real credential entity, not generic OperationOutcome.

The eight read-only scenarios need no invented contracts: readShopReservation
hydrates the original agreement; previewShopOrder hydrates a candidate ShopOrder;
browseShopProducts hydrates Goods[]; readShopCustomer/readShopCart/readShopMerchant
hydrate scalars; readShopInventory hydrates StockLine[]; readShopOrders hydrates
ShopOrder[]. No business return object or collection-wrapper contract is introduced.

## Return-request example

```spp
Scenario serial requestShopReturn(Buyer, ShopOrder, ReturnRequestAgreement) {
    checkReturnRequestAgreementInput;
    initializeReturnRequestAgreement;
    findBuyer; identify(Buyer);
    findShopOrder; identify(ShopOrder);
    checkOrderOwner;
    checkReturnRequest;
    prepareReturnRequest;
    prepareReturnRequestReceipt;
    transaction {
        updateShopOrder;
        insertReceipt(ReturnRequestAgreement.Receipt);
    }
}
```

Buyer carries membership number, ShopOrder its business number, and
ReturnRequestAgreement the reason. Initialization links buyer/original order;
queries hydrate the same entry instances. Explicit checks enforce ownership and
received-order eligibility before changing/saving state. Calculations hide no
checks or generic command interpreter.

## Transaction/check ordering

The historical 16 sales-domain changing scenarios declare transactions;
initializeStore initializes only when Merchant is not yet confirmed.
Quotes/reads do not write and have no settlement transaction.
Default propagation is REQUIRED. Payment's nested REQUIRED joins funds to the
order/receipt boundary under the applicable LOCAL resource contract.
See [transaction boundaries](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-boundaries.md).

Input validity can be checked before transactions. Eligibility requiring stored
facts is explicit after queries and before the first write. Ordinary check does
not automatically roll back; checks such as identify explicitly use breakTransction
when rollback is required. Caller bounds define quantum settlement limits;
enforcement rolls back the bound transaction, without hardcoded service limits.
SPP chooses neither quantum/AT/Saga modes nor exception catches.

Payment involves Buyer, Merchant, ShopOrder and payment agreement, not cart/stock.
Unpaid cancellation has no merchant fund movement. Cart removal is not rejected
because goods were withdrawn. Proposed profile values belong to ProfileAgreement
so hydrating Buyer does not overwrite them.

## Quantum services and physical guarantees

available/reserved/sold and wallet/cash are quantum. Reservation, dispatch, release,
return, payment and refund have separate quantum atomics with caller bounds.
Only part of a conserved resource may change, but its full snapshot participates:
reservation changes available/reserved while sold remains unchanged.

APP derives quantum resources and declares optional technical duplicate protection.
Ordinary CRUD/optimistic protection are generated independently. Develop PHY uses
shared localJdbc, default integer quantities and precision/scale BigDecimal amounts.
This is a same-database realization, not an SPP mode choice. Quantum stock reads
do not take ordinary-resource locks. This original example's evidence did not
establish every multiresource, Saga/TCC or remote-entity lifecycle guarantee.

## Participants and persistence

Services/scenarios declare only actual participants. Default names and aliases
coexist; omitted arguments bind uniquely by full type and collection dimension.
Scenarios cannot construct/rebind scalar participants. Goods[]/ShopOrder[] become
mutable Lists: queries replace contents, not container identity. Only atomics
create dynamic business details.

Saves take current participants only. APP/PHY retain ordinary facts and physical
revision baselines, without original/changed arguments or technical before fields
in contracts. Buyer identity uses membership number; APP stores participant
relationships as foreign-key projections, not SPP customerId. APP defines technical
keys; CRUD derives from participants/playing relationships.

Compiler checks cover participants, call restrictions, unused warnings,
construction scope, contracts for updates, technical fields and quantum constraints.
They do not replace business review. The original phase generated/compiled but
did not execute regression; historical PASS does not prove changed behavior and
old databases are not automatically upgraded.

Inventory atomics are provided through same-kind APP bindings, not calls to
openInventory. registerShopCustomer uses customer atomics in its own transaction
to register the subject and open Buyer in one flow. See [caller bounds](quantum-caller-limits.md).

Later refinements automatically lock ordinary in-transaction reads, including
mixed participants when ordinary fields change, not quantum-only work.
findStockLine does not lock quantum facts; lockStock was removed.
addCartItem eligibility moved outside the write transaction. APP selects
database/TCC/dedicated quantum service and PHY integrates it. Shared-JDBC
evidence is not every mode's evidence; see
[transaction reads](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

## Concurrent prequeries for cart addition

addCartItem uses parallel to query Buyer and the goods chain concurrently.
findGoods reads the role then product-domain facts. After both finish, the main
scenario block performs identify and available-goods checks, then locates Cart
and enters its write transaction. The branches do not write the same participant.

Checks stay in the main block because failure ends the lexical block containing
the check. Moving checks into a parallel branch's serial block would narrow the
failure scope and not ensure the whole scenario stops. No group-wide check-failure
semantics were agreed; this example does not change failure scope through
orchestration. Both queries may finish even if one participant is unconfirmed,
but no write transaction then starts.

Java can similarly compose queries with CompletableFuture. SPP expresses
dependencies with parallel/serial; APP/PHY/runtime own scheduling, shared query
identity state and technical failures. A concurrent group is not a cross-domain
transaction; these eligibility queries remain outside writes.

The historical four-domain set had 30 scenarios (22 changing/eight read-only).
Reliable optimistic baselines permit queries/checks/calculation before transactions
and atomic conditional writes inside. Admission-only reads are not commit
conditions; quantum settlement does not advance ordinary revisions.
Non-LOCAL commits quantum work first and uses Saga across modes; the recorded
develop setup uses LOCAL joint commit. See [audit](transaction-boundary-audit.md)
and [plan](transaction-evolution-plan.md).

## Nested versus sequential transactions

payShopOrder prequeries/checks/calculates; its outer transaction conditionally
updates order and saves receipt, while nested REQUIRED settles quantum funds.
Under LOCAL joint resources the inner scope is not an independent commit:
outer order/receipt failure rolls funds back too. It expresses unit composition
without scenario calls or mutually calling quantum atomics.

Sequential transactions differ: once the first commits, second-stage failure
cannot technically roll it back. Use business processes that explicitly permit
retained acceptance, not arbitrary splitting of refunds/order state.
reserveShopPet commits accepted agreement first, then reserves stock/marks
reserved in the second transaction; failure preserves acceptance.

## Enum codes and display text

Enums use English codes matching member names, for example
`Enum MemberTier { NORMAL("NORMAL"), VIP("VIP") }`.
Order/receipt/reservation states follow the same rule in API, storage and Java
inputs/assertions. The original Chinese UI mapped codes to display labels while
submitting NORMAL/VIP; localized labels never decided business state.
Existing localized stored values require migration or a new demo database;
the recorded phase did not migrate existing data.
