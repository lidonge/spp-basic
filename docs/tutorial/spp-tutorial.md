# S++ tutorial

This tutorial takes you from a business scenario to generated, executable Java,
then introduces persistence and custom technical services. It uses the **open
basic SDK**: no account, paid module, scenario quota or deployment-topology
license restriction is required. Instructions match the `1.0-SNAPSHOT` source
layout; use matching compiler and runtime artifacts throughout.

Public S++ documentation, examples and release notes use English as their
primary language. This tutorial has one English source of truth.

## 1. Understand the three layers

| Layer | Question it answers | Typical content |
| --- | --- | --- |
| SPP | What does the business do? | Participants, calculations, checks, scenarios and business transaction boundaries |
| APP | How is this business realized as an application? | Table identities, query bindings, service exposure, deployment and technical policies |
| PHY | Which concrete facilities execute it? | JDBC repositories, resource adapters, endpoints and technical service classes |

For example, SPP says “revise the entry and save it.” APP selects the entry key
and transaction resource. PHY supplies the database and repository adapter.
Changing databases or a request-handling policy should not require rewriting the
business scenario.

An `Entity` represents an independently identified business object. A `Role`
expresses the facts and responsibilities of a participant in a business context;
it can be mapped to an entity or another role. A `Contract` expresses a business
agreement and its terms. These are business concepts, not names for controller,
database-table or generic JSON DTO classes.

## 2. Prepare the basic SDK

Install Java 17+ and Maven 3.6.3+. The following commands use a POSIX shell and run
from the SDK source root. Windows users can use WSL, or adapt paths and classpath
separators to their shell.

```sh
java -version
mvn -version
mvn -Pbasic -pl :spp-basic-cli -am install -DskipTests
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar --version
```

`install` makes the matching runtime available to the generated application's
separate Maven build. `-DskipTests` shortens this setup command; it does not mean
tests have passed. Maven needs access to its dependency repositories on the first
build. The compiler itself works offline once its dependencies are available.
The template engine is included under `vendor/antlrmustache`; no sibling checkout
or manual template-engine installation is needed.

