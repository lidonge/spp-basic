# SPP_ENUM_ORDER_COMPARE

Ordering operators compare members of the same enum by declaration order.

For example, if `Priority` declares `LOW` before `HIGH`, then
`Priority.LOW < Priority.HIGH` is true regardless of their wire codes.
Confirm that the order in the declaration represents the intended business
order. Reordering members can change the result. Aliases and fallback values
use the position of the member they resolve to.
