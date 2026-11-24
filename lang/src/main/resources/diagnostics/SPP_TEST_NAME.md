# SPP_TEST_NAME

## Rule

A production declaration contains fixture, test or probe as a distinct identifier word.

## Review rationale

Use a name that describes the deployed behavior. Test-only declarations belong in acceptance sources. The checker recognizes directories named acceptance, test, tests, fixture or fixtures and checks identifier word boundaries, so latestQuote is allowed.

## Examples

Wrong

```spp
Role SettlementFixture { int quantity; }
```

Right

```spp
Role SettlementRequest { int quantity; }
```
