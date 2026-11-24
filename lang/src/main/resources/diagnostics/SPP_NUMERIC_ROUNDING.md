# SPP_NUMERIC_ROUNDING

## Rule

The compiler reports an implicit numeric rounding decision.

## Review rationale

Check the required rounding direction and scale for the business amount.

## Examples

Wrong

```spp
bill.amount = bill.total / bill.count;
```

Right

```spp
bill.amount = roundDown(bill.total / bill.count, 2);
```
