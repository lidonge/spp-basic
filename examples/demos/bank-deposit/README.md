# XBOD Deposit Demo

An English-first demonstration of selected bank deposit transactions, expressed in SPP, APP and PHY and executed through generated Java services. The default teller walkthrough uses US dollars (USD), including zero-balance opening, cash deposit, repayment allocation, reversal preview, cash reversal and receipt inquiries. Amounts are illustrative; no exchange-rate conversion is performed.

Original currency-specific eligibility and accrual branches remain intact. The CNY/JPY options and regression inputs exist to verify those rules; changing the public walkthrough to USD does not replace legacy business semantics. The customer, cash settlement and bank recipient accounts receive all fund components so that repayment never makes money disappear.

## Run the web workbench

From the repository root:

```bash
./demos/bank-deposit/run-demo.sh web
```

Open <http://127.0.0.1:8089/>. Set `SPP_WEB_HOST=0.0.0.0` for LAN access or `SPP_WEB_PORT` to choose a different port. `SPP_DEMO_SKIP_BUILD=1` reuses an existing build. The default persisted database is `target/xbod-deposit`; use an isolated database for repeatable demonstrations.

1. Open the zero-balance bank interest, collection-interest and principal-recovery accounts.
2. Open the customer and cash settlement accounts.
3. Submit a USD cash deposit and inspect balances, conserved movements, receipt and postings.
4. Preview reversal without moving funds, then submit the actual reversal.
5. Look up the original and reversal receipts to verify saved facts.

The browser preserves decimal response values as text and prefixes the currency code; it does not calculate or round bank amounts. The advanced panel allows participant JSON to be inspected or edited for business rejection tests. The layout is a custom workbench, while `templates.json` is derived from the generated API model and prepared with `tools/web-templates.py`.

## Modeling and implementation

Source under `src/main/spp/cboddeposit` is separated by business function: account opening, admission, calculation, accrual, fees, deposit, postings and reversal. Source files use Java-style indentation and separate statements. Models, checks, calculations, quantum services and scenarios remain entirely in SPP; deployment and technical realization remain in APP/PHY.

Opening creates account facts and zero quantum records. A deposit is a separate financial transaction. Quantum settlement applies actual deltas and call bounds; ordinary updates preserve account and agreement facts. Reversal is a new business transaction using the original settlement components and recipient accounts, rather than restoring an old account snapshot. Next-day cash reversal follows the preserved original bank rules.

Production business Java, HTTP controllers and transaction wrappers are generated. Java tests only compose generated services, prepare inputs, assert outcomes and inject technical faults. Existing failure/recovery limits are documented instead of hidden in handwritten demo implementations.

## Role composition and storage

The role-composition update preserves the bank model: the account roles still need their explicit BankAccount take mappings because their structures borrow scalar account facts rather than repeat participant members. Existing account-number primary keys are explicit business identities and remain supported. The new APP independent-key and same-table/separate-table player-storage policies are demonstrated by Petstore and covered by compiler/runtime tests.

## Acceptance

```bash
./demos/bank-deposit/run-demo.sh examples
# Equivalent explicit command:
mvn -pl :xbod-deposit-demo -am test -Dtest='Xbod*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

`tools/web-acceptance.cjs` uses Playwright against an isolated database to exercise opening, exact-decimal USD deposit, rejected input, reversal preview, submitted reversal, receipt inquiry and mobile layout. See [the web workbench record](docs/web-workbench.md), [latest refactoring verification](docs/latest-refactoring-verification.md), and [completion audit](docs/completion-audit.md) for previously established capabilities and remaining limits. Historical counts refer to their cited source revisions.

[English book](https://github.com/lidonge/spp-examples/blob/main/book/en/README.md) · [English presentation policy](https://github.com/lidonge/spp-examples/blob/main/book/references/english-demo-presentation.md)
