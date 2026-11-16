# Authenticated shell — F-00 / F-01

15 November 2026. Native presentation handoff; existing authorization and feature
flows remain authoritative. See [screen inventory](UI_SCREEN_PLAN.md) and
[UI guidelines](UI_GUIDELINES.md).

## Layout and navigation

- White canvas, a single shared outlet/role/access context header, then the existing
  destination content. Feature pages own their titles and loading/error/data states.
- Below 600dp: labeled bottom navigation. At 600dp and wider: labeled, vertically
  scrollable navigation rail. Pass expanded=true to existing features at >=840dp.
- At >=150% font scale or short windows (<480dp height), use a labeled menu button
  and a scrolling modal navigation drawer so labels/actions can grow. Keep the
  destination order, permissions and content identical across navigation forms.
  Keep feature content at the same composition location when navigation form changes
  so in-progress inputs survive window/IME/font changes.
- Cashier starts at Kasir; Owner/Manager at Ringkasan. Destinations remain exactly
  those from RoleNavigation. Recheck a restored selected name against the allowed
  set before rendering content. Reset presentation selection for changed account,
  brand, outlet or role. Use distinct rounded symbols with visible Indonesian labels.
- Show Katalog and Setelan in navigation without renaming existing demo/catalog
  screen copy globally. Selection uses both Material semantics and a green indicator.

## Context header and access state

Show outlet name prominently, a read-only localized role, and one access indicator.
Use “Akses offline” versus “Akses tervalidasi”: AuthState.offline is an authorization
mode, not a live Wi-Fi probe. Neither label means receipts are synced/backed up or
initial catalog catch-up is complete. Keep real delivery/recovery status in the
existing feature/settings panels. While validating, show an indeterminate progress
indicator without claiming completion. Keep auth messages visible above content.

Header action “Pilih outlet lain” calls existing switchOutlet/chooseOutlets. Disable
it while offline or busy and explain the online requirement when offline. It never
locally reassigns a role/outlet or clears retained sales. Reuse A-02 outlet selection
and subsequent verification/approval/error handling; do not invent an offline switch
or a cancellation RPC. Settings retains existing sign-out, revalidation, sync and
recovery actions. Sign-out stays away from the main navigation/header action.

## Acceptance

Host Compose tests cover role destinations/defaults, destination callbacks, header
roles/mode labels, offline/busy switch guards, account/outlet/role reset, restored
selection filtering, width breakpoints, short windows and 200% text drawer access.
Integration remains AuthRoot -> AccessPortal -> existing feature route callbacks.
Physical rotation, real IME, TalkBack, hosted revocation/outlet changes and POS's
initial-pull/recovery gates still need device acceptance. No schema/API/library
version change or production deployment belongs to this iteration.

## Owner cashier extension (15 November 2026)

Owner now has seven destinations: Ringkasan, Kasir, Riwayat, Katalog, Laporan, Staf,
Setelan. To retain readable targets on compact windows, use the existing scrollable
drawer rather than seven bottom items. Medium/expanded windows use the scrollable
rail; large text/short windows retain the drawer fallback. Owner starts at Ringkasan;
Kasir/Riwayat selection survives same-context refresh and recreation. Scope/role/account
changes still reset selection. Manager keeps five management destinations, Cashier
keeps Kasir/Riwayat/Setelan. Header identifies actual role and operational outlet.

## Drawer identity and app bar refinement (16 November 2026)

Drawer header: existing brand logo + verified brand name, then account email, actual
role and active outlet, followed by a divider and destination list. All header/list
content scrolls together at large text or short heights. Drawer logo tints white on
dark surfaces. No unsupported profile name or additional identity request is introduced.
The compact app bar keeps outlet/role and existing menu/switch actions; a 48dp icon
replaces the access text row. Validated, validating and offline labels remain accessible,
with a tap opening details. Access status never claims sale sync success. Offline
limitations and switching guidance remain available in the status details.

## Green header and appearance (16 November 2026)

Outlet switch is adjacent to the title/role block, before the trailing access indicator.
Green backgrounds use contrasting Light/Dark tokens; disabled offline/busy behavior
remains. Appearance updates the existing composition rather than resetting selected
routes or in-progress inputs. Saved device preference also applies to access screens.

## Drawer and outlet selector cleanup (16 November 2026)

Brand/account header leads straight into destination items; remove Menu aplikasi and
Tutup menu. Selection/outside tap/swipe/Back close the drawer. The outlet name/role
section is now the switch target, labeled Pilih outlet lain for accessibility, with
current outlet and role state. It has at least 48dp height and disables offline or
while validating. No separate swap icon. Green header/access-status details remain.
