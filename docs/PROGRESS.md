# Implementation progress

- [x] M0 Toolchain & skeleton
- [x] M1 Local schema & catalog
- [x] M2 POS & checkout offline
- [x] M3 Supabase foundation & security (local implementation; live acceptance pending)
- [ ] M4 Push sync
- [ ] M5 Pull sync & multi-device
- [ ] M6 Reports, staff, recovery
- [ ] M7 Release hardening

Update after each milestone with evidence of build/tests, blockers and next steps.

## M0 implementation — 2026-11-13

Scope: Android foundation only, preserving the planning documents and prompts.
No business features, production cloud schema, credentials, or deployments.

### Changed files

- Root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`,
  `gradlew.bat`, `gradle/wrapper/*`: pinned, portable Android build and wrapper.
- `.gitignore`, `local.properties.example`: build/IDE/local config exclusions and
  SDK path example. Development-only `local.properties` is ignored.
- `app/build.gradle.kts`, manifest and resources: Kotlin/Compose app, SDK levels,
  dependencies, Indonesian labels, platform theme, vector launcher icon and explicit
  Android 12+ cloud/device-transfer exclusion rules.
- `app/src/main/java/id/asterfed/dauntehpos/`: application/activity, design system,
  adaptive navigation, Room metadata database, Hilt modules, foundation domain
  repository, data implementation and StateFlow ViewModel.
- `app/schemas/.../1.json`: generated Room schema v1 for future migrations.
- `app/src/test/.../WindowLayoutTest.kt`: three adaptive breakpoint tests.
- `app/src/androidTest/.../{DatabaseTest,NavigationTest}.kt`: database reopen and
  navigation/retry instrumentation tests.
- `README.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md`: setup, decisions, evidence.

### Commands and evidence

All Gradle commands below use
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

- Read `AGENTS.md`, `README.md`, bootstrap prompt, all seven mandatory docs in
  order, then test plan, decisions, and progress.
- Inspected SDK platforms/build tools, cached Gradle/dependencies and JDKs.
  Default shell Java: 8u503; selected Android Studio JBR: 21.0.10.
  No Android project or Git repository existed in this folder.
- Initial sandboxed Gradle startup failed loading its native library. Running
  with approved cache/network access resolved the environment restriction.
- Cached Gradle 8.13 command:
  `gradle wrapper --gradle-version 8.13 --distribution-type all --no-daemon`:
  **BUILD SUCCESSFUL**, wrapper generated.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  debug APK built and three unit tests passed; overall **BUILD FAILED** because
  the Room instrumentation test used `use` on a non-Closeable RoomDatabase.
  Corrected to explicit `close()` in `finally`. Also made the string-resource
  annotation target explicit to remove a Kotlin annotation warning.
- `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 2m 31s**, 151 actionable tasks (90 executed, 61 up-to-date).
  Debug and unsigned release APKs generated; Hilt and Room generation compiled.
  Three unit tests passed in each debug/release variant (six executions, zero
  failures/errors). Lint completed successfully. Both instrumentation tests
  compiled and the instrumentation APK was generated; not run on a device.
- Artifacts: `app/build/outputs/apk/debug/app-debug.apk`,
  `app/build/outputs/apk/release/app-release-unsigned.apk`,
  `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
  Initial lint: zero errors, 21 warnings. Fixed missing icon, Indonesian locale
  declaration and Android 12+ extraction rules; remaining version notices retain
  the chosen compatible baseline.
- After those resource/manifest fixes, reran the same full build command:
  **BUILD SUCCESSFUL in 1m 6s**, 151 actionable tasks (59 executed, 92 up-to-date).
  Debug/release unit suites each passed all three tests again. Final lint:
  **zero errors, 18 warnings**, all dependency/toolchain/target-version notices.
  Instrumentation APK compilation and both app variants succeeded again.
  Reports: `app/build/reports/lint-results-debug.html` and
  `app/build/reports/tests/test{Debug,Release}UnitTest/index.html`.
- `adb devices`: no attached devices. SDK emulator executable absent; no AVDs
  available. Instrumentation tests and visual/device acceptance are not executed.

### Remaining validation and limitations

- Run `./gradlew :app:connectedDebugAndroidTest` on an API 26+ device/emulator,
  then inspect phone portrait, medium/split-screen, tablet landscape, dark mode,
  large text and resize/rotation. Previews exist but were not visually rendered here.
- Installed NDK 27.0.12077973 is incomplete (`source.properties` missing). Gradle
  packages dependency native libraries without stripping; no native app code is
  present. Repair/install the NDK before release hardening.
- No backend/auth/sync, catalog, sales or role enforcement is claimed. All
  navigation destinations are placeholders. Secure backup policy remains open.

### Exact next milestone

**M1 — Local schema & catalog**: add reviewed Room tables and a v1-to-v2 migration
preserving metadata; products/categories/modifiers, demo menu, domain repository
and offline CRUD/modifier tests. Do not begin checkout or Supabase work in M1.

## M1 — Local schema & catalog — 2026-11-13

Scope: local catalog persistence and validation, a read-only adaptive demo browser,
and repository CRUD. No checkout, authentication, backend or sync implementation.

### Files changed

- `core/database/{PosDatabase,CatalogEntities,CatalogDao,CatalogConstraints}.kt`:
  five catalog tables, composite brand-scoped FKs/indexes, additive explicit
  auto-migration 1→2 and validation triggers. Existing metadata and schema v1 kept.
- `app/schemas/id.asterfed.dauntehpos.core.database.PosDatabase/2.json`: exported
  Room schema. No destructive fallback or physical catalog deletions.
- `feature/catalog/domain/{Catalog,CatalogUseCases}.kt`: domain records, repository
  interface, loading use cases, UUID/name/price/selection validation.
- `feature/catalog/data/{RoomCatalogRepository,DemoCatalog}.kt`: transactional
  local CRUD/tombstones, optimistic conflicts, scoped observations, assignments
  and explicit idempotent debug-only example menu seeding.
- `feature/catalog/presentation/{CatalogViewModel,CatalogScreen}.kt`: StateFlow
  states, retry, responsive grid and read-only modifier details.
- `core/di/StorageModule.kt`, `MainActivity.kt`, `core/navigation/PosApp.kt`,
  `res/values/strings.xml`: Room callbacks, repository/Clock injection and Settings
  catalog entry. Root navigation and other placeholders preserved.
- `app/build.gradle.kts`: BuildConfig debug guard, Robolectric 4.17, schema test
  resources and Java 17+ test configuration. Runtime dependencies unchanged.
- JVM tests: `CatalogRulesTest`, `CatalogRepositoryTest`, `CatalogMigrationTest`.
  Android test: `CatalogScreenTest` for retry and modifier detail navigation.
- `README.md`, `docs/DATA_MODEL.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md`:
  current usage, schema, rationale, execution evidence and remaining validation.

### Commands and results

All Gradle commands use Android Studio JBR 21 via
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

- Read the implementation prompt and required product/architecture/data/security/
  sync/UX/milestone documents; inspected M0 source and existing tooling.
- Used the installed codebase-memory skill (indexed this project, searched the
  database/DI/navigation/activity symbols and checked coverage of those paths).
  Those Kotlin paths had no recorded gaps; source was also read. The index's
  unrelated gradlew shell parse gap was not used as implementation evidence.
- `./gradlew :app:kspDebugKotlin --no-daemon --console=plain`:
  **BUILD SUCCESSFUL**, generated schema v2 and the explicit auto-migration.
- First `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD FAILED** in the local migration test fixture (archived JSON absent from
  instrumentation assets under Robolectric). The other 19 debug tests passed,
  including actual Room CRUD, constraints, persistence, rollback and conflicts.
  Fixed the fixture to read the unchanged v1 JSON as test-only classpath resources
  and open the v1 database with real Room v2, invoking its runtime migration and
  full schema validation. No schema JSON is packaged into production app assets.
- Added a recoverable simulated SQLite failure test: failed seed rolls back all
  rows/marker; retry after removing the injected fault seeds the complete menu.
- Final `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 1m 35s**, 161 actionable tasks (34 executed, 127 up-to-date).
  Debug: **20 passed**, zero failures/errors/skips. Release: **19 passed**, zero
  failures/errors, one expected skip for debug-only seed fault/retry; release demo
  rejection is separately tested. Both variants passed the actual v1→v2 Room
  migration test. Lint: **zero errors, 18 version notices** (existing pinned
  toolchain/dependencies). Debug/release APKs and Android-test APK generated.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`, and
  `app/build/reports/lint-results-debug.html`.
  Debug app: `app/build/outputs/apk/debug/app-debug.apk`.
  Unit coverage includes UUID/name/price/bounds validation, required/optional/
  duplicate/unknown/inactive/unassigned modifier choices, all four catalog record
  types, persisted edits/tombstones, reopen, stale writes, rejected assignments,
  brand isolation, direct SQL constraints, seed idempotency, rollback/recovery,
  release seed guard and migration preservation.
- `adb devices`: no connected devices; emulator executable remains unavailable.
  Native Compose tests are compiled only, not executed. Robolectric is local
  Android simulation, not proof of actual phone/tablet or OEM behavior.

### Decisions and limitations

- Demonstration prices are examples; loading requires the debug-only button under
  Pengaturan → Katalog lokal. Never auto-seed real or release catalogs.
- Repository CRUD is available and tested locally; an editing UI and authorized
  shared catalog mutations remain dependent on M3 role/security work.
- Local optimistic revisions prevent stale local writes; they are not server
  revisions, remote conflict resolution or a sync guarantee. No outbox for local
  demo catalog changes is implied.
- Visually check phone/tablet/split-screen, rotation, large font, dark mode and
  error/empty states on devices. The existing incomplete NDK installation remains
  an environment issue for later native symbol stripping/release hardening.

### Exact next milestone

**M2 — POS & checkout offline**: adaptive cashier/cart, constrained modifier
selection, cash/manual QRIS checkout, immutable price/name/modifier snapshots,
atomic sale/payment/outbox writes, receipts and restart/duplicate-submit tests.
Do not begin Supabase or production cloud schema until M3.

## M1 follow-up — initial development schema — 2026-11-13

Per user direction, migration work above is historical and superseded for the
current development phase. No new milestone or business feature is introduced.

- Removed the declared auto-migration, CatalogMigration spec, migration test and
  schema fixture wiring from Gradle.
- Consolidated the current catalog into initial Room schema version 1 and removed
  schema v2. Kept creation-time validation triggers, repository tests and all catalog
  behavior. No automatic destructive reset is configured or executed.
- Updated README, data model, implementation plan and decision D017 with the
  development reset policy.
- Existing installs with the previous schema must clear development app data or
  reinstall. No attempt is made to upgrade/downgrade or retain those demo databases.
- Executed with Android Studio JBR 21:
  `./gradlew clean build :app:assembleDebugAndroidTest --no-daemon --console=plain`.
  **BUILD SUCCESSFUL in 3m 23s**, 162 actionable tasks (161 executed, one up-to-date).
  Debug: **19 passed**. Release: **18 passed**, one expected debug-only skip.
  Zero test failures/errors; lint zero errors and 18 existing version notices.
  Both app variants and Android-test APK compile. Device UI tests remain unrun.
- Verified the only exported schema is `1.json`, containing metadata and all five
  catalog tables. Next milestone remains M2 — offline POS and checkout.

## Application identity follow-up — 2026-11-13

- Changed applicationId and namespace to `com.dauntehnusantara.pos` in
  `app/build.gradle.kts`.
- Moved main, JVM-test and Android-test Kotlin packages/imports to
  `com/dauntehnusantara/pos`; moved the Room schema export to
  `app/schemas/com.dauntehnusantara.pos.core.database.PosDatabase/1.json`.
- Updated the README package command and decision D018. Relative manifest class
  names now resolve against the new namespace. No migrations or data reset added.
- Ran with Android Studio JBR 21:
  `./gradlew clean build :app:assembleDebugAndroidTest --no-daemon --console=plain`.
  **BUILD SUCCESSFUL in 3m 9s**, 162 actionable tasks (161 executed, one up-to-date).
  Debug: 19 passed; release: 18 passed and one expected debug-only skip. No test
  failures/errors. Lint: zero errors, 18 existing version notices. Android-test APK
  compilation passed; on-device tests remain unrun.
- Verified debug APK metadata, merged application/activity names and provider
  authorities use `com.dauntehnusantara.pos`; no old package references remain in
  application/test source, Gradle config, current schema exports or README.
  Historical documentation retains the prior identity and is superseded by D018.
- Next milestone remains M2 — offline POS and checkout.

## M2 — Offline POS & checkout — 2026-11-13

Scope: a debug-only local cashier flow, durable cart, cash/manual QRIS, immutable
receipts and atomic sale/outbox persistence. Authentication, cloud submission,
server schema and production authorization remain M3/M4 work.

### Changed files and decisions

- `feature/checkout/domain/{Checkout,CheckoutUseCases}.kt`: immutable line/modifier
  snapshots, checked integer IDR calculations, quantity/line limits, cash/change,
  manual QRIS validation, catalog conflicts and repository/use case contracts.
- `feature/checkout/data/{RoomCheckoutRepository,CheckoutJson}.kt`: durable draft
  edits, revision checks, stable UUID/request identity, transactional completion,
  immutable local payloads, matching-retry receipt lookup and recovery/history.
- `core/database/{SaleEntities,CheckoutDao,SaleConstraints}.kt`, `PosDatabase.kt`,
  `CatalogConstraints.kt`: session/sale/items/modifiers/payment/outbox tables,
  uniqueness/FKs/validation/append-only triggers. Schema remains initial v1 with
  no migrations and no destructive fallback. No development app data cleared.
- `app/schemas/com.dauntehnusantara.pos.core.database.PosDatabase/1.json`:
  regenerated current initial schema. Prior dev installs need manual reset.
- `feature/pos/presentation/{PosViewModel,PosScreen}.kt`: StateFlow, serialized
  UI actions, loading/error/retry/conflict/busy states, product/modifier UI,
  sticky phone cart sheet, persistent expanded cart, checkout and receipt/history.
- `core/designsystem/Formatting.kt`: shared integer IDR and Jakarta-time display.
- `MainActivity.kt`, `core/navigation/PosApp.kt`, `core/di/StorageModule.kt`,
  `res/values/strings.xml`: wire Kasir/local history to checkout via Hilt, retaining
  catalog/settings and other placeholders. Keep application ID/namespace
  `com.dauntehnusantara.pos`.
- Catalog data mapping is shared internally with checkout; catalog presentation
  uses the shared currency formatter. No composable accesses a DAO.
- `CheckoutRulesTest.kt`, `CheckoutRepositoryTest.kt`, Android `PosScreenTest.kt`:
  calculations, recovery, atomicity/idempotency/conflicts and UI payment controls.
- README, data model, test plan, decisions D019–D025 and this progress log updated.
  Runtime library/toolchain versions remain unchanged.

### Commands and evidence

All Gradle commands use Android Studio JBR 21.0.10 via
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

- Read AGENTS and required product/architecture/data/security/sync/UX/milestone
  docs, implementation prompt and prior decisions; inspected current source.
  SDK 36 remains installed; default shell Java is still Java 8.
- Used the installed codebase-memory skill. The old index reported renamed Kotlin
  paths as untracked; read those paths and refreshed the index. Targeted graph and
  coverage checks then matched the current database/DI/navigation/catalog files.
- `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`: **passed** for
  the checkout data foundation and again for the cashier/UI integration.
- `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 4m 15s**. Initial full integration tests and Android-test
  compilation passed. A transient Gradle client socket timeout was logged but did
  not fail the build. Subsequent UI/error/accessibility and schema refinements were
  included in the final clean verification below.
- Final `./gradlew clean build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 2m 47s**, 162 actionable tasks (161 executed, one up-to-date).
  Debug: **37 passed**, zero failures/errors/skips. Release: **28 passed**, zero
  failures/errors and **9 expected demo-only skips** (8 checkout demo tests and the
  catalog seed fault test); release repository guards were executed and passed.
  Lint: **zero errors, 18 existing version notices**. Both app APKs and the
  instrumentation-test APK compiled.
- Verified the sole exported schema remains version 1 and contains all 12 tables;
  no migration artifacts or dependency version changes were introduced.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`,
  `app/build/reports/lint-results-debug.html`.
  Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `adb devices`: no attached devices. SDK emulator executable absent. Native
  Compose tests are compiled only; phone/tablet rendering, actual force-stop/
  process-death, OEM lifecycle and disk-full acceptance have not been executed.
- Existing incomplete NDK 27.0.12077973 logs missing source.properties and packages
  dependency native libraries without symbol stripping. This is an existing
  environment limitation, not a checkout compilation failure.

### Scope and remaining limitations

- Demo outlet/cashier UUIDs and local device ID are explicitly not authentication
  or offline authorization. Release checkout is guarded until M3 enrollment.
- DEMO_SALE/PENDING_DEMO operations are local development data and must never be
  uploaded as real sales. No cloud sync, durable transport, server acceptance,
  revocation, restore guarantee or production QRIS verification is claimed.
- Local request hashing/idempotency is not a server payload-validation contract.
  Server idempotency and price policy must be implemented and tested in M4.
- No discounts/tax editing, split payment, void/refund, receipt numbering policy,
  inventory, printing or role-aware reporting is introduced.
- Manually run the M2 device acceptance checklist in TEST_PLAN.md on compact,
  medium/split screen, expanded, rotated, dark, large-font and screen-reader setups.

### Exact next milestone

**M3 — Supabase foundation & security**: reviewed server SQL schema/RLS/function
policies and tests, invitation/login, memberships, role/outlet-specific navigation,
secure device context and bounded offline authorization. Define the production
price/offline/QRIS/enrollment decisions before enabling real cashier use. Do not
implement M4 push transport in the same milestone.

## M3 — Supabase foundation & security — 2026-11-13

Implemented locally; hosted Auth/SMTP/REST and native-device acceptance remain
pending configuration and hardware. No migration or deployment was performed.

### Files changed

- `supabase/schema.sql`: fresh initial PostgreSQL schema with 19 exposed RLS tables,
  private scoped helpers, explicit grants/default-privilege hardening, immutable
  server sales, invitation/acceptance, role-bound device approval/enrollment and
  one-UTC-calendar-month lease RPCs. No authenticated direct table writes.
- `supabase/functions/invite-staff/{handler,index}.ts`: verified caller and scoped
  SQL intent before server-only Auth Admin invite; bounded input, timeout/error/
  rate/conflict handling, no token/email/provider-error logging.
- `supabase/{config.toml,bootstrap-owner.sql.example,templates/invite.html,README.md}`:
  development Auth settings, trusted bootstrap, one-time TokenHash activation and
  setup/live-acceptance instructions. No actual credentials or real IDs provided.
- `supabase/{package.json,package-lock.json,tests/*.test.mjs}`: pinned test-only
  PGlite 0.5.16 PostgreSQL/RLS and mocked Edge HTTP tests using installed Node 22.17.1.
- `feature/auth/domain/Auth.kt`: typed roles/context, lease/clock rules, repository
  and use cases, role-navigation and invitation capabilities. Maximum offline period
  chosen by user: one calendar month, capped at 31 days without client extension.
- `feature/auth/data/{AuthApi,AuthCache,AuthRepositoryImpl}.kt`: Auth REST/RPC adapter,
  project-bound encrypted-cache contents, token rotation, resumable password setup,
  first-online outlet enrollment, approval, offline/denial/reconnection state machine.
- `feature/auth/presentation/{AuthViewModel,AuthScreen}.kt`: login/activation, outlet
  selection, device approval/UUID matching, role-specific phone/rail portal, OS
  device-credential offline unlock, error/retry, periodic/reconnection validation
  and explicit debug-only entry to the isolated M2 demo.
- `core/common/{AccessClock,NetworkAvailability}.kt`, `core/network/{BackendConfig,
  JsonHttpClient,AndroidNetworkAvailability}.kt`, `core/security/KeystoreSecretStore.kt`:
  monotonic/boot/secure-lock observations, validated connectivity, HTTPS-only public
  config, no redirects/caching, bounded sensitive responses and atomic Keystore
  AES-GCM no-backup storage. Malformed authorization is not an offline fallback.
- `MainActivity.kt`, `core/di/StorageModule.kt`, manifest, strings, `app/build.gradle.kts`,
  `local.properties.example`, `.gitignore`: integration and public-only configuration
  with a build guard against secret/service-role keys. Android library versions and
  initial Room v1/12-table schema unchanged. No app data reset.
- JVM `OfflineLeaseTest.kt`, `AuthRepositoryTest.kt`; native `AuthScreenTest.kt`:
  clock/device/role bounds, cache/refresh/activation/approval/denial/reconnection and
  role UI tests. Existing M0–M2 coverage retained.
- README, implementation plan, product requirements, Auth/security, data model,
  test plan, decisions D026–D034 and this report updated.

### Commands actually executed and outcomes

- Read required docs, bootstrap/iteration policy, existing source and toolchain.
  Used the installed codebase-memory skill: refreshed the current project index,
  queried integration symbols and checked relevant path coverage; source was read.
- `npm view @electric-sql/pglite version`: verified 0.5.16, then
  `npm install --prefix supabase --ignore-scripts`: installed/locked the test-only
  dependency. No runtime Android dependency added.
- `npm test --prefix supabase`: final **28 passed, zero failed/skipped**. Actual
  PostgreSQL role/RLS execution covers own/nested sales, outlet/brand separation,
  unauthorized writes/creation, disabled profiles/memberships/brands/devices,
  invitation permissions/verified email/expiry/rate limits, device proof/approval,
  role-change reapproval, calendar-month bounds and scoped change-feed reads.
  Edge tests use mocked HTTP; no emails were sent. Node logs its expected
  experimental TypeScript-stripping notice.
- Gradle uses Android Studio JBR 21 via
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`: passed after fixing
  the initial Properties import (Gradle's java extension shadows a package name).
- `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  integrated M3 passed, then a clean run caught a stale AccessClock DI import after
  moving the abstraction into core. Corrected it; subsequent full builds passed.
  Final `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 2m 3s**, 161 actionable tasks (35 executed, 126 up-to-date).
  Debug: **55 passed**, no failures/errors/skips. Release: **46 passed**, no
  failures/errors, **9 expected demo-only skips**. Lint: **zero errors, 18 existing
  version notices**. Both app variants and all native-test sources/APK compile;
  native tests were not run. The preceding role-response integration build also
  passed in 2m 16s.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`,
  `app/build/reports/lint-results-debug.html`.
  Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
  Verified Room remains schema v1 with the same 12 tables; no Room migration added.
- Negative configuration check:
  `./gradlew help -PSUPABASE_PUBLIC_KEY=sb_secret_test_fixture --no-daemon --console=plain`:
  **correctly rejected before tasks/packaging** with the redacted public-key-only
  guard message. This is an expected failure test, not an unresolved build error.
- No Supabase CLI, Docker, Deno runtime or connected Android device/emulator is
  available. Supabase Auth REST contracts were checked against official sources;
  live GoTrue/PostgREST/SMTP, Deno hosting/config startup, hardware Keystore,
  credential prompt and real lifecycle/network acceptance were not executed.
  The existing incomplete NDK installation remains a release-hardening limitation.

### Decisions, practical limits and next milestone

- User selected up to one calendar month offline; disconnected revocation can be
  delayed to the next successful server check or expiry. Reboot/rollback/missing
  boot data or insecure OS lock requires online validation. No offline renewal.
- Private cache authority is not hardware attestation/root resistance. Generic Data
  API RLS is live user/outlet based; M4 must validate current approved device proof
  before accepting sales. Auth logout alone does not invalidate every issued JWT.
- Catalog/sales remain read-only through the authenticated backend API. Real checkout
  and synced catalog are not enabled merely by login; M2 DEMO_SALE rows stay isolated
  and must never be uploaded. No real credentials/accounts/roster are invented.
- Follow `supabase/README.md` to configure/review a fresh development project and
  run the listed live checks. No production schema application, deployment, account
  provisioning or email sending was authorized or performed.
- **Exact next milestone: M4 — idempotent push synchronization**, after live M3 setup
  validation and production price/device/QRIS decisions. Implement vetted atomic
  submit_sale, stable operation/hash dedupe, current membership/device checks,
  WorkManager retries/status and ack-loss/duplicate tests. Do not combine M5 pull.
