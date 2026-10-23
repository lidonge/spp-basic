# Petstore: an S++ capability demonstration

Model ordering, inventory reservation, payment and returns through explicit participants and business agreements.

## Learn the development method

1. **One sentence:** state the business scenario as a requirement.
2. **AI analysis:** expand the normal path, rejection paths, facts and acceptance cases.
3. **Roles and contracts:** decide who participates and what is agreed.
4. **Entities and domains:** identify enduring subjects and assign ownership.
5. **Detailed logic:** refine explicit checks, calculations, state changes and consistency.

Start with the [learning guide](docs/README.md) and its
[worked development walkthrough](docs/implementation-path.md).
Then explore [language syntax and semantics](docs/language-tour.md),
[domains and SPP/APP/PHY](docs/domain-and-layers.md),
[business rules](docs/business-rules.md) and [validation exercises](docs/validation-guide.md).

## Generate and run

From the examples root:

```bash
mvn -pl :petstore-demo -am test
./demos/petstore/run-demo.sh web
```

Open http://127.0.0.1:8086/. See [run instructions](docs/running-demo.md)
for SDK prerequisites, optional enhanced providers and database precautions.

## Read the implementation

[SPP/APP/PHY source](src/main/spp/) · [Java acceptance tests](src/test/java/)

Business behavior belongs in SPP, application bindings in APP and physical
resources in PHY. Production Java is generated; Java tests arrange participants,
invoke generated services, assert outcomes and inject technical faults.

Basic generation does not supply absent optional enhanced providers. Warnings
describe fallback behavior; ordinary optimistic protection remains.
This demonstration is not production-readiness or distributed-recovery certification.
