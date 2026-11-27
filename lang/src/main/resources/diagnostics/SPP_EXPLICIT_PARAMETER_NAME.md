# SPP_EXPLICIT_PARAMETER_NAME

## Rule

Write only the type in a scenario or atomic service parameter list. The type
supplies the name used in the service body. Calculation locals still declare
their own names.

## Repair

Change `atomic calculate total(ExternalLoss input)` to
`atomic calculate total(ExternalLoss)`. Refer to the parameter as
`externalLoss`, or declare `Entity ExternalLoss short loss { ... }` and use
`loss`. An array uses the scalar short name with an appended `s` unless the
type declares an array name with `short loss, losses`.
