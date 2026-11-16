# Overview and reports — detailed screen designs

15 November 2026 · R-01–R-03 / X-03 · Daun Teh Nusantara / Asterfed

Design handoff for the next native presentation iteration. The
[interactive reference](design/report-flow.html) uses fictional snapshots and outcomes
in browser memory. It never contacts Supabase, writes a sale, opens an Android picker
or saves a real CSV. Android behavior is unchanged by these design artifacts.

Follow [UI_GUIDELINES.md](UI_GUIDELINES.md), [SHELL_SCREEN_DESIGNS.md](SHELL_SCREEN_DESIGNS.md)
and [AUTH_SECURITY.md](AUTH_SECURITY.md). White canvas, restrained green emphasis,
neutral groups, readable integer IDR and labeled Material icons remain the foundation.
Icon attribution: [MATERIAL_SYMBOLS_SOURCES.md](MATERIAL_SYMBOLS_SOURCES.md).
Fictional static captures: [phone overview](design/report-phone.png),
[tablet report](design/report-tablet.png).

## 1. Source contracts and boundaries

| Existing source | Contract / design implication |
| --- | --- |
| [MainActivity.kt](../app/src/main/java/com/dauntehnusantara/pos/MainActivity.kt) | OVERVIEW and REPORTS currently call the same ReportRoute without destination context. Pass presentation mode to distinguish R-01/R-02; retain existing role navigation. |
| [ReportScreen.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/reports/presentation/ReportScreen.kt) | Current filters, summaries and lists; CreateDocument(text/csv), output stream and result callback; reuse existing load/retry/export behavior with the gaps below resolved. |
| [ReportViewModel.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/reports/presentation/ReportViewModel.kt) | Cache-first then online refresh, per-source selections, prepared export snapshot. Current cancellation/result state needs scope/request guards and explicit picker-cancel cleanup. |
| [Reports.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/reports/domain/Reports.kt) | ReportQuery, SalesReport, checked ReportRules partitions and unchanged CSV serialization. No new metric or business mutation. |
| [RoomReportRepository.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/reports/data/RoomReportRepository.kt) / [ReportJson.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/reports/data/ReportJson.kt) | Exact query cache and source identity; validated report, scope checks before/after network and on export. No arbitrary cached role/outlet reuse. |
| [AuthRepositoryImpl.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/auth/data/AuthRepositoryImpl.kt) | ReportingGateway uses existing approved management context and get_sales_report; no new endpoint. |
| [schema.sql](../supabase/schema.sql) get_sales_report | Reviewed directly in source because SQL graph coverage is partial. Server report scope, date interval, partitions, product snapshot grouping/top20 and response size guard below. This is not a full SQL/security audit or hosted verification. |

Owner can request all brand outlets or one returned authorized outlet; Manager stays
on the active assigned outlet; Cashier has no overview/report destination. Reporting
an outlet is distinct from switching the enrolled operational outlet in the shell.
Never create a brand picker, account provisioning, cashier dashboard or permission
shortcut. Current backend/brand/user/device/role/source-outlet binds both cache and
prepared export. Valid offline authority permits only already cached exact-query data.

Source report basis is accepted server sale graphs within the applied sale-created
Jakarta dates, **not** the device's bounded history/cache, pending count or upload date.
Late accepted uploads can change a later refresh of an older period. No local pending/
RETRY/REVIEW amount is added. QRIS is manually recorded payment, not bank settlement.
Sync, an accepted report and a CSV are not independent backup guarantees.

## 2. R-01 — Ringkasan

Hierarchy: Ringkasan → applied outlet/date context → accepted-server/as-of/cache
notice → four existing summary metrics → compact payment breakdown → “Lihat laporan”.
Reuse the same applied report snapshot as R-02. Navigation changes presentation only,
not query, refresh completion, authority or cache origin. Do not reset to an unrelated
"today" query merely because the route changes.

| Metric | Source / label |
| --- | --- |
| Sale count | summary.count · “Penjualan diterima server” |
| Total IDR | summary.total · “Nilai penjualan diterima” |
| Cash IDR | summary.cash · “Tunai tercatat” |
| Manual QRIS IDR | summary.qris · “QRIS manual tercatat” |

No profit, margin, bank balance, live stock, trend percentage, comparison period,
average order metric, unpaid sales estimate or decorative invented growth chart.
Use real returned fields only. Two metric columns if available width/text permits;
stack at narrow/large-text sizes. Large values wrap without rounding or clipping.

## 3. R-02 — Laporan

Hierarchy: Laporan → applied period/outlet → accepted-server/as-of/cache notice →
filter/change + refresh/export actions → same four summary metrics → readonly subtotal/
discount context → daily/outlet/product sections. “Subtotal” and “Diskon” read the
existing server snapshot fields; they are not new discount entry controls.

