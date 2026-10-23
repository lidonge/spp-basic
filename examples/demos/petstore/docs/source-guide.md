# Reading Petstore by SPP source file

> Historical design/evidence from revision `417a41f`. The 2026-10-11 transaction/
> storage update superseded earlier joint-commit, automatic player-save and
> CustomerSubject descriptions. See the [implementation contract](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> That update selected AT ordinary updates, quantum settlement first and Saga
> across modes; REQUIRED does not change it. Person/Product are maintained in
> their owning domains. Later LOCAL configuration is described below.

Start with [requirements](requirements-and-functions.md),
[scenario design](scenario-design.md) and
[business model boundaries](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-business-model-boundary.md),
then follow ordering or return requests through their atomic services.

| Directory/file | Business scope | Reading focus |
| --- | --- | --- |
| membership/ | Buyer opening, profile maintenance, buyer queries | Separate customer-subject and shop-role service calls |
| catalogue/ | Browsing, repricing, shop opening, stock receipt | Products playing goods, cross-domain catalogue queries, display constants |
| cart/ | Query/add/change quantity/remove/clear | Explicit participants, contracts and shared checks |
| order/ | Quote, place, query and cancel orders | Settled line snapshots, pricing and stock release |
| payment/ | Payment and wallet funding | Two-sided quantum funds and caller bounds |
| fulfillment/ | Ship/receive | Delivery eligibility and reserved-to-sold stock |
| returns/ | Return request and approved refund | Independent scenarios, business refunds and stock restoration |
| reservation/ | Acceptance, stock reservation and queries | Two sequential transactions retaining acceptance after reservation failure |
| shared/ | Merchant, receipts, external participants, enums, constants | Definitions shared across functions |
| petshop.spp | Shop domain declaration | Directories do not change domain identity |
| petshop.app | Tables, queries, deployment and web entries | Unified domain-level configuration |
| petshop.phy | Numeric representation, datasources, quantum storage | Physical configuration independent of business subdirectories |

Each functional directory keeps its models/contracts, checks, calculations,
service declarations and scenarios together. Former mixed files were split at
complete declaration boundaries. Names identify models, checks, services,
calculations, contract relationships and queries. Numeric prefixes preserve
stable generation scan order. Search by function; for example,
payment/petshop-10-services.spp contains payment quantum services.

Begin with [request return](../src/main/spp/petshop/returns/petshop-scene-requestShopReturn.spp):
Buyer, original order and request contract define the business scope; reason is
input, eligibility is explicitly checked, and persistence is transactional.
Compare [payment](../src/main/spp/petshop/payment/petshop-scene-payShopOrder.spp)
and [order placement](../src/main/spp/petshop/order/petshop-scene-placeShopOrder.spp).
Payment carries no inventory/cart; ordering explicitly declares catalogue
collections, inventory, order and receipt.

ShopOrder itself records settlement/reservation, without a PurchaseAgreement
wrapper. Quotes hydrate candidate orders without persistence. Goods/order queries
take Goods[]/ShopOrder[] directly; generated mutable Lists preserve entry containers.

Membership, product, order and receipt numbers are documented business credentials
in SPP. Platform keys, revisions and duplicate keys belong to APP/PHY. Orders
reference Buyer directly; lines reference Goods. Mutable status/wallet facts do
not change buyer identity by membership number.

Java tests compose generated scenarios only under src/test/java; production
Java is generated under target. The original checkpoint compiled without running
regression. See [implementation history](implementation-path.md) for entity,
multiresource, concurrency and recovery evidence; old PASS results do not prove
a changed model.

## Independent domains, centralized deployment

Read customer.spp/app/phy and product.spp/app/phy for entity ownership, then
inventory.spp/app/phy for role stock/services, then petshop scenarios.
APP puts four domains in one JVM; qualified local bindings connect consumer
atomics, and PHY configures transaction resources. Stock is per-product quantum
integer; Cart/CartLine/OrderLine are roles. See [domain boundaries](domain-boundaries.md).

## Two examples of default participant names

Cart clearing and return requests omit redundant instance names both in contract
fields and scenario inputs while remaining compatible with explicit aliases:

```spp
Contract CartClearingAgreement { Buyer; Cart; Receipt; }
Contract ReturnRequestAgreement { Buyer; ShopOrder; String reason; Receipt; }
```

The complete clearShopCart(Buyer, Cart, CartClearingAgreement) and
requestShopReturn(Buyer, ShopOrder, ReturnRequestAgreement) bodies are linked below.
Buyer; means Buyer Buyer;. The contract refers to CartClearingAgreement.Buyer,
while the scenario refers to its Buyer input. Primitive facts still need names,
such as String reason;. Use aliases to distinguish same-type participants.
Duplicate default names and ambiguous argument-free calls must be rejected.

[Clear cart](../src/main/spp/petshop/cart/petshop-scene-clearShopCart.spp) demonstrates
unique binding of checkBuyerCredential; and findBuyer;, plus identify(Buyer).
[Request return](../src/main/spp/petshop/returns/petshop-scene-requestShopReturn.spp)
uses ShopOrder, ReturnRequestAgreement and
insertReceipt(ReturnRequestAgreement.Receipt). Atomic declarations may retain
aliases or use default names; binding does not depend on callee aliases.

APP duplicate paths, web requests and Java inputs use these names consistently.
Generated JSON entry fields are Buyer/Cart/CartClearingAgreement and
Buyer/ShopOrder/ReturnRequestAgreement. Nested defaults are Buyer/Cart/Receipt
or Buyer/ShopOrder/Receipt. Participants remain explicit; only redundant aliases
are omitted. This checkpoint was generation/compilation only.

Product text constants are in product.spp; shop constants in
shared/petshop-02-constants.spp. catalogue/petshop-16-catalog-labels.spp calculates
current-language display facts from constants. APP selects language; PHY supplies
English values. Inventory increments/decrements are in inventory.spp and fund
changes in payment/, order/ and returns/ service files; APP retains resource
bindings only. See [constants/localization](constants-and-localization.md) and
[multidomain bindings](local-domain-binding.md).

## Transactions and reservation

Ordinary in-transaction queries generate locks; mixed participants lock when
ordinary attributes change, not for quantum-only changes. findStockLine does not
lock quantum facts; lockStock was removed. addCartItem eligibility moved outside
the write transaction. Quantum facts logically belong to the quantum domain;
APP selects database/TCC/dedicated service, PHY integrates it. Shared-JDBC evidence
does not prove every mode. See [transaction reads](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

The historical inventory was 30 scenarios across four domains (22 changing,
eight read-only), narrowed using reliable optimistic baselines. Queries/checks/
calculations precede transactions; atomic conditional writes occur inside.
Admission-only reads are not commit conditions, and quantum settlement does not
advance ordinary revisions. Reservation has sequential transactions; payment
has nested REQUIRED logical boundaries. Non-LOCAL commits quantum work first and
recovers across modes with Saga; the recorded develop configuration uses LOCAL
joint commit. See [audit](transaction-boundary-audit.md) and [plan](transaction-evolution-plan.md).

[Reservation definitions](../src/main/spp/petshop/reservation/petshop-35-reservation.spp)
contain contracts, pure calculations and checks.
[Reservation scenario](../src/main/spp/petshop/reservation/petshop-scene-reserveShopPet.spp)
declares two sequential transactions and read-only queries. This is not the same
commit structure as payment's nested REQUIRED.

## Zero opening and independent transfers

payment/petshop-00-model.spp defines settlement accounts/funding contracts;
inventory.spp defines supply roles and two-sided stock receipt.
petshop-15-store-opening.spp only prepares zero facts.
petshop-scene-fundShopWallet.spp and petshop-scene-receiveShopStock.spp are separate
funding/receipt scenarios with explicit nullable caller bounds. Opening does not
hide gifts of funds; UI/tests compose independent transfers to demonstrate
balances/stock.

## Source organization by domain

src/main/spp contains customer/, product/, inventory/ and petshop/. Shop source
is further grouped by function: dedicated contracts/services with their scenarios,
common definitions under shared/, and domain APP/PHY at the petshop root.
The file's domain declaration, not its directory, determines ownership/deployment.

The compiler recursively discovers source under the existing root, preserving
enum → SPP → APP/RDB → PHY phases, then filename order with relative paths breaking
same-name ties. YAML, generated Java packages and runtime entries retain their
contracts. i18n/product/ and i18n/petshop/ resources remain grouped by domain/language.

Historical layout acceptance: recursive scanning/nested PHY-profile selection
passed (`/tmp/spp-domain-layout-generator.log`); develop regenerated/compiled and
34 Petstore tests passed (`/tmp/spp-domain-layout-petstore.log`). Comparing all
43 moved source files found unchanged business contents; only product/petshop
PHY localization relative paths changed for the extra directory depth.

## Settled attribute snapshots

OrderLine.name/unitPrice map product attributes but persist independently using
APP snapshot(...); product name uses current(...). Old orders retain settled
values after product renaming/repricing, new orders use new values.
See [mapping/storage](https://github.com/lidonge/spp-examples/blob/main/book/references/role-mapped-storage.md).
reviseShopGoodsPrice demonstrates repricing; product's reviseProduct owns renaming.

The later functional-directory rearrangement checked all 173 top-level
declarations unchanged and passed recursive generation/Java compilation.
See [implementation history](implementation-path.md).

Reservation also adds fail business disposition with built-in system participant
ResultInfo. Insufficient stock and other reservation faults use different business
enum reasons. See [failure handling](reservation-failure-handling.md).

## APP technical services

Shop [technical-services.app](../src/main/spp/petshop/technical-services.app) configures
all-service interception; [technical-services.phy](../src/main/spp/petshop/technical-services.phy)
binds invocation logging/timing without changing business SPP.
See [technical services](technical-services.md) for phases, error policy and examples.
The basic compiler warns and omits the optional official tracing binding when its
implementation is absent; historical trace evidence presupposes that provider.
