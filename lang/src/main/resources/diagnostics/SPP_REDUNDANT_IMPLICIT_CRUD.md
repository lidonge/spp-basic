# SPP_REDUNDANT_IMPLICIT_CRUD

## Rule

An explicit `query`, `insert`, `update`, or `delete` service declares the standard built-in operation for one role, with the matching operation name and no custom body.

## Review rationale

The standard CRUD operation is already available implicitly. Removing the empty duplicate declaration keeps the model concise and lets callers use the built-in name. A custom signature or a real body is not covered by this rule.

## Examples

Wrong:

```spp
domain shop;
Role Buyer { String id; }
atomic query findBuyer(Buyer);
```

Right: omit the declaration and call the built-in operation where needed:

```spp
domain shop;
Role Buyer { String id; }
Scenario serial fetch(Buyer buyer) {
    findBuyer(buyer);
}
```
