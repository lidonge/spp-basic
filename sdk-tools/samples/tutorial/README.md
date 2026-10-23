# First S++ scenario

Follow the [S++ tutorial](../../../docs/tutorial/spp-tutorial.md) to check,
generate, compile and run this input with the open basic SDK. `prepare` creates
an example amount of 7, `twice` changes it to 14, and `verifyAmount` checks the
result. APP declares `preview` as a generic example-runner entry.

No business database, account or official enhanced module is needed. The runner
uses H2 for technical storage; its driver is added by the tutorial commands.

`VerifyTutorial.java` is an acceptance adapter: it invokes the generic runner and
asserts the business result. The runner's own PASS line only means no technical
exception escaped. All business preparation, calculation and checks stay in SPP.
