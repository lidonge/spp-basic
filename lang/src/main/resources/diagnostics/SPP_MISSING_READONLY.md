# SPP_MISSING_READONLY

## Rule

A scenario without the `readonly` modifier contains at least one call. Every call resolves to a query, check, calculation or participant identification, and the body contains no transaction block.

## Review rationale

Declaring an inquiry `readonly` lets the compiler catch a future persistent write anywhere in its branches. Calculations may still change the participant facts returned to the caller. The checker follows guarded and conditional branches. An unresolved call, remote binding, quantum service or locking query suppresses the suggestion. The warning points at the `Scenario` keyword.

## Examples

### Wrong for a read-only inquiry

```spp
domain ledger;
Entity Entry { int postingNumber; }
Scenario serial preview(Entry row) { findEntry(row); }
```

### Right for a read-only inquiry

```spp
domain ledger;
Entity Entry { int postingNumber; }
readonly Scenario serial preview(Entry row) { findEntry(row); }
```
