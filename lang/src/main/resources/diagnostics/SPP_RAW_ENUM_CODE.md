# SPP_RAW_ENUM_CODE

## Rule

An equality comparison or switch case compares an enum-typed field with a quoted wire value accepted by one of its members.

## Review rationale

Use the business member so reviewers can see which alternative is selected. Check the accepted aliases when changing a branch that intentionally distinguishes physical representations.

## Examples

Wrong

```spp
Enum State { OPEN:"O","OP", CLOSED:"C" }
Role Account { State state; }
Scenario review(Account item) {
    if (item.state == "O") { inspect(item); }
}
```

Right

```spp
Enum State { OPEN:"O","OP", CLOSED:"C" }
Role Account { State state; }
Scenario review(Account item) {
    if (item.state == State.OPEN) { inspect(item); }
}
```