| Section | Display / limits |
| --- | --- |
| Daily | Date, accepted sale count, total IDR; rows returned for dates with sales. Keep source date order. No synthesized trend or inferred missing-series value. |
| Outlets | Server-returned outlet name, accepted count, total; owner brand scope can contain several; manager/selected outlet remains narrow. Do not turn these into a receipt-history route. |
| Products | “Produk teratas · hingga 20 baris”; original product-name snapshot, quantity and gross line amount. Server groups by product ID + name snapshot and ranks gross descending; a renamed product can appear as separate rows. Preserve that key/order. |

Product gross is summed line_total_idr (includes original modifier prices), not profit
or net payment settlement. Top rows may sum to less than subtotal; never display them
as a complete product mix or a 100% pie. A short subtotal explanation is enough; no
new chart library. Checked Long IDR stays exact; counts/quantities are Long too.
ReportRules require subtotal-discount=total, cash+qris=total and matching daily/outlet
partitions. A failed/malformed/overflow response is an error, not partial financial truth.

Validated zero count is an explicit empty report with zero metrics and no detail rows.
Loading, absent exact-query cache, auth denial, invalid input or network/read failure
must never fabricate zero revenue. As-of is server snapshot time in Jakarta, not last
worker enqueue time. “Baru diterima” describes that response, not continuously live data.

## 4. R-03 — Draft versus applied filters

Use a bounded scrolling sheet on compact windows and an inline/bounded panel when
expanded; fields and action remain reachable under IME/200% text. Apply/Cancel and
Back have clear semantics. Editable draft is separate from displayed report.query.
Show “Perubahan filter belum diterapkan” while dirty. Cancel restores the applied
query without network IO. Keep same-scope draft through resize/rotation; do not persist
authority or a prepared file operation as saved UI state.

- ISO calendar date fields YYYY-MM-DD, start/end inclusive in Asia/Jakarta. Use
  existing ReportQuery / LocalDate validation, including real leap days and invalid
  dates; no string/lexicographic-only validation. The actual bound is to-from<=365:
  **up to 366 inclusive dates**. Exactly 365 difference is valid, 366 is invalid.
  Do not change it to “365 inclusive days” or add a future-date ban.
- Presets Hari ini / 7 hari terakhir / Bulan ini fill the draft using an injected
  Jakarta clock when selected. They do not auto-submit. Default existing range is
  today-6 through today. Compute end-exclusive UTC as to+1 at Jakarta midnight.
- Owner: Semua outlet merek or a returned authorized OutletChoice. Inactive returned
  outlets can be labeled “nonaktif” for historical reporting; that does not enable
  them for operations. Do not invent choices from stale foreign scopes, query text
  or a free-form UUID field. If choices are unavailable, keep All and the known
  current applied selection honestly labeled; do not rename an unresolved ID as All.
- Manager: fixed assigned outlet, no All/other-outlet selector. Reports do not switch
  the enrolled outlet or expand management access. Clear broader owner selection on
  role/scope change, then use the manager's actual outlet.
- Invalid draft shows inline correction and disables Apply. Disable Export while
  draft is dirty, request is busy, snapshot is absent or snapshot/source/query do not
  match current applied scope. This prevents exporting a different period by accident.
- Apply freezes a valid query; clear the old result when applying a different query
  until its exact cache/fresh response arrives. Refresh of the **same** applied query
  can retain its last validated snapshot with “Memperbarui…” and explicit cached/as-of
  state. Neither a draft edit nor a refresh relabels old totals with new parameters.

## 5. Loading, cache, error and scope outcomes

| State | Presentation / action |
| --- | --- |
| No result, loading | Skeleton/progress with period/outlet; no zero metrics |
| Exact-query cache found, refresh running | Keep validated snapshot and original as-of/cache label; busy/repeat guarded |
| Fresh validated response | Replace atomically for that applied request; mark response as newly received and show its as-of |
| Offline exact cache | “Snapshot tersimpan”; show period/as-of and accepted-sales basis; exact cached export allowed under valid authority |
| Offline no exact cache | “Belum ada snapshot untuk filter ini”; no zero totals or substitute query; adjust filters or connect/retry |
| Same-query network/temporary error | Retain last validated authorized snapshot, mark cached and failure; retry same query |
| TOO_LARGE | Ask to narrow period/outlet; no truncated/guessed totals or automatic endpoint pagination |
| Invalid date/query | Inline correction; retain prior applied context without claiming draft data |
| Auth denied / locked / changed scope | Clear snapshots, choices, draft, export intent and late success/error callbacks immediately; existing auth/revalidation flow owns recovery |
| Valid zero report | Explicit no accepted sales for applied filter at the as-of time; keep zero money/count and empty sections |
| Restored-feed conflict elsewhere | Keep recovery routing honest; no cursor reset. Report cache cannot grant checkout readiness or prove restored branch reconciliation. |

