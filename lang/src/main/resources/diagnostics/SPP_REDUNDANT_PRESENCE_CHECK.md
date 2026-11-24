# SPP_REDUNDANT_PRESENCE_CHECK

## Rule

A one-use check service tests only whether its single participant is identified, and its scenario call can be replaced by `identify`. For a custom rejection code, the rule applies when that check can be folded into the next compatible one-use check.

## Review rationale

An extra presence-only service can hide a standard identification guard or split adjacent checks. Inline the standard `identify` operation, or fold a custom guard into the following check when the diagnostic suggests it. Preserve checks with additional logic, multiple uses, or behavior that cannot be combined safely.

## Examples

Wrong:

```spp
domain D;
Entity E { String name; }
atomic check present(E item) {
    require(item, "PARTICIPANT_NOT_IDENTIFIED");
}
Scenario flow(E item) { present(item); }
```

Right:

```spp
domain D;
Entity E { String name; }
Scenario flow(E item) { identify(item); }
```
