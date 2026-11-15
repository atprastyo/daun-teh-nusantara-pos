# Daun Teh Nusantara — UI guidelines

Version 1 · 14 November 2026 · Asterfed

**Design specification for the next UI pass.** These guidelines and the
[visual reference](design/ui-reference.html) define the target; they are not a claim
that the Android screens already implement it. Existing role, offline, checkout and
sync rules remain the behavioral requirements in [UI_UX.md](UI_UX.md).

## 1. Direction: fresh, calm, quick to scan

Keep the main canvas **white**, with leaf-green actions, comfortable spacing and
rounded shapes. The app should feel like a welcoming tea shop while remaining a
clear cashier tool. Use photographs to add character; keep financial information
and operational states precise.

- Make the product, price and next action obvious before secondary information.
- Use spacing and text hierarchy to group content; avoid outlining every paragraph.
- Give each screen one primary action. Secondary controls should recede.
- Keep operational copy short and in Indonesian. Explain exceptions when needed.
- Use color as reinforcement, always paired with a label or recognizable symbol.
- Default to the current white light appearance. A white launcher logo in device
  night mode is independent of in-app theme selection; do not introduce automatic
  app dark-mode switching through this design pass.

### Code observations to address

These are source observations, not results from a device usability study:

| Current pattern | Target |
| --- | --- |
| Authenticated navigation uses `Info` for every destination | A distinct, consistently styled icon for each destination |
| Catalog shows `Category`, `Product`, `Group`, `Option` as stacked chips | A horizontal, scrollable Indonesian tab row |
| Global `Typography()` defaults and frequently similar text sizes | Named title/body/label/price/total roles |
| Many equally emphasized outlined cards and actions | Quiet surfaces, stronger primary action, fewer competing borders |
| Outlet, role and connection details repeat as separate lines | One compact context header and a labeled connection indicator |

Relevant source: [AuthScreen.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/auth/presentation/AuthScreen.kt),
[CatalogEditorScreen.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/catalog/presentation/CatalogEditorScreen.kt),
[Theme.kt](../app/src/main/java/com/dauntehnusantara/pos/core/designsystem/Theme.kt).

## 2. Color system

Use semantic roles through `MaterialTheme.colorScheme`; do not scatter hex values
through feature composables. The white canvas, green primary and dark text continue
the current brand. The softer selected surface and status pairs below are proposed
refinements to centralize during implementation.

| Token | Light value | Use |
| --- | --- | --- |
| `canvas` / `background` | `#FFFFFF` | Screen background; keep large areas white |
| `surface` | `#FFFFFF` | Cards, sheets, dialogs |
| `surfaceSubtle` | `#F7F8F5` | Photo placeholders and quiet grouped areas |
| `primary` | `#506B24` | Main action, selected icon, focus |
| `onPrimary` | `#FFFFFF` | Labels/icons on primary buttons |
| `selectedContainer` | `#EAF2DE` | Selected chip/navigation background |
| `onSelectedContainer` | `#304418` | Selected chip/navigation label |
| `textPrimary` | `#1A1D15` | Titles, product names, amounts |
| `textSecondary` | `#60665B` | Supporting copy, timestamps, field hints |
| `divider` | `#E3E7DC` | Decorative separators; not a control boundary |
| `controlOutline` | `#7C8474` | Required visible input/control boundaries |
| `success` / `successContainer` | `#2E6B3D` / `#EAF5ED` | Server-confirmed success |
| `warning` / `warningContainer` | `#7A4B00` / `#FFF3D6` | Pending upload, attention needed |
| `error` / `errorContainer` | `#B3261E` / `#FCEAE8` | Invalid data, rejection, destructive action |
| `info` / `infoContainer` | `#285B78` / `#EAF3FA` | Helpful explanation or progress context |

Rules:

- Use primary green sparingly: the main action, selected state and small accents.
- Do not tint the entire screen green or reintroduce a green cast on white surfaces.
- Normal text must meet **4.5:1** contrast; meaningful icons/control boundaries
  **3:1**. Validate actual foreground/background pairs, including selected states.
- The pale divider is decorative only. It cannot be the only way to recognize a
  text field, focus, selection or error.
- Offline is an expected operating mode: use a neutral, labeled indicator. Reserve
  red for actual failures/rejections, not every lack of internet.
- Secondary controls use a text/tonal treatment; avoid a page full of filled green buttons.

## 3. Typography

Use one readable Android sans-serif family: **Roboto where supplied by Android,
with `FontFamily.SansSerif` fallback**. No additional font download is required.
Keep the supplied serif lettering inside the logo; do not use that serif for forms,
prices or navigation. Browser reference fonts are approximate, not Android screenshots.

