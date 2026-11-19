# Daun Teh Nusantara POS

Native Android POS foundation by Asterfed. M0 provides the adaptive application shell; M1 adds the local catalog; M2 adds debug-only offline checkout and receipts. M3 adds authentication, role/outlet access and a server security foundation. M4 adds idempotent sale submission, a durable authenticated outbox and WorkManager delivery/status. M5 adds scoped catalog/sales pull, durable cursors and authenticated offline checkout/history; demo receipts never upload.

## Environments

Select **devDebug** or **prodDebug/prodRelease** in Android Studio Build Variants.
Use DEV_SUPABASE_URL/DEV_SUPABASE_PUBLIC_KEY and PROD_SUPABASE_URL/PROD_SUPABASE_PUBLIC_KEY
in ignored local.properties. Existing unprefixed properties remain a dev-only fallback.
Production never falls back to dev and requires explicit production configuration.
See [BUILD_ENVIRONMENTS.md](docs/BUILD_ENVIRONMENTS.md), including existing-install data
handling and the separate .dev application ID. No real production values are supplied.

## Build and run

Requires JDK 17 or 21, Android SDK Platform 36, and Build Tools 35.0.0.
The Gradle wrapper pins Gradle 8.13; initial builds need access to Google Maven,
Maven Central and the Gradle distribution service.

1. Open this existing folder in Android Studio and select JDK 17+ as the Gradle JDK.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your SDK path (Android Studio can generate this file).
3. From the project root:

```sh
./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest :app:lintDevDebug
./gradlew :app:assembleDevDebugAndroidTest
# With an API 26+ device or emulator connected:
./gradlew :app:connectedDevDebugAndroidTest
./gradlew :app:installDevDebug
```

