# Complete SPP Petstore Demo

An English-first shop demonstration covering catalogue browsing, customer registration, cart maintenance, quotation, ordering, payment, shipping, delivery, cancellation, refunds, returns and reservations. All monetary examples use US dollars (USD); the displayed amounts are illustrative, without exchange-rate conversion. English catalogue text is the default, with optional Chinese catalogue localization.

Business models and behavior are written only in SPP. APP defines deployment, storage mappings and technical policy; PHY selects numeric representation and concrete resources. Production Java is generated. Java acceptance tests prepare participants, invoke generated services and assert outcomes.

## Run the web demonstration

From the repository root:

```bash
./demos/petstore/run-demo.sh web
```

Open <http://127.0.0.1:8086/>. Set `SPP_WEB_HOST=0.0.0.0` for LAN access. After building, `SPP_DEMO_SKIP_BUILD=1` reuses the current build. The default database is under `target/petstore`; restarting preserves it, while `mvn clean` removes it. See [running the demo](docs/running-demo.md).

For an English/USD walkthrough, register Alex Taylor, fund the wallet from the settlement source, receive stock from the supply source, then place and pay an order. Opening creates zero balances; funding and inventory receipts are separate conserved transfers. A fresh database provides English seed facts. Existing persisted names and order snapshots are retained rather than overwritten by a language change.

## Source organization

| Domain | Entity and role responsibilities |
| --- | --- |
| `customer` | Owns Person and customer registration |
| `product` | Owns Product and the product catalogue |
| `inventory` | Owns stock facts and supply relationships |
| `petshop` | Uses external entities through Buyer, Merchant and Goods roles; owns shop scenarios and contracts |

Inside `petshop`, source is organized by `membership`, `catalogue`, `cart`, `order`, `payment`, `fulfillment`, `returns`, `reservation` and `shared`. Every original SPP/APP/PHY/enum file uses four-space indentation, separate statements and Java-style block formatting. The online source browser preserves these actual source lines.

A role owns its added or overridden facts, while player facts stay with their original domain. APP `snapshot` mappings retain agreed order-line names and unit prices; later product changes do not rewrite old orders. Scenarios have explicit participants, checks, calculations and transaction boundaries. Calls can omit instance names and bind by unique participant type, with explicit names where needed to resolve ambiguity.

Develop uses LOCAL realization: all participating domains share a JVM and the same database/schema/connection resource, allowing ordinary and quantum changes to commit together. Testing exercises separate domain and quantum resources. See [transaction design](docs/scenario-design.md), [transaction evolution](docs/transaction-evolution-plan.md), and [the deployment specification](https://github.com/lidonge/spp-examples/blob/main/book/references/deployment-environments-and-physical-defaults.md) for supported combinations and limits.

## Role composition and storage

CartLine now declares its intrinsic Buyer/Goods members directly, without repeated whole-player mappings. APP gives cart lines an independent technical key and stores their player links in SHOP_CART_LINE_PLAYERS. Inventory demonstrates inline player identity storage. See [composition and storage](https://github.com/lidonge/spp-examples/blob/main/book/references/role-composition-and-storage.md). Existing persistent demo databases require an explicit schema/data migration or an isolated new demo database; the compiler does not rewrite historical identities.

## Acceptance

```bash
mvn -pl :petstore-demo -am test -Dtest='Petstore*Test' -Dsurefire.failIfNoSpecifiedTests=false
./demos/petstore/run-demo.sh examples happyPathExample quantumLedgerExample
```

With Playwright available, `src/test/browser/PetstoreBrowserRegression.cjs` exercises the English web journey against an isolated database. It checks USD display, original receipt replay and reservations, as well as alternate catalogue localization.

Tests cover generated scenarios, insufficient inventory/funds, conserved quantum changes, conditional ordinary saves, request replay, transaction faults, concurrency and restart. Historical verification counts identify their recorded source revisions; they are not fresh results for every later formatting or localization change. See [Java acceptance](docs/java-tests.md) and [known issues](docs/known-issues.md).

## Further reading

- [Requirements and functions](docs/requirements-and-functions.md), [implementation path](docs/implementation-path.md), [capability matrix](docs/capability-matrix.md).
- [Source guide](docs/source-guide.md), [walkthrough](docs/demo-walkthrough.md), [comparison with Java](docs/spp-vs-java.md).
- [Technical-service interception](docs/technical-services.md), [language gaps](docs/language-gaps.md), [business model boundaries](https://github.com/lidonge/spp-examples/blob/main/book/references/spp-business-model-boundary.md).
- [English book](https://github.com/lidonge/spp-examples/blob/main/book/en/README.md), [English presentation policy](https://github.com/lidonge/spp-examples/blob/main/book/references/english-demo-presentation.md).

Public engineering records use one authoritative English version; historical
verification claims are explicitly distinguished from current acceptance.
