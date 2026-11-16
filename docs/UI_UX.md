# UX specification

## Design
Simple high-contrast drink POS. Theme: white main background, subtle neutral grouped surfaces, restrained tea-green accent, readable dark text, large tap targets. Avoid using color alone to signal status. Support dynamic font size and screen rotation.

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

## Detailed visual guidelines

Use [UI_GUIDELINES.md](UI_GUIDELINES.md) for the proposed semantic palette, type scale,
icon meanings, spacing/shapes, component hierarchy, Indonesian copy and adaptive
review checklist. A [visual reference](design/ui-reference.html) illustrates those
choices with fictional data. The target is a white canvas, restrained leaf-green
emphasis and softer grouping. These are design/handoff artifacts; applying them to
Android screens is a separate implementation pass, with the behavior above retained.

## Screen inventory and first-flow designs

Use [UI_SCREEN_PLAN.md](UI_SCREEN_PLAN.md) for the screen inventory, role/connectivity
matrix and flow maps. [AUTH_SCREEN_DESIGNS.md](AUTH_SCREEN_DESIGNS.md) details access,
device approval and OS credential handoff; its
[phone/tablet reference](design/access-flow.html) is a fictional simulation. These
are design artifacts for future Android implementation, not new auth endpoints or
a change to server authorization/offline policy.

The first native access-flow presentation now implements the detailed handoff:
login/outlets/locked/pending/offline and reviewer confirmation. Shared theme tokens
are applied. The shared shell is described below; other feature layouts remain pending.
Device/hosted/accessibility acceptance remains separate from host Compose checks.

The authenticated shell now implements F-00/F-01 with distinct rounded icons, one
outlet/role/access header and existing outlet revalidation. Bottom bar/rail switch at
600dp; large text and short windows use a scrolling labeled menu. Access mode is
separate from actual sync state. See [SHELL_SCREEN_DESIGNS.md](SHELL_SCREEN_DESIGNS.md)
for details, source icon attribution and remaining physical-device checks.

The next native catalog iteration is specified in
[CATALOG_SCREEN_DESIGNS.md](CATALOG_SCREEN_DESIGNS.md), with a
[fictional local reference](design/catalog-flow.html). Product-first localized tabs,
local name/status filters, adaptive forms, archive confirmation and draft/result
handling are proposed presentation work; existing shared-brand/security/photo/sync
contracts remain unchanged. These catalog designs are not implemented by the HTML.

Catalog CAT-UI-01–04 presentation is now implemented in native Compose, with host
regressions and device-test compilation. Forms keep raw draft/scroll in ViewModel
memory across layout changes, use whole integer values and explicit archive/photo
confirmation, and preserve unknown command identities. Refresh is complete only after
an observed successful pull, not on scheduling. Real device/hosted acceptance remains
open; browser references still use fictional data and are not native screenshots.


## Detailed POS flow

Use [POS_SCREEN_DESIGNS.md](POS_SCREEN_DESIGNS.md) for P-01–P-06 and
[design/pos-flow.html](design/pos-flow.html) for fictional compact/tablet interactions.
The handoff preserves scoped Room checkout, manual QRIS, immutable receipts and
price-change rejection. Native POS status and remaining manual checks are recorded below.


P-01–P-06 now has native presentation implementation: adaptive catalog/cart, bounded
modifier selection, focused integer cash/manual QRIS payment, uncertain local-save
readback and proven receipt delivery/review. See POS_SCREEN_DESIGNS.md section 10.
Known receipts remain readable during recovery without enabling new-sale mutations.
Host regressions do not replace device/hosted acceptance. Next: history H-01/H-02 design.


## Detailed history flow

Use [HISTORY_SCREEN_DESIGNS.md](HISTORY_SCREEN_DESIGNS.md) for H-01/H-02 and
[design/history-flow.html](design/history-flow.html) for a fictional phone/tablet
reference. Search/day/status filters apply only to the available bounded cache.
Read-only original receipts keep proven delivery/review labels; no history route
or checkout permission is added for Owner/Manager. Native implementation and remaining acceptance are recorded below.


Native H-01/H-02 is now implemented: cache-limited search/day/proof filters, responsive
list/detail, retained authorized snapshots and explicit local/pull/partial/error
refresh. Read-only receipt detail cannot dismiss a checkout receipt or mutate a sale.
See HISTORY_SCREEN_DESIGNS.md section 7 and PROGRESS.md for tests and physical/hosted
acceptance. Next: detailed overview/report/filter R-01–R-03 designs.


## Detailed overview/report flow

