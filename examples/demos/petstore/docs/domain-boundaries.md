# Independent Petstore business domains and centralized deployment

> Historical design from revision `417a41f`. The 2026-10-11 transaction/storage
> update superseded earlier joint-commit, automatic player-save and CustomerSubject
> descriptions. See the [implementation contract](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> That update selected AT ordinary updates, quantum settlement first and Saga
> recovery across modes; REQUIRED does not change it. Person/Product are explicitly
> maintained by their owning domains. Later LOCAL configuration is described below.

| Domain | Owned business facts | Cross-domain collaboration |
| --- | --- | --- |
| customer | Person (individual or organization), RegisteredSubject view, subject registration/details | Sales uses real registration/query/save atomic services to hydrate subject facts |
| product | Product, CatalogProduct, catalogue and product text | Sales imports catalogue/hydrates text, without directly reading product tables |
| inventory | StockLine role, InventoryOpeningAgreement, per-product stock | Sales binds query/save/quantum-stock services |
| petshop | Goods, Buyer, Merchant, Cart, CartLine, OrderLine, Receipt and order contracts | Organizes selection, ordering, payment and fulfillment with explicit participants/transactions |

Customer/product domains own entities. Inventory has only external References,
roles and contracts, showing that a business domain need not define an entity.
Sales models orders/carts with roles and contracts. There are no Operator/Shop
entity wrappers or StockBook aggregate state. StockLine.available/reserved/sold
are quantum int; CartLine/OrderLine quantities are int. These are default number
representations, replaceable globally or locally through PHY.

Person plays Buyer and Merchant. Merchant's take adds trading name, shop name
and received funds. Product plays Goods; Goods plays OrderLine/StockLine;
Goods and Buyer jointly play CartLine; Buyer plays Cart. `Goods goods = Goods;`
retains the whole playing-participant relationship, not a copied technical ID.
Simple roles may be conceptually compatible with entity inheritance; this phase
introduced neither new entity-inheritance syntax nor complex multi-entity examples.

The original one-click registration design registers the customer subject then
opens Buyer, Cart and Receipt inside the shop registration transaction.
Reference/take automatically bind customer-domain standard atomic services, not
customer scenarios. Customer registration and membership numbers are distinct
business credentials. Profile updates first query the provider, preserve its
platform baseline, then save subject and buyer facts.

APP declares customer/product/inventory/petshop in one JVM. Catalogs are isolated
by domain and services use qualified names. Concise bind selects only the
provider; parameter mapping follows cross-domain participant identity or take,
preserving entry instances. Collections match provider APP keys, with inventory
using `goods.sku`. Query confirmation and platform baselines follow call context.

PHY selects compatible shopDatabase JDBC backend, database/schema, resource and
connection scope. The host merges compatible resources to share DataSource and
transaction connection. Same JVM/schema alone does not establish joint commit.
Providers own quantum stock records; consumers do not duplicate them.
Caller bounds select permitted settlement ranges.

Source, generic generation, UI and Java inputs were synchronized. That checkpoint
only regenerated/compiled; it did not run services or regression. Joint commit,
rollback, concurrency and recovery still required execution. Old databases were
preserved without automatic migration. See [implementation/evidence](multidomain-implementation.md).

## Later transaction/read refinements

In-transaction ordinary queries generate locking. Mixed participants lock if
ordinary attributes also change, not for quantum-only changes. findStockLine does
not lock quantum facts; lockStock was removed. addCartItem admission queries moved
outside its write transaction. Quantum attributes logically belong to the quantum
domain: APP selects database/TCC/dedicated service, PHY integrates it.
Joint-JDBC evidence does not establish every mode. See
[transaction reads](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

The recorded four-domain inventory had 30 scenarios: 22 changing and eight
read-only. Reliable optimistic baselines allow ordinary queries/checks/calculation
before transactions and atomic conditional writes inside. Admission-only reads
are not commit conditions; quantum settlement does not advance ordinary revisions.
Reservation uses two sequential transactions, retaining acceptance after stock
rejection. Payment illustrates nested REQUIRED logical boundaries.
Non-LOCAL policies commit quantum work first and use Saga across modes; develop
in this phase uses LOCAL joint quantum/ordinary commit. See
[transaction audit](transaction-boundary-audit.md) and [plan](transaction-evolution-plan.md).

Default recursive query chains are Cart → Buyer → customer.Person and
StockLine → Goods → product.Product. Each role/entity uses its owning domain's
APP key. PERSON_REGISTRY and PRODUCT_FACTS are root entity tables;
PRODUCT_CATALOGUE is a catalogue role table. RegisteredSubject is only a Person
view, without another table. Subject registration/catalogue creation persist
their local root entities. Consumer-role updates cannot implicitly write customer/
product entities. Take mappings hydrate facts, not matching field names.

Old databases were not backfilled with root entities. Existing data needs
migration or reconstruction in an independent new database; this phase did not
modify the existing database. See the
[query specification](https://github.com/lidonge/spp-examples/blob/main/book/references/participant-query-binding.md).
Historical compilation/runtime statements here do not replace current acceptance.
