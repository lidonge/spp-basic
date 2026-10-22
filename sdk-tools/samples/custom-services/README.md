# Explicit custom technical service on the basic runtime

This independent example compiles SPP business logic and explicitly binds the
user-supplied `CallCounter` through APP/PHY. It depends on the public generator
and basic runtime only. The counter observes service phases, stores no participant
facts and is not registered by the SDK. It does not implement request replay,
transaction coordination or production audit storage.

From the compiler workspace:

```sh
mvn -Pbasic,basic-example -pl :spp-custom-services-example -am test
```

Or install the public compiler/runtime first and run `mvn test` here. The test
executes the generated `preview` scenario, verifies its business result and the
before/after callbacks on both the scenario and atomic service. No login or
commercial module is required. Generated Java stays in `target/`.

The same source project also declares `createRecord`, `readRecord` and
`reviseRecord`. APP supplies the entry key, repository lookup and local
transaction deployment; PHY binds an H2 JDBC repository. The tests invoke these
generated scenarios and inspect the real table. They verify that a full update
writes an explicit null and that two updates from the same read baseline yield
one success and one `PERSISTENCE_VERSION_CONFLICT`. No official change tracker,
partial-update service or request coordinator is installed. The MyBatis library
is needed to compile the generated mapper interface; this example does not use
the official enhanced MyBatis adapter.

The concurrent test uses the runtime's test-only barrier before `applyRevision`
so both requests have read the same version. Business field assignments and
transaction boundaries remain in SPP. This is a local H2 acceptance example,
not a distributed transaction or production database certification.
