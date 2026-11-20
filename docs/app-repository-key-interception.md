# APP repository key interception

Requirement APP-KEY-1, revision 1. Applies the ownership boundary in
[CORE-1 and EXT-1, revision 1](../EXTENSION-CONTRACTS.md).

Use a key interceptor on an explicit repository query when the physical key
has a different format from the input supplied by the business service.

```spp
repository(argument(0, CatalogRecord.classification), argument(0, CatalogRecord.code), phases)
    using parameterRecords intercept key {
        classification = "PHS";
        code = "00000000" + classification;
    } findPhase;
```

An assignment targets a declared String key path. Its expression contains
String literals and declared String key paths joined with `+`. References read
the original input key fields for this lookup. Other key values pass through.
Only referenced inputs and pass-through keys are required, using the ordinary
repository rules for missing keys. Interception changes the bound lookup values
for this rule without writing to the input participants. The query hydrates its
result as usual and retains its existing read policies.
Unknown or ambiguous key paths, duplicate assignments, and non-String operands
are compilation errors. Java technical service lifecycle hooks use their
existing bindings.

Acceptance covers successful queries through the generated application against
JDBC, unchanged call arguments, distinct bindings for consecutive lookups,
simultaneous assignment, invalid APP declarations, and propagated SQL failures.