The following sizes are app-specific proposed tokens, mapped to Compose roles:

| Role | Compose mapping | Size / line height | Weight | Example |
| --- | --- | --- | --- | --- |
| Screen title | `headlineMedium` | 28 / 36 sp | 600 | Katalog |
| Section title | `titleLarge` | 20 / 28 sp | 600 | Produk populer |
| Product/row title | `titleMedium` | 16 / 24 sp | 600 | Teh original |
| Body | `bodyLarge` | 16 / 24 sp | 400 | Main explanation |
| Supporting copy | `bodyMedium` | 14 / 20 sp | 400 | Pilih kategori produk |
| Button/tab label | `labelLarge` | 14 / 20 sp | 500 | Simpan produk |
| Metadata/status | `labelMedium` | 12 / 16 sp | 500 | Menunggu sinkronisasi |
| Product price | `PriceText` semantic style | 18 / 24 sp | 600 | Rp 6.000 |
| Checkout total | `TotalText` semantic style | 32 / 40 sp | 700 | Rp 18.000 |

- Use `sp`, honor font scaling and allow controls to grow vertically. The target
  review includes **200% font size**, not just the default font setting.
- Limit a product name to two visual lines in a grid; expose the full name in the
  detail/modifier sheet and accessible semantics. Do not truncate checkout totals.
- Use sentence case for UI labels; preserve user-entered product/brand names.
- Keep ordinary body text regular. Use semibold for headings/prices and bold mainly
  for the checkout total. Avoid making all labels bold.
- Align amounts on a common edge in cart/report rows. Use tabular digits when the
  selected font supports them; do not use a monospaced font for the whole screen.
- Display IDR using Indonesian grouping and no decimals: **Rp 6.000**. Keep integer
  IDR storage/calculation unchanged. Amount entry uses a visible Rp prefix and clear
  integer-input guidance; formatting must not disrupt cursor editing.

## 4. Icons

Target family: **Material Symbols Rounded**, regular/outlined appearance, optical
size 24 and weight 400. Selected navigation may use its filled counterpart. Keep
one family/weight; do not mix thin outline, heavy filled, emoji and brand-logo icons.

The names below are design asset identifiers, **not a claim that equivalent Kotlin
symbols exist in the current `material-icons-core` dependency**. During implementation,
verify availability and import the small required official vector set if necessary;
do not add an entire icon font/library without reviewing its scope/version/license.
Illustrations in the visual reference show semantics, not official downloaded assets.

| Meaning | Suggested symbol | Visible label / accessible action |
| --- | --- | --- |
| Overview | `dashboard` | Ringkasan |
| Cashier | `point_of_sale` | Kasir |
| Sales history | `receipt_long` | Riwayat |
| Product catalog | `local_cafe` | Katalog |
| Reports | `bar_chart` | Laporan |
| Staff | `group` | Staf |
| Settings | `settings` | Setelan |
| Outlet selector | `storefront` | Pilih outlet |
| Search | `search` | Cari produk |
| Add | `add` | Tambah produk |
| Edit | `edit` | Edit produk |
| Photo | `add_photo_alternate` | Tambah foto / Ganti foto |
| Archive | `archive` | Arsipkan produk |
| Delete photo | `delete` | Hapus foto |
| Retry sync | `sync` | Coba sinkronisasi lagi |
| Offline | `wifi_off` | Offline |
| Confirmed | `check_circle` | Tersinkron |
| Attention/rejection | `error` (outlined) | Perlu ditinjau |
| More actions | `more_vert` | Opsi produk |

Sizing and behavior:

- Standard icon: **24 dp**; supporting inline icon: **20 dp**; empty-state icon:
  **40–48 dp**. Preserve optical size/stroke consistency when scaling.
- Every interactive target is at least **48×48 dp**, even when its glyph is 20 dp.
- Keep navigation labels visible. Icons alone are insufficient for unfamiliar actions.
- Icon-only buttons need localized action descriptions. Decorative icons adjacent
  to the same text use a null description to avoid duplicate announcements.
- Distinguish destinations by meaning: `Info` is for help/information, not every tab.
- The brand logo belongs on login/about/brand contexts, not repeated in every toolbar.

## 5. Spacing, shapes and elevation

Use a **4 dp base grid** with spacing tokens `4, 8, 12, 16, 24, 32, 48`.

