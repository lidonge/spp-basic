# SPP_DUPLICATE_PARTICIPANT_TYPE

## Rule

A scenario or atomic service declares two participants of the same type, such as `T, T` or `T[], T[]`. Two participants of one type imply the same participant, so each must be its own Role.

## Review rationale

This is a compile error. Declare a Role per participant, e.g. `Role LossRegisteringOperator;` with `take Operator as LossRegisteringOperator;`, and use the roles as the participants. When the roles stand for records of an entity of the same domain, bind each in APP with a same-domain `play LossRegisteringOperator by <domain>.Operator { ... }`; the role needs no table of its own. A single object plus an array of its type (`T, T[]`), primitive and value types, and enums are not reported.

## Examples

Wrong

```spp
Entity Operator { String operatorNumber; String name; }
Scenario release(Operator, Operator) { findOperator; }
```

Right

```spp
Entity Operator { String operatorNumber; String operatorName; }
Role LossRegisteringOperator short registrar;
Role LossReleasingOperator short releaser;
Scenario release(LossRegisteringOperator, LossReleasingOperator) {
    findLossRegisteringOperator;
    findLossReleasingOperator;
}
```

```app
play LossRegisteringOperator by sample.Operator { operatorNumber = operatorNumber; }
play LossReleasingOperator by sample.Operator { operatorNumber = operatorNumber; }
```
