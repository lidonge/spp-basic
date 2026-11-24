# SPP_TOO_MANY_PARTICIPANTS

## Rule

A scenario declares more participants than the configured limit. Each formal parameter, including an array, counts once.

## Review rationale

The default limit is 8. Set a positive integer with the JVM property `spp.lint.maxParticipants`. For a CLI worker, use e.g. `JAVA_TOOL_OPTIONS=-Dspp.lint.maxParticipants=2`. A count equal to the limit is accepted. The example uses a limit of 2 and groups equivalent accounts.

## Examples

Wrong

```spp
Scenario settle(Header header, Account first, Account second) { post(header, first); post(header, second); }
```

Right

```spp
Scenario settle(Header header, Account[] accounts) { postBatch(header, accounts); }
```
