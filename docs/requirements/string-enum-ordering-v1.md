# String and enum ordering, version 1

Maintainer decision dated 2026-11-25. Applies [CORE-1 in revision 1 of the
public extension requirements](../../EXTENSION-CONTRACTS.md#core-1-business-behavior-and-persistence).

At Basic baseline `80774a3`, SPP ordering operators supported only numbers.
An alphanumeric range check could therefore fail to compile when translating
a legacy rule, including the XBOD GCM00029 level comparison.

- `<`, `<=`, `>`, and `>=` accept scalar String operands. Right-pad to equal
  length with U+0020 and compare by Unicode code point.
- APP can select `collation ebcdic;` for a deployment. Compare IBM-037 byte
  values as unsigned integers and place unmappable code points after all
  mapped characters, in Unicode code-point order. `collation binary;` is
  the default. SPP expressions are independent of the deployment declaration.
- An explicit BLANK operand represents spaces. Use the existing APP BLANK
  read policy when deciding whether a null compared with BLANK represents
  spaces. Any remaining null String value causes a runtime error.
- Two values of the same enum compare by member declaration order. Emit
  informational diagnostic `SPP_ENUM_ORDER_COMPARE` at the operator.
  Enum aliases and resolved fallback values have their member's order.
- Mixed ordering operands fail compilation with a hint to use matching
  operands. Existing equality semantics apply to `==` and `!=`.

The language tests compile valid comparisons and reject mixed operands.
The generation tests compile and execute generated Java under both
collations. The Basic CLI provides the informational diagnostic's explain
page.