| Element | Target |
| --- | --- |
| Screen padding | 16 dp compact; 24 dp medium; 24–32 dp expanded |
| Grid gap | 12 dp compact; 16 dp expanded |
| Card content padding | 12–16 dp |
| Icon to text | 8 dp |
| Label to field | 8 dp |
| Field to helper/error | 4 dp |
| Between form fields | 20–24 dp |
| Between sections | 24–32 dp |
| Field/button corner | 12–14 dp |
| Product/summary card corner | 20 dp |
| Dialog corner | 24 dp |
| Bottom-sheet top corners | 28 dp |
| Filter/compact status shape | Capsule; do not use it for every container |
| Primary button height | At least 48 dp; prefer 52–56 dp for checkout |

Use plain surfaces/spacing first. Normal cards have 0–1 dp elevation; a sticky cart
bar can have modest separation/elevation. Avoid thick outlines, shadows on every
row, cards nested inside cards, or arbitrary radii per screen. Group related fields
more tightly than separate sections: generous space should communicate relationships,
not stretch every control equally far apart.

## 6. Component rules

### Header and navigation

- A screen title with one supporting line is enough. Keep outlet selection and a
  short role/connection label in one compact context area, not repeated paragraphs.
- Keep existing permission-filtered destinations. Cashier starts at Kasir; Owner
  and Manager start at Ringkasan. Do not add cashier access to an Owner route by styling.
- Owner/Manager compact navigation can use the existing five short labels:
  Ringkasan, Katalog, Laporan, Staf, Setelan. Cashier uses Kasir, Riwayat, Setelan.
- Selected navigation uses a soft green indicator plus stronger icon/label. A color
  change alone is not enough. Use real icons rather than letters as stand-ins.

### Buttons

- **Filled green:** primary screen action, e.g. Simpan produk or Bayar.
- **Tonal:** meaningful secondary action, e.g. Tambah foto.
- **Text:** tertiary actions such as Batal, Edit or contextual retry.
- **Outlined:** when a secondary boundary is useful, not the default for all actions.
- **Error-colored text/action:** destructive or access-revocation actions, with
  confirmation/reason when required by the existing workflow.
- During an in-flight save, retain the label/context and show progress; disable
  duplicate submission. A transport retry must reuse the existing command identity.

### Product cards

- Square photograph or neutral tea-cup placeholder; rounded image corners.
- Name (up to two lines), readable price, then optional availability metadata.
- Whole card selects the product/modifiers. Avoid a separate nested action that
  bypasses required modifiers. A small visual "Pilih" cue is enough.
- Keep cart quantities in a separate labeled badge/row. Do not hide the price behind
  a floating add button or a photo overlay.
- Image loading uses a restrained placeholder. Failure exposes retry; it must not
  disable the card, checkout or original receipt access.
- Admin catalog rows use one obvious Edit action and contextual photo/overflow
  controls. Keep archive/remove away from the primary add/save action.

### Catalog tabs and forms

- Use a horizontal scrollable row: **Produk · Kategori · Grup pilihan · Pilihan**.
  For new setups, a category empty state can guide users to create a category first.
- Use labels above/inside persistent fields, never placeholders as the only label.
- Align category, price, active state and photo into coherent groups. Show optional
  fields as optional; use segmented/radio options where the choice set is small.
- Keep the save action easy to reach with the IME open. Allow scrolling and account
  for insets; do not put the only action behind the keyboard.
- Show shared-brand catalog scope once near the header or form context. Do not
  remove that warning just to shorten the page.

### Cart and checkout

- Cart rows: product name, selected modifiers, quantity control, line total.
- Minimum 48 dp touch targets for plus/minus/remove; keep adjacent targets separate.
- Make Total the strongest type treatment; keep change/tender clear and distinct.
- Cash and QRIS use labeled radio/segmented options. QRIS remains **manual** and
  requires the explicit cashier confirmation; no bank-verified visual implication.
- A saved local receipt and a server-accepted receipt are different states. Never
  turn the primary action into a spinner without accessible progress text.

### Reports, staff and settings

- Use aligned metrics and clear section headings instead of one bordered tile per
  line. Show report period, outlet scope and accepted-server/cached basis together.
- Staff rows pair name/role/access status with one contextual management action.
  Keep manual account provisioning and role restrictions visible where relevant.
- Settings is grouped into account/outlet, sync, device access and recovery sections.
  Put sign-out/revocation away from ordinary navigation and sync retry.

## 7. Adaptive layouts

Use available window width in dp; do not infer layout from device names.

| Width | Structure |
| --- | --- |
| <600 dp | Bottom navigation; vertical content; sticky cart summary; focused checkout |
| 600–839 dp | Navigation rail; flexible grid; collapsible cart/sheet |
| >=840 dp | Navigation rail; product area plus persistent cart where both fit |

