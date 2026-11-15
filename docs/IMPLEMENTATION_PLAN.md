# Implementation milestones

Do not implement all milestones in one Codex turn. Complete each with build/tests and a progress report.

## M0 Toolchain & skeleton
Verify Android SDK/Gradle/JDK, choose compatible pinned versions, scaffold Kotlin Compose app, adaptive theme, Hilt, Room, navigation, CI-ready build. Acceptance: debug build succeeds or blockers documented.

## M1 Local schema & catalog
Room schema, products/categories/modifiers, seed demo menu, catalog repository, unit/DAO tests. Acceptance: CRUD and modifiers work offline.

Development policy: maintain only the current initial Room schema during this development phase; no migration paths or migration tests are required yet. Reset development app data explicitly after incompatible schema changes. Introduce reviewed data-preserving migrations before releases that require retained data.

## M2 POS & checkout offline
Phone/tablet adaptive POS, cart, modifier selection, cash/manual QRIS, local atomic sale+outbox, receipts, tests. Acceptance: offline sale survives restart.

## M3 Supabase foundation & security
Initial development SQL schema for brands/outlets/members/catalog/sales/sync/audit (no development migrations), RLS policies and tests, manual staff provisioning/login, role-specific navigation and approved-device offline leases. Acceptance: unauthorized cross-outlet access denied server-side.

## M4 Push sync
Secure transactional `submit_sale`, operation dedupe, WorkManager retry/status. Acceptance: retry/ack-loss tests prove no duplicates.

## M5 Pull sync & multi-device
Monotonic change feed, scoped cursors, catalog and sales updates, Realtime hints optional. Acceptance: two devices converge after offline period.

## M6 Reports, staff, recovery
Role-scoped reports, staff management, backup/restore runbook, export, device revocation, audit trail. Acceptance: correct outlet scoping and verified restore rehearsal.

## M7 Release hardening
Security review, remaining authorized mobile catalog editor, end-to-end offline/online scenarios, UI accessibility, performance, migration and rollback plan, signed release checklist. Local controls and acceptance gates are tracked in `M7_RELEASE_CHECKLIST.md`; production acceptance remains open until the device/hosted/signing checks are executed.

## Every milestone report
Files changed; commands/tests actually run; unresolved blockers; assumptions/decisions; suggested next step.

## Optional product-photo iteration

One photo per saved product: online Owner/Manager picker/upload/remove, private
Storage setup/RLS, independent photo revisions/feed, POS/catalog display and bounded
scoped offline cache. Keep image failures out of checkout and sale snapshots; Room
v1 is unchanged. Setup/manual acceptance: `PRODUCT_PHOTOS.md`. This iteration does
not close the outstanding M7 hosted, device, signing or recovery acceptance gates.

## Access UI implementation iteration (2026-11-15)

Implement ACCESS-01–ACCESS-04 from `AUTH_SCREEN_DESIGNS.md` as one presentation
iteration, reusing auth/reviewer APIs and server security. Shared theme tokens and
adaptive access screens are now implemented. ACCESS-05 adds host regressions; device
OS credential, real keyboard/TalkBack and hosted two-actor acceptance remain open.
Next UI milestone: F-00/F-01 shared authenticated shell/navigation/context. Detail
catalog/POS and later screen groups before their own implementation iterations.

## Shared shell UI iteration (2026-11-15)

F-00/F-01: role-filtered navigation/icons, scoped/restorable selection, shared context
header, online-only outlet action and accessible drawer fallback are implemented.
Existing feature callbacks/settings recovery and auth/checkout gates are retained.
See SHELL_SCREEN_DESIGNS.md. Host regressions precede physical-device acceptance.
Next: detail the catalog C-01–C-08 layouts, forms/photo/archive states and acceptance
criteria before applying that feature's native redesign. M7 acceptance remains open.

## Catalog detailed design iteration (2026-11-15)

C-01–C-08 now has detailed layouts/state/action contracts and a fictional phone/tablet
reference in CATALOG_SCREEN_DESIGNS.md / design/catalog-flow.html. No native catalog
behavior changed in this documentation iteration. Next implementation milestone:
CAT-UI-01–CAT-UI-05, preserving existing editor/authorization/photo/command contracts
and adding observed refresh/draft-result state only where needed for the presentation.

## Native catalog presentation iteration (2026-11-15)

