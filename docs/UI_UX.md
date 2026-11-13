# UX specification

## Design
Simple high-contrast drink POS. Theme: white/off-white background, restrained tea-green accent, readable dark text, large tap targets. Avoid using color alone to signal status. Support dynamic font size and screen rotation.

## Phone compact (<600dp width)
Bottom navigation: Kasir, Riwayat, Laporan (role-based), Pengaturan. Two-column product grid where width permits; sticky cart summary; modifier selection and cart in bottom sheets; checkout as focused screen.

## Medium (600–839dp)
Adaptive navigation rail; product grid and collapsible cart according to available width.

## Expanded (>=840dp)
Navigation rail; product list/grid left and persistent cart/checkout panel right. Use actual window width rather than hardware type, support split-screen.

## Role-based entry
Owner: overview and outlet selector; Manager: assigned outlet overview; Cashier: POS as start screen. Only show permitted destinations, but server enforces authorization.

## Must-have states
Loading, empty, validation error, offline, pending sync count, sync failure, server rejection, and successful cloud confirmation. Do not show 'backed up' when only saved locally.

## Checkout details
Modifier constraints, size/sugar/ice/topping, quantity, integer IDR totals, cash change, manual QRIS verification confirmation, duplicate-submit prevention, confirmation and receipt details.


## M6 management and recovery

Reports state accepted-server basis, Jakarta period and snapshot timestamp/cache
status; loading, empty, validation, retry and offline states do not imply zero revenue.
Owner chooses brand/all or one outlet; manager stays the current assigned outlet.
Compact layouts stack metrics; expanded layouts show them side by side. CSV uses a
system document picker and the snapshot selected before opening it.

Staff mutations are online-only, reasoned, confirmed and versioned. Explain device
revocation, fresh approval after restoration and delayed disconnected revocation.
Show readable access audit labels rather than raw internal action codes. Account
creation remains manual. Settings recovery export is scoped/private, excludes
credentials and never claims a complete server backup. A restored-feed conflict tells
users to export retained data and contact the owner without clearing application data.
