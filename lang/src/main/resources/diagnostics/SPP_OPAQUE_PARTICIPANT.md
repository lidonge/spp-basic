# SPP_OPAQUE_PARTICIPANT

## Rule

A service parameter has a single-letter name, a letter with a number, or a numbered name sharing its stem with another participant.

## Review rationale

Name the participant for its business responsibility so readers can check argument order. An array also fits when all occurrences play the same role.

## Examples

Wrong

```spp
Scenario transfer(Account a, Account b) { post(a, b); }
```

Right

```spp
Scenario transfer(Account payer, Account payee) { post(payer, payee); }
```
