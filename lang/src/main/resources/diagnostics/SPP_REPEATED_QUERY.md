# SPP_REPEATED_QUERY

## Rule

Two consecutive calls in serial execution issue the same query with identical arguments and read bindings.

## Review rationale

The second call can fetch the same row again before the scenario uses the first result. Review whether the caller needs a fresh database observation. When a refresh is intentional, keep both calls.

## Examples

### Wrong when one observation suffices

```spp
domain ledger;
Entity Entry { int postingNumber; }
readonly Scenario serial preview(Entry row) {
    findEntry(row);
    findEntry(row);
}
```

### Right when one observation suffices

```spp
domain ledger;
Entity Entry { int postingNumber; }
readonly Scenario serial preview(Entry row) { findEntry(row); }
```
