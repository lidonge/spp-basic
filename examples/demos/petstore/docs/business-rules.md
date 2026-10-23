# Petstore business rules and invariants

## Quote before accepting an order

Use [order calculations](../src/main/spp/petshop/order/petshop-20-calculations.spp)
as the exact definition of quantities, discounts, shipping and totals. Review it
with [order checks](../src/main/spp/petshop/order/petshop-05-checks.spp).
Do not assume that one promotional formula describes every shop.

For each rule, ask AI for examples immediately below, at and above the boundary.
The business owner confirms the expected result before a test is written.
Amounts in the shop demonstration use USD; localization changes presentation,
not enum identity or pricing rules.

## Keep stock and money movements explainable

Opening a member or shop establishes identity and zero balances. Wallet funding
and stock receipt are separate transfers from their respective source participants.
They are not unexplained assignments of positive balances.

Order placement moves stock into reservation; payment transfers funds between
actual participants; cancellation releases or refunds the corresponding facts.
Read [payment](../src/main/spp/petshop/payment/), [inventory](../src/main/spp/inventory/)
and [fulfillment](../src/main/spp/petshop/fulfillment/) together.

For each movement, identify the source, destination, agreement, amount/quantity
and permitted lower/upper bounds. Assert both sides, not just a successful receipt.

## Keep lifecycle transitions separate

Unpaid cancellation does not refund a payment. Paid cancellation needs the seller
and refund agreement. Return request and return approval are distinct decisions.
Order ownership and current state must be checked against identified participants.

Receipt, original order and the new agreement are different business facts.
Technical retry protection does not replace the business test for whether a
transition is legal.

## Review persistence and failure together

The transaction must contain the writes that define one accepted outcome. Query
and pre-check success alone does not guarantee settlement; another writer may
change availability before commit.

Check actual stored order/cart/receipt state and settled quantities after success,
rejection and injected persistence failure. Default ordinary revision protection
must remain even when optional enhanced components are not installed.

See [validation guide](validation-guide.md) for exercises rather than development logs.
