# Multidomain local service binding

> Historical design from revision `417a41f`. The 2026-10-11 transaction/storage
> update superseded earlier joint-commit, automatic player-save and CustomerSubject
> descriptions. See the [implementation contract](https://github.com/lidonge/spp-examples/blob/main/book/references/transaction-storage-v1.md).
> That update selected AT ordinary updates, quantum settlement first and Saga
> recovery across modes; REQUIRED does not change it. Person/Product are maintained
> explicitly in their owning domains. Consult later LOCAL configuration below.

Business scenarios are separate from deployment. Consumers declare service intent;
APP binds query/update/calculate declarations without business bodies to provider
atomic services of the same kind. Scenarios cannot be binding targets, and
ordinary atomic services cannot call each other. Providers are real SPP domains,
not mocks.

Local services inherit the unique local scope and header without enumerating
ordinary service names again. Explicit configuration wins; ambiguous multiple
local scopes for unbound services are compile errors. Standard primary-key
repositories and unique external-participant services are generated. Complex
queries, quantum resources, duplicate protection and custom cross-domain targets
remain explicit; see [default local binding](https://github.com/lidonge/spp-examples/blob/main/book/references/app-default-local-binding.md).

```spp
Reference Person from customer.Person { String name; String email; String address; }
```

References may target entities, roles or contracts; provider identity/field types
are compile-checked. Legacy forms without from remain compatible, but this
multidomain example names sources. Imported quantum facts do not gain a second owner.

Custom cross-domain queries declare only their target; participant identity or
explicit playing relationships determine parameters:

```spp
Reference CatalogProduct from product.CatalogProduct {
    String sku; String name; String category; String description; boolean active;
}
take Goods as CatalogProduct { mapsame; }
```

```app
bind importCatalogue to product.listCatalogue;
```

Product's listCatalogue(CatalogProduct selection, CatalogProduct[] products)
uses its own APP repository condition for available products. Consumer
importCatalogue(Goods selection, Goods[] goods) maps parameters through take.
The compiler checks identity, parameter count and collection dimensions, invoking
generated mappings. Same-name fields cannot replace take. Collections match
provider APP keys while preserving entry instances, existing members and
containers. Legacy positional facts/identity blocks remain compatible but are
not used here.

Default queries uniquely locate records by APP primary key. Missing records or
incomplete key facts fail confirmation rather than searching other fields.
A role with its own table is queried first, then its players recursively through
take. A tableless role view queries through take directly. Root entities use
their owning domain's key services, then generated mappings hydrate roles:
Cart → Buyer → customer.Person and StockLine → Goods → product.Product.
Role selling price or wallet values are not copied as external entity attributes.

Non-key local queries use repositories directly, without extra binds. Available
goods, buyer orders and catalogue filters demonstrate this; no new complex
SQL/XML dialect was introduced. See the
[participant query specification](https://github.com/lidonge/spp-examples/blob/main/book/references/participant-query-binding.md).

Catalogs live under META-INF/spp/domains/<domain>; service names are qualified,
such as inventory.reserveInventory. Application manifests combine PHY, DDL,
roles, constants and web entries across domains. Provider quantum services record
per-element changes without duplicate consumer entries. Caller bounds project
through calls and are enforced by the settlement implementation.

The generic host merges compatible resources and rejects conflicts. Same JVM/
schema is insufficient for joint commit: PHY must declare shared backend,
transaction resource and connection scope. Conflicting storage business-type
names are rejected; there is no automatic table/type renaming.

The original checkpoint provided only generation, compilation and static evidence,
not runtime joint commit, rollback or UI verification. See
[domain boundaries](domain-boundaries.md) and [implementation record](implementation-path.md).

## Read and transaction refinements

Ordinary in-transaction queries automatically lock; mixed participants lock when
ordinary fields also change, not for quantum-only changes. findStockLine does
not lock quantum facts; lockStock was removed. addCartItem admission queries moved
outside the write transaction. APP selects the quantum backend and PHY integrates
it; a shared-JDBC example does not verify every mode. See
[transaction reads](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-transaction-reads.md).

The historical four-domain inventory had 30 scenarios (22 changing, eight
read-only) with narrowed transactions based on reliable optimistic baselines:
queries/checks/calculation before transactions, atomic conditional writes inside.
Admission-only reads are not commit conditions; quantum settlement does not
advance ordinary revisions. Reservation retains acceptance after later reservation
failure; payment uses nested REQUIRED logical boundaries. Non-LOCAL commits
quantum work first and recovers across modes through Saga; this phase's develop
uses LOCAL joint commit. See [audit](transaction-boundary-audit.md) and
[plan](transaction-evolution-plan.md).

Default role queries recursively follow take to root entities. Customer adds
PERSON_REGISTRY/findPerson; product adds PRODUCT_FACTS/findProduct.
Registration/catalogue creation persist local root entities and roles.
Ordinary local-role updates do not automatically write external entities.
Cross-domain profile changes explicitly call customer services and preserve
query baselines.

Inventory Reference services match a unique provider by canonical participant
domain identity and operation kind. Catalogue import, localized text and inventory
initialization keep custom target bindings. Subject registration/update/query
resolve built-in CRUD through Reference and explicit take, without local proxy
declarations or default binds. Creating a subject and creating a buyer role are
not one interchangeable write.

SHOP_MERCHANTS demonstrates APP-added technical key merchantRowId. Generated
CRUD uses it to locate role records; SPP neither declares nor manipulates it.
Merchant has one Person player, so the added key column derives from Person's
customerNumber. findMerchant/insertMerchant are generated without explicit
repository declarations. Order listing and available goods retain complex
repository examples; product catalogue retains non-key filtering.
