# SPP_REDUNDANT_VACUOUS_BOUNDS

## Rule

A quantum `bounds` entry sets both limits to `null`, so it places no limit on the value.

## Review rationale

An unconstrained bounds entry can suggest that a range was intended when none is configured. Remove it, or provide at least one meaningful limit when a constraint is required.

## Examples

Wrong:

```spp
domain d;
Entity E { int id; }
atomic calculate read(E) {}
Scenario all(E) {
    read bounds { value: [null, null]; };
}
```

Right, with no entry when no limit is intended:

```spp
domain d;
Entity E { int id; }
atomic calculate read(E) {}
Scenario all(E) {
    read;
}
```

An actual limit such as `[0, null]` is not redundant.