For VS Code installation, settings and navigation, see the
[SDK user guide](../../sdk-tools/docs/user-guide.md#vs-code).

## 3. Write a small business scenario

The complete input is checked in at
[`sdk-tools/samples/tutorial/src/main/spp`](../../sdk-tools/samples/tutorial/src/main/spp).
Its `ledger.spp` is:

```spp
domain ledger;

Role Facts {
    int amount;
}

atomic calculate prepare(Facts) {
    Facts.amount = 7;
}

atomic calculate twice(Facts) {
    Facts.amount *= 2;
}

atomic check verifyAmount(Facts) {
    require(Facts.amount == 14, "The amount must be 14");
}

Scenario serial preview(Facts) {
    prepare;
    twice;
    verifyAmount;
}
```

`domain` groups the model and services. `Facts` is the participant passed through
the scenario. A `calculate` service changes its in-memory business facts; it does
not by itself save those facts to a database. A `check` service confirms a
condition with `require` and must not modify the participant. A serial scenario
composes the named services in order: initialize 7, double it, then check 14.

The `prepare` service is an example fixture. A real application would normally
receive or query the starting facts, rather than reset every request to 7.

The accompanying `ledger.app` declares an executable example:

```app
domain ledger;

scope {
    local { preview; }
}

examples {
    run preview;
}
```

The local scope binds the example to local execution and supplies the default
for the domain’s other services. The generic example runner creates the request and its empty top-level
participants. SPP prepares the business data and checks the result. No handwritten
Java implementation of the scenario is needed. This example has no business
persistence and therefore needs no PHY file.

## 4. Check and generate

From the SDK source root:

```sh
java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar check \
  --source sdk-tools/samples/tutorial/src/main/spp

java -jar sdk-tools/basic-cli/target/spp-basic-cli-1.0-SNAPSHOT.jar generate \
  --source sdk-tools/samples/tutorial/src/main/spp \
  --output target/tutorial-java \
  --package example.generated
```

The output directory must **not already exist**. To repeat generation, choose a
new directory or deliberately remove only your previous generated output. The
CLI reports one JSON result on stdout; compiler diagnostics go to stderr.

The generated directory contains `pom.xml`, Java models and services, API
handlers, runtime catalogs under `src/main/resources/META-INF/spp`, OpenAPI
inputs under `openapi`, and `spp-generation.json`. This is an integration library,
not an automatically deployed web server. Keep your `.spp`/`.app`/`.phy` inputs
as the editable source; regenerate Java instead of patching it.

## 5. Build and run the example

```sh
cd target/tutorial-java
mvn verify
mvn dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
mvn dependency:copy -Dartifact=com.h2database:h2:2.2.224 \
  -DoutputDirectory=target/tutorial-lib
java -cp "target/classes:target/tutorial-lib/*:$(cat target/runtime-classpath.txt)" \
  ../../sdk-tools/samples/tutorial/VerifyTutorial.java
```

Expect `BUSINESS PASS preview`. The small
[`VerifyTutorial.java`](../../sdk-tools/samples/tutorial/VerifyTutorial.java)
acceptance adapter invokes the generic runner and checks the resulting business
status. It contains no implementation of the ledger calculation. Java 17 runs
this source file directly using the generated project's classpath.

The generic runner also prints `PASS preview`, meaning execution returned without
a technical exception. A failed `require` is a **business rejection**, so that
line alone is not proof that the business check passed. The adapter explicitly
checks `BusinessChecks.scenarioResult()` and exits with an assertion failure on
rejection. Hosts and tests must inspect business status, not just method return.

H2 supplies the runner's technical storage; this first lesson does not create a
business ledger table. The runner does not invent an outer business transaction.
For a failing exercise, change only `Facts.amount == 14` to `Facts.amount == 15`,
generate into a new output directory under `target/`, build it and run the same
adapter with that output's classpath: it must fail with `Business check rejected
preview`. Restore `14` afterwards. The message string is just a business rejection
code/message; changing it does not change the condition.

## 6. Add identity, queries and persistence

Continue with the complete
[custom-services example](../../sdk-tools/samples/custom-services/README.md).
From the SDK source root, run:

```sh
mvn -Pbasic,basic-example -pl :spp-custom-services-example -am test
```

Read its files in this order:

1. [`ledger.spp`](../../sdk-tools/samples/custom-services/src/main/spp/ledger.spp)
   introduces an `Entry`, a `Revision` agreement and create/read/revise scenarios.
2. [`ledger.app`](../../sdk-tools/samples/custom-services/src/main/spp/ledger.app)
   supplies the table key, query binding, LOCAL deployment and custom interceptor.
3. [`ledger.phy`](../../sdk-tools/samples/custom-services/src/main/spp/ledger.phy)
   selects JDBC, an H2 database, a transaction backend and the implementation class.
4. [`BasicPersistenceTest.java`](../../sdk-tools/samples/custom-services/src/test/java/example/technical/BasicPersistenceTest.java)
   invokes generated scenarios and checks the database results.

The revision scenario is:

```spp
Scenario serial reviseRecord(Entry, Revision) {
    findEntry;
    applyRevision;
    transaction { updateEntry; }
}
```

`findEntry` loads the existing participant, `applyRevision` changes its facts,
and `updateEntry` persists them inside the source transaction boundary. Standard
CRUD operations can be supplied by the compiler; an implicit `findEntry` or
`updateEntry` is not an undefined handwritten Java function.

The important query binding in this example is:

```app
scope {
    local {
        repository(argument(0, Entry.code), Entry) using records findEntry;
    }
}
```

The first participant argument supplies `Entry.code`; APP maps that property to
the table primary key. The query populates that participant from `records`.
It does not infer a SQL predicate from the function's spelling. A grouped or
non-primary-key query needs its own explicit APP binding and criteria; copying
this single-record binding does not turn it into a list query.

The example verifies both an explicit-null full update and a real version
conflict: two updates based on the same read version cannot silently overwrite
each other. The second conflicting write reports `PERSISTENCE_VERSION_CONFLICT`.
Basic optimistic protection works without official tracking or partial updates.
After a conflict, reload and re-evaluate the business decision; do not repeatedly
submit the stale object or disable protection just to make the write succeed.

The example's `jdbc:h2:mem:...;DB_CLOSE_DELAY=-1` database lasts for the JVM's
lifetime, even when connections close. A file URL persists across restarts.
Check the effective PHY URL when investigating retained data.

## 7. Add a custom technical service

The same example binds `callCounter` using APP:

```app
technical services { callCounter; }
intercept services {
    before: callCounter;
    after: callCounter;
    failure: callCounter;
}
```

PHY selects its implementation:

```phy
phy {
    technical service callCounter {
        implementation: "example.technical.CallCounter";
    }
}
```

[`CallCounter.java`](../../sdk-tools/samples/custom-services/src/main/java/example/technical/CallCounter.java)
implements the public `TechnicalService` interface. It observes service phases;
it does not implement request replay or transaction coordination. Custom
implementations are available in the open basic SDK. The SDK does not silently
install this counter for other projects.

`after` means the method has returned. An inner service may still be inside its
caller's transaction, and a business check may have rejected the operation.
Do not treat every `after` callback as a database commit notification.

## 8. Choose additional facilities

The open basic SDK provides the language, business generator, public extension
interfaces, basic persistence and optimistic conflict protection. Official
request idempotency, scenario duplicate submission protection, change tracking,
partial-update selection, invocation logging, automatic update scheduling and
Seata integration are separate paid-edition implementations.

The [built-in technical services manual](../../sdk-tools/docs/builtin-technical-services.md)
explains their prerequisites, independent APP switches and transaction boundaries.
The [SDK user guide](../../sdk-tools/docs/user-guide.md) covers CLI automation,
editor configuration, Java integration and troubleshooting. Reading those manuals
does not require installing the paid implementations.
