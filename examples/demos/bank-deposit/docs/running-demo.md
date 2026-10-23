> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. Current operation is documented in the
> [web workbench](web-workbench.md); see [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`. Commands below record old interfaces,
> not instructions to invoke removed entry points with today's script.

# Historical source-language transaction acceptance

From the then-current repository root, the script built the module and invoked
public GeneratedExampleRunner:

```bash
./demos/bank-deposit/run-demo.sh
./demos/bank-deposit/run-demo.sh ordinaryCashExample
./demos/bank-deposit/run-demo.sh pendingRestartSetupExampleRun
```

Arguments were APP examples entry names only. Business facts, full transactions,
queries, recovery and assertions came from SPP/APP/PHY. Ordinary entries used
PHY datasources directly. APP declared two-stage restart entries; the runner
launched two independent JVMs, with stage two recreating empty participants and
querying durable facts. Dedicated H2 files and stage logs lived under
target/spp-examples/restart-*. This was actual process-restart acceptance, not
crash/disaster recovery.

spp.examples.output adjusted stage output. APP using datasource keys supported
dotted or quoted hyphenated names; the bank used "spp.xbod.datasource-url",
consistent with three shared PHY resources. Without it, the runner selected a
dedicated file database. Application settings/environment were forwarded; child
JVMs chose their own default JVM core properties.

At that stage `mvn -pl :xbod-deposit-demo -am verify` automatically ran all APP
entries. Success printed PASS; failure/timeout printed FAIL and exited unsuccessfully.
Stage-completion messages alone were not sufficient evidence. Old banking Java
entries/tests had been removed; the script no longer invoked DepositDemoMain or
interpreted arguments as report directories. Final clean verification was still
in progress at this intermediate checkpoint; see [source-only audit](source-language-only.md)
and [coverage responsibilities](source-example-coverage.md).

## Declarative aggregate reports

depositCashReportSetupExampleRun passed two-JVM cash reporting: three trades/
24 postings, pending authorization/replay, current 2300/version 3/product 2200,
and two unexecuted inverse candidates. New depositCompleteReportSetupExampleRun
and depositActualReversalReportSetupExampleRun summarized ten cases/six trades/
five candidates and four actual reversals/20 quantum records respectively.
Both passed before per-case provenance was strengthened; final clean results
were required for the latest source.

Maven verify/script exported JSON and Markdown to target/spp-examples/evidence.
Names used actual stage service names; parent restart results added __restart
to avoid overwriting stage evidence. Direct Java-runner invocation could use
spp.examples.evidenceDirectory. This was generic configuration; bank titles,
values, execution flags and assertions came from SPP.

# Earlier Java implementation and report evidence

The rest preserves an even earlier configuration/output format and migration
responsibilities. Its startup/path arguments are obsolete, not current pure-source
feature claims.

## Historical command interface

The old `./demos/bank-deposit/run-demo.sh` built required modules, ran tests and
started DepositDemoMain. Maven package generated runtime classpath; the main used
fresh classes/dependencies without requiring local Maven installation.
An optional report-root argument was then supported:

```bash
./demos/bank-deposit/run-demo.sh /tmp/xbod-deposit-report
```

Each run made run-<UUID> with report.json, report.md and deposit.mv.db, neither
reusing business databases nor modifying XBOD source. Main normally shut down H2;
this was not crash/disaster recovery.

## Demonstration configuration

All accounts/parameters were fictitious fixtures, with no real customer data.
Currency CNY, business date 20261007 and next-date case 20261008.
Initial detail count was 10; each trade explicitly added one.

| Setting | Value |
| --- | --- |
| Daily cash warning | 500 |
| Teller authorization limit / A-level multiplier | 1000 / 2 |
| Customer same-area cross-institution SAC00001 rate/discount/fixed fee | .001 / 1 / 1 |
| Minimum/maximum fee | 2 / 50 |
| Product balance ceiling | 10000 |
| Ordinary/overdraft product units and ordinary calendar | 1 / .01 / ACT; no agreement by default |
| Accounts | DEMO.CASH, DEMO.DEPOSIT_LIABILITY, DEMO.OVERDRAFT_PRINCIPAL, DEMO.OVERDRAFT_INTEREST, DEMO.COLLECTION_INTEREST, DEMO.FEE_INCOME |

Institution/area facts served fee classification and selected deposit-range
eligibility. City allowed this same-area cross-institution deposit. This was
explicit demo configuration, not bank approval; see [qualification](bank-qualification.md).

## Cases and expected values

| Case | Known expectation |
| --- | --- |
| Deposit 600 into ordinary balance 100 | Net 700; F0554 warning permits execution; fee 2 |
| Deposit 50 with ordinary debt: interest 30/40, principal 100 | Repay interest 30/20, principal 0; net -120 |
| Same input on bad-debt account | Repay principal 50, interest 0/0; net -120 |
| Deposit .01, interest .005/.004, principal .01 | Preserve three-decimal repayment; principal repaid .001; net -.009 |
| Deposit another 1500 after 600 | None credentials stop pending; B resumes settlement; net 2200 |
| Replay original 600 as 600.00 | Return original trade; current net remains 2200 after later business; no version advance |
| Deposit 100 next business date | Daily total resets to 100; net 2300 |
| Locked account / old value date | EN432 / E1043; no trade/postings, only identity and attempt audit |

First three amount expectations came from migrated formulas. Fractional cases
use confirmed exact business semantics, not digit-for-digit legacy PIC results.
Authorization/fees use the explicit configuration above. Main compared expected
status, error, net balance and revision with actual reads and failed on mismatch.
JUnit additionally checked allocations, exported fields and file close/reopen.

## Machine reports and evidence limits

cases recorded each actual request, business date, authorization level, pre-run
revision, post-run queried snapshot, result/error, independent expectation,
source identifier and audits observed so far. trades contained six actual trades'
plans/before-after facts, database-read postings and full audit history.
Pending/repeated requests did not create extra trades.

Every persisted posting was compared with its plan; debit/credit and net-balance
relationships were checked. A replay Receipt referred to original settlement,
not the later current account. Reports retained both.

remaining listed unfinished work; limitations recorded unavailable bank settings/
original-runtime evidence. cobolExecuted, bankConfigurationObtained and goalComplete
were false in that stage's report. Passing tests did not imply full COBOL
equivalence, and missing production settings did not expand scope into production
deployment.

CLI accounts later all gained accrual facts. Next-date accrual used old balance
2200 for one actual day, yielding 2200 and committing with accounting; JSON retained
period/components. The stage covered deposit, authorization, fees, posting, daily
totals and selected accrual model. Complete eligibility, remaining agreement/
accrual boundaries and business/duplicate/cross-date reversal were still pending.

Bank-range/operation facts entered all CLI accounts/original trades; default
runs had no lost documents. DepositBankQualificationTest covered simultaneous
WN200/WN201/F0554 with permitted settlement.

reversalCandidates added five unexecuted plans: cash with no/full original-fee
refund, ordinary/bad-debt and fractional repayment. Each had executed=false,
actual original ledger, current version, core inverse amounts, explicit refund
and inverse-posting totals. Markdown labeled them unexecuted financial/posting
candidates. They changed neither six trades nor 48 postings and did not prove
complete duplicate/cross-date reversal.

## Later CLI cash-reversal and quantum output

After the deposit report, CLI executed four cash reversals: ordinary cash,
ordinary/bad-debt overdraft repayment and fractional amount, repeating each identity
to check replay. cash-reversal-report.json/.md saved new trades/current accounts/
actual inverse postings. quantum-report.json retained 20 quantum records from ten
successful business trades and current values per account.

Original report.json remained a deposit-stage snapshot with earlier read-only
candidates; later reversals did not rewrite it. Final state required the reversal
report and database. Cross-date policy followed confirmed XBOD rules; see
[cash source contract](cash-reversal-source.md) and
[quantum transactions](quantum-and-transactions.md).
Source derivation/demo configuration still did not mean original COBOL execution.

## Historical final state, 2026-10-09

Source-only refactoring then completed: zero handwritten main/test Java; no
remaining classes from 14 old mains/21 test families; clean generation/compilation,
945 public JUnit tests and 86 APP entries all passed. Five two-JVM groups,
strengthened per-case input/authorization/source evidence, six trades/48 postings/
five candidates, four reversals/32 inverse postings/20 quantum rows were checked.
See [final verification](source-only-final-verification.md) for that milestone.
Earlier sections retain staged history and limits.

These records come from revision `417a41f`. Later rules permit technical Java
tests; current scripts use JUnit and the workbench. Historical replay claims do
not promise coordination when optional enhanced providers are absent.