- Product cards prefer 148–220 dp widths. At narrow widths or large text, reduce the
  column count rather than squeeze content. Two columns are a preference, not a rule
  that overrides readability on a small phone.
- In expanded POS, cart prefers **300–360 dp**, with the remaining width for products.
  If the panes cannot fit with readable text, collapse the cart instead of clipping.
- Forms/dialog content generally caps at **560 dp**; login form at **480 dp**.
- Avoid stretching long paragraphs or text fields across an entire tablet.
- Large screens may show product, cart and totals simultaneously; keep the same
  labels, order and business behavior as compact screens.
- In dense reports, align values; never solve overflow by shrinking text below the
  defined type scale. Support horizontal table scrolling when a real table needs it.

Compact POS hierarchy:

```text
Outlet / role                         connection status
Kasir
Cari produk
[Semua] [Teh susu] [Teh buah]  → horizontal filters
[photo · name · price] [photo · name · price]
[photo · name · price] [photo · name · price]
[3 item · Rp 18.000                   Lihat keranjang]
[Kasir]                  [Riwayat]                [Setelan]
```

## 8. Operational states and Indonesian copy

These are wording examples, not new server guarantees:

| State | Visual treatment | Example |
| --- | --- | --- |
| Initial loading | Placeholder/progress; keep structure stable | Memuat katalog… |
| Empty catalog | Tea icon, short guidance and allowed action | Belum ada produk. Tambahkan produk pertama. |
| Offline | Neutral icon + label, non-blocking for authorized sales | Offline · penjualan tetap tersimpan di perangkat |
| Locally saved, pending | Clock/sync icon + amber label | Tersimpan di perangkat · menunggu sinkronisasi |
| Server acknowledged | Check icon + green label | Tersinkron |
| Temporary failure | Inline error + relevant retry | Belum bisa terhubung. Coba lagi. |
| Price conflict/rejection | Error icon + review action | Perlu ditinjau · harga katalog berubah |
| Waiting for local photo/catalog pull | Progress + explanation | Tersimpan di server · memperbarui katalog lokal… |
| Invalid field | Error below field; retain other inputs | Masukkan harga dalam rupiah tanpa desimal. |
| Unknown save outcome | Neutral attention + same-command retry | Hasil penyimpanan belum dapat dipastikan. Coba lagi. |
| Device awaiting approval | Lock/device icon + next step | Perangkat menunggu persetujuan pemilik. |
| Restored feed mismatch | Blocking explanation + recovery/export | Ekspor data tersimpan dan hubungi pemilik. |

Offline access remains lease/device-scoped. Do not hide expiry/revocation warnings,
clear retained sales, automatically reprice a paid receipt, or call local persistence
"backed up". Keep authority/role checks and explicit recovery gates when redesigning.

## 9. Interaction, motion and accessibility

- Use Material ripple/pressed/focus states. Prefer 120–180 ms state transitions and
  180–240 ms panel/sheet motion; respect system animation settings/reduced motion.
- Do not animate totals/counts through misleading intermediate values. Show actual
  accepted values and clear progress rather than a fake success animation.
- Keep focus visible and a logical TalkBack/keyboard order. Announce save/error
  state changes without repeatedly announcing routine background sync.
- Merge a product card's accessible content sensibly. Decorative photos/icons beside
  a name should not make TalkBack read the same label twice.
- Handle rotation, split screen, keyboard/IME, status/navigation insets and large text.
- Never hide a required action on swipe/long-press alone. Provide a labeled alternative.

## 10. Implementation handoff and review

Suggested shared design-system building blocks (proposed, not existing APIs):
`DaunTehTypography`, `DaunTehShapes`, `UiSpacing`, `AppIcons`, `ScreenHeader`,
`ConnectionStatus`, `StatusBadge`, `ProductCard`, `MoneyText`, `PrimaryAction`.
Keep business data/state in the existing ViewModel/domain flow. Reuse Material 3
semantics and behavior instead of replacing everything with custom drawing.

Recommended order for the next implementation iterations:

1. **Foundation and navigation:** shared type/shape/spacing/icon tokens, distinct
   destination icons, context header and consistent Indonesian labels.
2. **Catalog and POS:** horizontal tabs/filters, product cards, photo states and cart.
3. **Checkout and history:** totals hierarchy, payment controls, receipt/sync states.
4. **Reports, staff and settings:** grouped sections, metrics and contextual actions.

Acceptance for each affected screen:

