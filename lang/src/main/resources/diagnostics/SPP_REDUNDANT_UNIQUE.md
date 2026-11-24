# SPP_REDUNDANT_UNIQUE

## Rule

A unique read already selects the complete primary key.

## Review rationale

The key guarantees that at most one row can match.

## Examples

Wrong

```spp
unique findItem by item.id = 1;
```

Right

```spp
findItem by item.id = 1;
```
