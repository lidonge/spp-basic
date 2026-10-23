# Petstore scenario transaction-boundary audit

> Historical audit from revision `417a41f`. The 2026-10-11 transaction/storage
> update superseded earlier joint-commit, automatic player-save and CustomerSubject
> descriptions. See [implementation rules](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> That update used AT ordinary updates, quantum settlement first and Saga across
> modes; REQUIRED does not change it. Person/Product belong to their owning
> domains. Joint rollback statements below require the actual LOCAL shared-resource
> configuration, not every realization.

The recorded four-domain inventory had 30 scenarios: 22 changing and eight
read-only. Ordinary queries, checks and calculations that can finish early occur
outside transactions. APP defaults to atomic conditional updates against queried
revision baselines. Write transactions focus on jointly committed changes;
quantum settlement enforces caller bounds.

## Narrowing rules

Read ordinary facts/revisions together; preserve baselines across stable
participants/domain projections. Updates compare the original query baseline
rather than rereading/revalidating inside the transaction. Admission-only roles
are not ordinary write comparisons. Quantum changes do not advance ordinary
revisions. Unique constraints protect inserts. Independent queries may run
parallel; dependent hydration stays serial. Checks remain in the main block so
failure ends the scenario.

## Per-scenario audit

| Domain/scenario | Before transaction: queries/checks/calculation | Transactional work |
| --- | --- | --- |
| petshop.initializeStore | Subject/catalogue hydration, opening facts | Open goods/stock/merchant together |
| petshop.registerShopCustomer | Admission, registration role/cart/receipt preparation | Register customer subject and open sales Buyer/Cart/Receipt |
| petshop.updateShopCustomer | Buyer/customer subject queries, proposed values/input checks | Conditional ordinary updates in both domains and receipt |
| petshop.addCartItem | Parallel buyer/goods query chains, cart query/check/addition calculation | Conditional cart update and receipt |
| petshop.setCartQuantity | Parallel buyer/goods chains, cart query/quantity calculation | Conditional cart update and receipt |
| petshop.removeCartItem | Buyer/cart query, removal calculation | Conditional cart update and receipt |
| petshop.clearShopCart | Buyer/cart query, clearing calculation; default participant names | Conditional cart update and receipt |
| petshop.placeShopOrder | Parallel buyer/catalogue, cart, quote, stock range/eligibility, order preparation | Insert order, reserve stock, conditional cart update, receipt hydrated after assigned order number |
| petshop.payShopOrder | Parallel buyer/merchant, order/eligibility/payment calculation, receipt preparation | Nested REQUIRED funds join outer conditional order update/receipt |
| petshop.shipShopOrder | Parallel buyer/order, stock query, shipping checks/calculation | Reserved stock to dispatched, conditional order update, receipt |
| petshop.deliverShopOrder | Buyer/order query, receipt eligibility/calculation | Conditional order update and receipt |
| petshop.cancelUnpaidShopOrder | Parallel buyer/order, stock query, cancellation checks/calculation | Release reservation, conditional order update, receipt |
| petshop.cancelPaidShopOrder | Parallel buyer/merchant/order, stock query, cancellation/refund calculation | Release reservation, refund, conditional order update, receipt |
| petshop.requestShopReturn | Buyer/order query, reason/eligibility, request calculation | Conditional order update and receipt |
| petshop.approveShopReturn | Parallel buyer/merchant/order, stock query, return checks/refund calculation | Restore stock, refund, conditional order update, receipt |
| petshop.reserveShopPet | Parallel buyer/goods, input checks; after stage one prepare/query stock | Transaction one accepts; two reserves/conditionally updates; after recovery, fail uses transaction three for rejection/reason, retaining stage one |
| petshop.previewShopOrder | Query/check/quote | No write transaction |
| petshop.browseShopProducts | Catalogue hydration/localization/filter | No write transaction |
| petshop.readShopCustomer | Buyer/subject query | No write transaction |
| petshop.readShopCart | Cart query | No write transaction |
| petshop.readShopOrders | Order collection query | No write transaction |
| petshop.readShopInventory | Goods range/stock query | No write transaction |
| petshop.readShopMerchant | Merchant query | No write transaction |
| petshop.readShopReservation | Reservation agreement query | No write transaction |
| customer.registerSubject | Input/existence/registration preparation | Subject insert; unique constraint rejects duplicates |
| customer.initializeCustomerDomain | Opening facts/existence/input checks | Initial subject insert |
| customer.updateSubjectProfile | Subject query/proposed facts/checks | Conditional subject update |
| product.initializeProductDomain | Catalogue query/initial calculation | Initial catalogue collection insert |
| product.reviseProduct | Product query/revision calculation/checks | Conditional product update |
| inventory.openInventory | Initial stock/agreement preparation | Open stock role and quantum initial facts |

## Why the order is not reread

Order, stock range and business decisions use one pre-read set of facts; commit
compares that set's revision. If another transaction changes the order, the
conditional update rejects, with quantum changes/receipt undone under the bound
LOCAL transaction. Rereading the order while retaining old stock ranges and
calculations mixes baselines rather than providing protection. There is no
default success path using obsolete checks after the version changes.

Cart and actual cross-domain profile updates follow the same rule. Later buyer
deactivation does not retroactively reject already admitted in-flight business.
Authoritative settlement baselines/bounds still protect quantum funds/stock.

## Sequential/nested transactions and request coordination

Accepted reservation stage one remains queryable by reservation number even if
stage two lacks stock. fail saves REJECTED/INSUFFICIENT_STOCK rather than undoing
acceptance. Payment's inner REQUIRED is not an independent commit: order/receipt
failure undoes funds within LOCAL shared resources. Atomics do not call atomics,
and scenarios do not call scenarios.

JDBC deduplication registration/result storage uses short technical transactions,
not a transaction wrapped around the scenario. Failures after independent commits
or uncertain outcomes retain recovery state; the entire scenario cannot be
automatically replayed. An automatic recovery coordinator was not implemented.
These coordination semantics require the optional provider; absence in basic
builds is warned, not presented as equivalent replay behavior.

## Comparison and verification boundary

Java can provide the same guarantees, often through handwritten version checks
and method transactions. SPP exposes admission, parallel dependencies and joint/
staged boundaries; APP manages baselines and PHY conditional writes/quantum
settlement. Framework correctness is required for cross-domain baselines,
authoritative fact composition and recovery protocols.

This example's joint boundary uses common JDBC resource/schema/connection.
Other quantum providers must implement loading/initialization protocols.
Existing databases are not automatically migrated; see
[optimistic protection](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-optimistic-participant-facts.md)
for snapshot revision columns and separated quantum facts. The original audit
phase generated/compiled/statically reviewed only, without running regression,
services or databases. Later runs must supply their own evidence.
