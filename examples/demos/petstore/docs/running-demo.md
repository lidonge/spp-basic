# Run the Petstore demonstration

From the examples root (spp-basic/examples in the integrated distribution):

```bash
mvn -pl :petstore-demo -am test
./demos/petstore/run-demo.sh web
```

Open http://127.0.0.1:8086/. The build requires Java 17, Maven and the matching S++
toolchain installed in your Maven repository. Follow the distribution's SDK
installation instructions before generating the demo.

The default profile uses the basic toolchain. Optional enhanced providers must be
installed separately before running:

```bash
mvn -Penhanced -pl :petstore-demo -am test
```

Missing optional providers produce warnings and basic fallback behavior, not an
assertion that enhanced replay, tracing or partial updates are active.

## Explore the business journey

1. Open/register a member and inspect the zero starting balances.
2. Fund the wallet and receive inventory through the supplied transfer actions.
3. Add goods to a cart, quote and place the order.
4. Pay, ship and deliver; inspect the associated receipts.
5. Try cancellation/returns on eligible orders and compare state transitions.
6. Repeat with insufficient stock or funds and inspect unchanged committed facts.

Set SPP_WEB_HOST=0.0.0.0 only if LAN access is intended. The default database is
under target/petstore; restarting preserves it, while Maven clean removes target.
Use an isolated demo database for experiments, never production data.
SPP_DEMO_SKIP_BUILD=1 reuses a build and should not be used after changing source.

[Development method](implementation-path.md) · [Validation](validation-guide.md)
