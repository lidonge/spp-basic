# SPP_SEMANTIC_ERROR

## Rule

A parsed expression or declaration violates a compiler rule.

## Review rationale

Use the reported source location and business rule to correct the program.

## Examples

Wrong

```spp
atomic calculate clear(Item item) { item.unknown = 0; }
```

Right

```spp
atomic calculate clear(Item item) { item.quantity = 0; }
```
