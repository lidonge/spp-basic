# SPP_REDUNDANT_DUPLICATE_SERVICE

## Rule

Two non-empty services of the same kind and parameter types contain the same operations, possibly differing only in one literal or declared constant.

## Review rationale

Duplicate service bodies create two places to maintain equivalent behavior. Consider one service with an array participant or a shared implementation when that matches the domain. Similar-looking services with different operations or fields are not necessarily duplicates.

## Examples

Wrong:

```spp
domain D;
Entity E { String name; }
atomic calculate first(E a) { a.name = "one"; }
atomic calculate second(E b) { b.name = "one"; }
```

Right, when both names represent the same operation:

```spp
domain D;
Entity E { String name; }
atomic calculate setName(E item) { item.name = "one"; }
```
