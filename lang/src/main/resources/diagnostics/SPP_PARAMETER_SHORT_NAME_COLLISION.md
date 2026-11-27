# SPP_PARAMETER_SHORT_NAME_COLLISION

## Rule

Every parameter of a service must have a distinct short name. Both scalar and
array names take part in this check.

## Repair

Give one type a distinct short name in its declaration. For example, for
`ExternalLoss[]` and `ExternalLosss`, use
`Entity ExternalLoss short loss, losses { ... }` and refer to the array as
`losses`. If the same participant type occurs twice in the same shape, declare
distinct roles for the two participants.
