# SPP_REDUNDANT_PADDED_BLANK

## Rule

A string reference is assigned, initialized, or compared directly with an empty or whitespace-only string literal.

## Review rationale

Use the `BLANK` value to express an intentionally blank string. This makes the intent explicit and avoids carrying literal spaces as a blank marker. Meaningful text, such as `"x"`, is not reported.

## Examples

Wrong:

```spp
domain d;
Role Input { String value; }
atomic calculate set(Input) {
    Input.value = "  ";
    if (Input.value == "") ;
}
```

Right:

```spp
domain d;
Role Input { String value; }
atomic calculate set(Input) {
    Input.value = BLANK;
    if (Input.value == BLANK) ;
}
```
