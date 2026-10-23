# Build and run

Use JDK 17, Maven and installed SDK dependencies. From the examples directory
(the directory containing its root `pom.xml`):

```bash
mvn -pl :petstore-demo -am package -DskipTests
./demos/petstore/run-demo.sh web
```

The default web address is http://127.0.0.1:8086. To allow LAN access:

```bash
SPP_WEB_HOST=0.0.0.0 ./demos/petstore/run-demo.sh web
```

Use SPP_WEB_PORT to change the port and SPP_DEMO_SKIP_BUILD=1 to reuse an existing
build. The web script normally compiles production and test code while skipping
test execution. This demonstration server does not provide production access
control; expose it only to a trusted test network.

The script runs public GeneratedWebServer, loading APP web catalogs, PHY resources
and generated services. The default persistent database is under
`demos/petstore/target/petstore`. Restart retains data; `mvn clean` deletes that
directory. SPP_PETSTORE_JDBC_URL selects another H2 URL.

APP `web start` initializes customer, product and shop domains, initially creating
the merchant, four products and corresponding stock/supply/settlement records.
Existing records are not reseeded on restart. Prefilled customer details are not
registered accounts: click Register to register a shopper. Balances and available
stock start at zero; funding and stock receipt are separate steps below.

Over LAN HTTP, browsers may lack `crypto.randomUUID()`; the UI uses
`crypto.getRandomValues()` to generate request serials. Ctrl+F5 refreshes cached
scripts after changes.

The default build uses the basic SDK. Missing optional enhancements emit compiler
warnings: serial headers alone do not provide replay or duplicate suppression
without a coordination provider. Do not retry financial commands blindly after
an uncertain response. The optional `-Penhanced` Maven profile requires separately
supplied enhanced artifacts; it is not needed to build the basic example.

## Java acceptance

These commands execute tests; use them when regression execution is intended:

```bash
./demos/petstore/run-demo.sh examples
./demos/petstore/run-demo.sh examples happyPathExample quantumLedgerExample
mvn -pl :petstore-demo -am test -Dtest=PetstoreScenarioTest -Dsurefire.failIfNoSpecifiedTests=false
```

`examples` remains the script command name but now invokes JUnit, not APP examples
or GeneratedExampleRunner. Tests live under `src/test/java` and normally use
independent in-memory databases, not the web database. Persistence restart tests
launch two independent child JVMs against dedicated temporary file databases.
The earlier document's 28-method count was a historical inventory, not a fixed
current test total.

Generated production Java, SQL/OpenAPI and catalogs stay under target; compiled
tests are in `target/test-classes`, and JUnit reports in
`target/surefire-reports`. Old `target/spp-examples/evidence` contains historical
APP acceptance evidence, not current test results. See [Java acceptance](java-tests.md)
for coverage and boundaries.

Browser request-serial regression:
`node --test demos/petstore/src/test/browser/RequestIdTest.cjs`.
With Playwright installed and an independent test database prepared, run:

```bash
SPP_DEMO_URL=http://<LAN-IP>:8089 node demos/petstore/src/test/browser/PetstoreLanCartRegression.cjs
```

It checks HTTP compatibility, registration, funding, stock receipt, adding two
different products and ordering. It writes test customers/orders; do not point it
at a database currently used for manual work.

## Historical requirements/design checkpoint

The original phase ran `test-compile -DskipTests`: production and Java tests
compiled, but no regression, HTTP business flow or browser workflow executed.
Generation/test compilation alone did not establish runtime acceptance. At that
checkpoint old nested scenarios in other modules had not been migrated. This is
historical scope from revision `417a41f`, not a description of every later run.

## New model and old data

The model removed generic commands, technical business fields and collection
wrappers, changing keys, foreign-key projections and aggregate snapshots.
The old target/petstore database was preserved, not automatically migrated.
Use an independent database, for example:

```bash
SPP_PETSTORE_JDBC_URL='jdbc:h2:file:/tmp/spp-petstore-new-model' ./demos/petstore/run-demo.sh web
```

Do not clean away old data or treat old snapshots as new-model acceptance.
The original document did not claim its run commands had been executed during
that documentation phase.

A new demo database starts with zero balances and stock. Register a buyer, fund
from third-party settlement (example amount 5000), then receive from supply
(example quantity 10 per product). Registration, funding and stock receipt are
independent scenarios. The latter two have explicit negative source accounts
and conserved transfers. Restart does not automatically repeat them.
