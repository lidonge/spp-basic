# Deposit workbench

The UI under `src/main/resources/web/` is served as static resources by
GeneratedWebServer. Backend logic is entirely generated from SPP/APP/PHY, with
no handwritten Java business implementation or HTTP controller.

## Walkthrough

1. Set customer account, cash settlement account and business date. On a fresh
   database, open bank internal accounts before the customer account.
2. Enter an amount and submit a cash deposit. The UI shows customer/counterparty
   balances, receipt and eight-slot postings, retaining the original receipt number.
3. Preview a cash reversal before confirming it. Change the top-level business
   date for cross-date tests. Mode2 is only input; service rules determine behavior.
4. Query funds by account and actual persisted deposit/reversal contracts/postings
   by receipt. Recent receipts exist only in this browser, not a complete server
   transaction list. Selecting one restores its customer, cash settlement and
   internal bank accounts while keeping the currently selected business date.
5. Advanced testing creates complete participant JSON for a selected scenario.
   Edit business parameters and execute the JSON request. Ordinary buttons do
   not use changes in this editor.

Business rejection preserves backend error codes. Failed/recovery-required
ResultInfo is not shown as successful settlement. Communication interruption
shows an unconfirmed outcome; the page never automatically retries updates.
Query accounting first. Failed requests do not present input zeros or candidate
postings as confirmed balances/trades. Communication failure clears the previous
result. Account selectors, forms and JSON are frozen while a request runs to
avoid mismatched request/display context. The UI does not calculate funds, fees,
interest or compensation, or implement business validation.

## Relationship to OpenAPI

This is a custom workbench, historically first written in Chinese, not a generic
OpenAPI UI generator. Input skeletons in `templates.json` derive from compiled
OpenAPI property types/enums and SPP scenario participant declarations; layout,
forms, actions and result presentation are custom UI code. Amount inputs use
strings and response numbers retain original decimal text to avoid browser
floating-point changes.

After participant changes, run Maven generation, then refresh skeletons with PyYAML:

```bash
python3 demos/bank-deposit/tools/web-templates.py
```

Teller, authorization, agreement and accounting-subject fields are visible demo
inputs inherited from acceptance fixtures, not trusted production external facts.
No repayment, fee or reversal algorithm is implemented in the browser.

## Service entries

APP explicitly exposes account opening, deposit, preview, reversal and query
scenarios. readDepositAccount queries eligibility, accrual and fund accounts;
readCashDeposit queries original trade/postings. Both only query/check, without
transactions or updates. Default atomic CRUD is not directly exposed over HTTP.

## Start

From the examples root:

```bash
./demos/bank-deposit/run-demo.sh web
```

Default URL: http://127.0.0.1:8089/. Data persists in the demo module's
`target/xbod-deposit` H2 file. `mvn clean` removes databases under target.
For longer retention, point SPP_XBOD_JDBC_URL elsewhere.

For trusted LAN demonstration:

```bash
SPP_WEB_HOST=0.0.0.0 ./demos/bank-deposit/run-demo.sh web
```

Use SPP_ENVIRONMENT=testing for split databases, SPP_DEMO_SKIP_BUILD=1 for an
existing build, and the run script's datasource/port variables as usual.
There is no login; this is a demonstration service, not a public production host.
The basic SDK emits warnings for unavailable enhancements and does not promise
automatic replay/duplicate suppression without a coordination provider.

## Verification

XbodHttpTest checks the static homepage, APP-exposed account query, profile-specific
duplicate behavior, reversal and inaccessible default atomic services.
`tools/web-acceptance.cjs` uses Playwright against a real browser, starting with
new accounts and testing opening, exact fractional deposit, negative rejection,
preview, reversal, receipt lookup, refresh and mobile layout without script errors.

```bash
DEMO_URL=http://127.0.0.1:18089 node demos/bank-deposit/tools/web-acceptance.cjs
```

Install Playwright in the Node environment and target an isolated test service.
The script uses separate accounts but writes actual demo transactions; use a
temporary database. Chrome defaults to /usr/bin/google-chrome; CHROME_BIN overrides it.

Historical 2026-10-13 evidence: XbodHttpTest plus XbodReversalPreviewTest passed
three tests in each develop/testing environment. Both complete browser flows
passed exact display of 100.005, unchanged balance after negative rejection,
read-only preview, reversal to zero, server receipt queries and 390px mobile
layout. Logs: `/tmp/xbod-web-tests.log` and `/tmp/xbod-web-testing-tests.log`.

Follow-up develop browser acceptance also passed historical-receipt account
restoration, advanced JSON rejection without original trade, frozen forms during
execution, unconfirmed outcome/result clearing after technical network failure,
and no script errors. Log: `/tmp/xbod-ui-followup-browser.log`. Backend regression
was not repeated then because changes concerned UI interaction only.

These historical browser results come from revision `417a41f`; translating this
guide does not rerun them or establish browser acceptance under every SDK profile.
