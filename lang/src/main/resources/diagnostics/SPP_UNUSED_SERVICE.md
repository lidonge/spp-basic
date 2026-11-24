# SPP_UNUSED_SERVICE

## Rule

An atomic service has no identifiable call or deployment reference elsewhere in the selected domain.

## Review rationale

Check other domains and external bindings before deleting the service. A scenario is a callable entry point. Its declaration alone is sufficient to keep it out of this check.

## Examples

Wrong

```spp
atomic calculate applyFee(Account item) { item.amount += 5; }
Scenario charge(Account item) { }
```

Right

```spp
atomic calculate applyFee(Account item) { item.amount += 5; }
Scenario charge(Account item) { applyFee(item); }
```