CAT-UI-01–04 are implemented, with CAT-UI-05 host regressions and instrumented-test
compilation. The native editor now has localized product-first browsing, cached
filters, adaptive forms, confirmation flows and explicit immutable-command/refresh/
local-apply states. See CATALOG_SCREEN_DESIGNS.md for manual acceptance and retained
process-loss/hosted limitations. Real device acceptance remains open.
Next milestone: detail POS P-01–P-06 layouts/modifier/cart/payment/receipt states before
applying their native redesign. Do not add payment gateway or refund behavior.


## POS design iteration (2026-11-15)

P-01–P-06 detailed designs and a fictional browser reference are complete. See
POS_SCREEN_DESIGNS.md for contracts and POS-UI-01–05 implementation tickets.
Next milestone: native POS presentation redesign, starting with scoped cancellation
and observed command results, then adaptive catalog/cart/payment/receipt and a
read-only scoped delivery projection. Preserve original sale/outbox identities,
manual QRIS, guarded offline access and strict changed-price review. No schema
migration, gateway, refund or server deployment is included in the design milestone.


## Native POS presentation iteration (2026-11-15)

POS-UI-01–04 are implemented; POS-UI-05 host regressions/build and remaining manual
checks are recorded in PROGRESS.md / POS_SCREEN_DESIGNS.md. No schema/dependency/
server changes were required. Local-save reconciliation and per-receipt delivery
read models preserve original sale/outbox/receipt identity. Native device and hosted
acceptance remains open. Exact next milestone: detailed history H-01/H-02 designs
for scoped list/filter/detail and delivery/review states before native implementation.
No void/refund, printing or gateway scope is added.


## History detailed design iteration (2026-11-15)

H-01/H-02 detailed handoff and fictional phone/tablet reference are complete in
HISTORY_SCREEN_DESIGNS.md / design/history-flow.html. Android behavior is unchanged
by these artifacts. Exact next milestone: HIST-UI-01–04 native history presentation:
scoped cache selectors/selection, adaptive list/detail, observed refresh/read/pull
outcomes and regressions. Preserve the existing bounded merged result and do not
claim a complete latest-100 archive. No schema/RPC/version change is assumed; broader
archive pagination and adjustment/printing features require a separate scope.


## Native history presentation iteration (2026-11-15)

HIST-UI-01–03 are implemented; HIST-UI-04 tests/build and device/hosted limits are
recorded in PROGRESS.md. History has its own scoped in-memory session/read model and
explicit bounded pull refresh, plus read-only focused/split receipt presentation.
Existing Room limits, immutable sales/outbox, auth policy and dependencies remain.
Next milestone: detail R-01–R-03 Ringkasan/Laporan/filter layouts and existing X-03
report export behavior before native report redesign; no new report metrics/endpoints.


## Overview/report detailed design iteration (2026-11-15)

R-01–R-03 / report X-03 detailed handoff and fictional phone/tablet reference are
complete in REPORT_SCREEN_DESIGNS.md / design/report-flow.html. Android behavior is
unchanged. Exact next milestone: native RPT-UI-01–04—scoped draft/applied query and
request guards; destination-aware overview/report hierarchy; captured picker/export
lifecycle; financial/date/cache/scope/Compose/provider regressions and full build.
Reuse existing report fields, ReportQuery (<=365 day difference), top20 snapshot
product grouping and CSV format. No new dependency/schema/RPC/deployment is assumed.


## Native report iteration (2026-11-15)

RPT-UI-01–03 are implemented: scoped session/draft/applied identity and request guards,
destination-aware shared overview/report presentation, adaptive metrics and rows,
bounded filters, retained exact-query cache, captured export/cancel/provider outcome.
RPT-UI-04 automated verification and physical limits are recorded in PROGRESS.md.
No dependency/schema/RPC/migration/deployment changes. Remaining report acceptance
uses normal application/Hilt/auth, hosted data and real document providers; fixture
runs are not end-to-end security or financial acceptance.
Exact next milestone: detailed S-01–S-03 staff/access/confirmation design handoff,
preserving manual staff provisioning, Owner/Manager limits and existing server actions.


## Staff/access detailed design iteration (2026-11-15)

S-01–S-03 handoff is complete in STAFF_SCREEN_DESIGNS.md and design/staff-flow.html,
with fictional compact/tablet captures and browser checks recorded in PROGRESS.md.
Next milestone: native STAFF-UI-01–04—scoped memory session/request guards; adaptive
member/device/audit views and separate approval lifecycle; captured reasoned/versioned
confirmations and ack/readback/uncertain/conflict outcomes; relevant regressions,
full Gradle build and feasible isolated phone checks. Retain current provisioning,
fields/permissions/RPCs; no migration, account creation, backend change or deployment.


