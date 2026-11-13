# Daun Teh Nusantara POS

Native Android POS foundation by Asterfed. M0 provides the adaptive application shell; M1 adds the local catalog; M2 adds debug-only offline checkout and receipts. M3 adds authentication, role/outlet access and a server security foundation. M4 adds idempotent sale submission, a durable authenticated outbox and WorkManager delivery/status. M5 adds scoped catalog/sales pull, durable cursors and authenticated offline checkout/history; demo receipts never upload.

## Build and run

Requires JDK 17 or 21, Android SDK Platform 36, and Build Tools 35.0.0.
The Gradle wrapper pins Gradle 8.13; initial builds need access to Google Maven,
Maven Central and the Gradle distribution service.

1. Open this existing folder in Android Studio and select JDK 17+ as the Gradle JDK.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your SDK path (Android Studio can generate this file).
3. From the project root:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# With an API 26+ device or emulator connected:
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
```

On Windows use `gradlew.bat`. Set `JAVA_HOME` if your shell uses an older JDK.
On this development Mac, Android Studio supplies a working JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
Launch **Daun Teh Nusantara** from the device launcher. Without backend configuration,
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

Add only `SUPABASE_URL` (HTTPS) and `SUPABASE_PUBLIC_KEY` (public anon/publishable)
to ignored `local.properties`, following the example, then rebuild. Secret/service
keys are forbidden in the Android app. An unconfigured build stays at a clear
configuration/login screen with the explicit debug demo entry.

An administrator creates each Supabase Auth account and assigns its profile/outlets
using the provisioning guide. Staff sign in with email/password, select an assigned
outlet and enroll the device. There is no invite or activation step. Owners can self-enroll; manager
and cashier devices require the appropriate owner/manager approval through Staf.
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
./gradlew build :app:assembleDebugAndroidTest
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
non-owner re-enrollment rotates its secret and needs fresh device approval. Revoked
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
revoked device to pending, and fresh approval is required. Owners see the selected
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
:app:assembleDebugAndroidTest :app:bundleRelease --no-daemon --no-parallel
--max-workers=2 --console=plain` with the documented Android Studio JBR. Artifacts
are unsigned until signing is configured externally. See
[release acceptance](docs/M7_RELEASE_CHECKLIST.md) and
[security review](docs/M7_SECURITY_REVIEW.md); physical-device, hosted concurrency,
signing and hosted recovery remain acceptance gates. No production deployment or
destructive migration is part of this iteration.
