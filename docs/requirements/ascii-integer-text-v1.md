# TEXT-1 v1: exact unsigned integer text

This additive business-expression contract supplements CORE-1 in public
extension requirements revision 1. Business checks sometimes distinguish
malformed text from a well-formed number outside a business range. Conversion
must not erase that distinction or move business rejection into a technical
adapter. Existing numeric and Gregorian date functions retain their semantics.

`isAsciiDigits(text)` accepts one scalar String (or null) and returns boolean.
It is true exactly for a nonempty sequence of ASCII characters U+0030–U+0039.
Null, empty text, whitespace, signs, decimal points, exponents, separators and
non-ASCII digits return false. There is no trimming or Unicode normalization.

`parseUnsignedInteger(text)` accepts the same argument type and returns an
exact numeric value with scale zero. Leading zeroes are permitted; there is no
machine integer width restriction. Invalid text throws IllegalArgumentException
instead of fabricating a value or a business rejection code. SPP may first
require the predicate with its own business code. Destination numeric bindings
and declared precision continue to enforce their existing limits.

The compiler rejects wrong arity, non-String scalars, participants and arrays.
Generation uses a generic public runtime helper and supports existing numeric
adapters. Acceptance covers lexical boundaries, exact large values, invalid
conversion, semantic diagnostics and compilation/execution of generated code.
This contract specifies ASCII decimal text, not locale-specific numeric syntax
or any particular wire encoding. No private application source is included.
