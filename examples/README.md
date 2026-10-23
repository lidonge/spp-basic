# S++ business examples

Petstore and bank-deposit sources are included directly in this checkout, not
as a submodule. Production business Java is generated from SPP/APP/PHY; Java
acceptance tests prepare participants, invoke generated services and check actual
outcomes. These demonstrations are not production banking or payment systems.

## Build and run

From the SDK root, install the basic toolchain first (Java 17 and Maven required):

```sh
mvn -Pbasic -DskipTests install
mvn -f examples/pom.xml clean test
cd examples
./demos/petstore/run-demo.sh web
# or ./demos/bank-deposit/run-demo.sh web
```

The default build needs no commercial artifacts or membership. Missing optional
enhancements generate explicit warnings; ordinary full-field persistence and
optimistic revision protection remain. Request replay/duplicate suppression are
not provided when the corresponding providers are absent. Separately installed
matching official enhancements can be selected with `mvn -Penhanced clean test`
from this directory. See [toolchain requirements](docs/toolchain.md) and the
[basic fallback contract](../docs/basic-optional-enhancements.md).

- [Petstore walkthrough](demos/petstore/README.md)
- [Bank-deposit walkthrough](demos/bank-deposit/README.md)
- [English book](https://us.servpp.top/book/)
- [Website and book source](https://github.com/lidonge/spp-examples)

## Origin and licensing

The examples originate from the public `lidonge/spp-examples` repository, with
basic-profile support and English documentation updates. Only source files are
imported, not its Git history, generated artifacts, databases or commercial code.
Historical acceptance records retain their original scope; they are not claims
of current test runs. Local links to unbundled book/site material point to its
public source instead of copying Chinese manuscripts or publication binaries.

The original [MIT license](LICENSE) applies to the scoped demo code/documentation,
not the SDK's Apache-2.0 license. The unbundled book remains all rights reserved;
its [copyright notice](book/COPYRIGHT.md) is retained without relicensing it.
See [import provenance](IMPORT.json) for source and imported file hashes.

Public demo Markdown is English. Before publication run
`python3 examples/tools/check_english_demo_docs.py` from the SDK root.
