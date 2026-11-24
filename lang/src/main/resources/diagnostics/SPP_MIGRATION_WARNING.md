# SPP_MIGRATION_WARNING

## Rule

A strict compiler rule is running in migration warning mode.

## Review rationale

Resolve the reported construct before enabling strict enforcement.

## Examples

Wrong

```spp
balance.amount -= payment.amount;
```

Right

```spp
balance.amount -> clearing.amount : payment.amount;
```