Refresh success requires a matching validated report response, not a sync enqueue,
a last-success counter or a changed spinner. On logout clear all prepared export and
sensitive in-memory selections. Scope tokens must protect slow cancelled IO, cached
reads, error/finally paths and OS callbacks, including returning to the same scope.

Baseline gaps: current ReportViewModel has no action-generation guards; its old job
can update a newer screen. It clears report on every load, not just query changes;
editable/applied query state needs explicit ownership. Its initial "today" is fixed
at construction, so new presets must use current injected time. Current UI resolves
outlet labels from report.choices and falls back to All even for an unresolved ID;
the redesigned label must remain honest. Do not weaken repository guards to fix UX.

## 6. X-03 — Export captured snapshot

1. Export enabled only for a valid visible matching snapshot with clean applied
   filters and no in-flight request/export. Freeze the immutable SalesReport, source,
   request generation and filename before launching existing Android CreateDocument.
   No new file-manager screen or storage permission.
2. OS picker owns destination and overwrite choices. Cancel returns without writing,
   failure toast or lingering prepared snapshot; explicitly clear prepared intent.
3. On result revalidate current source before generating/writing captured bytes.
   Report changes while picker is open must never replace the captured snapshot.
   A changed account/device/backend/role/source-outlet invalidates the callback.
4. Write UTF-8 through the returned provider using existing ReportCsv: same columns,
   summary/period/outlet/daily/product rows, CRLF, integer IDR and formula-prefix/quote
   escaping. Do not silently change CSV format or infer export contains every product.
5. Show success only after provider write/close succeeds for the still-current source.
   On provider failure keep original snapshot and allow a **new picker attempt**;
   do not promise no partial destination file. A mid-write scope change cannot retract
   bytes already written; suppress old-scope UI callbacks and retain normal provider
   error handling. No automatic retry overwriting a chosen document.

Current URI-null callback does not clear prepared state, and current export shares
load's action/busy state. Implement explicit prepare/picker/cancel/writing/result
state and request/scope correlation; do not persist a URI, credentials or prepared
snapshot as new durable authority. Cached export remains allowed under existing
repository checks. The browser uses a labeled picker illustration and simulated
write outcomes only; there is no actual download or OS integration.

## 7. Adaptive handoff and implementation tickets

Compact: single scrolling report, stacked/wrapping metrics, expandable sections,
bounded filter sheet, full-width primary actions where useful. Expanded: 2–4 metric
columns only when readable; detail sections can sit in two columns, with scrollable
rows and headings. At large text/short/split width collapse cleanly without dropping
draft, applied snapshot or prepared export. Reuse the management shell/context once.

1. **RPT-UI-01:** scoped report session, explicit draft/applied query, request-generation
   guards and exact-query retained refresh; fresh clock presets and outlet labeling.
2. **RPT-UI-02:** destination-aware Ringkasan versus Laporan hierarchy, responsive
   existing metrics/daily/outlet/top-product rows, loading/empty/cache/error states.
3. **RPT-UI-03:** captured export intent, picker cancellation, authority correlation,
   provider outcome and unchanged CSV; test without real account/file data.
4. **RPT-UI-04:** date/leap/365-difference/Long/partition, role/outlet/cache, cancelled
   IO/OS callback, export capture/cancel/write-error tests and Compose compact/tablet/
   large-text regressions; full build and feasible isolated device checks.

Acceptance includes valid 366 inclusive dates; invalid next-day/366-difference and
calendar parsing; UTC+7 inclusive/end-exclusive boundaries; draft/cancel/apply and no
old-query totals; owner All/one/inactive choices, manager fixed scope, cashier denial;
no-cache versus valid-zero, cached offline exports, fresh/error retention, TOO_LARGE;
product rename grouping/top20 partial subtotal; exact Long formatting; same-scope
refresh and late old-scope callbacks; export cancel/provider failure/captured query,
source change while picker/write runs; phone/tablet/short/200% text/IME/rotation/TalkBack.
SQL/hosted, Android provider, Hilt/auth/lease and physical acceptance remain separate.

Exact next milestone: native RPT-UI-01–04 overview/report presentation iteration.


## 7. Native implementation — 15 November 2026

RPT-UI-01–03 are implemented. Ringkasan and Laporan use the same report/session
through the shared management shell; Lihat laporan selects the shell's actual
Reports destination through its role-limited navigation callback. Operational outlet
context remains in the shell; the report filter card explicitly says Outlet laporan.
Existing count/total/cash/manual QRIS render in 1/2/4 metric columns according to
available width and font scale. Daily/outlet/product rows use a virtualized list,
aligned amounts and a vertical large-text fallback. The filter sheet scrolls its
fields while keeping Cancel/Apply available; no new chart or editable discount.

