# Run the bank deposit demonstration

From the examples root (spp-basic/examples in the integrated distribution):

```bash
mvn -pl :xbod-deposit-demo -am test
./demos/bank-deposit/run-demo.sh web
```

Use Java 17, Maven and a matching locally installed S++ toolchain. Follow the
distribution's SDK installation instructions first. Read the web address printed
by the launcher. Use only isolated demonstration data.

Basic is the default. If official enhanced artifacts are separately installed:

```bash
mvn -Penhanced -pl :xbod-deposit-demo -am test
```

Missing optional enhancements produce warnings and basic fallback behavior;
they do not disable ordinary optimistic protection or supply enhanced replay.

## Explore the model

1. Open an account and inspect its identity, eligibility and initial facts.
2. Preview or submit a cash deposit with explicit currency and decimal input.
3. Compare debt components, account movements, posting evidence and receipt.
4. Try a rejected amount or eligibility case; inspect committed facts.
5. Preview and submit an eligible original-trade reversal.
6. Compare the new reversal agreement with the original deposit.

The UI submits operations and displays results. Admission, repayment, fees,
accrual and reversal rules belong in SPP, not JavaScript.
Generated code and local database files belong under target; Maven clean removes
that directory. Preserve needed test evidence before cleaning.

[Development method](implementation-path.md) · [Validation](validation-guide.md)
