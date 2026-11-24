# SPP_DEEP_NESTING

## Rule

Control flow in a scenario or atomic body reaches a depth greater than the configured limit. The warning points to the first construct at the deepest level.

## Review rationale

Each nested condition, loop, switch, serial or parallel group, transaction or guarded call body adds one level. Sibling branches and else-if chains share a level. The default limit is 4. Set a positive integer with the compiler JVM property `spp.lint.maxNesting`. The example uses `JAVA_TOOL_OPTIONS=-Dspp.lint.maxNesting=1`.

## Examples

Wrong

```spp
Scenario review(Account item) {
    if (item.open) {
        if (item.overdue) { notifyOwner(item); }
    }
}
```

Right

```spp
Scenario review(Account item) {
    if (item.open && item.overdue) { notifyOwner(item); }
}
```
