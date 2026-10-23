# Petstore domains and layers

## Ownership before deployment

The [source tree](../src/main/spp/) separates customer, product, inventory and
petshop. Person and Product are owned subjects; Buyer, Merchant and Goods express
shop responsibilities. Reference declarations and take mappings connect them.
A single application deployment does not merge their ownership or permit a
consumer to bypass the owner's service and read its tables.

A role stores its own additional or overridden facts. Player facts remain with
the player unless a mapping explicitly creates a snapshot. Order-line names and
unit prices are agreed snapshots, not live catalogue values.

## Three kinds of decisions

| Layer | Question | Petstore example |
| --- | --- | --- |
| SPP | What is agreed, checked, calculated and changed? | Buyer, ShopOrder, quoteOrder, placeShopOrder and transaction boundaries |
| APP | How is the model exposed and bound? | Application deployment, tables/keys, player mapping, snapshots and optional request coordination |
| PHY | Which representations and resources implement it? | Numeric representation, JDBC repositories and quantum storage |

Follow the SPP, APP and PHY files side by side in the
[petshop source directory](../src/main/spp/petshop/) and the other owning domains.

Changing a database URL should not rewrite the quotation formula. Changing a
shipping rule should not require embedding shop-specific code in the runtime.
Frontend code submits commands and displays returned facts; it does not implement
eligibility, pricing or inventory settlement.

## Consistency is not just a deployment label

The develop configuration uses LOCAL realization with compatible shared
JVM/database/schema/connection resources. Under that contract, ordinary and quantum
changes can commit together. Merely selecting LOCAL does not make independent
resources atomic.

Ordinary read-modify-write retains optimistic revision protection. Quantum
settlement checks bounds. Optional property tracking and request coordination
are separate capabilities, not prerequisites for basic lost-update protection.

The testing topology separates resources and requires the configured recovery
capabilities. A local H2 demonstration is not a production distributed-system
certification. See [validation](validation-guide.md).