On Windows use `gradlew.bat`. Set `JAVA_HOME` if your shell uses an older JDK.
On this development Mac, Android Studio supplies a working JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDevDebug
```

The debug APK is `app/build/outputs/apk/dev/debug/app-dev-debug.apk`.
Launch **Daun Teh Nusantara Dev** from the device launcher for development. Without backend configuration,
use **Buka demo lokal (debug)** for the isolated M2 demo; authenticated entry requires
a manually provisioned account and configured development backend. The demo opens without internet after installation;
dependency downloads are still required for a first build.

## M0 foundation

- Kotlin, Compose Material 3 light/dark tea theme, edge-to-edge layout.
- Window width under 600dp uses bottom navigation; 600–839dp uses a rail;
  840dp and above adds a persistent empty cart panel to the Kasir placeholder.
  Resizing and split screen use actual available width.
- Navigation placeholders: Kasir, Riwayat, Laporan, Pengaturan. They do not grant
  permissions or implement reports. Kasir now opens the M2 debug demo POS; Riwayat
  shows recent local receipts, and Settings links to the catalog browser.
  Role-scoped entry is scheduled for M3.
- `core/designsystem` and `core/navigation` own the shell. Phone, tablet, split
  screen, and large-font previews are in `PosApp.kt`.
- `feature/foundation/presentation` uses a Hilt ViewModel and StateFlow;
  its domain repository interface is implemented in `feature/foundation/data`.
  Composables never access Room directly.
- `core/database` contains the initial development catalog schema and metadata.
  A real Room observation drives loading, ready, error, and retry states.
  M2 adds local checkout and outbox tables. No cloud submission, migration or
  destructive fallback is configured.
- `core/di` supplies a singleton Room database and repository. DataStore and
  WorkManager dependencies are pinned for later milestones; no jobs are scheduled.
- Android automatic backup is disabled pending an explicit recovery/security policy.

Version choices and official references are recorded in `docs/DECISIONS.md`.
Unit tests cover adaptive breakpoints. Instrumentation tests cover Room reopen
persistence and navigation/retry. See `docs/PROGRESS.md` for actual execution results.

## M1 local catalog

In a **debug** build, choose **Buka demo lokal (debug)**, then open **Pengaturan → Katalog lokal → Muat menu demo (debug)**.
The menu is illustrative, uses its own fixed local demo brand UUID, and is never
seeded automatically or enabled in release. Load/retry is transactional and
idempotent; existing changes and tombstones are not reset. No internet is needed
for catalog use after installation.

Tap a product to inspect its category, integer IDR base price, and size/sugar/ice/
topping option prices and min/max rules. The adaptive grid supports narrow phone
windows and wide tablets. Loading, empty, read error/retry, and seed progress/error
states are included. Reports remains a placeholder; the debug cashier and local receipt history are available in M2.

`feature/catalog/domain` defines catalog models, validation, use cases and the
repository interface. `feature/catalog/data` implements brand-scoped local CRUD,
soft deletion, atomic product/group assignments and optimistic version checks.
No management editor is exposed before role-scoped access is implemented in M3.
Local revisions are not server versions or authorization. These writes do not
claim to sync and are not a substitute for the later catalog sync protocol.

`core/database` adds categories, products, modifier groups/options and product-group
links with composite foreign keys preventing cross-brand relationships. UTC times
are epoch milliseconds, prices are `Long` IDR, and IDs are canonical UUIDs. The current
catalog is the initial schema (version 1). No migration history is maintained
during this development phase; validation triggers are installed on fresh creation.
After a schema change, clear the development app data or reinstall before running:
`adb shell pm clear com.dauntehnusantara.pos`. This deletes local app data; it is a
manual development reset, never an automatic production fallback.

Robolectric **4.17** runs actual Room CRUD, rollback, reopen and version-conflict
tests on the JVM (API 28 simulation), alongside domain rule tests.
Its Android runtime downloads on the first test run. Native Android Compose tests
still require a device/emulator. All M0 commands above remain valid.

## M2 offline cashier and receipts

Use a debug build. After loading the demo menu in Settings, return to **Kasir**:

1. Tap a product, choose the required size/sugar/ice and optional toppings, set a
   quantity, and add it to the cart. Required/min/max rules and integer overflow
   are checked before accepting the line.
2. On phones/medium windows, open the sticky cart summary; expanded windows show
   a persistent cart beside the product grid. Adjust quantities or remove lines.
3. Open **Pembayaran**. For cash, enter integer IDR tender and review the change.
   For manual QRIS, check the merchant payment application and explicitly confirm
   receipt. The POS does not automatically verify QRIS or display a payment QR.
4. Save locally. A receipt appears with immutable product/modifier names, prices,
   payment, change and a Jakarta timestamp. Use **Transaksi baru** to start another
   sale. **Riwayat** opens the latest 100 local receipts; older rows remain stored.

Cart changes persist to Room before they are acknowledged. Each draft has durable
sale/operation UUIDs and a revision. Sale/items/modifiers/payment/outbox and consumed
cart state commit atomically. After restart, a saved draft or the last undismissed
receipt is recovered. Retrying the same completion request returns the original
receipt; a different payment under the same operation is rejected. Completed sale
rows and snapshot payloads cannot be overwritten or deleted through app SQL paths.

If catalog names, prices, versions or modifier rules change, the app rejects
checkout and preserves the cart. Remove/reselect changed lines to approve current
prices. It never silently reprices a completed sale. The demo supports 1–999 units
per line and at most 100 lines, no discounts/tax adjustments or split payments;
total equals the sum of immutable line totals. UUIDs identify receipts until a
human receipt numbering policy is defined.

M2 remains **debug-only**, isolated from M3 authenticated membership, outlet and
device enrollment until the M4/M5 sale/catalog contracts are ready. Demo outlet/cashier IDs are clearly local placeholders; they
are not offline authorization. Outbox rows are `DEMO_SALE` / `PENDING_DEMO`, with
immutable versioned local JSON payloads. They must never be sent as real sales.
No WorkManager submission or server acceptance is claimed. The local request hash
is not the future server payload hash. See the decision log for this boundary.

The initial development schema is still version 1 with no migrations. Existing
M1 installations need an explicit development data clear or reinstall. No app data
was automatically cleared. Tests cover calculations, rollback, duplicate retries,
concurrent submits, persisted drafts/receipts after DB reopen and local conflicts.
Actual process-kill and phone/tablet visual acceptance still require a device.

## M3 authentication and server security

See [supabase/README.md](supabase/README.md) for the fresh development schema, owner
bootstrap example, Auth settings and [manual staff provisioning](docs/MANUAL_STAFF_PROVISIONING.md). No hosted
project, credentials, accounts, email delivery or deployment has been configured
or executed here. Migrations remain deferred during development.

Add `DEV_SUPABASE_URL` (HTTPS) and `DEV_SUPABASE_PUBLIC_KEY` (public anon/publishable)
to ignored `local.properties`, following the example, then rebuild. Secret/service
keys are forbidden in the Android app. An unconfigured build stays at a clear
configuration/login screen with the explicit debug demo entry.

An administrator creates each Supabase Auth account and assigns its profile/outlets
using the provisioning guide. Staff sign in with email/password, select an assigned
outlet and enroll the device. There is no invite or activation step. Active registered staff devices automatically approve under the default server policy.
Owners can disable outlet access through Staf.
Role-specific navigation is based on server membership, with RLS/RPC enforcement.
M4/M5 connect real sale submission and synced catalog; the portal never reuses the
M2 demo IDs/data as real transactions.

Offline access is bounded to **one calendar month**, as requested. It requires a
server-issued approved-device lease, secure OS device credentials, original boot
and valid monotonic/wall-clock bounds. Reboot, rollback, expiry and confirmed online
denial require online validation. Disconnected revocation is delayed until the next
successful server check or expiry. Tokens/context are Keystore AES-GCM encrypted;
passwords are not saved. Live hardware/Auth integration checks remain pending.

```sh
npm ci --prefix supabase --ignore-scripts
npm test --prefix supabase
./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest :app:lintDevDebug :app:assembleDevDebugAndroidTest
```

The backend tests run actual PostgreSQL/RLS/provisioning/push/pull in PGlite under
Node 22.17+. They do not require Docker, Supabase credentials or email delivery.
Existing Android tests include cached-auth, manual sign-in, retired partial-cache rejection, token refresh,
revocation, offline clocks/device binding and role-navigation cases.

## Continue development

Read `AGENTS.md` and the mandatory documents before changes. Continue one milestone
at a time using `prompts/02_IMPLEMENT_MILESTONE.md`; next is M7 (release hardening and remaining MVP gaps). Review all migrations and permission policies before deployment.

## Documentation map
- `docs/PRODUCT_REQUIREMENTS.md`: scope, roles, acceptance criteria
- `docs/ARCHITECTURE.md`: Android/cloud layering
- `docs/DATA_MODEL.md`: proposed tables, constraints
- `docs/AUTH_SECURITY.md`: login, manual provisioning, access policy
- `docs/MANUAL_STAFF_PROVISIONING.md`: administrator account/outlet setup
- `docs/SYNC_PROTOCOL.md`: offline sync contract and conflict rules
- `docs/UI_UX.md`: phone/tablet layouts
- `docs/IMPLEMENTATION_PLAN.md`: implementation milestones
- `docs/TEST_PLAN.md`: test cases
- `docs/DECISIONS.md`: assumptions and decisions
- `docs/PROGRESS.md`: milestone tracking
- `prompts/`: reusable Codex prompts

## Important
This is an implementation blueprint, not a ready-to-deploy app. Supabase URLs, keys, migrations, and API implementations must be configured and verified during development. Never commit service-role keys.

## M4 push synchronization

Authenticated Settings shows this account/device/outlet queue, retry and review counts.
Changed catalog prices/versions are rejected for review without changing the local
receipt. Retry reuses the same operation and payload; reviewed rows require explicit
future reconciliation and are never blindly retried. `SyncRepository.record` supplies atomic authenticated receipt persistence; M5 now
connects it to durable real checkout after initial catalog pull. Existing debug checkout continues to write `DEMO_SALE` only.

Use the current **fresh development** `supabase/schema.sql`; no live schema was applied.
See [push contract and acceptance checks](docs/M4_TEST_PLAN.md). Room stays at initial
version 1 with an updated export. Existing incompatible development installs require
an explicit app-data reset/reinstall; no migration or destructive fallback is added.
Logging out clears credentials. Re-login recovers the original queued device ID;
non-owner re-enrollment rotates its secret and receives approval under the current server policy. Revoked
devices remain denied; their receipts are retained for later authorized recovery.

M5 implements scoped pull below; M6 adds reports/staff/recovery below; next is M7 hardening.


## M5 authenticated checkout and pull

After online sign-in, outlet selection and device approval, a cashier's **Kasir**
uses the server catalog after the first complete catch-up. Cash and manually checked
QRIS sales persist with a durable draft/outbox and work offline within the M3 lease.
**Riwayat** merges local receipts and downloaded own sales across devices. Manager/
owner catalog browsing is read-only; shared edits require trusted server writes
until a vetted mobile catalog editor is added. M6 reporting/staff/recovery is described below.

Settings shows push/pull progress and retry errors. Catalog/receipt pages and their
scoped cursor commit together; rejected/partial pages preserve the last good state.
Role changes use separate cursors/history, and completed prices are immutable. No
Realtime setup is required. Scope and live acceptance details: [M5 test plan](docs/M5_TEST_PLAN.md).

Keep using a **fresh development schema**, installed before importing real catalog
rows so triggers publish their initial records. Room stays v1 (14 tables); explicitly
reset/reinstall incompatible development installs. No automatic data deletion,
migration, hosted deployment or real provisioning was performed. A different backend
URL requires reviewed data export/reset/rebootstrap rather than mixing caches.

M6 now provides the scoped reports, staff access controls and recovery tools below. Live M3–M5 acceptance
and the outstanding authorized mobile catalog editor remain production prerequisites.


## M6 reports, staff access and recovery

Owners can open reports for the brand or one outlet; managers get the selected
assigned outlet. Date filters use Asia/Jakarta (up to 366 days). Totals include only
accepted server sales, with daily/outlet and top-product snapshot breakdowns. Cached
reports retain their exact fetch time when offline; pending/review local sales are
excluded. Export the selected snapshot as CSV through the Android document picker.

Staff accounts and roles remain manually provisioned. Online authorized reviewers
can enable/disable outlet access, revoke devices and inspect approval requests.
Changes require reasons and expected versions; stale updates conflict. Disable also
revokes outlet devices; reenable does not restore them. Only owners can restore a
revoked device to pending; active staff auto-approve on their next valid online lease check. Owners see the selected
outlet's latest 50 access audit entries. Cashiers cannot access these management APIs.

Settings exports the account/device/outlet's retained original sale/outbox/draft
records as checksummed recovery JSON, excluding credentials and demo data. There is
no automatic archive import or full-server backup claim. A restored server's rotated
feed generation blocks old cursors and new checkout while preserving local work.
Follow the [backup/restore runbook](docs/BACKUP_RESTORE_RUNBOOK.md) and
[M6 acceptance plan](docs/M6_TEST_PLAN.md). An actual isolated PGlite dump/load/RLS/
dedupe/generation rehearsal is automated; hosted Supabase and device restore still
need live acceptance and explicit operator signoff.

Room remains initial v1 (14 tables) with serverEpoch added to the cursor. Explicitly
export and review retained work before any development reset/reinstall required by
an incompatible initial schema. No migration, app-data deletion, hosted deployment,
account change, backup subscription or live restore was performed.

**Next: M7 — release hardening.** Live Auth/device/document-provider/concurrency and
hosted restore checks, chosen backup retention/RPO/RTO, a reviewed production recovery
harness and the outstanding authorized mobile catalog editor remain release blockers.

## M7 local release hardening

The real catalog now supports online Owner/Manager creation, editing and archiving
of categories, products, modifier groups/options and product group assignments.
Changes affect every outlet in the brand. Server role/device/version/epoch checks,
operation deduplication and atomic feed/audit writes protect the command; Room is
updated by pull before another edit is enabled. Offline catalog remains readable.
This supersedes the earlier read-only real catalog limitation. Staff account creation
remains manual; no invitation flow was added.

Release builds enable R8/resource shrinking and prohibit cleartext HTTP. JSON nesting
and public-key validation are hardened. Run `./gradlew build
:app:assembleDevDebugAndroidTest :app:bundleProdRelease --no-daemon --no-parallel
--max-workers=2 --console=plain` with the documented Android Studio JBR. Artifacts
are unsigned until signing is configured externally. See
[release acceptance](docs/M7_RELEASE_CHECKLIST.md) and
[security review](docs/M7_SECURITY_REVIEW.md); physical-device, hosted concurrency,
signing and hosted recovery remain acceptance gates. No production deployment or
destructive migration is part of this iteration.

## Branding

The launcher uses an alpha-transparent extraction of the supplied Daun Teh Nusantara
logo with mask-safe padding. The app defaults to a white light appearance regardless
of device night mode, with leaf-green actions. `core/designsystem/Theme.kt` retains
an explicit dark palette option; native startup colors are in `res/values/colors.xml`
and `res/values-night/colors.xml`.

The launcher logo keeps its original colors in device light mode and uses a white
alpha-preserving tint in device dark mode (`drawable-night/ic_launcher_foreground.xml`).
Launchers may cache icons until they refresh. This does not switch the app's white UI.

## Optional product photos

Owner/Manager can add, replace or detach one product photo from Katalog → Product.
Photos appear in catalog/POS cards and cache locally after loading; missing images
never block checkout. First review/run `supabase/product-photos.sql` once after the
base schema, and update all Android sync clients before publishing photos. Keep the
bucket private. Room stays v1 with no migration. See
[product photo setup and manual checks](docs/PRODUCT_PHOTOS.md) for limits, retries,
cache behavior and the hosted Storage checks still required.

## UI design reference

The proposed visual system is in [UI guidelines](docs/UI_GUIDELINES.md), with an
[offline component/screen reference](docs/design/ui-reference.html). It specifies
colors, typography, icons, spacing, adaptive layouts and operational feedback. These
are design targets for the next Android UI pass; the reference uses fictional data.

Screen-level planning starts with the [screen inventory and flow maps](docs/UI_SCREEN_PLAN.md).
The first detailed flow is [login/outlet/device access](docs/AUTH_SCREEN_DESIGNS.md),
with an [interactive phone/tablet reference](docs/design/access-flow.html). Remaining
screen groups are inventoried and will receive detailed designs in later iterations.

The first native access redesign now applies the handoff to login, outlet selection,
locked/pending/offline entry and device-request review. It uses a bounded form with
an expanded-window brand pane and a single scrolling column for large text. Approval
requires confirmation; offline unlock still uses Android's device-credential prompt.
Host Compose checks run with `:app:testDevDebugUnitTest`; real OS credential/keyboard,
TalkBack and hosted approval acceptance still require Android devices and test users.
The shared authenticated shell is described below; remaining screen groups receive
later redesign iterations.

The authenticated shell now has distinct rounded destination icons, localized roles
and a single outlet/access header. It uses bottom navigation on phones, a rail on
wider windows, and a scrolling menu for large text or short windows. Header outlet
switching reuses online validation and is disabled offline/busy. “Akses tervalidasi”
is access context, not a receipt-sync/network guarantee. See the
[shell handoff and checks](docs/SHELL_SCREEN_DESIGNS.md) and
[icon provenance/license](docs/MATERIAL_SYMBOLS_SOURCES.md). Feature layouts and
cashier initial-pull/recovery gates stay with their existing routes.

Catalog C-01–C-08 now has a [detailed design handoff](docs/CATALOG_SCREEN_DESIGNS.md)
and [interactive phone/tablet reference](docs/design/catalog-flow.html). It covers
products/categories/modifiers, photos, archive, dependency validation and uncertain
save/pull states using fictional local data. The reference remains fictional; the native implementation is described below.
CAT-UI-01–CAT-UI-05 track its implementation and acceptance.

The native catalog redesign now applies that handoff: localized product-first tabs,
name/status filtering, adaptive product/category/modifier forms and explicit archive/
photo-removal confirmations. Drafts survive rotation/layout changes in memory; they
clear on scope change/logout and are not a durable offline editor. Unknown saves
retain their original command for retry; conflicts wait for observed pull and review.
Save completion waits for local application, with photo revisions separate from
product prices. Real picker/IME/rotation/TalkBack and hosted two-device checks remain
required. The next UI handoff is POS/modifiers/cart/payment/receipts.


## POS screen designs

POS P-01–P-06 now has a [detailed handoff](docs/POS_SCREEN_DESIGNS.md) and
[interactive phone/tablet reference](docs/design/pos-flow.html). Open the HTML locally;
all data and outcomes are fictional and reset on refresh. Covers selection, cart,
cash/manual QRIS, local-save reconciliation and immutable receipt delivery/review.
Native POS-UI-01–05 status is described below; the browser remains fictional.


## Native POS redesign

P-01–P-06 now uses adaptive Compose product search/category filters, modifier limits,
compact/persistent cart, focused cash/manual QRIS payment and immutable receipt
delivery/review states. Authenticated unknown local saves freeze the original command
until scoped Room readback; retries retain the same operation/payment. No DB migration
or backend deployment is needed. See [native status/manual checks](docs/POS_SCREEN_DESIGNS.md#10-native-implementation--15-november-2026)
and [verification results](docs/PROGRESS.md). Real phone/tablet/hosted acceptance remains
open. Next: detailed transaction-history H-01/H-02 designs before its native redesign.


## History screen designs

H-01/H-02 now has a [detailed design handoff](docs/HISTORY_SCREEN_DESIGNS.md) and
[interactive phone/tablet reference](docs/design/history-flow.html). Open the HTML
locally; all receipts/outcomes are fictional browser-memory fixtures. The design
covers cache-limited search/day/status filtering, scoped read-only receipt detail,
refresh and recovery. Native Android is unchanged by this design iteration.
Next: implement HIST-UI-01–04; physical/hosted M7 acceptance remains open.


## Native history redesign

Cashier Riwayat now has cache-only UUID/original-name search, Jakarta day/status
filters, adaptive list/detail and read-only immutable receipts. Refresh separates
local-only, observed pull, partial and failed outcomes without modifying checkout
commands or receipt pointers. See [native details/manual checks](docs/HISTORY_SCREEN_DESIGNS.md#7-native-implementation--15-november-2026)
and [verification](docs/PROGRESS.md). No migration/backend deployment is needed.
Component tests use a plain test Application to skip production startup sync;
MainActivity/Hilt/Auth end-to-end acceptance needs a separate normal-app runner.
The subsequent overview/report redesign is described below.


## Overview/report screen designs

R-01–R-03 and the X-03 export handoff now have [detailed designs](docs/REPORT_SCREEN_DESIGNS.md)
and an [interactive phone/tablet reference](docs/design/report-flow.html). Open the HTML
locally; data, report outcomes and picker/write behavior are fictional browser fixtures.
The native RPT-UI-01–03 implementation now shares overview/report state, uses a
separate draft filter sheet, adaptive metrics/rows, scoped cached reports and captured
CSV export. See [native behavior and remaining manual checks](docs/REPORT_SCREEN_DESIGNS.md#7-native-implementation--15-november-2026)
and [executed verification](docs/PROGRESS.md). No migration or backend deployment is
needed. Next: S-01–S-03 staff/access/confirmation design handoff.


## Staff/access screen designs

S-01–S-03 now has a [detailed handoff](docs/STAFF_SCREEN_DESIGNS.md) and
[interactive local reference](docs/design/staff-flow.html), with fictional
[phone](docs/design/staff-phone.png) and [tablet](docs/design/staff-tablet.png) captures.
Open the HTML locally to review member/device/audit views and reasoned confirmations,
including conflict and uncertain response simulations. It changes no real staff access.
Next: native STAFF-UI-01–04. Manual account provisioning and role rules remain intact.


## Native staff/access redesign

Staf now separates Anggota, Perangkat and Owner-only Aktivitas akses. Confirmations
show the captured target/effect and audited reason, preflight current target versions,
and distinguish conflict, uncertain outcome and acknowledged changes with failed
readback. Staff operations remain online-only; account provisioning remains manual.
See [native behavior and limits](docs/STAFF_SCREEN_DESIGNS.md#9-native-implementation--15-november-2026)
and [executed verification](docs/PROGRESS.md). No migration/backend deployment needed.
Next: settings/recovery/sign-out T-01–T-04 detailed screen designs.


## Settings/recovery screen designs

T-01–T-04/sign-out and recovery export now have a [detailed handoff](docs/SETTINGS_SCREEN_DESIGNS.md)
and [interactive local reference](docs/design/settings-flow.html), with fictional
[phone](docs/design/settings-phone.png) and [tablet](docs/design/settings-tablet.png) captures.
Open the HTML locally to review queue/recovery/export/sign-out states. It creates no
file and changes no session or retained data. Android behavior is unchanged by this
design step. Next: native SETTINGS-UI-01–04; no reset/import/restore workflow is added.


## Native settings/recovery redesign

Setelan now groups account/access, scoped sync state, recovery export and session
confirmation. Scheduling is separate from delivery; recovery-required never offers
reset. Recovery JSON is captured and validated before the picker, with guarded
callbacks and private in-memory bytes. Sign-out preserves local work and warns when
queue status is unknown. See [native behavior and limits](docs/SETTINGS_SCREEN_DESIGNS.md#9-native-implementation--15-november-2026)
and [executed checks](docs/PROGRESS.md). No migration/backend deployment is needed.
Next: cross-screen normal-app phone acceptance and M7 release checklist review.

## M7 readiness and device acceptance

See [the current acceptance record](docs/M7_DEVICE_ACCEPTANCE.md) for normal-app
startup, artifact hashes, ELF/ZIP checks and open sign-in/provider/signing gates.
Read-only artifact verification is repeatable with
`python3 scripts/verify_android_artifacts.py`; use `--help` for mapping/zipalign options.
The working-brand project is not used for fabricated sales or access/restore tests.

Normal-app acceptance also found and fixed periodic validation resetting navigation.
Unchanged confirmed access now keeps the current screen while validation is busy;
changed roles/denials still clear the old context. Latest results and remaining
controlled-device gates are in [M7_DEVICE_ACCEPTANCE.md](docs/M7_DEVICE_ACCEPTANCE.md).

Periodic background push/pull sync runs hourly (Android scheduling is best effort).
Existing periodic entries update on startup. Event-driven, startup and manual sync
nudges remain available; access validation has its own separate 60-second interval.

The launcher icon uses a fixed white background with the green brand mark in both
light and dark modes. The in-app logo/theme remains independent of launcher styling.

## Shared Compose components

Repeated screen elements now live in core/designsystem/components; see
[UI_COMPONENTS.md](docs/UI_COMPONENTS.md) for APIs and adoption. Catalog money fields
and cash tender show `Rp`/thousands grouping (for example `10.000`) while keeping raw
digits and exact Long IDR validation. Valid `Rp 10.000` paste is supported; decimals,
negative/malformed amounts and overflow never silently become a different price.

Cash payment supports quick tender amounts: Rp5.000, Rp10.000, Rp20.000, Rp50.000
and Rp100.000. Tap to replace the received amount, or use Uang pas/manual input.

## Create staff in the app

After [staff provisioning setup](docs/IN_APP_STAFF_PROVISIONING.md), open Staf →
Tambah staf for the selected outlet. Owners can create Managers/Cashiers; Managers
can create Cashiers only. New staff replace the temporary password before POS access
and receive automatic device approval when active. Existing email accounts require administrator review.
The SQL setup and staff-admin Edge Function must be deployed separately; never add
a service-role key to Android.

## Owner cashier access

Owners keep Ringkasan as their start screen and can open Kasir or Riwayat for the
selected outlet. On compact phones, use the header navigation menu; on tablets use
the navigation rail. The context remains Pemilik and the sale records the signed-in
Owner user ID. Normal approved-device, initial-sync, offline and payment guards apply.
Owner history shows the available selected-outlet cache; Cashier history remains own
receipts. No role switch, extra Cashier account or backend setup is needed for this
navigation change. Manager navigation remains management-only.

## Per-outlet staff limits

Supabase administrators configure separate Manager/Cashier maximums per outlet.
Multiple Cashiers are supported up to that limit. The app shows used/maximum counts
and blocks creation when full or unconfigured; disabled memberships and pending
creations still count. Owners/Managers cannot change quotas in the app. Setup:
[STAFF_LIMITS.md](docs/STAFF_LIMITS.md). Apply staff-limits.sql once after provisioning
setup and choose actual limits using configure-staff-limits.sql.example.

The navigation drawer shows brand identity and the signed-in email/role/outlet. The
app bar uses a compact access-status icon; tap it for validation/offline details.

Change appearance in **Setelan → Tampilan → Terang / Gelap**. The preference is saved
on the device, works offline and also applies to sign-in after logout. Light remains
the default. The green app bar keeps the outlet switch beside the outlet title.

Device names now appear in staff device lists and approval reviews. Apply the optional
[device-name setup](docs/DEVICE_NAMES.md), then restart/validate the updated staff app
and refresh the reviewer list. Existing devices need no reenrollment; unknown labels
keep the device ID fallback. Names are display-only, not hardware identity proof.

Tap the outlet name/role in the app bar to change outlets when online. The drawer
closes by destination selection, outside tap, swipe or Back, without extra menu wording.

Device approval troubleshooting: use the updated APK on the waiting and reviewer
phones, approve the current displayed device UUID in the same outlet, and keep the
waiting phone online (automatic checks every 15 seconds, or retry manually). Device
enrollment is retained across login/outlet switches; sign-out still removes tokens
and offline access. No SQL deployment or device-table reset is required. See
[authentication policy](docs/AUTH_SECURITY.md#enrollment-retention-and-approval-refresh-16-november-2026).

At the root screen, press Back twice within two seconds to exit. The first press
shows a reminder; drawers, dialogs and details keep their normal Back behavior.

Printer setup and a fictional text-test probe are available under **Setelan → Printer
struk**. Pair in Android settings, load paired devices, select the printer and confirm
**Cetak tes**. The generic 58 mm model is unverified; no sale receipt is printed yet.
See [BLUETOOTH_PRINTING_PLAN.md](docs/BLUETOOTH_PRINTING_PLAN.md) for setup, compatibility
limits and remaining receipt implementation milestones.

Form inputs share the login field sizing, including money, search and date fields.
Multiline reasons and validation messages grow naturally; see docs/UI_COMPONENTS.md.

Managers can now open **Kasir** and **Riwayat** for their selected assigned outlet.
Ringkasan remains the default; sales retain the actual Manager identity and approved
device. History shows available outlet receipts under existing Manager permissions.

Reports now include daily sales bars, top-product bars, cash/manual-QRIS proportions
and quick period filters. Exact daily/product tables expand below the charts; CSV
export remains unchanged. Charts use accepted server snapshots, excluding pending
offline sales. Product Unit mode ranks only returned snapshot groups.

Per-outlet stock movement is planned in [OUTLET_STOCK_PLAN.md](docs/OUTLET_STOCK_PLAN.md),
starting with a proposed manual ledger before automatic consumption and transfers.
Tracking type/units and insufficient-stock policy still need confirmation.

Both periodic background sync and signed-in access refresh use a one-hour interval.
Login/foreground/reconnection/manual actions may validate sooner; waiting-device
approval checks remain every15 seconds and do not refresh an unlocked screen.

For a fresh production Supabase project, follow
[PRODUCTION_SUPABASE_SETUP.md](docs/PRODUCTION_SUPABASE_SETUP.md): ordered SQL scripts,
guarded DEV catalog/limit exports, fresh production Owner and staff-admin deployment.
It intentionally excludes development users/devices/financial records.

Kebijakan Privasi bahasa Indonesia: [Markdown](docs/PRIVACY_POLICY.md) dan
[HTML GitHub Pages](docs/privacy-policy.html). Cara hosting dan pekerjaan Play Console
yang masih perlu diselesaikan tersedia di [PRIVACY_PUBLISHING.md](docs/PRIVACY_PUBLISHING.md).

Registered staff devices now automatically approve after login/password setup with
active outlet access. Owners can deactivate staff through Staf. Existing projects
apply [device-auto-approval.sql](supabase/device-auto-approval.sql); see
[device policy](docs/DEVICE_APPROVAL_ALLOWLIST.md) for deployment, manual mode and
offline deactivation limits. No app rebuild or Edge redeploy is required.
