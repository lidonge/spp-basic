# Petstore language tour

The following excerpts come from the linked source files. They are excerpts,
not standalone compilation units; generate the complete demo.

## Role: a subject in a business interaction

From [membership model](../src/main/spp/petshop/membership/petshop-00-model.spp):

```spp
take Person as Buyer {
    mapsame;
}
```

Buyer is declared as a Role with membership facts and a quantum wallet. Person is
a Reference to the customer domain. This take mapping connects the player to its
role; mapsame maps same-named facts, not every business responsibility.

## Contract: record the agreement, not the execution machinery

From [order model](../src/main/spp/petshop/order/petshop-00-model.spp):

```spp
Contract ShopOrder {
    business("Order reference presented to the buyer") String orderNumber;
    Buyer buyer;
    OrderStatus status;
    OrderLine[] lines;
    int reservedQuantity;
    BigDecimal(19, 2) subtotal;
    BigDecimal(19, 2) discount;
    BigDecimal(19, 2) shipping;
    BigDecimal(19, 2) total;
    String address;
    String tracking;
    String reason;
}
```

The contract carries the agreed facts. OrderStatus is a finite business state;
technical request IDs, retry retention and deployment endpoints do not belong here.
OrderLine keeps the agreed quantity and price, so later catalogue changes must not
rewrite an accepted order.

## Scenario: readable coordination of atomics

From [placeShopOrder](../src/main/spp/petshop/order/petshop-scene-placeShopOrder.spp):

```spp
    parallel {
        findBuyer;
        listProducts;
    }
    identify(Buyer);
    locateBuyerCart;
    findCart;
    identify(Cart);
    checkCart;
    checkQuotationInput;
    quoteOrder;
```

Scenario serial defines the overall ordering; an explicit parallel group admits
independent work. Generated CRUD names such as findBuyer hydrate declared
participants. identify confirms that a query identified the participant; it is
not a null-test convention. Argument-free calls require unambiguous binding by
participant type and collection dimensions.

## Transaction and quantum bounds

Another excerpt from that scenario:

```spp
        reserveInventory bounds {
            StockLine[].available: [0, null];
            StockLine[].reserved: [0, null];
        };
```

Quantum updates express changes to shared quantities. The caller constrains their
settlement: availability and reserved stock may not become negative; null means
that side is unbounded. Bounds are enforced at settlement, not merely by a stale
pre-check. The surrounding transaction also contains ordinary persistence.

An ordinary check does not imply rollback of every surrounding transaction.
Read its failure semantics and the declared boundary; do not assume Java-style
exception behavior. Transaction realization still depends on APP/PHY resources.

## Read the next capability in source

| Capability | Source |
| --- | --- |
| Separate payment agreement and movement | [payment](../src/main/spp/petshop/payment/) |
| Cancellation and refund contracts | [order model](../src/main/spp/petshop/order/petshop-00-model.spp) |
| Return workflow | [returns](../src/main/spp/petshop/returns/) |
| Reservation with explicit failure handling | [reservation](../src/main/spp/petshop/reservation/) |
| Cross-domain references | [external entities](../src/main/spp/petshop/shared/petshop-01-external-entities.spp) |

[Domains and layers](domain-and-layers.md) explains what these declarations do not configure.
