# From a shopping requirement to S++

## A. Describe the scenario in one sentence

> A registered buyer places an order for the goods in their cart, reserving available stock and receiving an order receipt.

This sentence defines order placement, not payment, shipping or refunds. Those
are separate agreements and scenarios. The walkthrough develops this requirement
against [placeShopOrder](../src/main/spp/petshop/order/petshop-scene-placeShopOrder.spp).

## B. Ask AI to expand the business process

Use this prompt as a starting point, not as a claim that an AI answer is correct:

> Expand the requirement into a normal path, rejection paths, business facts,
> state changes and acceptance examples. Separate assumptions from confirmed
> rules. Do not choose a database, HTTP framework or deployment topology.

Review the analysis before generating code:

| Step | Business question | Result |
| --- | --- | --- |
| Identify buyer | Is this a known member? | Confirmed Buyer |
| Read cart and catalogue | What is selected, and what may be sold? | Confirmed Cart and Goods |
| Check and quote | Are quantities and address valid; what price rules apply? | Proposed ShopOrder |
| Read inventory | Can each selected item be reserved? | Confirmed StockLine participants |
| Accept order | Which facts must change together? | Order lines, order, reservation, cart and receipt |
| Reject | What if identity, cart or stock is invalid? | No accepted partial order |

Ask AI to list unresolved decisions such as discount thresholds, shipping charges
and what a reservation guarantees. Resolve them from the business specification
or the sample's checks/calculations; do not invent a rule just to make code compile.

## C. Choose participant roles and contracts

Roles describe what a subject does in this interaction, not an HTTP request DTO.

- Buyer is the customer's shop membership, including the wallet used by later payment.
- Goods is the shop-facing view of a product.
- Cart and CartLine express the buyer's current selection.
- StockLine supplies stock availability/reservation facts.
- ShopOrder is the business agreement: buyer, agreed lines, prices, total and status.
- Receipt is a business credential, not a generic success wrapper.

Read [Buyer and membership agreements](../src/main/spp/petshop/membership/petshop-00-model.spp)
and [ShopOrder and OrderLine](../src/main/spp/petshop/order/petshop-00-model.spp).
The scenario's declared participants are the inputs to its business conversation.
Queries hydrate those participants; a scenario does not silently create new ones.

## D. Find entities and assign business domains

Ask which subject exists independently of this particular purchase:

| Subject | Owner | Shop-facing participant |
| --- | --- | --- |
| Person | customer | Buyer or Merchant |
| Product | product | Goods |
| Stock facts | inventory | StockLine collaboration |
| Purchase agreement | petshop | ShopOrder and Receipt |

[References and take mappings](../src/main/spp/petshop/shared/petshop-01-external-entities.spp)
connect shop roles to the owning domains. A Buyer is not a second copy of the
customer-domain database. Agree ownership before choosing storage.

The folders inside petshop, such as order and payment, group business functions;
they do not automatically create independently deployed domains.

## E. Refine business logic, checks and calculations

Review [order checks](../src/main/spp/petshop/order/petshop-05-checks.spp),
[calculations](../src/main/spp/petshop/order/petshop-20-calculations.spp)
and the complete scenario together.

The source makes credential checks, participant identification, cart validation,
quotation, inventory checks and preparation visible before the transaction.
Inside the transaction it deletes cart lines, persists the agreed order, reserves
stock with non-negative bounds, updates the cart and inserts the receipt.

Ask AI to produce a rule table before editing these atomics: input facts, formula
or predicate, output facts and rejected boundary cases. Review empty carts,
withdrawn goods, invalid quantities, missing address and insufficient stock.
Calculations compute; checks validate; the scenario expresses their order.
Do not hide a check inside a calculation or replace this flow with a generic
command dispatcher.

## Continue the lifecycle without enlarging one scenario

Payment, shipping, cancellation and returns consume the original order as a
participant and use their own contracts. The caller selects the next scenario;
scenarios do not call other scenarios. This keeps each agreement reviewable.

Next: [language tour](language-tour.md), [business rules](business-rules.md),
and [validation](validation-guide.md).
