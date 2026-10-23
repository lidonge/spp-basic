# Validate the Petstore model

Turn each business claim into an observable outcome before asking AI to implement it.

| Exercise | Expected observation |
| --- | --- |
| Empty or invalid cart | No accepted order or inventory movement |
| Valid order | Agreed lines/total, reserved inventory, updated cart and receipt agree |
| Stock bound exceeded | No negative availability or partial accepted order |
| Payment | Buyer and merchant movements match the payment agreement |
| Unpaid versus paid cancellation | Only the paid path refunds funds |
| Catalogue changes after purchase | Existing order snapshots retain agreed prices/names |
| Stale ordinary update | Revision protection prevents lost updates |
| Persistence failure | Verify committed database state, not just thrown errors |

Inspect [Java acceptance tests](../src/test/java/) and
[browser tests](../src/test/browser/). Java prepares participants, calls generated
services, asserts results and injects technical faults; it must not implement
shop business logic.

Run the basic profile first using [these commands](running-demo.md). If testing
optional replay or enhanced update behavior, install the relevant providers and
use the enhanced profile. Without them, the compiler warns and basic generation
does not promise duplicate suppression.

These are validation instructions, not a claim that a particular run passed.
Record actual execution results in engineering evidence outside this tutorial.
A successful local test is not proof of production distributed recovery.
