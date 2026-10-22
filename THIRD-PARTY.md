# Third-party source and dependency notices

The root Apache-2.0 license covers S++'s own files in this source distribution.
It does not replace third-party terms or authorize separate closed components.

| Source input | Origin and terms |
| --- | --- |
| `lang/src/main/resources/Java.g4` | ANTLR Java grammar, copyright 2013 Terence Parr and Sam Harwell. The full BSD notice remains in the file and in `licenses/ANTLR-Java-BSD.txt`. |
| `generator/src/main/resources/JavaSpring/methodBody.mustache` | Modified counterpart of the OpenAPI Generator 6.5.0 JavaSpring template, Apache-2.0. S++ customizes generated method dispatch. The original license and copyright notices are retained in `licenses/OpenAPI-Generator-6.5.0.txt`. See the [fixed upstream license](https://github.com/OpenAPITools/openapi-generator/blob/v6.5.0/LICENSE). |
| `generator/src/main/resources/spp.mustache` | OpenAPI sample-shaped output template, retaining its upstream-style sample metadata. This metadata is not a license grant for a user's business application. |

Maven dependencies and Node packages are resolved from the coordinates and lock
file in this tree. They are not copied into a source export. Their own licenses
apply when building or distributing JARs/VSIX files. The editor client build
copies the license texts of its bundled Node dependencies into `client-dist`.
Shaded Java distributions additionally require a complete transitive dependency
license/NOTICE inventory; retaining one colliding `META-INF/LICENSE` is insufficient.

The generator uses `com.servpp:mustache:1.0-SNAPSHOT` and
`com.servpp:multiexpr:1.0-SNAPSHOT`, both built from the included
[template engine source](vendor/antlrmustache/SPP-INTEGRATION.md). The maintainer
confirmed ownership and authorized both modules for Apache-2.0 distribution on
2026-10-22. Their fixed upstream revision and original file hashes are recorded
in `vendor/antlrmustache/ORIGIN.json`; local changes and original author notices
are preserved. No sibling checkout or preinstalled template JAR is required.
