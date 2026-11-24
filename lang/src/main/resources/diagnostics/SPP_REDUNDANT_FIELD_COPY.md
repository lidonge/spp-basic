# SPP_REDUNDANT_FIELD_COPY

## Rule

A calculation service consists only of direct field assignments from one participant to another, with no transformations or other statements, and the target is a role that can be expressed by a `take` mapping.

## Review rationale

Repeated assignments encode a field mapping procedurally. A `take` declaration states that relationship in one place and can be reused. Keep a calculation when it transforms values or contains other logic.

## Examples

Wrong:

```spp
domain ledger;
Entity Source { String name; int amount; }
Role Target { String name; int amount; }
atomic calculate project(Source source, Target target) {
    target.name = source.name;
    target.amount = source.amount;
}
```

Right:

```spp
domain ledger;
Entity Source { String name; int amount; }
Role Target { String name; int amount; }
take Source as Target { mapsame; }
```