## Native staff/access iteration (2026-11-15)

STAFF-UI-01–03 implement scoped memory/session and guarded IO, adaptive rows/tabs,
captured effect-specific confirmations, fresh target/version preflight and separate
ack/readback/uncertain/conflict handling. Approval remains independent with shared
presentation busy gates. STAFF-UI-04 verification is recorded in PROGRESS.md; real
hosted/Hilt/lease/approval/physical accessibility gates remain open. No dependency,
Room/schema, migration or backend deployment change.
Exact next milestone: detailed T-01–T-04 settings/recovery/sign-out and existing X-03
recovery-export designs before native implementation; no new reset/import/restore API.


## Settings/recovery detailed design iteration (2026-11-15)

T-01–T-04/sign-out/recovery X-03 handoff and fictional phone/tablet reference are
complete in SETTINGS_SCREEN_DESIGNS.md / design/settings-flow.html. Next milestone:
native SETTINGS-UI-01–04—scoped adaptive settings/read model, safe push/pull observation
and bounded retry status, frozen validated recovery archive/ticket/provider lifecycle,
sign-out confirmation retaining data, tests/full Gradle/feasible phone checks. Reuse
existing interfaces, archive format, authorization and M7 recovery gates. No automatic
reset, archive import, live restore, new account setting or server deployment.


## Native settings/recovery iteration (2026-11-15)

SETTINGS-UI-01–03 implement the scoped adaptive route/read model, safe push/pull
observation/retry state, frozen validated archive/ticket/provider lifecycle and
retained-data sign-out confirmation. SETTINGS-UI-04 results are recorded in PROGRESS.md.
Existing repository/archive/auth contracts remain; no migration, endpoint or library
version change. Exact next milestone: cross-screen normal-application acceptance on
the connected phone and M7 checklist review. Hosted auth/RLS/recovery, actual file
providers/lease/sign-out, physical tablet/accessibility and signing remain gated.

## M7 normal-app readiness review (2026-11-15)

Normal debug startup reached sign-in on the connected Infinix API36/4 KB phone. Current
APK/mapping hashes, manifest/signature/ELF/ZIP checks and 52 freshly executed isolated
backend tests are recorded in M7_DEVICE_ACCEPTANCE.md. Added a repeatable artifact
verifier and four parser tests. The user confirmed a working-brand backend; authenticated
read-only checks await sign-in on the device. No live transaction/access/restore test,
signing, data clearing or backend deployment. Next: complete the read-only authenticated
navigation/cancel matrix, then arrange controlled hosted/provider/release gates.

## Signed-in normal-app follow-up (2026-11-15)

Observed management navigation and real confirmation/JSON-picker cancellation. Fixed
same-context auth revalidation removing the shell: keep READY/busy only after current
choices confirm identical context; real role/permission changes still clear authority.
Three regressions and full build passed (295 debug, 286 release +9 expected skips).
Manual validation and >70s Settings observation passed on the updated phone. Further
report/staff checks await an idle phone; controlled completed-file/other-role/offline/
concurrency/restore/signing acceptance remains. See M7_DEVICE_ACCEPTANCE.md.

## Hourly background sweep (2026-11-15)

User-selected periodic WorkManager interval is now one hour. UPDATE applies the
new specification to the existing unique sweep on startup. A real WorkManager
persistence regression verifies the old 15-minute entry updates in place; push/pull
regressions and local debug assembly are recorded in PROGRESS.md. No device checks
or install while the user's device-check pause is active. Access-validation frequency
and event-driven sync remain separate. Next: install when appropriate and resume only
explicitly authorized device acceptance; release/signing/hosted gates remain open.

## Shared components and money input (2026-11-15)

Extracted common presentation and adopted it across catalog/POS/reports/staff/settings.
MoneyInputField formats whole IDR over raw drafts with strict grouped paste, cursor
mapping and unchanged domain validation. No business/schema/auth/dependency changes.
See UI_COMPONENTS.md and PROGRESS.md for contracts, verification and limits. Next:
incremental adoption in remaining specialized screens only where contracts match;
normal-device/IME/accessibility acceptance resumes only at the user's request.
