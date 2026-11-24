# SPP_REDUNDANT_KEY_SETTER

## Rule

A scenario calls a calculation service to assign a participant's primary-key field and then calls the matching `find` service using that participant.

## Review rationale

The separate setter may only prepare key values for a lookup. A direct `find` binding can express the lookup from the available participant fields or constants, avoiding a preparatory call. Review suggested mappings carefully when key fields have different names.

## Examples

Wrong:

```spp
domain ledger;
const String KEY = "A";
Role Lookup { String id; String label; }
atomic query findLookup(Lookup selection);
atomic calculate selectLookup(Lookup selection) { selection.id = KEY; }
Scenario serial fetch(Lookup selection) {
    selectLookup(selection);
    findLookup(selection);
}
```

Right, bind the key directly in the `find` call:

```spp
domain ledger;
const String KEY = "A";
Role Lookup { String id; String label; }
atomic query findLookup(Lookup selection);
Scenario serial fetch(Lookup selection) {
    findLookup(selection) by selection.id = KEY;
}
```
