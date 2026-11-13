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
