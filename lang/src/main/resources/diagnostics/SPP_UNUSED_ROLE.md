# SPP_UNUSED_ROLE

## Rule

A Role declaration has no identifiable use elsewhere in the selected SPP, APP or PHY sources for its domain.

## Review rationale

A spare role adds another shape for reviewers to reconcile with the domain model. Check external bindings before removing it, or declare its actual participant or mapping.

## Examples

Wrong

```spp
Role PaymentDraft { int amount; }
Scenario pay() { }
```

Right

```spp
Role PaymentDraft { int amount; }
Scenario pay(PaymentDraft draft) { post(draft); }
```
