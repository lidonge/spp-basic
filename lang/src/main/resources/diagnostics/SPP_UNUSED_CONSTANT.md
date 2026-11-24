# SPP_UNUSED_CONSTANT

## Rule

A domain constant has no identifiable use elsewhere in the selected SPP, APP or PHY sources.

## Review rationale

Use the constant where its business value is intended. Check external clients and translations before dropping an obsolete declaration.

## Examples

Wrong

```spp
const int DAILY_LIMIT = 250;
atomic calculate reset(Allowance item) { item.remaining = 250; }
```

Right

```spp
const int DAILY_LIMIT = 250;
atomic calculate reset(Allowance item) { item.remaining = DAILY_LIMIT; }
```
