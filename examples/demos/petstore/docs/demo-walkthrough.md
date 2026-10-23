# A ten-minute demo walkthrough

This is a requirements walkthrough with expected values, translated from revision
`417a41f`. The model-refactoring stage described here did not run web or business
regression tests. Use an isolated database before running a new build; see
[running the demo](running-demo.md).

First register a buyer with a member account number and details. The wallet opens
at zero. Separately fund it with 5,000 from the third-party settlement source, then
receive 10 units of each product from its supply source. Read or update buyer
details and choose a normal or VIP membership level.

1. Add two bags of staple cat food. Expected merchandise is 59.80, discount 0,
   shipping 8 and total 67.80. `quoteOrder` reads server-side product facts;
   `priceLine` applies explicit business rounding. The UI only displays results.
2. Place the order. Expect an empty cart, available stock 8, reserved 2 and sold 0;
   the order awaits payment. `placeShopOrder` declares Buyer, Cart, StockLine[],
   ShopOrder, product-query criteria and results, and Receipt. Its transaction
   creates the order, reservation and receipt.
3. Pay: wallet 4932.20, merchant income 67.80. With request replay enabled, repeating
   the same request returns its original outcome. Duplicate keys and result storage
   belong to APP/PHY, not a generic command inside Receipt.
4. Ship and receive: reserved 0, sold 2. The shipment agreement contains shipment
   evidence and shipped quantity; explicit scenario checks determine eligibility.
5. Request a return separately, then confirm the return and refund. Expect stock
   restored to 10, wallet 5000 and merchant income 0. The return agreement directly
   relates Buyer, the original order and the reason. This is a new business
   transaction, not technical rollback.
6. Try unpaid cancellation and paid-but-unshipped cancellation. These are separate
   scenarios: one releases stock, the other also refunds payment. A shipped order
   cannot be canceled directly.
7. For a VIP buying four bags, expect 119.60 - 11.96 + 0 = 107.64. For a normal
   buyer purchasing two bags and one cat teaser, expect 72.15 + 8 = 80.15.
   Insufficient stock on a multi-line order must not leave a partial order.

Follow the [source guide](source-guide.md) to compare participants and agreements,
entities playing roles, direct relations, shared numeric expressions, explicit
eligibility and transactions. Stock and merchant income are shared across buyers.
Six concurrent UI refresh queries are not a single transactional snapshot. Do not
apply isolated-case conservation expectations to an existing multi-customer database.

Product copy can use the available locale resources. Locate PHY translations by
constant names in `product.spp`. Sales scenarios choose quantum call `bounds`;
`inventory.spp` defines only increments/decrements. APP and PHY respectively
describe same-JVM four-domain deployment and the shared physical resource.

Replay expectations require a compatible optional request-coordination provider.
A basic compilation warning that replay or duplicate protection is unavailable
means those enhancement-specific assertions do not apply; business eligibility,
stock conservation and transaction guarantees still apply.