ReportSession owns memory-only drafts. ReportViewModel owns applied queries,
exact-cache/fresh/retained states, independent load/export jobs and authority/request
guards. Changing a query clears old totals until its own snapshot is available.
Invalid/corrupt data never renders as financial results; refresh may recover from a
corrupt cache. Owner inactive choices are historical only; Manager cannot choose All.
The report product list remains top20 by original product/name snapshot, not complete
product mix, profit or net inventory. Export preserves the existing UTF-8/CRLF CSV.

ReportRoute uses an opaque ticket and captured report before the system picker;
null result/launch failure/navigation away clears intent. A stale process/scope
callback has no in-memory authority and cannot write. The provider opens only after
scope checks, writes/closes off Main and reports success only after a further scope
check. A provider can leave partial bytes if an error, cancellation or authority
change happens during a write. No overwrite retry or persisted file authority exists.

Automated results and commands: see the latest native report entry in PROGRESS.md.
Remaining normal-app acceptance: Owner/Manager hosted authorization and revocation,
real document-provider cancel/rotation/process recreation/failure, partial file after
mid-write scope change, Hilt/lease and secure offline unlock, physical tablet/split
window/IME/200% text/TalkBack. Component fixtures use a plain test Application and do
not authorize those end-to-end claims. No backend or database migration/deployment.

Exact next milestone: detail S-01–S-03 staff/access screens and existing confirmation
flows before their native redesign; keep manual account provisioning and role limits.

### Normal-app manual checks still to execute

1. Sign in with an authorized Owner on an approved device. Open Ringkasan, note the
   period/outlet/as-of, then Lihat laporan. Confirm the shell selects Laporan and
   the same applied period/snapshot remains. Operational outlet in the header must
   not change when the report outlet changes.
2. Open Ubah filter, edit dates/preset/outlet, then Batal. Confirm no report request
   and no changed totals. Apply a valid different period: old totals must disappear
   until its own cache/response arrives. Try invalid leap dates and a 366-day
   difference (reject); 365-day difference and valid future dates remain allowed.
3. For Owner, select a returned inactive historical outlet and verify its label.
   For Manager, verify no All/foreign outlet choice; Cashier has no report route.
   Use test accounts and hosted permission fixtures, never manipulate real access
   to exercise a UI screenshot.
4. After obtaining a valid snapshot, unlock with the approved bounded offline grant.
   Refresh the same query: cached/as-of remains; an uncached query must not show
   zero sales. Pending/review local receipts must not enter accepted-server totals.
5. Export the visible snapshot, cancel the system picker, then try again. Verify
   UTF-8 CSV totals/dates/outlet/as-of match the captured report, including formulas
   escaped and exact Long IDR. Exercise provider failure and discard partial files;
   a retry requires choosing a destination again. Rotate in the picker, recreate the
   process, and change scope during a write; no stale callback may create a report
   for the new scope or claim a completed export after loss of authority.
6. Repeat on phone and physical tablet/split window, landscape/short window, IME,
   dark/light and 200% text. Verify fields scroll with Apply/Cancel reachable,
   headings and rows are readable, and TalkBack traverses context/filter/metrics/
   details/actions without duplicate operational context.

## Accepted-data charts and quick filters (16 November 2026)

This approved presentation iteration supersedes the previous no-chart handoff for
existing returned fields. Overview/report both show the payment value split and
daily categorical bars. Daily controls switch Rupiah/Transaksi; bars expose full
date/count/Long IDR through semantics and a selected-value label. Horizontal scrolling
handles long periods. Only returned dates appear, explicitly labeled as categorical:
no filled missing dates, interpolation, synthetic growth, hourly or profit series.

Detailed reports add five product bars ranked by gross or units within the returned
<=20 ID/name snapshot groups. The help text states this boundary and modifier/gross
meaning; no complete product-mix pie is implied. Expandable daily/product tables
retain exact rows and product UUIDs, preserving original snapshot separation. Outlet
rows and CSV content remain unchanged. Payment split labels exact cash/manual QRIS
amounts and rounded one-decimal percentages; zero totals show an em dash, not NaN or
100%. Graph proportions alone use floating point; financial displays remain Long.

Quick Today/7 days/Month buttons use the ViewModel's Jakarta clock and retain the
applied outlet. They apply through the normal scoped/cache-first load path and are
disabled during requests/export/pickers or custom draft editing. Custom draft filter
Apply/Cancel and source/as-of/cache indicators remain. Native custom-date picker,
comparison periods, AOV metric and outlet chart are deferred beyond this iteration.
Daily/payment panels sit side by side at >=900dp available width and font scale
below 1.5; otherwise they stack. Payment labels include matching color markers and
exact values, so color alone is never required. Chart mode/day selection and table
expansion are presentation-only memory state, not authorization or sale state.
No third-party chart library, RPC, Room schema or CSV format change is introduced.
