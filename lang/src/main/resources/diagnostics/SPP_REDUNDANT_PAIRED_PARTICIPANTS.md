# SPP_REDUNDANT_PAIRED_PARTICIPANTS

## Rule

Two same-type participants with paired names, such as `source` and `target`, are processed identically in an atomic service or receive matching scenario calls.

## Review rationale

Identical handling can often be represented by one array participant and one repeated operation. Keep separate participants when their behavior or meaning differs; the rule does not report differing assignments or calls.

## Examples

Wrong:

```spp
domain D;
Entity E { String name; }
atomic calculate move(E source, E target) {
    source.name = "active";
    target.name = "active";
}
```

Right, when both participants are intentionally handled alike:

```spp
domain D;
Entity E { String name; }
atomic calculate move(E[] items) {
    items.name = "active";
}
```