- [ ] One clear primary action and a readable visual hierarchy.
- [ ] Correct semantic color/icon/type/spacing tokens; no arbitrary new hex/styles.
- [ ] 48 dp touch targets, visible focus and localized icon descriptions.
- [ ] Actual contrast pairs pass; selected/error states do not rely only on color.
- [ ] 200% font scale, compact phone, landscape, tablet and split-screen checked.
- [ ] Loading, empty, offline, retry, invalid and conflict states reviewed.
- [ ] Keyboard cannot cover the only save/pay action.
- [ ] Photo failure does not gate checkout; unknown retries retain operation identity.
- [ ] Role/outlet/device authorization and immutable paid snapshots remain intact.
- [ ] Relevant build/domain/DAO/UI checks run; device-only gaps reported honestly.

## Sources

App-specific sizes, palette refinements and component choices above are design
proposals. Platform accessibility and Material references:

- [Android Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Android design accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility)
- [Material 3 typography](https://m3.material.io/styles/typography/applying-type)
- [Material Symbols guide and individual assets](https://developers.google.com/fonts/docs/material_symbols)

## Screen-level handoff

[UI_SCREEN_PLAN.md](UI_SCREEN_PLAN.md) now inventories screen/sheet/inline/OS surfaces
and their role/connectivity gates, with flow maps and dependency order. The first
flow is detailed in [AUTH_SCREEN_DESIGNS.md](AUTH_SCREEN_DESIGNS.md) and the
[access reference](design/access-flow.html). Shared tokens remain the prerequisite;
access-flow presentation is the first connected screen implementation, followed by
the shared shell and the remaining groups. Detailed designs for later groups follow
in their own iterations rather than expanding every surface into a route now.

## Implementation status — 15 November 2026

Shared light palette, named type roles and rounded shapes now live in Theme.kt.
Access screens and device-review confirmation use the first native adaptive layout
and local outline vectors. The destination icon/context-header iteration is described below; catalog/POS
and later feature redesigns remain pending. The app still defaults
explicitly to white light mode and uses the original-color logo on its access canvas.

F-00/F-01 now implements distinct Material Symbols Rounded destination icons,
localized roles and one context header. Source/license and the Compose tint-only
adaptation are recorded in [MATERIAL_SYMBOLS_SOURCES.md](MATERIAL_SYMBOLS_SOURCES.md).
Large-text/short-window navigation uses a scrolling labeled drawer. Catalog/POS and
other feature layouts still need their detailed redesign and device review.

Catalog layouts, fields, archive/photo states and command-lifetime acceptance are now
specified in [CATALOG_SCREEN_DESIGNS.md](CATALOG_SCREEN_DESIGNS.md) and the
[catalog reference](design/catalog-flow.html). Native application follows separately.

The catalog handoff is now applied natively: localized tabs/filtering, adaptive forms,
quiet grouping, actionable validation and archive/photo confirmations. Missing-photo
retry keeps a 48dp target in small thumbnails. POS/checkout/history and later groups
still require their own detailed designs and physical-device review.


## POS application of these guidelines

P-01–P-06 layouts and state/action contracts are specified in
[POS_SCREEN_DESIGNS.md](POS_SCREEN_DESIGNS.md), with a local fictional
[interactive reference](design/pos-flow.html). Use the existing green/white tokens,
Material Symbols, readable money hierarchy and adaptive shell; native acceptance
remains part of POS-UI-01–05.


## History application of these guidelines

H-01/H-02 is detailed in [HISTORY_SCREEN_DESIGNS.md](HISTORY_SCREEN_DESIGNS.md),
with a [local interactive reference](design/history-flow.html). Reuse the shared
shell, green selection, neutral grouping and receipt typography. Distinguish
cache-limited filtering, available snapshots and proven receipt delivery. History
detail is read-only; closing it never dismisses the checkout receipt or changes
the cart. Native implementation and device acceptance are separate milestones.


## Overview/report application of these guidelines

R-01–R-03 / X-03 is detailed in [REPORT_SCREEN_DESIGNS.md](REPORT_SCREEN_DESIGNS.md)
and [design/report-flow.html](design/report-flow.html). Prioritize applied date/outlet,
accepted-server basis and snapshot time; use green emphasis on the total, restrained
metric cards and readable daily/outlet/product rows. Keep draft versus applied query
and snapshot export clear; do not invent trend/profit/bank metrics for decoration.
Native implementation and provider/device/hosted acceptance are separate.

## Shared native component APIs

Use [UI_COMPONENTS.md](UI_COMPONENTS.md) and core/designsystem/components for the native
shared headers, sections, notices/errors, rows/empty/identity, fields/buttons and
adaptive confirmations. Initial adoption covers catalog/POS/reports/staff/settings;
feature state and security remain outside these components. Monetary input now uses
Rp/dot grouping with exact raw Long validation and strict paste handling; other
integer fields keep their own quantity/order/bound rules.
