# Bank deposit domains and layers

## Begin with business ownership

The demo's domain is cboddeposit. Its functional folders group account identity,
admission, calculations, fees, accrual, postings and deposit/reversal agreements.
They do not by themselves imply microservices or independent databases.

BankAccount is an enduring subject. Roles consume account facts for a specific
responsibility; agreements retain the evidence needed to explain a completed
operation. A reversal must identify original recipients as well as the original
deposit, not accept arbitrary replacement accounts.

## Keep the three layers separate

| Layer | Responsibility | Source |
| --- | --- | --- |
| SPP | Participants, rules, formulas, workflow and transaction boundaries | [business source](../src/main/spp/cboddeposit/) |
| APP | Deployment, table/key mappings, exposure and technical request policies | [cboddeposit.app](../src/main/spp/cboddeposit/cboddeposit.app) |
| PHY | Numeric substitution, adapters and concrete storage/quantum resources | [cboddeposit.phy](../src/main/spp/cboddeposit/cboddeposit.phy) |

The PHY numeric mapping replaces BigDecimal(19, 2) with BigDecimal(25, 6).
That representation choice does not decide a banking rounding rule or fee policy.
Such business decisions must remain visible in checks and calculations.

APP declares request idempotency retention and duplicate windows. Those declarations
do not implement coordination when its optional provider is absent.
SPP must not compensate by adding technical request tables or hidden retry logic.

## Match transaction claims to resources

Develop uses LOCAL realization with compatible shared resources. Testing separates
ordinary and quantum resources and exercises the configured recovery path.
A transaction keyword alone does not prove distributed atomicity.

Ordinary revision protection is independent of optional partial updates.
Quantum bounds, ordinary persistence, idempotency and recovery each need their
own observable validation. Confirm actual commits and rollback/recovery outcomes.

Production Java is generated. Java tests may arrange input and faults and assert
results, but neither tests nor the shared runtime should contain handwritten
bank-specific business implementations.

[Development method](implementation-path.md) · [Validation](validation-guide.md)