Use [REPORT_SCREEN_DESIGNS.md](REPORT_SCREEN_DESIGNS.md) for R-01–R-03 / X-03 and
[design/report-flow.html](design/report-flow.html) for the fictional phone/tablet
reference. Ringkasan and Laporan share an applied accepted-server snapshot; editable
filters, cache/as-of labels and captured CSV scope stay distinct. No new metrics,
permissions or server endpoints are introduced. Next: native RPT-UI-01–04.


### Native reports (15 November 2026)

Ringkasan and Laporan share applied server-report state. Draft filters live in a
bounded scrolling sheet, with fixed Apply/Cancel; metric columns collapse for large
text and report rows remain virtualized. Operational outlet and report outlet have
separate labels. Cached/no-cache/zero/loading/failure states remain distinct. CSV
export captures the displayed report before the picker and correlates callbacks by
ticket. See REPORT_SCREEN_DESIGNS.md section 7 and PROGRESS.md for verification limits.


## Detailed staff/access flow

Use [STAFF_SCREEN_DESIGNS.md](STAFF_SCREEN_DESIGNS.md) for S-01–S-03 and
[design/staff-flow.html](design/staff-flow.html) for fictional responsive examples.
Member/device/audit views use readable rows and scoped effect-specific confirmations.
Membership enable, device restoration and approval are separate actions. Online-only
management distinguishes conflict, uncertain response and acknowledged write with
failed readback. Current Android behavior is unchanged by the design artifacts.
Next: native STAFF-UI-01–04; physical/hosted/IME/TalkBack acceptance remains separate.


### Native staff/access

S-01–S-03 now uses scoped in-memory tabs/confirmations, virtualized rows and a wide
screen guidance pane. Target/effect/reason are explicit; offline hides the directory,
conflicts and uncertain writes require fresh review, and acknowledged changes with
readback failure never become a blind retry. Approval has its own load/result state
in Perangkat. See STAFF_SCREEN_DESIGNS.md section 9 and PROGRESS.md for verification
and remaining hosted, normal-app, physical-device and accessibility acceptance.


## Detailed settings, sync and recovery flow

Use [SETTINGS_SCREEN_DESIGNS.md](SETTINGS_SCREEN_DESIGNS.md) and the
[fictional reference](design/settings-flow.html) for T-01–T-04/sign-out/recovery X-03.
Groups separate account/access, observed delivery versus downloaded data, private local
recovery export and session actions. Missing counts, restored-feed conflicts and
provider failures retain honest states; scheduling is not success, export is not a
server backup, and sign-out never deletes Room. Next: native SETTINGS-UI-01–04.


### Native settings/recovery

T-01–T-04/sign-out/recovery X-03 now uses a scoped adaptive settings route. Observed
push/pull, retry scheduling, captured export and auth outcomes stay distinct; missing
counts are unavailable, sums use Long, and recovery-required never implies reset.
The provider uses frozen validated archive bytes and correlated callbacks. Sign-out
retains local work and never requires export. See SETTINGS_SCREEN_DESIGNS.md section 9
and PROGRESS.md for checks and normal-app/provider/device acceptance still open.

## Staff creation extension (15 November 2026)

Staf → Anggota → Tambah staf opens a scoped, scrollable adaptive form with fixed
submit/cancel actions, permitted role choices and masked temporary-password confirmation.
App-created accounts use a dedicated mandatory password-replacement screen before
outlet selection or device enrollment. See IN_APP_STAFF_PROVISIONING.md and the staff
screen extension for retry, scope-loss and accessibility acceptance requirements.

## Owner cashier navigation (15 November 2026)

Owner destinations are Ringkasan, Kasir, Riwayat, Katalog, Laporan, Staf and Setelan.
Ringkasan stays the default. Seven destinations use the scrolling menu on compact
phones; tablet rail and short-window/large-text drawer adapt as before. Header context
continues to identify Pemilik and the selected outlet. Owner history is explicitly
outlet-cache scope; Cashier history remains personal. This supersedes the earlier
Owner history-route exclusion in the detailed-history handoff.

## Staff quotas (16 November 2026)

Online Anggota and Tambah staf show server-returned used/maximum counts for the
selected outlet. Owners see Manager/Cashier quotas; Managers see Cashier quota.
Unavailable or unconfigured capacity blocks new creation, full roles cannot be chosen,
and role defaults remain Cashier (no automatic selection of a higher-privilege role).
Unknown already-reserved commands remain retryable even when their slot fills the cap.
Quota editing belongs only to Supabase admin tools. See STAFF_LIMITS.md.

## Saved appearance and green app bar (16 November 2026)

Settings offers device-local Terang/Gelap choices, independent of role or network.
Selection persists across restart/logout and updates the app root in place. Read/write
failures are explicit; failed writes retain the previous appearance. Green app-bar
surfaces use contrasting theme content, with outlet switching beside the outlet title
and role, while access status stays at the trailing edge. Dark in-app logos are white.
