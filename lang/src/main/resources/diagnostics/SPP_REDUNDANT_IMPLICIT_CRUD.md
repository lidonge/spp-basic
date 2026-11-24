# SPP_REDUNDANT_IMPLICIT_CRUD

## Rule

An explicit `query`, `insert`, `update`, or `delete` service declares the standard built-in operation for one role, with the matching operation name and no custom body.

## Review rationale

The standard CRUD operation is already available implicitly. Removing the empty duplicate declaration keeps the model concise and lets callers use the built-in name. Safe deletion requires an SPP call that materializes the same scalar service and generated request parameter. Java integrations may import an explicit declaration’s request class even when there are no SPP callers.

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
