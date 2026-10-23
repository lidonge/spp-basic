# Zero opening and conserved transfers

Account opening, role maintenance and demo database startup create only zero-balance
or zero-stock records; they do not create funds or goods. Buyer funding is a separate
scenario: the third-party settlement account decreases and the buyer wallet increases.
The settlement account starts at zero, with no lower limit in the call's `bounds`.
Stock receipt is also separate: the supply-source balance decreases and saleable
stock increases, with no lower limit on the source. A negative source balance records
cumulative net transfers from outside the system, not resources created from nothing.
The paired changes in the quantum store remain conserved.

SPP describes `SettlementAccount`, `StockSupply`, `WalletFundingAgreement`,
`StockReceiptAgreement` and their scenarios. APP defines role keys and exposes entry
points; PHY retains the unified quantum system. The web UI offers separate funding
and per-product stock-receipt buttons, without automatic funding after registration.
Java acceptance tests compose generated scenarios to prepare the original balance
of 5,000 and stock of 10 units per product; they do not implement business formulas.

The recorded implementation path starts with modeling and zero-value opening,
explicit two-sided quantum transfers and call bounds, followed by the framework's
zero-fact storage creation mechanism without hidden nonzero credits. The UI and
acceptance tests are then updated, followed by the complete `develop` suite and the
three-JVM `testing` web flow. This is not a claim of new test runs during translation.

Assigning initial balances or stock directly obscures their origin. This design
separates opening from transactions and applies consistent conservation and bounds,
at the cost of explicit funding and stock-receipt steps in the demo. Cross-database
zero-record creation and failure retries are encapsulated by a generic technical
task mechanism; see [technical recovery](quantum-opening-recovery.md).

English translation of the document at examples revision `417a41f`; business
identifiers and the original implementation scope are unchanged.
