> Historical document: this describes pre-refactoring implementation/acceptance,
> not current source or test results. See [current refactoring](latest-petstore-refactoring.md).
> Old sources are under `legacy-source/`.

# Final verification of the source-only refactoring

The historical 2026-10-09 run of
`mvn -pl :xbod-deposit-demo -am clean verify` completed with BUILD SUCCESS in
15 minutes 19 seconds. Log: `/tmp/spp-source-only-final-clean-validation.log`.

- Handwritten Java in main/test/other source directories: zero. The 14 original
  main classes, 21 original test families and nested classes were absent.
  The JAR contained no old banking main classes.
- From empty target directories, 4,207 Java files were generated and compiled.
  Business logic, transactions, cases, assertions and Chinese report contents
  originated in SPP; APP bound queries/updates, deployment and acceptance
  controls; PHY bound BigDecimal and shared JDBC/quantum resources.
- All 945 public JUnit tests passed: lang 303, appframe 537, generator 105;
  zero failures/errors/skips. Banking had no handwritten JUnit at that stage;
  all 86 APP entries executed independently and passed.
- Five two-independent-JVM groups passed: reversal identity/later transactions,
  pending authorization continuation, cash report, complete deposit report and
  actual reversal report. Each stage queried a file database from empty
  participants. Only one JVM held it open at a time; the next JVM reopened it.
  The 60-second limit remained unchanged.
- All 96 JSON and 96 Markdown statuses under `target/spp-examples/evidence`
  were PASS. Deposit resume: ten cases, six actual trades/48 postings, five
  unexecuted candidates, balance 2300/version 3/product 2200, EN432/E1043
  rejections, exact principal 0.001, and per-case request/date/authorization/source
  facts. Replay observed current 2200/version 2 but returned original 700/version 1.
- Reversal resume: four actual reversals/32 inverse postings, 20 quantum records
  with zero summed delta. First reversal and replay/duplicate rejection retained
  14 rows, cash 1700/version 4/product 2200, refund 2. Normal/bad/fractional debt
  restored original components while preserving current products and later deposits.
- Full regression also covered actual concurrency, snapshot/document tampering,
  version races, four JDBC commit points, unknown-outcome queries, partial posting
  failure, unavailable audit, exact quantum persistence and six Saga recovery
  cases. Entry responsibilities are in [source coverage](source-example-coverage.md);
  transaction sources are 92/93/98/99b/99zv.
- Searches of public Java/templates found no old banking classes or hardcoded
  KSADEP0/GSA01102/EN432/F0554. Banking business logic was not moved into the
  framework. Public extensions/semantic tests are in the refactoring record.

Final reports were
`target/spp-examples/evidence/depositCompleteReportResumeExampleRun.json` and
its Markdown counterpart, plus `depositActualReversalReportResumeExampleRun.json`
and its Markdown counterpart in the same directory. This was acceptance of the
agreed source-only SPP refactoring scope, not production bank replacement or
golden equivalence to original COBOL. The scope represented by
`productionReplacementVerified=false` remains unchanged.

At that historical milestone, P1–P4, full transaction/recovery responsibilities,
handwritten-implementation retirement, startup/storage/reporting and documentation
migration were recorded complete. See [acceptance audit](acceptance-audit.md),
[implementation path](implementation-path.md) and [comparison](spp-vs-cobol-java.md).

These claims are preserved from revision `417a41f`, not newly executed tests.
Later rules permit Java acceptance tests; the zero-test-Java requirement and
Chinese report description above record that historical stage, not the current
public documentation policy.
