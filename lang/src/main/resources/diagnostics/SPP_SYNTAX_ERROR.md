# SPP_SYNTAX_ERROR

## Rule

The lexer or parser rejected source input.

## Review rationale

Fix the first unexpected token before reviewing later parser errors.

## Examples

Wrong

```spp
Role Item { int ; }
```

Right

```spp
Role Item { int quantity; }
```
