# Implementation progress

- [x] M0 Toolchain & skeleton
- [x] M1 Local schema & catalog
- [x] M2 POS & checkout offline
- [x] M3 Supabase foundation & security (local implementation; live acceptance pending)
- [x] M4 Push sync (local implementation; live acceptance pending)
- [x] M5 Pull sync & multi-device (local implementation; live acceptance pending)
- [x] M6 Reports, staff, recovery (local implementation/rehearsal; hosted acceptance pending)
- [ ] M7 Release hardening

Update after each milestone with evidence of build/tests, blockers and next steps.

Current onboarding: manually provisioned Supabase Auth accounts and outlet assignments.
See `docs/MANUAL_STAFF_PROVISIONING.md`; earlier invitation implementation below is
historical and superseded by the manual provisioning revision at the end.

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


## M4 implementation — 2026-11-13

Scope: idempotent authenticated sale push, durable retry/status, strict price review.
M5 pull and real cashier/catalog integration remain separate. No live deployment,
credentials, data reset, business repricing or migration was performed.

### Changed files and behavior

- `supabase/schema.sql`: vetted `submit_sale(text,text)` RPC in the current initial
  schema; current membership/device/role proof, server canonical hash, transactional
  operation dedupe, strict catalog/modifier/payment/IDR checks, append-only graph,
  one ordered SALE feed append, review audit and explicit execution privileges.
  Non-owner re-enrollment now rotates proof and resets approval.
- `feature/sync/domain/PushSync.kt`, `data/RoomSyncRepository.kt`, `data/SyncWorker.kt`:
  scoped authenticated producer atomically stores sale graph and immutable outbox;
  worker persists attempts/backoff before IO, validates acknowledgement, recovers
  cancellation/ack loss, pauses auth errors and quarantines review/conflicts.
  KEEP work + periodic/startup sweep; explicit Retry replaces backoff-delayed work.
- `core/database/{SaleEntities,CheckoutDao,SaleConstraints}.kt`, exported `1.json`:
  due time/error/ack fields, scoped queue queries and immutable terminal transitions.
  Room remains **version 1, 12 tables**. No upgrade path or destructive fallback.
- Auth data/domain/DI: privately restore/refresh matching encrypted credentials,
  validate exact payload acknowledgement SHA-256, recover original public device ID
  after logout without retaining tokens/proof, require reapproval on re-enrollment.
  Invalid refresh grants pause for re-login instead of permanently reviewing sales.
- `PosApplication.kt`, manifest: Hilt-injected custom WorkerFactory and startup sweep;
  remove only WorkManager's default initializer. Reuse existing pinned dependencies.
- `feature/sync/presentation/SyncStatusPanel.kt`, auth Settings/resources/ViewModel:
  scoped loading/empty/error/retry/review/accepted counts, no demo upload or cloud
  acceptance claim. Phone/tablet use the existing adaptive portal and wrapping text.
- `PushSyncTest.kt`, extended `AuthRepositoryTest.kt`, native `SyncStatusTest.kt` and
  updated `AuthScreenTest.kt`, `supabase/tests/push.test.mjs`: persistence, ack loss,
  cancellation, hashing, auth, price review, rollback and UI acceptance sources.
- README, decisions D035–D040, sync/auth/data docs, Supabase README and M4_TEST_PLAN:
  concrete contract, operational boundaries, manual/live acceptance instructions.

### Decisions and limits

- User selected reject changed prices for review. Original completed local receipts
  stay unchanged; reviewed rows cannot be blindly retried or silently reassigned.
- M2 DEMO_SALE remains local. Real cashier entry remains gated until M5 provides
  synced catalog. The authenticated producer is implemented/tested, not exposed as
  an empty or fake menu. No M5 pull/cursor/Realtime implementation is included.
- The server's one-calendar-month admission window is not device attestation or
  proof of checkout time. Local M3 offline lease enforcement remains necessary;
  server current authorization wins on reconnect. Revoked-device recovery needs
  an explicitly authorized future workflow; retained receipts are never discarded.
- PostgreSQL WASM tests execute real SQL, but single-instance execution does not
  prove hosted concurrent-session behavior. Live JWT/PostgREST/Keystore/background
  lifecycle, screenshots/visual acceptance and native UI tests remain unverified.

### Verification

- `npm test --prefix supabase`: **38 passed**, zero failures/skips (including 10
  new push SQL cases). Executed actual current initial schema; no live database used.
- With Android Studio JBR 21 `JAVA_HOME`, final source verification:
  `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 2m 21s**, 161 tasks (44 executed, 117 up-to-date).
  **67 debug passed**; **58 release passed + 9 expected demo-only skips**; zero
  failures/errors. Includes eight push persistence tests and four new auth push tests.
- Lint identified a missing explicit non-exported attribute on the Startup provider;
  added `android:exported="false"`. Final rerun of the same build command:
  **BUILD SUCCESSFUL in 55s**, 161 tasks (10 executed, 151 up-to-date).
  **Zero lint errors; 18 existing toolchain/dependency-version notices**.
- Intermediate compilation failures (updated test constructor and new UI test/source
  ordering) were resolved before the successful full builds; no failed check remains.
- `git diff --check`: passed. Inspected merged manifest: WorkManager default initializer
  absent, other Startup initialization retained. Initial Room export: v1, 12 tables,
  three new outbox metadata fields. Runtime versions, namespace and app ID unchanged.
- `adb devices` initially failed to bind its listener inside the sandbox; rerun with
  permitted host access succeeded and listed **no connected devices**. Therefore native
  UI tests were compiled into the APK, not executed. No emulator/live backend checks.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`,
  `app/build/reports/lint-results-debug.html`; APK:
  `app/build/outputs/apk/debug/app-debug.apk`.
- Existing incomplete NDK installation remains a release-symbol/toolchain limitation;
  no new build blocker. No migrations, destructive fallback, app-data reset, live
  Supabase application/deployment or real emails were performed.

**Exact next milestone: M5 — scoped catalog/sales pull and multi-device convergence**.
Implement durable ordered cursors and atomic page application, supply the authenticated
catalog/checkout UI, and test two devices converging after offline operation. Run M3/M4
live acceptance against a fresh configured development backend before production use.
Reviewed/revoked-device reconciliation remains explicit future recovery work.


## M5 implementation — 2026-11-13

Scope: scoped catalog/sales pull, durable atomic cursors and authenticated offline
cashier integration. M4 changes in the working tree were preserved. No deployment,
real accounts/menu provisioning, migration or app-data reset was performed.

### Changed files and behavior

- `supabase/schema.sql`: automatic catalog INSERT/UPDATE feed events, server version
  increments and full tombstones; physical deletion denied. Approved-device/live-role
  `pull_changes` with ordered record/byte-bounded pages and stable committed high-water
  boundaries across hidden gaps. Reuses the M4 feed lock; catalog direct writes remain
  denied. Trusted server catalog import/updates are required until a mobile editor.
- `core/database/PullEntities.kt`, `PosDatabase.kt`, `CatalogDao.kt`, `CheckoutDao.kt`,
  current exported `1.json`: cursor/remote receipt tables, scoped sale queries and
  assignment application. **Initial schema v1, 14 tables**; no destructive fallback.
- `feature/sync/domain/PullSync.kt`, `data/{PullJson,RoomPullRepository}.kt`:
  project/brand/outlet/user/device/role cursors; full page+data+cursor transaction,
  version-aware replay, separate immutable downloaded receipts, matching durable
  acknowledgement echoes and conflicting identity protection. Partial/reversed/
  unknown/foreign pages preserve the previous cursor; errors persist for retry.
- Auth API/repository, worker and DI: private token refresh/device proof for each
  vetted pull request; live denial clears offline authority. Push then bounded pull
  per work pass; same connected periodic/foreground/manual recovery. HTTP response
  limit 1 MiB accommodates bounded 512 KiB pages and legitimate large receipts.
- `feature/checkout/{domain/AuthenticatedCheckoutFactory,data/AuthenticatedCheckoutRepository}.kt`:
  genuine scope-specific persisted drafts, first-catch-up gate, stable command dedupe,
  and sale graph/outbox/consumed draft in one transaction. Auth validation precedes
  Room transaction acquisition to avoid lock inversion. No debug identity conversion.
- `feature/pos/presentation/{AuthenticatedPosViewModel,AuthenticatedPosRoute}.kt`,
  existing `PosScreen.kt`, `PosViewModel.kt`, AuthRoot/MainActivity and resources:
  real cashier menu/modifiers/cash/manual QRIS/receipt/history through the existing
  phone/tablet adaptive UI. Real receipt/empty/pending labels exclude demo claims.
  Manager/owner catalog browsing is read-only; staff/reports remain the M6 scope.
- Sync Settings panel: push status plus first-pull/error/last-complete-catch-up status,
  manual retries and lifecycle cleanup. Cached last good catalog remains usable
  within M3 offline authority while later refresh fails; first bootstrap is gated.
- `PullSyncTest.kt`, extended `AuthRepositoryTest.kt`, `PosScreenTest.kt`, backend
  push/RLS tests: two distinct Room databases with shared fake transport, interrupted
  pages, restart/ack loss, atomic rollback, tombstones, role backfill/demotion,
  backend binding, immutable sale identity conflicts and real receipt/UI sources.
  Backend tests execute the actual initial SQL in PostgreSQL WASM.
- README, decisions D041–D046, sync/auth/data docs, Supabase README and M5_TEST_PLAN:
  current contract, architecture choices and exact remaining live acceptance.

### Decisions and limitations

- Cashiers see own sales across devices; managers/owners can pull the selected outlet.
  Promotion starts its role cursor to backfill older permitted sales; demotion never
  queries the broader cached history. Catalog replay cannot roll back newer versions.
- No Realtime dependency: durable pull plus startup/periodic/network/foreground/manual
  nudges are sufficient. OS timing is best effort; no exactly-once transport claim.
- Reviewed sales/old snapshots and unsent local work remain immutable. Pull does not
  reprice, delete, silently reassign or replace them. Backend config mismatch fails
  closed and requires reviewed export/reset/rebootstrap rather than cache mixing.
- Catalog writes in the real app remain deliberately read-only for this pull milestone;
  a vetted role-authorized mobile catalog editor is outstanding MVP work. Existing
  local demo editing remains isolated. This limitation is visible in README/decisions.
- Fresh development SQL must be installed before importing catalog rows. No production
  backfill, feed pruning, restore/reseed cursor recovery or concurrent hosted-session
  verification was implemented/claimed. M6 recovery and later hardening must address
  retention/rebootstrap and live concurrent locking acceptance explicitly.

### Verification

- `npm test --prefix supabase`: **43 passed**, zero failures/skips. Includes actual
  catalog triggers/version/tombstone rollback, same-user device feeds, role denial,
  hidden-gap cursor progress and byte-bounded pages of large valid submitted receipts.
- Final with Android Studio JBR 21 `JAVA_HOME`:
  `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 2m 6s**, 161 tasks (40 executed, 121 up-to-date).
  **79 debug passed**, **70 release passed + 9 expected demo-only skips**, zero
  failures/errors. M5 adds ten Room/pull/convergence cases and two auth pull cases.
  Both app variants and instrumentation APK compile. Lint: **zero errors, 18 existing
  dependency/toolchain version notices**; no new lint warning remains.
- Intermediate checks: an implicit Compose width receiver compilation error was
  corrected by capturing expanded width explicitly. The first full test run caught
  an invalid empty-menu gate fixture and page-order preflight behavior; corrected
  both and reran full checks successfully. SQL test expectations were updated for
  automatically emitted catalog events/version increments; rollback fixtures now
  clear failed transactions correctly. No failed check remains unresolved.
- `adb devices`: success, **no connected devices**. Native UI tests are compiled,
  not executed. No emulator screenshots, physical offline/background/Keystore tests,
  live GoTrue/PostgREST/JWT, real concurrent hosted sessions or SMTP were exercised.
- `git diff --check`: passed. Verified current Room export remains schema **v1 with
  14 tables**; no migration, fallback or user-app data reset. Package and namespace
  remain `com.dauntehnusantara.pos`; dependency versions unchanged.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`,
  `app/build/reports/lint-results-debug.html`; debug APK:
  `app/build/outputs/apk/debug/app-debug.apk`.
- Existing incomplete NDK installation remains a release-symbol/toolchain limitation;
  it does not block the verified builds. Live acceptance needs configured development
  Supabase and two actual devices; no production changes or real provisioning occurred.

**Exact next milestone: M6 — role-scoped reports, staff management and recovery**.
Use the existing scoped immutable sales/cache and server authorization; add report
calculations, staff disable/revoke flows, export and backup/restore rehearsal runbook.
Run M3–M5 live acceptance first on a fresh disposable backend. The authorized mobile
catalog editor remains an explicit MVP gap; do not enable real local-only editing.


## Manual staff provisioning revision — 2026-11-13

User-requested onboarding simplification only; M6 is not implemented by this iteration.
All preexisting M4/M5 working-tree changes and documentation files were preserved.

### Changed files and behavior

- Auth domain/API/repository/cache/ViewModel/UI/resources: ordinary email/password
  sign-in, preassigned context only; removed activation and invite capabilities.
  Staff panel explains administrator provisioning while retaining device approvals.
  No account/profile/membership creation on login. Completed old sessions retain
  enrollment; unfinished legacy activation sessions fail closed to fresh sign-in.
- `supabase/schema.sql`, `config.toml`: current initial server schema has **18 public
  application tables** with RLS, no staff invitation table/create/accept RPC/private
  invite helper or invitation function configuration. No live DROP/migration applied.
- Removed `supabase/functions/invite-staff/{handler,index}.ts`, `templates/invite.html`
  and obsolete `tests/invite.test.mjs`; they are retired executable/template assets,
  not documentation files. Node test command no longer needs TypeScript stripping.
- Added `supabase/provision-staff.sql.example`; updated owner bootstrap wording.
  Trusted SQL operator assigns confirmed Auth UUID/profile/outlet and records audit
  atomically. Same active assignment is idempotent; OWNER, silent role changes or
  reactivation, missing/unconfirmed user and foreign-brand attribution are rejected.
  Signed-in Android/owner/manager clients still cannot execute these table writes.
- Replaced invitation RLS tests with actual provisioning-script/access checks, and
  auth tests with manual-login/no-mutation plus completed/partial cache compatibility.
  Updated role navigation and native login/Staff tests to retain device approval.
- README, Supabase README, product/architecture/security/data/test/implementation
  documents and security-review prompt updated; decisions D047–D050 and new
  `docs/MANUAL_STAFF_PROVISIONING.md` describe current setup and operational limits.
  Historical progress/decisions remain available with explicit supersession.

### Commands and evidence

- `npm test --prefix supabase`: **36 passed**, zero failures/errors/skips. Tests
  execute actual initial SQL and configured copies of the provisioning example in
  isolated PGlite; retained RLS, lease/device, push/pull/ack-loss cases still pass.
  Removed seven obsolete mocked invitation-email handler tests; no coverage claim
  is made for features intentionally removed.
- With Android Studio JBR 21 `JAVA_HOME`:
  `./gradlew build :app:assembleDebugAndroidTest --no-daemon --console=plain`:
  **BUILD SUCCESSFUL in 2m 52s**, 161 tasks (64 executed, 97 up-to-date).
  **80 debug passed**, **71 release passed + 9 expected demo-only skips**; zero
  failures/errors. Lint: **zero errors, 18 existing toolchain/dependency notices**.
  Both app variants and native UI-test APK compile; no compilation fix was needed.
- `git diff --check`: passed. Inspected retired-flow references; only deliberate old
  cache rejection, absence tests and historical/supersession docs remain. No runtime
  invite/activation endpoint call, signup UI or account-creation endpoint was added.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`,
  `app/build/reports/lint-results-debug.html`; APK:
  `app/build/outputs/apk/debug/app-debug.apk`.
- Room remains **v1, 14 tables**, unchanged by this revision. Namespace/application
  ID stays `com.dauntehnusantara.pos`; dependency versions unchanged. No retained
  user-app data or queued sale was reset, reassigned or removed.

### Remaining limits and next milestone

- No hosted Supabase schema/deployment/account/credential/email was created, changed
  or deleted. If invitation functions/RPCs already exist in a hosted project, retire
  them through reviewed operational cleanup; current initial SQL is not an upgrade.
- Live Dashboard/manual creation, GoTrue/PostgREST and actual Android devices remain
  unverified. The last device check found none connected; native UI tests are compiled,
  not executed. Existing incomplete NDK remains a release-symbol limitation.
- Password provisioning/recovery is a trusted admin/Auth task. No forced initial
  password-change or password-reset UI was added. Device approval and the original
  one-calendar-month offline policy still apply to manually created users.
- **Exact next milestone: M6 — role-scoped reports, staff access management and
  recovery**, using manual account provisioning. The authorized mobile catalog editor
  remains an explicit MVP gap; do not enable real local-only editing.


## M6 implementation — 2026-11-13

Scope: role-scoped reports, online staff access/device controls, export and recovery
runbook/rehearsal. Manual account creation remains unchanged. No hosted deployment,
account change, live restore, app-data reset or destructive migration was performed.

### Changed files and behavior

- `supabase/schema.sql`: approved-device management helper; selected-outlet/brand
  report RPC with Jakarta date boundaries, accepted historical snapshots, exact
  integer-string totals and bounded payloads. Owner brand/one outlet, manager current
  assigned outlet, cashier denied. Staff/device reasoned version checks, disable+
  revocation, reenable without restoring devices, owner-only restore-to-pending and
  atomic audits. Owner-only latest 50 selected-outlet access audit entries.
- Initial server schema keeps **18 public RLS tables** and adds private.sync_generation.
  Membership/device versions support optimistic actions; private epoch plus amended
  pull contract prevents old cursors silently continuing after an approved restore.
  No new public account-creation/profile/role mutation API; provisioning stays manual.
- `feature/reports/{domain,data,presentation}`: validated Long totals/reconciliation,
  Jakarta range/filter choices, scope-keyed Room metadata report cache, accepted-only
  daily/outlet/top-20 product breakdowns, offline snapshot/error/empty/retry states,
  exact CSV quoting/formula escaping and frozen snapshot export through Android SAF.
  Phone metrics stack; expanded metrics share available width and wrap large values.
- `feature/staff/{domain,data,presentation}`: scoped online roster/actions, reasons and
  confirmation, expected-version conflict handling, current-role target rules, pending
  device restoration and readable owner audit. Staff data is not cached for offline
  management; existing device approval UI remains available.
- `feature/recovery/{domain,data,presentation}`: authorized scoped original real sale/
  outbox/draft/command export, including retained SYNCED rows. No credentials/demo/
  other-user data. Checksummed versioned JSON, integrity/scope validation, 16 MiB
  explicit failure bound and no automatic import/replay/reset. Picker source scopes
  and report snapshots are captured before opening external document providers.
- Auth API/repository/DI/MainActivity/AuthRoot: private credential refresh/proof for
  new management RPCs, role/cached-read authorization, offline mutation denial,
  conflict vs denial handling and real Overview/Reports/Staff/Settings integration.
- Pull domain/API/JSON/Room/cursor/UI: persist serverEpoch atomically with page,
  reject HTTP 410/generation mismatch, retain previous data/cursor, gate new checkout
  and show explicit export/contact-owner/do-not-clear-data guidance. Room remains
  **initial v1, 14 tables**, with updated cursor column/export; no migrations/fallback.
- `supabase/rotate-feed-generation.sql.example`: trusted reviewed post-restore epoch
  comparison/rotation under feed lock plus audit. It is not run on a live database.
- `supabase/tests/management.test.mjs` and adjusted pull fixtures: actual report and
  access RLS/function behavior, dates/versions/audits, isolated dump/load and configured
  rotation-script replay/stale-version rejection. New JVM/Room/native UI sources in
  ManagementRulesTest, ReportCacheTest, existing AuthRepositoryTest/PullSyncTest,
  ManagementScreenTest and PosScreenTest cover the new behavioral boundaries.
- README, Supabase README, manual provisioning guide, decisions D051–D056, data/auth/
  sync/UX docs and new BACKUP_RESTORE_RUNBOOK/M6_TEST_PLAN describe current operation,
  source-of-truth distinctions and exact live/recovery limitations.

### Verification and restore evidence

- `npm test --prefix supabase`: **45 passed**, zero failures/errors/skips. Includes
  nine new management/restore cases plus retained provisioning/RLS/push/pull cases.
- Actual isolated rehearsal: dump/load PGlite's data directory into a fresh instance;
  restored sale/item/payment/operation/audit counts and report totals match; replayed
  operation retains hash and creates exactly one sale; restored RLS denies other
  outlets. Configured generation-rotation script writes one audit, rejects stale
  repeat rotation and blocks old epochs while fresh bootstrap works.
  TAP diagnostic: `backup_bytes=4895003, unique_replayed_sale=1,
  restored_graph_matches=true, role_scope_checked=true, old_epoch_blocked=true`.
  This is test-only PostgreSQL WASM with test Auth, not hosted Supabase recovery.
- Android source compile initially caught a composable function-type syntax error;
  corrected it. Initial full lint hit stale/missing generated Hilt source. No lint
  suppression was added. Clean rebuild:
  `./gradlew clean build :app:assembleDebugAndroidTest --no-daemon --no-parallel --max-workers=2 --console=plain`:
  **BUILD SUCCESSFUL in 4m 17s**, 162 tasks (161 executed, 1 up-to-date).
- After explicit recovery guidance/audit labels:
  `./gradlew build :app:assembleDebugAndroidTest --no-daemon --no-parallel --max-workers=2 --console=plain`:
  **BUILD SUCCESSFUL in 3m 29s**, 161 tasks (68 executed, 93 up-to-date).
  **93 debug passed**, **84 release passed + 9 expected demo-only skips**, zero
  failures/errors. Lint: **zero errors, 18 existing toolchain/dependency notices**.
  Four new native UI cases compile but were not run on a device.
- Final responsive report-layout verification, same full build command:
  **BUILD SUCCESSFUL in 2m 39s**, 161 tasks (37 executed, 124 up-to-date).
  Final results remain **93 debug passed, 84 release passed + 9 expected skips**,
  zero failures/errors; lint zero errors and 18 existing notices. No required local
  build/test check remains failed. Native test APK compiled; actual device checks pending.
- `adb devices`: succeeded; **no connected devices**. Native document picker, actual
  file-provider writes/cancel/process recreation, physical offline clocks/Keystore,
  screenshots/visual layout and live multi-device/network/concurrent hosted-session
  acceptance remain unexecuted. Existing incomplete NDK limits release symbol stripping.
- `git diff --check`: passed. Schema remains Room v1/14 tables; package/namespace
  remains `com.dauntehnusantara.pos`; pinned dependency versions unchanged. Existing
  M4/M5/manual-provisioning work and documentation preserved; no user data cleared.
- Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
  `app/build/reports/tests/testReleaseUnitTest/index.html`,
  `app/build/reports/lint-results-debug.html`; APK:
  `app/build/outputs/apk/debug/app-debug.apk`.

### Practical limits and next milestone

- Reports exclude pending/review device sales and are sale receipts, not profit,
  taxation or bank-verified QRIS. No refund/void policy was invented.
- Hosted backups/retention/RPO/RTO, actual hosted Auth/Storage/PITR restore and a
  reviewed production recovery harness remain unconfigured/unverified. The archive
  is not encrypted/signed/full backup and has no automatic import UI. Operators must
  explicitly authorize/reconcile restore/rebootstrap; app never deletes retained work.
- Staff account creation, passwords, role assignment and global-profile reactivation
  remain trusted administrator tasks. Large roster pagination and actual document-
  provider/accessibility/performance acceptance need hardening.
- **Exact next milestone: M7 — release hardening**, with live M3–M6 acceptance,
  security/accessibility/performance/restore validation and reviewed release controls.
  The authorized mobile catalog editor remains an explicit MVP gap that must be
  resolved or explicitly accepted before production; no local-only real editor enabled.

## M7 — local release hardening (acceptance in progress)

### Implemented

- Added `EditCatalog`, `CatalogCommandJson`, `CatalogEditorViewModel/Screen`, Hilt
  binding and real catalog routing in MainActivity. Owner/Manager online forms cover
  all four catalog records, archiving and product assignments. Shared brand impact,
  offline, loading, empty, invalid, conflict, retry and pull-wait states are explicit.
- Added `save_catalog_record` and a private operation ledger to the initial server
  schema. Live approved-device/role/epoch/brand/version checks, immutable option
  parent and required-option validation guard writes. Catalog/link tombstones,
  operation dedupe, audit and feed are atomic. Exact retry does not allocate new feed.
- Added JSON size/depth checks before recursive parsing, exact legacy public-key role
  validation, enrollment identity serialization, cleartext prohibition, R8/resource
  shrinking and worker-name retention. No dependency upgrades or Room migrations.
- Added backend catalog regression tests and JVM/native editor hardening cases.
  README, Supabase contract, auth/sync documentation, D057–D060, security review and
  release checklist record final behavior and the remaining acceptance requirements.

### Verification so far

- `npm test --prefix supabase`: **48 passed**, zero failures/errors/skips. A new test
  initially used `name_snapshot`; corrected to existing `product_name_snapshot`.
- `./gradlew build :app:assembleDebugAndroidTest :app:bundleRelease --no-daemon
  --no-parallel --max-workers=2 --console=plain`: **BUILD SUCCESSFUL in 1h 29m 30s**,
  168 tasks (78 executed, 90 up-to-date). Initial R8 execution was slow locally.
  That run: 93 debug passed, 84 release passed + 9 expected demo-only skips.
- Optimized unsigned APK and AAB produced. Manifest package is
  `com.dauntehnusantara.pos`, min API 26/target 36, backups/cleartext false and no
  debuggable flag. Neither artifact contains signing signature files.
- `zipalign -c -P 16 4 <release APK>` passed. All eight packaged native libraries'
  PT_LOAD alignments are 16,384 bytes. Mapping retains SyncWorker's persisted name.
  This is artifact inspection, not runtime validation on a 16 KB device.
- ADB initially had no devices. The user then connected Infinix X6873 over wireless
  ADB: API 36, 1224×2720 physical display, 4,096-byte page size. Final build plus
  new regression tests and `connectedDebugAndroidTest` now running against that phone.

M7 remains open for production acceptance; see M7_RELEASE_CHECKLIST.md. No live
Supabase deployment, signed distribution, actual hosted restore or data reset.

### Final regression and release artifact checks

- Added regressions: **99 debug passed**, **90 release passed + 9 expected demo-only
  skips**, zero failures/errors. Lint: zero errors, 18 existing version notices.
- Combined build/device run completed build/test/bundle tasks but failed at
  `connectedDebugAndroidTest`: the phone went offline during APK installation,
  then disappeared. Device report records **zero executed tests**, not a UI failure.
  Reconnected via the discovered same IP/port; targeted device retry in progress.
- Separate `./gradlew build :app:bundleRelease --no-daemon --no-parallel
  --max-workers=2 --console=plain`: **BUILD SUCCESSFUL in 19s**, 136 tasks
  (1 executed, 135 up-to-date).
- Fake-key Gradle `help` regression: escaped `service_role` plus unrelated `anon`
  note is rejected with the required guard; root `role=anon` is accepted. Neither
  fixture is a real key, and no fixture was saved in project configuration.
- Final unsigned optimized APK: 1,750,702 bytes, SHA-256
  `e7bf7768020d0af7a2ce5033b185c9581d2867b0f2e2e6398041c0a7bb365040`.
  Final unsigned AAB: 4,060,842 bytes, SHA-256
  `c895bbda756404ce420a8d3df91e16a3bc242da07ea07df6d98f91e0d75a1712`.
  These hashes identify local unsigned artifacts, not a signed distribution.
- Final APK 16 KB ZIP alignment and `git diff --check`: passed.

### Physical-device checkout regression

The first connected retry executed all 21 tests: 19 passed, two checkout tests
failed because their save-button taps were outside the visible scrolled viewport
(after showing the cash keyboard or the longer QRIS confirmation). Production form
already uses vertical scrolling and IME padding. Tests now scroll to the button,
assert it is displayed/enabled, then tap; disabled-busy/payment assertions remain.
Full device suite rerun in progress. No checkout implementation change or app data
clear was needed for these test interactions.

### Final connected-device result

`ANDROID_SERIAL=192.168.1.13:42999 ./gradlew :app:connectedDebugAndroidTest
--no-daemon --no-parallel --max-workers=2 --console=plain` with Android Studio JBR:
**BUILD SUCCESSFUL in 1m 26s**, 78 tasks (7 executed, 71 up-to-date).
**21 instrumentation tests passed**, zero failures/errors/skips on Infinix X6873,
API 36, 4 KB pages. This resolves the earlier connection failure and the two
viewport-dependent checkout test failures. The full native suite covers auth/role
screens, compact/expanded navigation, catalog browsing/editing, cash/manual QRIS
confirmation and repeat-submit gating, reports/staff UI, sync/recovery labels and
scratch-database reopen persistence. These use test fixtures and do not establish
live Supabase or signed/minified device acceptance.

Final evidence: 48 backend tests passed; 99 debug JVM tests passed; 90 release JVM
tests passed plus 9 expected skips; 21 physical-device tests passed; lint zero
errors/18 existing notices; optimized APK/AAB build and alignment checks passed.
Native report: `app/build/reports/androidTests/connected/debug/index.html`.
No required local check remains failed. All documentation retained; package/namespace
and Room initial v1 are preserved. No production data was cleared or deployed.

**Exact next step: finish M7 release acceptance**, using M7_RELEASE_CHECKLIST.md:
live Supabase Auth/RLS/concurrent-device and recovery rehearsal, real tablet and
API 26/16 KB runtime/accessibility/performance checks, then externally configured
signing and a smoke test of the minified signed build. Local hardening is complete;
production readiness is not claimed. Staff provisioning remains manual without invites.

## Brand icon and green theme — 2026-11-14

Requested scope: apply the supplied brand image as the app icon and green as the
main theme color. Added `drawable-nodpi/brand_logo.png` (560×560, aspect-preserving
white padding), adaptive launcher XML/foreground and manifest regular/round icon
references. Updated `core/designsystem/Theme.kt` to consistent light/dark greens,
including secondary/tertiary colors and surfaces. Native day/night colors/styles
match startup backgrounds and accents. Kept all documentation and existing features.

Primary/label contrast checked with the standard relative-luminance calculation;
light primary/secondary/tertiary and dark primary pairs all exceed 4.5:1. Build and
lint checks are in progress. No device installation or launcher-mask runtime check
has been performed for this branding change. Next milestone remains the existing
M7 release acceptance; this is a scoped branding update, not a new business feature.

Branding verification completed: `./gradlew :app:assembleDebug :app:lintDebug
--no-daemon --no-parallel --max-workers=2 --console=plain` (Android Studio JBR)
**BUILD SUCCESSFUL in 1m 53s**, 54 tasks (25 executed, 29 up-to-date). Android
resources/theme compiled and debug APK generated. Lint passed; no unit or device
suite rerun for this asset/palette-only change. Measured label contrast: light
primary 6.05:1, secondary 6.38:1, tertiary 6.50:1; dark primary 7.75:1.
`git diff --check` passed. Device launcher appearance still requires a visual check.

Final resource cleanup removed the unused placeholder vector and redundant launcher
API folder (min SDK is already 26). The incremental resource linker initially failed
to find the moved mipmap. A clean rebuild resolved its stale generated state:
`./gradlew clean :app:assembleDebug :app:lintDebug --no-daemon --no-parallel
--max-workers=2 --console=plain` passed. APK inspection confirms the application
references the packaged adaptive launcher icon. Lint has zero errors; optional
monochrome/themed launcher artwork is not supplied because this change uses the
provided full-color logo. Existing dependency/toolchain notices remain. The existing
incomplete NDK still packages native libraries without stripping; it did not prevent
this debug build. No app data was cleared by cleaning build outputs.

Final clean command: **BUILD SUCCESSFUL in 2m 30s**, all 55 tasks executed;
lint **0 errors / 20 warnings** (version/toolchain notices and optional monochrome
launcher artwork). Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Transparent logo and white light-mode correction — 2026-11-14

The previous theme followed `isSystemInDarkTheme()`, so a phone in night mode opened
only the dark appearance. `DaunTehTheme` now defaults explicitly to light mode;
white backgrounds/surfaces/containers and disabled surface tint keep the main UI
white while preserving green actions. Explicit dark palette opt-in remains available
in the composable API. Native day/night startup colors/styles and MainActivity's
edge-to-edge system-bar styles now match the light UI, including dark system icons.

Used the imagegen skill's background-extraction workflow for the supplied logo and
packaged the selected RGBA PNG at 560×560. Verified an alpha channel exists and the
generated source's corner is fully transparent. Brand text, leaves and ring remain.
Build/lint and connected-device appearance verification are in progress. No Room
migration, app-data reset, credentials or business feature changes were needed.

Initial assembly passed, but lint caught four API compatibility errors from native
style attributes added for navigation-bar appearance (API 27) and force-dark (API
29). Removed those XML attributes; AndroidX SystemBarStyle handles navigation icon
compatibility and the decor force-dark opt-out is guarded with SDK >=29. No lint
suppression or minimum-SDK change. Adaptive launcher background now also uses
transparent rather than an explicit white plate; launchers can still impose their
own icon mask/backdrop. Final build/lint/install verification is running.

The corrected APK assembled, but the combined build/lint/install command stopped
at installDebug: wireless APK transfer failed with EOF and the phone disappeared
from both `adb devices` and mDNS discovery. No device smoke result is claimed.
Before the disconnect, `adb shell cmd uimode night` returned `Night mode: yes`,
confirming the previous dark appearance followed the device setting. Running the
final assembly/lint separately so connectivity cannot interrupt local verification.

Final independent verification:
`./gradlew :app:assembleDebug :app:lintDebug --no-daemon --no-parallel
--max-workers=2 --console=plain` with Android Studio JBR — **BUILD SUCCESSFUL in
2m 36s**, 54 tasks (18 executed, 36 up-to-date), lint **0 errors / 20 warnings**
(existing toolchain/version notices and optional monochrome launcher artwork).
`sips -g hasAlpha` confirms the packaged 560×560 logo retains alpha transparency.
`git diff --check` passed. No unit/instrumentation suite rerun for this visual update.

ADB later rediscovered the phone on a different port, and a reconnect was attempted.
Updated device installation/visual confirmation is not yet verified; local APK is
ready at `app/build/outputs/apk/debug/app-debug.apk`. The user can install over the
existing debug app without a data reset. Next release milestone remains M7 acceptance;
this request changes branding/theme defaults only.

## White launcher logo in device dark mode — 2026-11-14

Added `res/drawable-night/ic_launcher_foreground.xml`: white SRC_IN bitmap tint
preserves the existing logo's alpha/letter shapes and transparent background. The
normal drawable remains full color. Android's night resource selection controls the
launcher foreground independently of the app's explicitly white Compose UI. No new
bitmap, dependency, business logic or database changes. Build/lint verification running.

Verification: `./gradlew :app:assembleDebug :app:lintDebug --no-daemon --no-parallel
--max-workers=2 --console=plain` with Android Studio JBR passed. Native bitmap tint
and SRC_IN alpha-mask semantics were checked against the installed SDK's
BitmapDrawable resource attributes. `git diff --check` passed. Device launcher
refresh/appearance has not been rechecked; no app data reset or business tests needed
for this drawable-only update. Next milestone remains M7 release acceptance.

## Product-photo implementation — 2026-11-14

### Changes and decisions

- Added feature/catalog/photos domain gateways/repository/processor, bounded native
  image preparation, HTTPS Storage transfer and scoped atomic 32 MiB LRU cache.
  Photo Picker selects a source; center-crop/EXIF orientation/new JPEG encoding
  produces 512×512 photos <=200 KiB, omitting original GPS/EXIF. No new libraries.
- Added photo loading/placeholders/retry in POS and catalog cards; saved product rows
  expose Add/Replace/Remove. Editor now uses a LazyColumn to keep thumbnail memory
  limited to visible rows. Failed disk cache writes never prevent upload publication
  or displaying downloaded bytes. Binary transfer runs outside the auth mutex.
- Picker launcher lives at activity scope; VM retains selected source context through
  foreground revalidation and retains immutable pending command/path/bytes across
  same-context transitions for retries. Other scopes cannot publish/read those bytes.
- Added optional `supabase/product-photos.sql`, to review/run once after base schema:
  private bounded JPEG bucket, approved Owner/Manager upload RLS, published-brand
  reads, photo table/private ledger and proof/role/epoch/version/idempotent publication.
  Photo row, operation, audit and feed are atomic. No client overwrite/delete policy.
- Photo-only revisions do not change product price versions or paid receipts. Room
  v1/14 tables and exported schema remain unchanged: photo references use existing
  catalog metadata within the existing pull/cursor transaction. No migration added.
- Added real SQL RLS/RPC tests, JVM codec/cache/scope/mutex/pull tests, native photo
  action/offline/retry test sources, PRODUCT_PHOTOS guide, model/auth/sync/plan docs,
  README setup links and decisions D067–D070. Added `.kotlin/` build-cache ignore.
  Existing branding/manual provisioning and all other work are preserved.

### Commands and executed evidence

- `npm test --prefix supabase`: **52 passed**, zero failures/errors/skips. Four new
  photo cases run the optional setup and actual policies/functions against an
  isolated Storage-schema fixture. They cover private bucket limits/config, role/
  brand/path/proof/epoch/version checks, immutable client objects, atomic publication,
  stable retries/audit/feed, removal and unchanged sale/product price snapshots.
  This is not a hosted Storage HTTP/byte-enforcement test.
- Initial Android compile/debug checks:
  `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
  :app:lintDebug --no-daemon --no-parallel --max-workers=2 --console=plain`:
  **BUILD SUCCESSFUL in 3m 36s**, 97 tasks (63 executed, 34 up-to-date).
- Full build after photo/picker/regression integration:
  `./gradlew build :app:assembleDebugAndroidTest --no-daemon --no-parallel
  --max-workers=2 --console=plain`: **BUILD SUCCESSFUL in 7m 24s**, 161 tasks
  (91 executed, 70 up-to-date).
- Final bounded-memory/cache-failure/EXIF regression build, same full command:
  **BUILD SUCCESSFUL in 5m 54s**, 161 tasks (65 executed, 96 up-to-date).
  **107 debug JVM passed; 98 release JVM passed + 9 expected demo-only skips**,
  zero failures/errors. New codec tests use native Robolectric graphics and cover
  center crop, physical EXIF rotation/GPS removal, invalid files and byte bounds.
  New cache tests verify repository recreation/offline reuse, account/role/project
  isolation, corruption retry and unavailable-disk behavior. A suspended upload test
  proves local checkout scope access is not held by binary IO. Pull test proves photo
  references survive DB reopen and photo removal without rewriting paid receipts.
  Decoder diagnostics for deliberately invalid fixture bytes are expected; no test fails.
- Native test APK compiled (23 UI/database test methods total), including two new
  photo-action cases. `adb devices -l`: no connected devices. These native cases were
  **not executed** in this iteration. No app data cleared or live images uploaded.
- JVM commands used Android Studio's bundled JBR via JAVA_HOME; package/namespace,
  pinned dependencies and initial Room schema remain unchanged.

### Limits and exact next step

Apply the reviewed optional photo setup in the development Supabase project and
install updated Android clients before the first photo event. Test real picker and
cloud providers, upload/replace/remove, HTTP size/MIME enforcement, lost ACK, two-device
convergence and offline cache/missing-photo checkout. Pictures load when displayed;
OS/LRU eviction can remove cached files. No interactive crop editor or durable photo
outbox; process loss requires refreshing before selecting again. Unreferenced/old
Storage objects are retained pending reviewed operator cleanup, never automatic
physical deletion. Existing M7 live/signed/tablet/performance/recovery gates remain open.

Final lint: **0 errors / 24 warnings**. Four photo-related notices recommend
AndroidX ExifInterface and KTX convenience APIs; this iteration deliberately uses
bounded framework image/EXIF processing without adding a library version. No lint
baseline or suppression was added. Remaining notices are existing toolchain/version
and optional monochrome icon recommendations. `git diff --exit-code --
app/schemas/com.dauntehnusantara.pos.core.database.PosDatabase/1.json` and final
`git diff --check` passed. Debug APK and minified unsigned release APK are generated;
no signing configuration, Storage service deployment or live account changes occurred.

## UI guidelines and visual reference — 2026-11-14

Scope: design specification and review artifacts, not an Android screen redesign.

- Added `docs/UI_GUIDELINES.md`: white/leaf-green direction, semantic color pairs,
  typography/price/total roles, Material Symbols Rounded target mappings, spacing,
  shapes, button/card/form/navigation patterns, phone/tablet sizing, Indonesian status
  copy, accessibility and implementation handoff. Clearly distinguishes proposed
  tokens/assets/component APIs from the current Android implementation.
- Added self-contained `docs/design/ui-reference.html`: fictional cashier layout,
  product/filter/cart hierarchy, palette/type/control/icon/status/spacing examples.
  Browser controls demonstrate local state only; no data submission, external fonts,
  scripts, new dependency, hosting or network service is used by the reference.
- Source observations identify repeated Info icons in authenticated navigation,
  internal English catalog enum labels and weak hierarchy. This was source review,
  not a new physical-device usability audit. Brand white UI and independent white
  night-mode launcher logo remain the design baseline. Existing authorization,
  offline lease, immutable receipts and idempotent retries remain constraints.
- Linked from README/UI_UX and recorded D071–D073. Existing documentation retained.

Verification executed:

- Relative Markdown/HTML resources, unique IDs, SVG/ARIA references and local paths
  checked with Python HTMLParser/path validation; all resolved. `node --check` passed
  for the extracted reference script. Reference size: 21,623 bytes at validation.
- Nine proposed foreground/background pairs checked using WCAG relative luminance:
  primary 6.05:1, primary text 17.07:1, supporting text 5.92:1, selected 9.29:1,
  success 5.71:1, warning 6.72:1, error 5.63:1, information 6.54:1. Each exceeds 4.5:1;
  input/control outline 3.88:1 exceeds 3:1. Decorative divider is explicitly exempt
  from serving as the sole control boundary/selection indicator.
- Rendered and visually inspected in isolated headless Chrome. True viewport sizes
  390×1800 and 1440×1800 verified through browser emulation (Mac window sizing can
  otherwise clamp narrow widths). Neither viewport had horizontal overflow.
- Browser interaction checks passed: all/milk/fruit filtering, selected product and
  status label, cart open/close with aria-expanded, and secondary-action feedback.
  Closed the isolated preview afterward. No publication or external app write.
- `git diff --check` passed. No Gradle/domain/DAO/native test rerun: changes are
  documentation/reference-only, with no Kotlin, manifest, database or runtime edits.

Exact next iteration: apply shared type/shape/spacing/icon tokens, distinct role-
filtered navigation icons and a compact context header. Then update catalog/POS,
checkout/history and management screens incrementally. Validate 200% font scale,
TalkBack, phone/tablet, keyboard/insets and business-state regressions when applying
those changes. The existing M7 live/signing/recovery acceptance gates remain open.

## Screen inventory and access-flow designs — 2026-11-15

Completed a documentation/design iteration; Android runtime behavior is unchanged.

- Added `UI_SCREEN_PLAN.md` with 40 distinct design surfaces, role/connectivity gates,
  existing implementation anchors, three flow maps and an incremental handoff order.
  Full screens, sheets, inline states and OS boundaries are distinguished; IDs are
  not new navigation routes. Catalog, POS, reports and management remain inventory-only.
- Added `AUTH_SCREEN_DESIGNS.md` with detailed phone/tablet access layouts, state/action
  mapping, copy, acceptance scenarios and ACCESS-01–ACCESS-05 implementation tickets.
  Covers restore/login, outlet selection, pending approval, locked/offline entry,
  reviewer requests/confirmation and Android's credential boundary.
- Added `design/access-flow.html` and phone/tablet PNG previews. Read-only fictional
  credentials and external scenario controls demonstrate two separate approval actors.
  No Supabase call, real credential, native OS authentication or real approval occurs.
- Preserved white/green branding and current authorization constraints, including
  bounded offline access and the separate initial catalog-pull gate. Documented that
  HTTP 429 classification and stronger approval retry guarantees are not implemented
  merely by presenting those design states.
- Linked the handoff from README/UI_UX/UI_GUIDELINES and recorded D074–D076.

Verification executed:

- Source-reviewed existing Auth state/actions and server enrollment/approval contracts.
  Graph coverage was best-effort; relevant partially indexed SQL was read directly.
- Isolated headless Chrome checks passed at 390px phone and 1440px tablet widths:
  nine state layouts each, no horizontal overflow and valid SVG references.
- Browser interaction checks passed for password visibility/configuration, outlet
  selection, pending/reviewer approval, role-specific handoff, credential-prompt
  cancellation, expiry during the simulated OS prompt and valid offline handoff.
  No browser runtime exception occurred. Captured local previews and closed Chrome.
- Final local resource/Markdown link checks passed (41 links), extracted JavaScript
  passed `node --check`, and `git diff --check` passed.
- No Gradle or native tests run: this iteration changes only documentation and browser
  design references. Android accessibility, OS authentication and physical-device
  acceptance remain unverified by these prototype checks.

Exact next iteration: implement ACCESS-01–ACCESS-05 against the detailed access
handoff, then F-00/F-01 shared navigation/context. Detail the remaining screen groups
before their redesign. Existing M7 production/security/signing/recovery gates remain open.

## Native access-flow UI — 2026-11-15

Implemented the first connected UI handoff (ACCESS-01–ACCESS-04); device acceptance
within ACCESS-05 remains open.

Changed files and decisions:

- `core/designsystem/Theme.kt`: shared guideline palette refinements, named type
  styles and rounded shapes; white default and explicit dark option remain.
- `auth/presentation/AccessComponents.kt`, `AccessScreen.kt`, `AuthScreen.kt`:
  original-color logo, bounded scrolling form, expanded brand pane, large-font
  single column, persistent field labels, password visibility/IME actions, busy
  guards, localized read-only outlet roles and distinct access/error states.
- Password stays memory-only and clears on submit/leaving the form. Email validation
  uses existing required/@/length constraints; passwords are not trimmed. Debug demo
  remains debug-only. Device UUID copy never includes tokens or device proof.
- `DeviceRequestsPanel.kt`: separate reviewer list, unloaded versus successful empty
  states, phone confirmation sheet/tablet dialog, identity/outlet/role/full UUID,
  cancellation and in-flight submit/dismiss guards. Existing approve-then-list action
  stays authoritative; unknown outcomes follow existing fail-closed handling.
- `Auth.kt` / `AuthRepositoryImpl.kt`: memory-only `deviceRequestsLoaded` and
  `lockedContext` display metadata plus the original eligible lease deadline. Locked
  states keep authoritative `membership` null; no new grant/lease/endpoint or persisted
  schema is introduced. No production deployment, migration or app-data reset.
- Added four local outline vector icons and Indonesian strings; removed obsolete
  invitation/activation wording from sign-in errors. Existing role navigation and
  POS initial-pull/recovery gates are retained.
- Added `AccessPresentationTest` (Robolectric/Compose) and repository assertions;
  updated the native invalid-offline test to expect no unlock action. Test dependencies
  reuse the existing BOM, runner and JUnit versions. The Compose host activity
  is registered only inside the Robolectric rule, never in the production manifest.
- Updated README, detailed designs/manual checks, UI_UX, UI_GUIDELINES,
  IMPLEMENTATION_PLAN and D077–D079. Preserved prior documentation/design artifacts.

Verification notes:

- Initial compile caught an unsupported Material 3 sheet parameter; removed it and
  retained state/dismissal guards supported by the installed version.
- Release host tests initially could not resolve the Compose test activity. Merely
  merging the test manifest did not update release resource-APK activity resolution;
  register the host activity in Robolectric before ActivityScenario starts instead.
  This setup remains test-only and leaves the production manifest unchanged.
- Host tests cover password visibility/unchanged submission/clearing, malformed email,
  configuration gating, busy outlet selection, pending approval with no bypass,
  unloaded lists, reviewer busy confirmation, tablet cancellation, bounded tablet
  fields and 200% text fallback. They do not exercise a real Android credential prompt.
- Resource XML/local handoff links and `git diff --check` passed.
- ADB device discovery ran successfully but returned no attached device; no emulator
  is installed in the configured SDK. Instrumented execution, real IME/rotation,
  TalkBack, OS unlock cancellation/success and hosted two-actor acceptance remain open.

Final verification executed with Android Studio JBR:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
```

- Final command: BUILD SUCCESSFUL (2m 23s). Debug/release APKs and the instrumented
  test APK generated; release remains unsigned.
- Debug JVM: 118 passed, zero failures/errors/skips. Release JVM: 109 passed, nine
  expected existing skips, zero failures/errors. All ten new access Compose tests
  pass in each variant, including phone sheet and tablet confirmation cancellation.
- Lint: zero errors, 31 warnings across the project. No warning location is in the
  new access components/screens/reviewer panel or shared Theme.kt.
- Verified the production release manifest excludes the Compose host test activity.
- Artifacts: `app/build/outputs/apk/debug/app-debug.apk`,
  `app/build/outputs/apk/release/app-release-unsigned.apk` and
  `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Next UI milestone: F-00/F-01 shared authenticated shell/navigation/context. Later
screen groups need their own detailed designs. M7 hosted/signing/recovery gates remain open.

## Shared authenticated shell — 2026-11-15

Implemented F-00/F-01 as one presentation iteration; retained the previous access
redesign and documentation.

Changed files and decisions:

- Added `auth/presentation/AuthenticatedShell.kt` and integrated it in `AuthScreen.kt`.
  One outlet/role/access header replaces repeated context lines. Navigation has
  distinct icons and short Indonesian labels; Cashier starts at Kasir, Owner/Manager
  at Ringkasan. Existing destination callbacks remain connected to MainActivity.
- Below 600dp use bottom navigation; >=600dp use a scrolling rail. Existing features
  still receive expanded=true at >=840dp. At >=150% text or <480dp window height,
  use a labeled, scrolling modal drawer to preserve readable actions. Keep content
  mounted at the same composition location across navigation-form changes. Long outlet
  names wrap to two visual lines, retaining their full text in accessibility semantics.
- Scope presentation selection to account email/brand/outlet/role name. Filter saved
  selection against the permitted destination set before rendering; unknown/restored
  management names cannot grant a cashier route. Locked display context cannot render
  the shell. Existing server authorization and domain gates remain authoritative.
- Header outlet action reuses AuthViewModel.switchOutlet -> chooseOutlets, disables
  while offline/busy and explains the online requirement. It never locally reassigns
  scope, clears sales or enrolls a device by itself. Settings keeps sync/recovery,
  actual lease deadline, revalidation and sign-out; repeated header context is removed.
- “Akses offline” / “Akses tervalidasi” describes authorization mode, not live Wi-Fi,
  initialized catalog, completed receipt sync or backup. Keep auth messages/progress
  visible and retain POS's initial-pull/recovery gates inside its unchanged feature.
- Imported twelve official rounded 24px vectors, removing only their AppCompat theme
  tint so Compose supplies semantic colors. Source commit and upstream/local hashes
  are in MATERIAL_SYMBOLS_SOURCES.md. Retain the Apache license/source attribution in
  docs/licenses and app assets for APK distribution. No library versions changed.
- Added `AuthenticatedShellTest`; source and integration contracts checked using the
  codebase-memory skill plus direct source review. Added SHELL_SCREEN_DESIGNS.md and
  updated README/UI_UX/UI_GUIDELINES/UI_SCREEN_PLAN/IMPLEMENTATION_PLAN and D080–D083.
- No backend/API/Room/auth policy, real transaction or production deployment change.
  Existing unrelated/uncommitted work and documentation remain preserved.

Verification executed so far:

- Initial resource link caught upstream `colorControlNormal`, absent in this Compose
  app. Removed only the root vector tint; retained paths, viewport and size. Debug
  compilation, existing unit tests, Android-test APK compilation and lint then passed.
- Thirteen initial focused host Compose tests passed: role defaults/destinations, route
  callbacks, localized context, offline/busy action guards, scope resets, state
  restoration, 600/840dp layouts, 200% text drawer, short window scrolling, locked
  state rejection and AuthRoot header -> repository outlet-selection integration.
  Added a fourteenth regression for typed feature input surviving navigation-form
  changes; its final result is included in the full-build outcomes below.
- Local documentation links, vector XML, all recorded local icon hashes, retained
  license equality and `git diff --check` passed.
- ADB device discovery succeeded with no devices attached. Physical rotation/IME,
  TalkBack, wireless reconnect, live outlet revocation and real cashier initial-pull/
  recovery acceptance remain unexecuted. Host tests are not physical-device acceptance.

Final verification executed using Android Studio JBR:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
```

- Final command: BUILD SUCCESSFUL (31m 21s). Debug APK, unsigned release APK and
  Android instrumented-test APK generated. No instrumented device execution occurred.
- Debug JVM: 132 passed, zero failures/errors/skips. Release JVM: 123 passed, nine
  expected existing skips, zero failures/errors. All fourteen shell tests passed in
  each variant, including the final input-preservation regression. The earlier ten
  access-flow tests also passed in each variant.
- Lint: zero errors, 33 warnings. No warning location is in AuthenticatedShell.kt,
  AuthScreen.kt or the twelve shell icon drawables; retained unused resources and
  other existing project warnings remain outside this iteration's UI changes.
- Verified both APK ZIPs contain the retained icon license bytes and pinned source
  attribution. Local icon hashes/XML/link checks and `git diff --check` passed.
- Artifacts: `app/build/outputs/apk/debug/app-debug.apk`,
  `app/build/outputs/apk/release/app-release-unsigned.apk`, and
  `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Exact next milestone: detail catalog C-01–C-08 screen/form/photo/archive designs and
acceptance criteria before applying the native catalog redesign. M7 hosted, signing,
recovery and device acceptance gates remain open.

## Catalog detailed designs — 2026-11-15

Completed the planned C-01–C-08 design handoff. Native catalog behavior is unchanged.

- Added `CATALOG_SCREEN_DESIGNS.md`: adaptive list/editor/form layouts, localized
  product-first tabs, local search/status filters, exact field/parent/group semantics,
  photo states, brand-wide archive confirmation and CAT-UI-01–CAT-UI-05 tickets.
- Mapped requirements to current CatalogEditorState/editable, ViewModel callbacks,
  CatalogMutation/ack, independent PhotoCommand and server dependency/version rules.
  Used the codebase-memory skill; relevant partially indexed SQL (596–695) was read
  directly. Scope-bound auth, shared-brand catalog, immutable paid snapshots, frozen
  uncertain commands and observed pull completion remain constraints.
- Explicitly identified implementation gaps: current form closes on save; local
  filtering/tab order/adaptive editor/archive confirmation are proposed presentation
  work. retrySync enqueues work rather than acknowledging refresh completion; native
  draft/result/refresh correlation needs observed state, not invented freshness/API.
- Added `design/catalog-flow.html`: no-network, fictional phone/tablet state reference,
  OS picker illustration, external role/outcome/pull controls and read-only display of
  operational boundaries. No file/gallery read, real credentials, upload or real save.
  Reuses retained Material Symbols paths with existing source/license attribution.
- Added browser captures `design/catalog-phone.png` and `design/catalog-tablet.png`.
  They are fictional browser renders, not Android screenshots. Linked the handoff
  from README/UI_UX/UI_GUIDELINES/UI_SCREEN_PLAN/IMPLEMENTATION_PLAN; D084–D086 recorded.
  Preserved prior application work and all documentation.

Verification executed:

- JavaScript extracted and `node --check` passed. Static HTML IDs/label references,
  relative documentation links and inline script network boundaries checked. An early
  browser check caught an SVG/search-input ID collision; prefixed symbol IDs and reran.
- Isolated headless Chrome browser checks passed across 390px phone, 800px constrained
  tablet and 1440px wide tablet. Eleven states per width (33 layout cases) had no
  horizontal overflow and all SVG references resolved; no runtime exceptions occurred.
- Interaction checks passed: four tabs, case-insensitive search, archive/no-match
  filtering, empty-category setup, whole-IDR/Int overflow validation, group bounds,
  immutable existing option parent, archive cancel/commit and explicit save+archive,
  preserving stored fields on dedicated archive, unknown-result same-command retries,
  conflict/refresh, picker cancellation/keyboard Escape/focus return, independent
  photo revisions and retained unsaved product text after photo update/removal.
- Offline/loading/bootstrap/recovery disable mutations; cashier variants show no
  catalog controls. These are fixture checks, not proof of native/server authorization.
- Visually inspected final phone list/wide editor captures, copied them into docs and
  closed the isolated browser. No publishing or external application write.
- `git diff --check` passed. No Gradle, JVM, DAO, hosted or native/device tests rerun:
  this iteration modifies documentation, HTML and browser captures only. The prior
  shell iteration's build results are not presented as new catalog verification.

Exact next milestone: implement CAT-UI-01–CAT-UI-05 in native Compose/ViewModel using
this handoff, then run appropriate build/domain/Compose checks and report remaining
physical-device/hosted acceptance. POS and later groups still need detailed designs;
M7 device/security/signing/recovery gates remain open.

## Native catalog UI — 2026-11-15

Implemented the catalog handoff (CAT-UI-01–04), with host regression coverage and
instrumented test compilation for CAT-UI-05. Physical-device/hosted acceptance is open.

Changes and decisions:

- `CatalogEditorScreen.kt`: localized product-first scrollable tabs, local name/status
  filters, clear empty/no-match/bootstrap/offline states, readable rows, formatted
  integer IDR, brand-wide scope notice and wrapping primary/refresh actions.
- New `CatalogEditorForm.kt`: focused compact/large-text/short-window forms, bounded
  medium dialog, 480dp wide editor pane beside the list at >=1100dp available width,
  scrollable fields and IME-aware pinned actions. Another edit/add is disabled while
  a form is open; no silent replacement of typed input. Keep existing shell/routes.
- New `CatalogEditorSession.kt`: raw fields, tab/filter/query, scroll and submission
  correlation in memory owned by the activity ViewModel. Rotation/layout changes keep
  that draft; scope changes/logout clear it. No editor/photo state goes to SavedState,
  Room or a durable admin outbox. Process loss still requires refresh/inspection.
- Category/product/group/option fields validate whole nonnegative Long IDR and Int
  bounds, name, parent/required-option constraints and ordered unique assignments.
  Existing option parent is read-only. Zero price is valid; there is no invented cap,
  automatic repricing, new stock/discount field or server-rule replacement.
- Archive confirmation separates stored-field archive from save-and-archive with
  current edits. Archive cancellation makes no call. Reactivation uses the same
  existing versioned save. Photo removal has its own confirmation/version; photo
  changes retain typed product text and do not rewrite prices or paid receipts.
- `CatalogEditorViewModel.kt`: immutable pending command copies, explicit refresh
  request/baseline state, observed newer successful pull and post-checkpoint catalog
  reread, plus matching operation/local target visibility before closing a form.
  Conflicts preserve the draft for explicit refresh/review; corrected submissions
  get new operation UUIDs, retries retain the original command.
- Before waiting for cancelled IO, remove old-scope display state; updates from actions
  and observation are scope guarded. An uncancellable old request cannot repopulate
  another outlet's screen. Existing scoped unresolved-work retention remains memory-only.
- `ProductPhoto.kt`: the missing-image retry button now has its own thumbnail space;
  a decorative icon no longer consumes its 48dp touch target. Missing photos still do
  not gate product selection, checkout or receipt access.
- Added Indonesian strings; new CatalogEditorSessionTest, CatalogEditorViewModelTest
  and CatalogEditorPresentationTest. Updated existing native CatalogEditorScreenTest
  and ProductPhotoScreenTest to localized/default-tab and confirmation behavior.
- Updated README/UI_UX/UI_GUIDELINES/UI_SCREEN_PLAN/IMPLEMENTATION_PLAN,
  CATALOG_SCREEN_DESIGNS and D087–D090. Preserved earlier work/documentation. No schema,
  API, dependency version, signing, production deployment or transaction contract change.

Verification before the final full build:

- Initial debug app and instrumented-test APK compiled successfully.
- The first 25 new catalog tests passed. A subsequent photo-target assertion detected
  the old 32dp retry button; fixed the thumbnail layout and reran successfully.
- Full debug JVM suite, Android-test APK compilation and lint passed. Twenty-seven
  new catalog tests pass (7 form/session, 7 ViewModel, 13 Compose), covering numeric
  edges/dependencies, unknown retry, observed refresh/conflict review, ACK/pull/local
  record gating, separate photo removal/retry/version, scope/logout/cancelled IO,
  localized browsing, archive paths and compact/medium/wide/200% text layouts.
- Tests also confirm old data clears before slow cancelled IO finishes, drafts survive
  photo application/layout changes, and missing-image retry is at least 48dp high.
- Source flow review used codebase-memory plus direct source. Room checkpoint behavior
  was checked directly; metadata coverage remains best-effort. No backend SQL changed.
- ADB discovery returned no attached device. Real OS picker/providers, IME/insets,
  physical rotation/TalkBack and hosted concurrent-device/Storage checks were not run.
- `git diff --check` passed. Final full debug/release command results follow below.

Exact next milestone: detailed POS P-01–P-06 designs for catalog selection/modifiers,
cart/payment/local receipt/sync-review states, then their native redesign. Existing
M7 hosted/device/signing/recovery gates remain open.

Final native catalog verification:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
```

- BUILD SUCCESSFUL (27m 34s); debug/release compilation, full JVM suites, lint and
  instrumented-test APK compilation completed. Release remains unsigned.
- Debug: 159 passed, zero skipped/failures/errors. Release: 150 passed, nine expected
  existing skips, zero failures/errors. All 27 new catalog tests passed per variant.
- Lint: zero errors, 34 warnings across the project; no warning location is in the
  changed catalog/photo Kotlin files. Retained unused-resource/project warnings remain.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Native test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- `git diff --check` and final local documentation/resource XML checks passed.
  No real account/sale/photo was submitted by the host tests or build. Device/hosted,
  signing and recovery acceptance remain separate, unexecuted gates for this iteration.


## POS screen design iteration — 2026-11-15

Completed P-01–P-06 detailed handoff in POS_SCREEN_DESIGNS.md and the fictional
local design/pos-flow.html reference, with phone receipt/review and tablet catalog/
cart captures. Linked the handoff from README, UI guidance, screen plan and
implementation plan. No Android source, dependency, schema or backend changes
were made in this iteration. Existing uncommitted work was preserved.

Source review used codebase-memory search/trace/snippets and clean metadata coverage
for ten relied-on checkout/POS/sync/test files. Key gaps for native work: correlate
observed add/save results with scope and draft revision, reconcile uncertain local
saves, retain known receipts during recovery without enabling new sales, and expose
a separate scoped receipt-delivery read projection. Aggregate queue status is not
proof for an individual receipt.

Executed verification:

- `node --check /tmp/pos-flow.js` passed for the extracted reference script.
- `node /tmp/daunteh-check-pos-flow.mjs` passed against isolated local headless Chrome:
  selection required/max/quantity limits, failed-add retention, separate cart lines,
  cash bounds, zero total, checked overflow, explicit QRIS and reset-on-method-change,
  frozen same-command retry, commit-before-observation readback, one receipt/outbox,
  unchanged receipts across delivery states, review/auth/unknown retry restrictions,
  recovery receipt guard, stale cart, 100-line limit and role restrictions.
- 51 viewport/scenario checks (390/800/1440px, 17 scenarios) passed without document
  horizontal overflow; zero runtime exceptions. Two PNG captures were generated
  and visually inspected. These are browser designs, not Android screenshots.
- Script syntax, local reference links and `git diff --check` were checked.

Gradle/native/Room/hosted/ADB checks were not rerun for this documentation-only
iteration. Browser fixture outcomes do not validate persistence, server authorization,
actual payment, process death or physical IME/TalkBack/rotation behavior. Existing
M7 hosted/device/signing/recovery acceptance remains open.

Exact next milestone: native POS-UI-01–05, as listed in POS_SCREEN_DESIGNS.md.


## Native POS presentation iteration — 2026-11-15

Implemented POS-UI-01–04 and POS-UI-05 host regressions from the P-01–P-06 handoff.

Changed files/responsibility:

- `feature/pos/presentation/PosScreen.kt`: name/category filtering, accessible product
  actions separate from photo retry, adaptive grid/compact focused cart/persistent
  expanded pane, stale snapshot feedback and selection success correlation.
- New `PosSelection.kt` / `PosCheckout.kt`: scrolling modifier sheet with fixed Add,
  explicit optional single-choice None, min/max/quantity/100-line/checked total guards,
  focused whole-IDR cash/manual QRIS form, frozen unknown-save surface and readable
  original receipt with scoped delivery/review/revalidation labels.
- `AuthenticatedPosViewModel.kt`, `AuthenticatedPosRoute.kt`, `PosViewModel.kt`:
  observed add results, original authenticated command/draft freezing, readback and
  identical verified-unsaved retry; scope/observation generations reject cancelled old
  results and consumed-cart resurrection. Recovery permits known receipt viewing but
  blocks next sale/new writes. Demo remains a separate explicit debug entry.
- `checkout/domain/Checkout.kt` / `data/AuthenticatedCheckoutRepository.kt`: separate
  read-only delivery projection and exact original-command reconciliation. Validated
  feed proof or scoped outbox state supplies labels; absent proof remains unknown.
  Immutable Receipt/payload and existing atomic transaction/auth guards stay intact.
- New `AuthenticatedPosViewModelTest.kt` (11 tests) / `PosPresentationTest.kt` (15),
  four additional `PullSyncTest.kt` Room tests; existing instrumented POS tests use
  the now-fixed save action directly. Receipt/error/action copy updated in strings.xml.
- README, UI guidance/screen plan, POS handoff, implementation plan and decisions
  updated. Existing uncommitted access/shell/catalog/design work was preserved.

Decisions: D094–D097. No dependency/version, schema, migration, backend endpoint,
production deployment or DB reset was introduced. Manual QRIS flag is memory-only;
unsaved UI/frozen command input is not a durable payment queue. Durable original
cart/sale/outbox/receipt remain the restart recovery basis.

Verification so far:

- Initial compilation caught a Material API parameter unsupported by the pinned
  version; replaced it with existing sheet-state transition confirmation.
- Focused tests exposed two test expectations (the next-sale label and retrying
  after a failed add); corrected the action copy and two-attempt test scenario.
- `./gradlew :app:testDebugUnitTest --tests '*AuthenticatedPosViewModelTest'
  --tests '*PosPresentationTest' --tests '*PullSyncTest' --offline --max-workers=2
  --console=plain` passed: 43 tests, no failures/errors/skips. Includes uncertainty
  before/after commit, exact retry identity, restart/readback scope and revision,
  cancelled old scope/observation, readiness/stale gates, immutable REVIEW, ACK/feed
  proof, cash/QRIS guards, optional/multi selection bounds, 100-line/quantity limits,
  compact and tablet/200% text fallback, and outlet input reset.
- Android test APK compilation succeeded in the preceding focused run.
- Full build was interrupted to include the later observation guard, then restarted.
  The completed full run passed (9m 5s): 189 debug passed; release 180 passed / nine
  expected skips; zero failures/errors. Final visual polish (search icon, inline cached-
  refresh retry and neutral grouping) was followed by the final build recorded below.
- ADB `devices -l` and `mdns services` returned empty lists. No phone/tablet instrumentation,
  actual IME/TalkBack/rotation, real OS photo cache or hosted sale was exercised.
- Codebase-memory search/trace/snippets + relied-path metadata coverage and direct
  source review used. No graph-based completeness claim or SQL inference was made.

Remaining acceptance: real phone/tablet and hosted offline/reconnect/price rejection,
process kill, accessibility/IME/rotation, original M7 device/signing/recovery gates.
Exact next milestone: detailed history H-01/H-02 designs before native history redesign.


Final native POS verification (final delivered source):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
```

- BUILD SUCCESSFUL (4m 11s). Debug/release assembly, full JVM suites, lint and
  Android-test APK compilation completed. Final log: `app/build/pos-final-build.log`
  (generated local artifact, not committed).
- Debug: 189 passed, zero skipped/failures/errors. Release: 180 passed, nine existing
  expected demo-only skips, zero failures/errors. All 30 added host regressions pass
  in each variant (11 ViewModel, 15 Compose, four additional Room/pull tests).
- Lint: zero errors, 41 warnings. These include seven retained POS string resources
  made unused by the redesign, plus existing project/resource/dependency warnings.
  No warning location is in the changed POS/checkout Kotlin implementation.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Instrumented-test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- `git diff --check`, strings resource XML parsing and POS handoff/screen-plan local
  link checks passed. No real credential, sale or backend write was used by these
  host checks. No Room schema, migration, dependency or backend SQL changed.

Device/hosted/signing/recovery acceptance remains open. ADB and wireless discovery
returned no device. Exact next milestone: detailed history H-01/H-02 designs, then
its own native presentation iteration.


## History screen design iteration — 2026-11-15

Completed H-01/H-02 detailed design handoff in HISTORY_SCREEN_DESIGNS.md and
fictional local design/history-flow.html, with tablet list/detail and phone receipt
review captures. Linked from README, UI guidance, screen plan and implementation
plan; recorded D098–D100. Existing documentation and uncommitted native work were
preserved. No Android source, schema, dependency, server or authorization changes
were made in this documentation iteration.

Source review used codebase-memory search/trace/snippet and clean best-effort
metadata coverage for eleven relied-on source paths, supplemented by direct reads.
Verified: cashier-only history navigation; current scoped local/feed merge with UUID
dedupe/local precedence; local and remote 100-row input limits and final 100 cap;
remote ordering by feed sequence; immutable Receipt and separate delivery projection;
shared retry currently lacking an explicit observed refresh lifecycle. The design
therefore avoids archive completeness/global latest-100 and origin/receipt-number
claims and provides native HIST-UI-01–04 tickets for filtering, adaptive list/detail,
refresh/scope/selection handling and regressions.

Executed verification:

- `node --check /tmp/history-flow.js` passed for the extracted reference script.
- `node /tmp/daunteh-check-history-flow.mjs` passed in isolated local headless Chrome:
  fixture merge/dedupe/order, UUID/original-name case-insensitive search, AND filters,
  Jakarta midnight date boundaries, six delivery states and unknown proof, original
  cash/zero/QRIS manual detail, close/focus restoration, selection eviction after
  filter/evidence changes, local-read versus observed-pull refresh and error retention,
  initial/empty/read-error/offline/recovery, role denial and outlet input clearing.
- 36 viewport/scenario checks passed at 390/800/1440px with no document horizontal
  overflow; zero runtime exceptions. Two PNG captures were generated and visually
  inspected. These are fictional browser references, not Android screenshots.
- A repeated test run initially reused the prior selected fixture; the harness now
  reloads the page before assertions and the final rerun passed. No application
  behavior was changed to accommodate that harness setup issue.
- Static script/HTML IDs/labels, network/storage independence, local documentation
  links and `git diff --check` were verified.

Gradle/native/Room/ADB/hosted tests were not rerun for this documentation-only
iteration. The preceding native POS build/test results above remain historical;
browser checks cannot validate persistence, real authorization, server completeness,
physical IME/TalkBack/rotation or payment. No real sale/credential/backend was used.
M7 device/hosted/signing/recovery acceptance remains open.

Exact next milestone: native history HIST-UI-01–04 as listed in HISTORY_SCREEN_DESIGNS.md.


## Native history presentation iteration — 2026-11-15

Implemented HIST-UI-01–03 with HIST-UI-04 automated verification underway.

Changed files/responsibility:

- New `feature/history/presentation/HistorySession.kt`: local original-name/UUID,
  Jakarta day and per-receipt proof selectors; selected receipt, cache eviction and
  list scroll; stale UI callback generation guard. No complete-archive assumption.
- New `HistoryViewModel.kt`: isolated cashier scope and read/refresh state, retained
  same-scope snapshots, post-read device/backend checks, cancellation generations,
  Clock-driven day rollover, local-only/recovery versus actual bounded pull outcomes.
- New `HistoryScreen.kt`: accessible local filters, grouped cards with original
  money/method/status, focused compact detail and expanded list/detail with a
  large-text/short-window fallback. No new role destination or sale action.
- MainActivity / AuthenticatedPosRoute connect the independent history route;
  PosScreen's debug history adapter reuses the presentation. PosCheckout supplies
  selectable UUID, local-availability banner and suppresses checkout/queue actions
  for history. Receipt/QRIS wording and history UI strings are localized in strings.xml.
- New `HistorySessionTest` (8), `HistoryViewModelTest` (11), `HistoryPresentationTest`
  (9), two new PullSyncTest Room regressions; eight fixture HistoryScreenTest device
  cases. ComponentTestRunner overrides production startup Application in Android
  component/Room tests; build.gradle.kts points instrumented tests to this runner.
- README/design/status/plan/decision documentation updated. Existing uncommitted
  access/shell/catalog/POS/design work and all documentation were preserved.

Decisions D101–D104. Pinned dependencies, production applicationId, Room schema,
migrations, server/RLS/RPC contracts, immutable Receipt/payload and checkout rules
are unchanged. History does not call checkout mutation/dismissal methods. Test runner
is for fixtures, not Hilt MainActivity/Keystore/login end-to-end acceptance.

Executed verification so far:

- Initial debug Kotlin compilation passed. Focused UI tests required proper lazy-
  list scrolling and the existing QRIS text assertion; corrected the test setup.
- Date-rollover test cleanup initially kept the ViewModel ticker alive under virtual
  time; a thread dump identified test cleanup advancing indefinitely. The run was
  stopped and tests now clear the ViewModel within each test body/finally, including
  deferred fake IO cleanup. No production clock behavior was changed for the harness.
- Focused debug command selecting HistorySessionTest, HistoryViewModelTest,
  HistoryPresentationTest and PullSyncTest plus assembleDebugAndroidTest passed:
  47 tests, zero failures/errors/skips (39s). Thirty new host regressions cover
  filter intersections, UUID/snapshot search, missing proof, Jakarta midnight/year
  rollover, late UI/IO/scope/device/backend results, actual pull completion with
  unchanged timestamp, partial/offline/recovery/failure retention, read-only details,
  compact/tablet/200% text fallback, bounded-cache ordering and unchanged durable
  draft/receipt pointer/outbox after history reads.
- Full build and isolated phone component results are recorded below when complete.
- ADB found a connected Samsung SM-M146B and its wireless service. Device fixtures
  use no login/account/real receipt, and preserve application data. No DB reset or
  production deployment is part of this iteration.
- Codebase-memory search/trace/snippets, relied-path metadata coverage and direct
  source review were used; no exhaustive graph/security-completeness claim.

Remaining acceptance: normal-application/Hilt/Auth/Keystore/hosted multi-device and
price rejection, physical tablet/split/IME/TalkBack/rotation and M7 signing/recovery.
Exact next milestone: detailed R-01–R-03 overview/report/filter designs before native
implementation, preserving existing accepted-server metrics and X-03 export.


Final native history host/build verification:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
```

- BUILD SUCCESSFUL (26m 34s): debug/release assembly, full JVM suites, lint and
  Android-test APK compilation completed. Log: `app/build/history-build.log`
  (generated local artifact). Release remains unsigned.
- Debug: 219 passed, zero skipped/failures/errors. Release: 210 passed, nine existing
  expected demo-only skips, zero failures/errors. All 30 added host regressions pass
  in both variants (8 selectors/session, 11 ViewModel, 9 Compose, two Room/pull).
- Lint: zero errors, 45 project warnings. Four additional unused string warnings
  include retained old history copy and the detail label resource; no warning
  location is in the new history Kotlin implementation. Existing dependency/SDK/
  resource warnings remain. No dependency upgrade is included in this UI milestone.
- APKs: `app/build/outputs/apk/debug/app-debug.apk`,
  `app/build/outputs/apk/release/app-release-unsigned.apk`,
  `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- `git diff --check`, resource XML parsing and history handoff local links passed.
  No schema/migration, production deployment, real credentials or sale mutation
  was used by these host checks. Phone component-test outcome follows below.


Final corrected build and physical phone component verification:

- Repeated the full build command above after the phone exposed one additional
  lazy-list test assumption: the refresh-result banner was outside the composed
  viewport after scrolling to a receipt. Both host and device tests now scroll to
  the banner and assert it is displayed. No product source change was needed.
- Final BUILD SUCCESSFUL (1m 59s), log `app/build/history-final-build.log`.
  Debug: 219 passed; release: 210 passed plus nine expected existing skips;
  zero failures/errors. Lint remains zero errors / 45 warnings.
- Installed the verified debug APK and test APK with `adb install -r -t`; both
  installations succeeded, preserving app data. Wireless ADB briefly disconnected
  during the long build, then rediscovery found the same Samsung SM-M146B.
- Ran `adb shell am instrument -w -e class
  com.dauntehnusantara.pos.HistoryScreenTest
  com.dauntehnusantara.pos.test/com.dauntehnusantara.pos.ComponentTestRunner`.
  Final result: **OK (8 tests)** in 17.919s. Log: `app/build/history-device-tests.log`;
  the initial seven-pass/one-visibility-failure run is retained in
  `app/build/history-device-tests-initial.log`.
- Phone cases cover search/filters, focused read-only detail and return, original
  review/recovery and manual QRIS/missing-proof messages, refresh retention,
  selected-receipt eviction, and retained/read-error/initial states. Fixtures use
  synthetic receipts and a plain Application; no login or checkout mutation API
  is invoked. After completion the fixture process was stopped, without data clear,
  so the next manual launch uses normal PosApplication/Hilt startup.
- Final `git diff --check`, resource XML and local handoff links passed.

The current debug APK is installed on the phone. Normal Hilt/MainActivity/login/
Keystore, hosted offline/reconnect/price rejection, physical tablet/rotation/IME/
TalkBack and release signing/recovery remain open acceptance gates.
Exact next milestone: detailed R-01–R-03 overview/report/filter designs.


## Overview/report design iteration — 2026-11-15

Completed R-01–R-03 / report X-03 detailed handoff in REPORT_SCREEN_DESIGNS.md,
fictional design/report-flow.html and phone overview/tablet report captures. Linked
from README, visual/UX guidance, screen inventory and implementation plan; recorded
D105–D107. Existing documentation and uncommitted native work were preserved. No
Android, Room, dependency, server SQL, permission or production deployment changes.

Source review used codebase-memory search/trace/snippets and clean best-effort
metadata coverage for relied-on Kotlin paths. SQL coverage is partial: inspected
get_sales_report directly (schema.sql lines 500–537 and execute grants at 597–598),
not the entire schema/security surface. Confirmed existing accepted-server fields,
Owner/Manager scope, Jakarta inclusive/end-exclusive dates and <=365 difference,
product ID + snapshot-name grouping/top20, exact-query cache and captured CSV.
Current ReportViewModel/Route needs draft/applied ownership, generation guards,
retained refresh and picker-cancel/result lifecycle; these are explicit RPT tickets.

Executed verification:

- `node --check /tmp/report-flow.js` passed for the extracted reference script.
- `node /tmp/daunteh-check-report-flow.mjs` passed against isolated local headless
  Chrome: shared overview/report snapshot, exact integer partitions, snapshot-name
  product grouping/ranking; invalid calendar/leap/365-difference/366-difference and
  allowed future dates; preset draft versus Apply/Cancel; owner outlet/inactive
  choices versus fixed Manager/cashier denial; no-cache versus validated zero;
  same-query retained failures/TOO_LARGE, offline no fabricated fresh success, stale
  request scope rejection; captured export despite displayed snapshot change, picker
  cancel, provider failure/new attempt and scope change clearing prepared intent.
- 30 viewport/scenario checks passed (390/800/1440px) without document horizontal
  overflow, zero runtime exceptions. Two fictional PNG captures were generated and
  visually inspected; they are not Android screenshots.
- Static HTML/script IDs/labels, no external asset/network/storage APIs, local
  handoff links and `git diff --check` were verified.

No Gradle/native/Room/ADB/hosted/provider checks were rerun for this documentation-
only milestone. Browser fixtures do not validate real money, SQL authorization,
cache persistence, Android destination/write behavior, lease/Hilt or physical
accessibility/IME/rotation. Earlier native build/device results remain historical;
M7 hosted/provider/device/signing/recovery gates remain open.

Exact next milestone: native overview/report RPT-UI-01–04 from REPORT_SCREEN_DESIGNS.md.

## Native report presentation — 15 November 2026 (RPT-UI-01–04)

Implemented the R-01–R-03 / X-03 handoff in existing Kotlin/Compose presentation.
Changed ReportScreen/ReportViewModel and added ReportSession; MainActivity supplies
Overview versus Reports destination context and AuthenticatedShell exposes a
role-limited internal navigation callback for Lihat laporan. The session shares
applied filters/snapshot between those destinations and separates unsubmitted
filter edits. The bounded sheet uses current Jakarta-clock presets, strict calendar
validation, <=365 difference, authorized Owner/inactive history choices and fixed
Manager scope. Adaptive existing metric cards and virtualized detail rows collapse
for large text. Accepted-server basis, manual QRIS, as-of/cache/no-cache/zero and
loading/error/retry remain explicit; no new financial field or business mutation.

Exact-query snapshots survive refresh errors; different applied queries clear old
totals. Scope/session/request generations reject late responses/errors/finally.
Authority is rechecked around report reads and exports, including device/backend
scope changes. Immutable captured export tickets correlate picker results; cancel,
launch failure, navigation away, stale callbacks and provider failures cannot claim
success. Provider write/close happens on IO; files partially written before an
error/scope change cannot be retracted. Existing integer/formula-safe UTF-8/CRLF CSV,
Room cache, Hilt bindings, endpoints, pinned libraries and schema are retained.

Added ReportSessionTest, ReportViewModelTest, ReportPresentationTest and isolated
ReportScreenTest device fixtures. Updated README, decisions D108–D110, report handoff,
UI_UX, UI_SCREEN_PLAN and IMPLEMENTATION_PLAN. Structural verification used graph
search/trace/snippets with clean best-effort coverage metadata for relied-on paths,
plus direct source review. No SQL, migration or production deployment this iteration.

Verification so far:

- Initial compile identified an unqualified AccessDestination reference; corrected.
- Initial targeted run: 21 tests, 2 UI assertion failures caused by incorrect fixture
  label text; corrected to actual resources. Added scope-during-write, launch failure
  and fixed Manager outlet scenarios.
- `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
  ./gradlew :app:testDebugUnitTest --tests '*Report*Test' --offline --max-workers=2
  --console=plain`: passed all 24 tests (4 session, 11 ViewModel, 6 Compose, 3 existing
  Room cache), no failures/skips.
- Full `./gradlew build :app:assembleDebugAndroidTest --offline --max-workers=2
  --console=plain`: BUILD SUCCESSFUL in 9m. Debug 240 passed; release 231 passed and
  9 expected demo-only skips, no failures/errors. Debug/release and test APKs built.
  Lint: 0 errors, 46 warnings (one additional unused legacy report_all_outlets label).
  Log: app/build/report-build.log. The full run includes the strengthened export
  success/terminal-state assertions, waiting on StateFlow instead of a timing loop.
- Debug and test APKs installed successfully with `adb install -r -t` on the
  connected Samsung SM-M146B; app data preserved. Targeted command:
  `adb -s <connected-device> shell am instrument -w -e class
  com.dauntehnusantara.pos.ReportScreenTest
  com.dauntehnusantara.pos.test/com.dauntehnusantara.pos.ComponentTestRunner`:
  OK (6 tests), 10.383s. Covered draft cancellation, absent snapshot, overview action,
  outstanding picker, fixed Manager outlet and large-text reachability with fixtures.
  Log: app/build/report-device-tests.log. Target app force-stopped afterwards so the
  next manual launch uses normal PosApplication, not the fixture Application.
- Final `git diff --check` passed. No application data clear/uninstall or real
  login/sale/access/provider workflow was used to manufacture a test result.

Remaining acceptance: hosted Owner/Manager/RLS/revocation, normal MainActivity/Hilt/
secure offline lease, real document-provider cancellation/rotation/process recreation/
failure and partial files, physical tablet/split-window/IME/200% text/TalkBack,
release signing and M7 recovery gates. Automated fixtures use fake scoped records and
plain Application without production startup sync; they do not establish those gates.

Exact next milestone: S-01–S-03 staff/access/confirmation detailed design handoff,
preserving private-brand manual user provisioning and existing role/server limits.

## Staff/access detailed design — 15 November 2026 (S-01–S-03)

Completed one design/handoff milestone. Added STAFF_SCREEN_DESIGNS.md with scoped
member/device/audit layouts, effect-specific reasoned confirmations, permission and
profile-state tables, adaptive/accessibility guidance and STAFF-UI-01–04 native
implementation tickets. Added design/staff-flow.html and fictional phone/tablet PNGs.
Updated README, UI_UX, UI_SCREEN_PLAN, IMPLEMENTATION_PLAN and decisions D111–D113.
No Android, Gradle, dependency, Room, backend SQL, permission or deployment changes.

Source review: codebase-memory index/status, staff search (44 results, no remaining
page), StaffScreen direct callers/callees and domain/VM/parser snippets, plus targeted
source reads. Relied-on Kotlin files had clean best-effort coverage metadata. SQL
coverage remains partial: read list_staff/set_staff_access/set_device_access and grants
at schema.sql 538–598 directly, not the entire security surface. Reused the existing
store symbol from ic_shell_store.xml; attribution remains MATERIAL_SYMBOLS_SOURCES.md.

Confirmed contracts: roster is the selected operational outlet even for Owner;
Manager members may include read-only peers but returned devices are live cashiers;
Owner-only audit returns <=50 selected-outlet events. Membership enable leaves devices
revoked; Owner restoration requires live target role and returns pending approval.
Current-device exclusion differs from membership self-target exclusion. Profile and
membership inactivity are separate. No hardware name/last-seen/complete audit data is
invented. Approval remains A-07/A-08 with independent load/result state.

Native gaps are explicit tickets: scoped memory/session and late result/error/finally
guards, stable captured target/version/desired boolean, strict trimmed 3–240 reason,
acknowledgement distinct from failed readback, conflict refresh/review and no blind
replay after uncertain response. Existing expected-version RPCs have no operation UUID
ledger. Cancellation/process loss cannot guarantee rollback. Offline/scope loss clears
sensitive roster/drafts; a fresh authorized read precedes any new action.

Executed verification:

- `node --check /tmp/staff-flow.js`: passed for extracted reference JavaScript.
- `node /tmp/daunteh-check-staff-flow.mjs`: passed in isolated local headless Chrome.
  An initial harness-only selector quoting syntax error was corrected before the
  browser run. Final checks passed with realistic fictional UUIDs.
- 63 viewport/role/scenario checks passed at 390/800/1440px for Owner/Manager/Cashier
  and ready/empty/loading/offline/error/TOO_LARGE/denied, without document horizontal
  overflow; zero runtime exceptions. Additional short/200% text reachability checked.
- Interaction assertions passed: whitespace/2/3/240/241 reason limits; cancel without
  write; duplicate-submit disabling; disable+revoke; enable still revoked; restore
  pending/unapproved; independently loaded approval examples; role-null/current-device
  restoration restrictions; manager peer/audit/device restriction; conflict, uncertain
  commit and ack+read failure requiring refresh; stale scope callback rejection.
- Two fictional PNGs generated and visually inspected. Static unique document IDs,
  local links/assets, no network/storage APIs, expected artifacts and `git diff --check`
  passed. These are browser references, not Android screenshots or hosted evidence.

No Gradle/native/Room/ADB/hosted tests were rerun for this documentation-only milestone.
The prior native report build/device results remain historical. No real staff accounts,
passwords, access changes or SQL were used. Normal-app provider/security/lease/device/
physical tablet/IME/TalkBack, release signing and M7 recovery gates remain open.

Exact next milestone: native STAFF-UI-01–04 presentation implementation from
STAFF_SCREEN_DESIGNS.md, preserving manual provisioning and current server contracts.

## Native staff/access presentation — 15 November 2026 (STAFF-UI-01–04)

Implemented S-01–S-03 in StaffScreen/StaffViewModel and added StaffSession. Session
keeps tab/target/reason only in scoped memory, invalidates the generation and clears
state without waiting for cancelled IO, and rejects obsolete callbacks. ViewModel validates full returned roster scope/identities,
rechecks current authority around IO, preflights captured target/version before the
existing expected-version mutation, and guards result/error/finally by generation.
Captured immutable targets determine intended access; no inverse refreshed-row toggle.

Mutation acknowledgement is separate from roster readback: SAVED requires ack and
validated readback; ACK_REFRESH_FAILED means refresh only; uncertain response or
cancellation after dispatch blocks replay until fresh review; CONFLICT requires new
review. Offline/scope loss hides sensitive staff data and clears drafts, never queues
an access mutation. Existing manual provisioning, profile/membership/device separation,
expected-version RPCs and reason validation remain unchanged.

Compose now has Anggota/Perangkat/Owner-only Aktivitas akses tabs, virtualized separated
rows, localized role names, profile versus membership labels, revoked-first device
state and selectable full identities. Wide windows use a guidance pane; large text
uses a single readable list. Bounded dialog/sheet bodies scroll with IME padding and
fixed action buttons; Cancel restores originating action focus. Existing A-07/A-08
is composed once in Perangkat with independent load/result state and presentation busy
gates. MainActivity/AuthScreen/DeviceRequestsPanel wire that gate without changing APIs
or approvals. No dependency, Room/schema/migration/backend deployment changes.

Added StaffSessionTest, StaffViewModelTest, StaffPresentationTest and isolated
StaffScreenTest fixtures; updated existing ManagementScreenTest for local tabs and
confirmation semantics. Updated README, decisions D114–D116, STAFF_SCREEN_DESIGNS,
UI_SCREEN_PLAN, UI_UX and IMPLEMENTATION_PLAN. Structural review used prior graph
search/trace/snippets, refreshed index readiness and clean best-effort coverage for
relied-on Kotlin paths, plus direct reads. No SQL/security-surface audit this iteration.

Executed checks:

- `./gradlew :app:compileDebugKotlin --offline --max-workers=2 --console=plain` passed.
- `./gradlew :app:testDebugUnitTest --tests '*Staff*Test' --offline --max-workers=2
  --console=plain` passed all 22 then-current tests: 3 session, 12 ViewModel, 7 Compose.
  Covered reason bounds/untruncated draft, profile/current-device/role-null restrictions,
  duplicate taps/captured intent, fresh-version conflict, server conflict, ack+read
  failure, uncertain response/cancellation, offline clearing, old IO, backend change,
  failed refresh, reviewer blocking, tabs/device priority and 200% tablet text.
- Added one further old-error/new-outlet/finally regression to the full suite.
- Full `./gradlew :app:assembleDebugAndroidTest build --offline --max-workers=2
  --console=plain`: BUILD SUCCESSFUL in 9m 3s using Android Studio JBR via JAVA_HOME.
  Debug: 263 passed. Release: 254 passed, 9 expected demo-only skips; no failures/errors.
  Includes all 23 staff regressions (3 session, 13 ViewModel, 7 Compose), including
  the additional late-old-error/new-outlet/finally case. Debug/release/test APKs built.
  Lint: 0 errors, 54 warnings; 8 additional warnings are now-unused legacy staff copy
  resources retained for later cleanup. Log: app/build/staff-build.log.
- Debug and test APKs installed with `adb install -r -t` on Samsung SM-M146B, preserving
  app data. Targeted command `adb -s <device> shell am instrument -w -e class
  com.dauntehnusantara.pos.StaffScreenTest,com.dauntehnusantara.pos.ManagementScreenTest
  com.dauntehnusantara.pos.test/com.dauntehnusantara.pos.ComponentTestRunner` passed:
  OK (10 tests), 19.4s. Seven new staff and three existing management fixtures passed.
  Log: app/build/staff-device-tests.log. App force-stopped afterwards so next manual
  launch runs normal PosApplication; no data clear/uninstall was used.
- Final `git diff --check` passed. No real staff/credential/access workflow was used
  to manufacture automated results.

Normal-app acceptance remains: hosted Owner/Manager/RLS and concurrent operator
changes, approval + real Keystore/offline revocation, process recreation, physical
phone/tablet/split-window/IME/TalkBack, release signing and M7 restore/recovery gates.
Component fixtures use fake records and plain Application without production startup
sync; they do not validate those end-to-end controls. No real staff access mutations,
accounts or credentials are needed for the component checks.

Exact next milestone: detail T-01–T-04 settings/recovery/sign-out and existing X-03
recovery-export screens before their native presentation redesign; retain current
export/recovery boundaries and add no automatic reset/import/restore capability.

## Settings/recovery detailed design — 15 November 2026 (T-01–T-04 / X-03)

Completed one design/handoff milestone. Added SETTINGS_SCREEN_DESIGNS.md with account/
access, scoped push/pull status, local recovery export, restored-server and sign-out
layouts/state contracts. Added design/settings-flow.html and fictional phone/tablet
captures. Updated README, UI_UX, UI_SCREEN_PLAN, IMPLEMENTATION_PLAN and D117–D119.
No Android, dependency, Room/schema, backend or production deployment changes.

Source verification used codebase-memory index readiness, search (43 results, no
remaining page), RecoveryPanel caller/callee trace and exact snippets plus targeted
source reads. Relied-on Kotlin paths had clean best-effort coverage metadata. Read
BACKUP_RESTORE_RUNBOOK and current settings/auth, sync, archive and test anchors;
no exhaustive security/SQL audit or new hosted evidence is claimed.

Preserved boundaries: exact account/outlet/device/role/project scope; push delivery
separate from last successful pull; scheduled work is not delivered/backup; missing
counts are not zero. Existing recovery JSON keeps original operations/draft, checksum
and 16 MiB UTF-8 bound without credentials or automatic import. Proposed native capture
freezes validated archive before picker (current code prepares scope only), keeps
callback authority in memory and handles cancel/process/scope/provider loss. Sign-out
warns about retained work and unknown counts, does not require export or clear Room.
Recovery-required remains after login/export and never offers reset/rebootstrap.

Executed verification:

- `node --check /tmp/settings-flow.js` passed for extracted reference JavaScript.
- `node --check /tmp/daunteh-check-settings-flow.mjs` and
  `node /tmp/daunteh-check-settings-flow.mjs` passed against isolated local headless
  Chrome. Temporary harness checked 63 viewport/role/scenario combinations at
  390/800/1440px for Owner/Manager/Cashier and ready/empty/loading/error/offline/
  recovery/denied without document horizontal overflow; zero runtime exceptions.
- Interaction assertions passed: scheduling leaves delivery counts unchanged;
  capture-before-picker metadata stays frozen when queue changes; cancellation,
  size-bound and provider failure clear prepared intent; late old-scope write result
  is ignored; offline disables network actions but permits authorized local export;
  access refresh leaves restored-server gate intact; no reset/import/server-restore
  action; unknown-queue sign-out warning, cancel/export-return and explicit sign-out
  without deleting simulated retained work. Large text applies to page and modal;
  short-window confirmation actions remain reachable.
- Two fictional PNG captures generated and visually inspected. Static unique document
  IDs, local links/assets, no network/storage APIs, expected artifacts and final
  `git diff --check` passed. Browser stores only fictional metadata; it does not create
  or inspect real archives, schedule work or sign out an actual Android session.

No Gradle/native/Room/ADB/hosted tests rerun for this documentation-only milestone;
previous native staff build/device results remain historical. Actual document-provider,
Keystore/Hilt/lease, WorkManager concurrency, retained-data sign-out/re-login, physical
phone/tablet/IME/TalkBack, hosted restore/signing and M7 recovery acceptance remain open.
No credentials, live accounts, device data deletion, real exports or restore performed.

Exact next milestone: native SETTINGS-UI-01–04 presentation implementation from
SETTINGS_SCREEN_DESIGNS.md; preserve existing recovery/auth/sync/data contracts.

## Native settings/recovery — 15 November 2026 (SETTINGS-UI-01–04)

Implemented SettingsViewModel/SettingsRoute/SettingsScreen and replaced the production
inline AccessPortal settings branch. Groups separate account/access, scoped push/pull
status, recovery archive and session confirmation. Wide windows use two columns;
large text collapses. Bounded scrolling dialogs/sheets keep actions visible and
request focus restoration on Cancel/return to export. Shell context is reused once.
MainActivity/AuthScreen wire the new route without new permissions or onboarding.

Hardened SyncStatusViewModel with full PullScope binding, stale observation/error/
retry guards, clear-before-cancel behavior, explicit scheduling outcome and no unknown
scope enqueue. Missing counts are not zero; sums use Long. Existing scoped repository
retry force-enqueues work; UI never treats scheduling as delivery. Last-pull time is
labeled download, and recovery-required prevents retry without offering reset/import.

Added RecoverySnapshot metadata at the domain/data boundary without changing existing
capture String callers, archive schema/checksum/16 MiB UTF-8 limit or Room transaction.
Capture/size checks run on IO before CreateDocument. Memory-only frozen bytes/source/
ticket correlate results; only opaque callback ID is saved for configuration recreation.
Null/launch/navigation/process/scope loss consumes prepared intent. Provider writes
and close run on IO, current authority is checked before/after, and interrupted or
failed writes cannot claim success. Foreground validation is awaited, not bypassed.
Confirmed repository capture denial clears visible scope. Superseded scope-only
RecoveryPanel/RecoveryViewModel code was removed, not retained as another export route.

Sign-out confirms current account/outlet and warns about retained or unknown work,
allows Cancel/return to export, never requires successful export and never deletes
Room. Current AuthState controls leaving the shell; remote logout remains best effort.
Auth, archive, sale/outbox, endpoint, schema and library-version policies are retained.
No migration, automatic rebootstrap/reset/import, production deployment or real restore.

Added SettingsViewModelTest, SettingsSyncTest, SettingsPresentationTest and isolated
SettingsScreenTest; extended existing PullSyncTest to check archive snapshot metadata
and preserved operations. Updated README, decisions D120–D122, UI_UX, UI_SCREEN_PLAN,
IMPLEMENTATION_PLAN and SETTINGS_SCREEN_DESIGNS section 9. Structural source review
reused graph search/trace/snippets, refreshed index readiness and clean best-effort
coverage for relied-on Kotlin paths, with direct verification reads. No SQL audit.

Verification so far:

- `./gradlew :app:compileDebugKotlin --offline --max-workers=2 --console=plain` passed.
- `./gradlew :app:testDebugUnitTest --tests '*Settings*Test' --tests '*PullSyncTest'
  --offline --max-workers=2 --console=plain` passed all 47 then-current tests: 14
  settings VM, 7 sync VM, 7 Compose and 19 existing/extended Room/pull tests.
- Final review added a confirmed capture-denial scope-clear guard and one regression;
  an intermediate full build was intentionally cancelled (exit 130) before completing.
  The final build also includes focus restoration and bounded modal dismissal gates.
- Final `./gradlew :app:assembleDebugAndroidTest build --offline --max-workers=2
  --console=plain`: BUILD SUCCESSFUL in 5m 4s, using Android Studio JBR via JAVA_HOME.
  Debug: 292 passed. Release: 283 passed with 9 expected demo-only skips, no failures/
  errors. Includes all 29 settings regressions (15 settings VM, 7 sync VM, 7 Compose)
  and extended real Room archive metadata checks. Debug/release/test APKs assembled.
  Lint: 0 errors, 45 warnings. Final log: app/build/settings-build-final.log.
- Debug/test APKs installed successfully using `adb install -r -t` on connected
  Infinix X6873; app data preserved. Targeted command:
  `adb -s <connected-device> shell am instrument -w -e class
  com.dauntehnusantara.pos.SettingsScreenTest,com.dauntehnusantara.pos.SyncStatusTest
  com.dauntehnusantara.pos.test/com.dauntehnusantara.pos.ComponentTestRunner`:
  OK (9 tests), seven settings and two existing sync UI fixtures.
  Log: app/build/settings-device-tests.log. Target app force-stopped afterwards so
  next manual launch runs normal PosApplication. No data clear/uninstall.
- Final `git diff --check` passed. No data clear/uninstall or real credential/export/
  sign-out/sale/access mutation was used to manufacture test results.

Remaining acceptance: normal MainActivity/Hilt and real auth/Keystore/offline grants,
actual document-provider cancel/rotation/process loss/partial files, sign-out/re-login
with retained work, concurrent workers, physical tablet/split-window/IME/TalkBack,
hosted RLS/restore/signing and remaining M7 gates. Component fixtures use fake scoped
records and plain Application, not production startup sync; they do not prove these
end-to-end controls. No real credentials/export/sign-out/sale workflow used for fixtures.

Exact next milestone: cross-screen normal-application acceptance on the connected
phone and M7 release checklist review, preserving retained data and recording actual
provider/auth/offline/sign-out results separately from component tests.

## M7 normal-app readiness review — 15 November 2026

Reviewed M7_RELEASE_CHECKLIST/M7_SECURITY_REVIEW and normal application startup.
The user confirmed the connected app uses the brand's working Supabase project.
Scope stays read-only navigation and cancellation: no fabricated sales, catalog/access
changes, actual sign-out, hosted SQL/restore or data reset. Authenticated checks are
waiting for sign-in directly on the phone; credentials are not requested in chat.

Executed normal-app checks on Infinix X6873, API36, 4096-byte pages:

- `adb shell am start -W -n com.dauntehnusantara.pos/.MainActivity`: Status ok,
  COLD, TotalTime439ms/WaitTime445ms. One observation, not a performance benchmark.
- Normal PosApplication/Hilt/Activity reached Masuk with recognized Email/Kata sandi
  labels. A filtered hierarchy printed only known labels/coordinates and app-node
  counts, no credential/account/business text or screenshots. App process remained
  running. It was not cleared/uninstalled or replaced by the component runner.
- Production startup schedules normal existing WorkManager jobs as designed; this
  is not a claim that startup background traffic was suppressed.

Executed independent local readiness checks:

- `npm test --prefix supabase`: 52 passed, 0 failures/skips, isolated PGlite fixtures;
  app/build/m7-backend-tests.log. No working-project credentials or hosted operations.
- `python3 -m unittest discover -s scripts -p 'test_verify_android_artifacts.py'`:
  4 tests passed (ELF32/64/both endian formats, low/non-power alignment rejection,
  missing load segments, malformed/truncated bounds).
- `python3 scripts/verify_android_artifacts.py --mapping
  app/build/outputs/mapping/release/mapping.txt --zipalign
  /Users/agustro/Library/Android/sdk/build-tools/36.0.0/zipalign`: passed; artifact
  fingerprints and eight native library PT_LOAD alignment records in generated
  app/build/m7-artifact-check.json. All eight libraries uncompressed, alignments>=16384.
- Build-tools36.0.0 zipalign `-c -P 16 4` passed for current release APK. Debug APK
  apksigner verification passed (v2, one signer). Unsigned release verification failed
  as expected: no signing manifest; no keystore/certificate configured or read.
- AAPT2 release metadata confirmed app ID/version1/0.1.0/min26/target36 and manifest
  backup/full-backup/cleartext false. SHA-256/size values are in M7_DEVICE_ACCEPTANCE.
- `git diff --check` passed. No Gradle rerun: Android sources/artifacts unchanged in
  this review. Prior 292 debug/283 release+9 skips and 9 UI fixtures remain historical.

Added scripts/verify_android_artifacts.py, its four tests, Python cache ignore rules
and docs/M7_DEVICE_ACCEPTANCE.md. Updated README, implementation plan, release
checklist and D123–D125. Graph source review reused task-directed exploration and
fresh best-effort coverage for relied-on application/test paths. This is not a full
source-security audit. No Android library/schema/version changes or deployment.

Remaining: authenticated read-only navigation, real picker and confirmation Cancel
require device sign-in. Completed provider writes/rotation/process loss, OS credential/
lease, live concurrency/recovery, signed/minified runtime, API26/tablet/16 KB/TalkBack
and performance/operator gates remain open. The current unsigned artifact and alignment
checks do not establish production readiness. No required release checkbox was closed
using weaker fixture/readiness evidence.

Exact next step: once signed in on the phone, complete the read-only authenticated
screen and cancellation matrix in M7_DEVICE_ACCEPTANCE.md; use a controlled acceptance
setup for live money/access/recovery tests and operator-approved signing/release gates.

## Signed-in M7 follow-up and refresh fix — 15 November 2026

The user signed in directly on the Infinix. Continued the working-project pass with
read-only navigation/cancellation, no test sale/catalog/staff/device/restore or actual
logout. Credentials stayed on the device. Filtered UI inspection logged only fixed
labels/coordinates/booleans, never account/business content or screenshots.

Normal app observations:

- Opened Ringkasan, Katalog, Laporan, Staf and Setelan. Owner-only audit tab was
  visible in this session; no other role's navigation/authorization is claimed.
- Sign-out confirmation opened and Cancel retained the session. Recovery explanation
  opened and Cancel did not prepare/write an artifact. These were normal app actions,
  not plain-Application component fixtures.
- Real authorized JSON capture opened Android DocumentsUI; Back cancelled it without
  choosing a destination. No completion message appeared, export re-enabled and
  Settings remained visible. No private export file was saved by the test. Logs:
  app/build/m7-normal-navigation.jsonl and app/build/m7-normal-cancellations.log.
- Found periodic navigation reset. AuthViewModel refreshes every60s; loadChoices
  emitted OUTLETS even for unchanged confirmed context, removing the shell and risking
  pending export loss during foreground validation. Fixed AuthRepositoryImpl to retain
  READY/busy only after live choices confirm same brand/outlet/role/account/enrolled
  user. Real scope/role change, device denial and existing failure policies remain.
- Updated debug APK installed with `adb install -r -t`, data/session preserved.
  Normal launch restored the signed-in session without credentials. Initial UI lookup
  ran before restore finished and was retried after READY; no login bypass was used.
- On the fixed app, sign-out and explanation Cancel passed again. JSON picker Cancel
  retained Settings; explicit online validation retained Settings. Settings stayed
  visible for >70s across the nominal periodic interval. No performance budget implied.
- Report-filter/staff follow-up was interrupted when foreground left POS. The process
  remained alive, crash buffer had no app crash record, and a later package check
  confirmed another foreground app. Do not count those interrupted assertions as
  a pass or an app crash. No further UI taps were sent; the temporary helper was
  tightened to require the POS package before a tap. Resume when the phone is idle.

Executed fix verification:

- AuthRepositoryTest: 27 passed, including 3 new regressions for READY/busy during
  same-context lease validation, clearing old context before role-change validation,
  and confirmed device denial still locking access.
- `JAVA_HOME='<Android Studio JBR>' ./gradlew :app:assembleDebugAndroidTest build
  --offline --max-workers=2 --console=plain`: BUILD SUCCESSFUL in 5m22s. Debug295
  passed; release286 passed +9 expected demo-only skips, no failures/errors. Lint0
  errors/45 warnings. All APKs built; app/build/m7-refresh-build.log.
- Reran the artifact verifier and build-tools36.0.0 ZIP16 KB check for the changed
  unsigned release: eight native libraries passed ELF checks. New debug APK signature
  verifies (v2, one signer). Updated APK/mapping SHA-256 values and limits recorded in
  M7_DEVICE_ACCEPTANCE.md / generated app/build/m7-artifact-check.json. Release still
  unsigned; current phone still4 KB, not 16 KB runtime evidence.

Changed AuthRepositoryImpl/AuthRepositoryTest and updated M7 acceptance/security/release
records, README, implementation plan and D126. Existing schema, dependencies, offline
month policy and server permissions retained. Graph best-effort coverage clean for
relied-on auth/test paths; direct source review confirmed the refresh/periodic path.
No deployment, data clearing, credential extraction or live mutation test. Remaining
controlled provider writes/failures, other roles, offline/OS credentials/process death,
concurrency/restore, signing/API26/tablet/16 KB/TalkBack gates stay open.

Exact next step: resume interrupted read-only filter/staff checks on an idle phone;
use a controlled acceptance setup for completed private exports and write/offline/
other-role/recovery tests. Production release remains unapproved.

## Hourly background sync sweep — 15 November 2026

Changed WorkSyncScheduler.ensurePeriodic from 15 minutes to 1 hour and KEEP to UPDATE,
preserving the push-sales-sweep unique name. UPDATE makes existing persisted entries
adopt the new specification at normal startup without interrupting an active run.
Connected-network constraint and 30-second exponential backoff remain. Startup,
event-driven/manual nudges and separate 60-second access validation are unchanged.
No dependency, Room migration, server/schema or authorization changes.

Read installed WorkManager2.10.2 source for UPDATE semantics. Graph lookup/snippet/
exact trace and best-effort coverage used for SyncWorker.kt; direct source verified
scheduling. Added WorkSyncSchedulerTest using real WorkManager persistence with no
schedulers so no sync/network worker executes. It seeds the old 15-minute unique work,
checks interval/network/backoff update and stable identity/no duplicate after repeat.
Updated SYNC_PROTOCOL, README, implementation plan and D127.

Executed `./gradlew :app:testDebugUnitTest --tests '*WorkSyncSchedulerTest'
--tests '*PushSyncTest' --tests '*PullSyncTest' --offline --max-workers=2
--console=plain`: BUILD SUCCESSFUL, all 28 tests passed (1 schedule update, 8 push,
19 pull), no failures/skips. Log: app/build/hourly-sync-tests.log.
Local `./gradlew :app:assembleDebug --offline --max-workers=2 --console=plain` passed
in 3s. Log: app/build/hourly-sync-build.log. Final `git diff --check` passed. No full release/lint suite rerun for
this bounded scheduler change; previous full-build evidence remains historical.
No ADB, installation, device taps or working-project operations were performed;
device checks remain paused at the user's explicit request. Next: install the updated
APK when appropriate, then resume device checks only when requested. Production
signing/hosted/provider/offline and other M7 acceptance gates remain open.

## Solid launcher background — 15 November 2026

Updated mipmap-anydpi/ic_launcher.xml background from transparent to fixed Android
white. Removed drawable-night/ic_launcher_foreground.xml so both themes resolve the
original green foreground; this avoids a white mark on the new white backdrop.
Original transparent brand_logo bitmap, foreground inset, manifest normal/round icon
references and in-app branding/theme remain. No bitmap regeneration or dependency,
schema/permission changes. Updated README and D128.

Local `./gradlew :app:assembleDebug --offline --max-workers=2 --console=plain`:
BUILD SUCCESSFUL in 6s; app/build/solid-launcher-build.log. Static XML/background/
foreground/day-night fallback checks and `git diff --check` passed.
This is a resource-only change; no new implementation-mirroring test
or full unit/release suite. No ADB, device install or launcher manipulation while
phone checks are paused. Physical Samsung/Infinix launcher appearance remains to
verify after installation. Current debug artifact supersedes earlier local debug
fingerprints; historical M7 runtime/signing acceptance is not extended by this build.

## Shared components and exact IDR input — 15 November 2026

Implemented core/designsystem/components/Components.kt and FormFields.kt. Shared
headers, sections, notices/errors, rows, empty/identity presentation, text/money fields,
primary buttons and adaptive confirmation now use the existing Material3 theme tokens.
Adopted across catalog, POS, reports, staff and settings. Catalog text/integer and POS
quantity share AppTextField; product/option price and cash tender use MoneyInputField.
Staff/settings confirmations share bounded dialog/sheet body/actions and busy gating.
Feature-owned targets, reasons, focus restoration, role checks and callbacks remain.
No generic domain form engine or component-owned business state/network access.

MoneyInputField shows Rp and dot grouping over raw digit text. Strict valid grouped/
Rp paste normalizes to digits; decimals/negative/malformed/overflow remain visibly
invalid. No Float/Double, decimal rounding, truncation into another amount or blank
coercion to zero. Long parsing and existing catalog/checkout validation remain; no
sale snapshots, repositories, schemas, permissions, sync policy or dependency changes.
String-based offset mapping preserves editing/selection around grouping separators.
Quantity, order and modifier bounds keep ordinary integer semantics.

Added MoneyInputTest (4 tests) and SharedComponentsPresentationTest (3 tests). Added
UI_COMPONENTS.md with API/adoption/input contracts; updated UI_GUIDELINES, README,
implementation plan and D129–D130. Task-directed graph search/coverage and direct source
reads used; a partial StaffScreen parse line was read directly. Metadata is best effort,
not an exhaustive architecture/security audit. Existing documentation preserved.

Executed checks:

- Initial `:app:compileDebugKotlin --offline --max-workers=2 --console=plain` passed.
- Existing migrated-screen/formatter tests passed. New cursor-injection test first
  required an ExperimentalTestApi opt-in; corrected in the test, not suppressed.
- `./gradlew :app:testDebugUnitTest --tests '*MoneyInputTest'
  --tests '*SharedComponentsPresentationTest' --tests '*PosPresentationTest'
  --tests '*CatalogEditorPresentationTest' --tests '*StaffPresentationTest'
  --tests '*SettingsPresentationTest' --tests '*ReportPresentationTest'
  --offline --max-workers=2 --console=plain`: 55 tests passed, no failures/skips.
  Includes strict paste, whole-IDR/Long bounds, every cursor offset for lengths0–80,
  middle insertion/backspace/clear/paste, invalid decimal preservation, busy submit,
  and existing compact/tablet/large-text/permission/command/checkout regressions.
  Log: app/build/components-tests.log.
- Full `./gradlew :app:assembleDebugAndroidTest build --offline --max-workers=2
  --console=plain` initially passed in 5m23s: debug303 passed, release294 passed +9
  expected demo-only skips; lint0 errors/46 warnings. One newly introduced shared-header
  modifier-parameter ordering warning was corrected without suppression. The final same
  build command passed in 5m51s: debug303 passed, release294 passed +9 expected skips,
  lint0 errors/45 existing warnings. Final log: app/build/components-build-final.log.
  Initial log: app/build/components-build.log. Uses Android Studio JBR via JAVA_HOME.
  Final source also includes shared report-row separators and the generic POS quantity field.
- Final `git diff --check` passed.

No ADB, device installs or real account/business operations; device checks remain
paused by the user. Physical IME/cursor/TalkBack/phone/tablet and normal-app acceptance
remain separate from host regressions. New local artifacts supersede previous M7
fingerprints; release signing/hosted/provider/recovery gates remain open.

Exact next step: review/adopt these shared APIs for remaining specialized surfaces
where contracts fit, and resume normal-device money/IME/accessibility acceptance only
when requested. No role/financial/server capability was added by this refactor.

## Cash tender shortcuts (2026-11-15)

Added five fixed cash-received buttons to CheckoutForm, plus retained Uang pas.
Buttons replace the raw whole-IDR draft and wrap on narrow/large-text layouts, with
48dp minimum height. Existing insufficient-cash/change/submit rules remain; controls
are disabled while saving and absent for QRIS. No schema/server/dependency change.
Added host regressions for replacement, insufficient cash, exact tender submitted,
change, exact total, saving/QRIS guards and large-text tablet reachability.

Verification: offline `:app:testDebugUnitTest --tests '*PosPresentationTest'
--tests '*MoneyInputTest' :app:assembleDebug :app:lintDebug --max-workers=2
--console=plain` passed in 2m9s using Android Studio JBR. All 22 targeted tests passed;
lint has zero errors and 45 existing warnings. Log: app/build/cash-shortcuts-checks.log.
`git diff --check` passed. No ADB/install
or real business operations; device checks remain paused. Next: physical money/IME
and accessibility acceptance only when the user resumes device checks.

## Resumed device check: cash shortcuts (2026-11-15)

User resumed ADB checks and confirmed the phone was idle. Installed the current
app/build/outputs/apk/debug/app-debug.apk with `adb install -r` successfully on the
connected Samsung SM-M146B, preserving app data. Launched MainActivity and inspected
visible controls with UI Automator. Normal app opened authenticated Ringkasan as
Pemilik with validated access; navigation exposes Ringkasan/Katalog/Laporan/Staf/
Setelan, with no Kasir route for this role. Thus physical cash-shortcut/IME acceptance
was not executed. No sale, draft, membership or account/session changes were made.
Next: sign in directly on the phone with an existing authorized Cashier account to
verify the cash shortcuts without saving a sale. Host tests and debug build results
from the cash-shortcut iteration remain valid; this check does not close M7 gates.

## In-app staff account creation (2026-11-15)

Implemented protected Edge staff-admin handler plus optional initial staff-provisioning
SQL setup: live approved-device/role checks, selected outlet, immutable reserved UUID,
existing-email refusal, 10/hour actor reservation rate, atomic profile/membership/audit
finalization and server-wide temporary-password gate. Owner can create Manager/Cashier;
Manager only Cashier. Added memory-only adaptive Tambah staf form with stable uncertain
retry, ack/readback distinction, masked credentials and first-password replacement screen.
Existing device approval, offline lease, manual provisioning and Owner bootstrap remain.

Executed checks:

- Initial `:app:compileDebugKotlin --offline --max-workers=2 --console=plain` passed
  in 1m58s. Log: app/build/staff-create-compile.log.
- First targeted Android run: 59 tests, one failure in an old exact-login-call-list
  assertion. Updated it to include the new read-only password-status check. No
  production behavior or test was suppressed to pass the assertion.
- Final targeted `:app:testDebugUnitTest --tests '*Staff*Test' --tests '*AuthRepositoryTest'
  :app:assembleDebug --offline --max-workers=2 --console=plain` passed in 3m8s:
  64 tests, zero failures/errors/skips. Covers role/UTF-8 validation, scope loss,
  duplicate submit, uncertain frozen retry, rejected-password correction, ack/readback
  failure, proof/ack scope, legacy backend, password gate and compact/large-text forms.
  Log: app/build/staff-create-tests-final.log. Android Studio JBR via JAVA_HOME.
- `npm test --prefix supabase`: 64 passed, zero failed/skipped (42.3s). Includes all
  existing isolated backend suites, five new SQL provisioning tests and seven new
  mocked Edge tests (JWT/role/proof ordering, Auth marker/retry/conflict/rate/revocation,
  password gate/reauth/update ordering and lost Auth create response). No live backend.
  Log: app/build/staff-create-backend-verified.log.
- Full `:app:assembleDebugAndroidTest build --offline --max-workers=2 --console=plain`
  completed successfully on the final invocation (52s, 159 tasks up-to-date). First
  invocation completed the debug suite/lint and compiled release/instrumentation code,
  but its command session ended before recording the release test result; the final
  invocation completed release tests. Final XML: debug320 passed, release311 passed +9
  expected demo-only skips; zero failures/errors. Lint: zero errors/45 existing warnings.
  Logs: app/build/staff-create-build.log and app/build/staff-create-build-final.log.
- `git diff --check` passed. Supabase config explicitly keeps staff-admin JWT verification
  enabled. A convenience config parse using system Python was unavailable because that
  Python lacks tomllib; no Deno/CLI validation is claimed.
Deno/Supabase CLI is not installed here; no actual Deno serve or hosted deployment
was run. No real accounts, memberships, passwords, sales or device state were changed.
Setup and operational failure handling: IN_APP_STAFF_PROVISIONING.md.

Final handoff: Android, domain, gateway and SQL/Edge source plus setup/runbook are
complete for this milestone. No APK install/ADB checks were performed for provisioning.
Exact next milestone: deploy the optional SQL/Edge setup to a disposable hosted project
and execute controlled two-role/first-login/device-approval acceptance. Applying it to
the brand's working project requires explicit deployment approval; do not rerun/reset
schema.sql or create fabricated staff in the working project. No production readiness,
hosted Auth/Edge integration, Deno runtime or physical accessibility acceptance claimed.

## Owner cashier and history access (2026-11-15)

Implemented Owner Kasir/Riwayat alongside existing management destinations, retaining
Overview default and Owner/outlet header context. Compact Owner navigation uses the
existing scrolling drawer; tablet rail remains. History now accepts matching Owner
scope and labels its authorized selected-outlet cache explicitly; Manager history
remains unavailable and Cashier own-history behavior is retained. Checkout already
uses actual authenticated Owner user/device/outlet identity and server guards; no
role impersonation, schema/RPC/dependency/deployment change.

Added shell navigation/restoration/large-text coverage, Owner history label/role tests,
Room receipt/outbox/restart/initial-sync/scope-lock test, and isolated server Owner
submit/replay/actual-actor/proof/approval/revocation coverage. Verification running;
results will be recorded below. No live sale or hosted permission changes. Resumed previously authorized ADB checks
after successful builds; device result recorded below.

- Targeted `:app:testDebugUnitTest --tests '*AuthenticatedShellTest'
  --tests '*OfflineLeaseTest' --tests '*History*Test' --tests '*PullSyncTest'
  --tests '*AuthenticatedPosViewModelTest' :app:assembleDebug --offline
  --max-workers=2 --console=plain` passed in 1m36s: 81 tests, zero failures/skips.
  Android Studio JBR via JAVA_HOME; log app/build/owner-cashier-tests.log.
- `npm test --prefix supabase` passed: 65 tests, zero failures/skips (15.1s).
  Includes Owner submit/replay/actual actor/approval/revocation against isolated PGlite;
  no live backend. Log app/build/owner-cashier-backend.log.
- Full `:app:assembleDebugAndroidTest build --offline --max-workers=2 --console=plain`
  passed in 6m43s: debug323 passed, release314 passed +9 expected demo-only skips,
  zero failures/errors. Lint: zero errors/45 existing warnings. Instrumentation APK
  compiled; no instrumentation run claimed. Log app/build/owner-cashier-build.log.
- `git diff --check` passed.
Exact next step: normal-app Owner navigation and unsaved input acceptance when the
phone is available; existing hosted provisioning/signing/accessibility gates remain.

Device follow-up: prior ADB/idle authorization remained active. `adb install -r`
succeeded on Samsung SM-M146B, retaining app data; MainActivity opened with validated
Pemilik access and the navigation-menu header. A menu-check tap coincided with the
notification shade; dismissed it once. Subsequent hierarchy showed the actual Owner
payment screen with cash presets and calculated change. No product/cart/payment
input or save command was sent by this check. Stopped further taps with the current
payment untouched. Physical Riwayat/menu traversal, rotation, IME and TalkBack remain
unverified; host regressions cover navigation/history. The temporary device hierarchy
was removed. No real sale, account, membership or backend change was performed.
The installed build includes the earlier staff-provisioning code; that feature still
requires its separately documented SQL/Edge deployment.

## Per-outlet staff count limits (2026-11-16)

Implemented admin-only outlet_staff_limits setup and example configuration; independent
Manager/Cashier caps with disabled/pending counting and deduplicated reserved conversion.
Reservation/member insertion and role/outlet changes serialize through a real limit-row
update. Existing users/reserved slots survive lowering; missing configuration blocks new
creation. Added scoped roster counts, read-only quota notices, role/submit guards and
allowlisted bounded HTTP quota errors, preserving same-operation uncertain retry.
No Super Admin app role, quota editor, Room migration, hosted deploy or working values.

Checks running. Initial Kotlin compile passed (55s). Initial isolated quota test run
had one duplicate synthetic-profile fixture error; corrected fixture to update the
existing profile. Full backend suite then passed 71 tests (25.6s). Targeted Android
`:app:testDebugUnitTest --tests '*Staff*Test' --tests '*AuthRepositoryTest'
:app:assembleDebug --offline --max-workers=2 --console=plain` passed in 1m2s:
72 tests, zero failures/errors/skips; log app/build/staff-limits-tests.log. Subsequent
bounded error-stream tests and final role-default/error-state refinements are included
in the full `:app:assembleDebugAndroidTest build --offline --max-workers=2
--console=plain`, which passed in 6m7s. Final XML: debug333 passed, release324 passed
+9 expected demo-only skips; zero failures/errors. Lint zero errors/45 existing warnings.
Debug/release and instrumentation APKs compiled; no instrumentation run claimed.
Log app/build/staff-limits-build.log. Android Studio JBR via JAVA_HOME.
Final `git diff --check` passed. No ADB/real account/sale/permission changes in this iteration.
No native PostgreSQL/psql/Docker/Deno/Supabase CLI available; true independent-connection
concurrency remains a hosted acceptance gate. See STAFF_LIMITS.md for exact setup.

Exact next milestone: apply optional quota setup and actual per-outlet counts in a
separately approved disposable hosted project, deploy the updated Edge Function and
verify independent-connection last-slot/admin-edit races plus real Auth/physical UI.
Working-project deployment/configuration and any test staff creation require explicit
approval. No working Supabase data or device app was changed by this quota iteration.

## Drawer identity and compact app bar (2026-11-16)

Added logo/verified brand name followed by signed-in email, role and selected outlet
inside the scrollable drawer. Long identity text wraps; dark surfaces use a white
logo tint. Removed the full access-status row from the app bar, retaining outlet/role
and existing actions with an accessible 48dp status icon. Validation shows a spinner,
offline uses a distinct glyph, and a tap explains authorization versus sale sync and
bounded offline/outlet-switch behavior. No new profile/credential storage, permission,
backend, schema, dependency or navigation capability. Status details reuse the shared
adaptive AppConfirmation with scrolling content and fixed close action.

Verification: `:app:testDebugUnitTest --tests '*AuthenticatedShellTest' :app:assembleDebug
:app:lintDebug --offline --max-workers=2 --console=plain` passed in 2m50s using Android
Studio JBR via JAVA_HOME. All 18 shell tests passed, covering drawer brand/account
ordering, dark/long identity, large-text/short-window navigation, status explanation,
offline/busy controls and existing scope/restoration guards. First run found two
ambiguous test selectors after role/outlet also appeared in the drawer; scoped those
to the app bar without suppressing assertions. Final log app/build/drawer-header-checks-final.log;
initial log app/build/drawer-header-checks.log. Lint: zero errors/45 existing warnings.
Final `git diff --check` passed. Release build and physical/TalkBack checks were not run
for this focused presentation change. Device checks/install were not resumed.
Exact next step: idle-device visual/accessibility acceptance of drawer identity and
status details in light/dark/large text, without modifying the working-brand cart.

## Green app bar and saved Light/Dark theme (2026-11-16)

Moved the outlet switch beside the title/role block, keeping its 48dp target and
existing offline/busy guards. Green app bar uses primary/onPrimary in Light and
primaryContainer/onPrimaryContainer in Dark; status/action icons inherit contrasting
content. Added Settings → Tampilan → Terang/Gelap with device-local DataStore storage,
Hilt repository/ViewModel, loading/save/error/retry states and Light default. App root
waits for the initial preference read, applies theme without recreation/navigation-key
changes and adjusts system-bar icon contrast. Login/drawer branding tints white in Dark.
No new dependency, backend/account/Room schema change. Device app was not modified.
Initial `:app:compileDebugKotlin --offline --max-workers=2 --console=plain` passed
(47s). Targeted `:app:testDebugUnitTest --tests '*Appearance*Test'
--tests '*AuthenticatedShellTest' --tests '*Settings*Test' :app:assembleDebug --offline
--max-workers=2 --console=plain` passed in 1m3s: 54 tests, zero failures/errors/skips.
Includes real DataStore file reopen/unknown value, save/read errors and retry,
duplicate-write gating, offline Settings selection, adjacent-switch bounds and
navigation/draft retention after theme recomposition. Log app/build/appearance-tests.log.
Full `:app:assembleDebugAndroidTest build --offline --max-workers=2 --console=plain`
passed in 5m17s: debug343 passed, release334 passed +9 expected demo-only skips,
zero failures/errors. Lint zero errors/45 existing warnings. Instrumentation APK
compiled; no instrumentation/device execution claimed. Log app/build/appearance-build.log.
Android Studio JBR via JAVA_HOME. Final `git diff --check` passed.
Remaining: physical Light/Dark/system-bar/IME/TalkBack acceptance on an idle phone;
no install or device interaction was performed. No ADB or working-brand operations in this iteration.

## Device names in staff approval (2026-11-16)

Added optional device-labels.sql with separate RLS metadata and own-user/secret report
RPC, live reviewer outlet/role read scope, bounded manufacturer/model and no device
version/credential/approval writes. Android reports once per selected device/session
and joins names into pending approvals and staff directory/confirmations, with unknown
fallback and control-character sanitization. Device-label changes do not alter the
authorization-target equality check. No Room/API signature migration or permissions.
Initial Kotlin compile passed (50s); 3 isolated SQL tests passed (6.3s), including
pending reporting without approval/version change and scoped denial.
Targeted `:app:testDebugUnitTest --tests '*DeviceNameTest' --tests '*AuthRepositoryTest'
--tests '*AccessPresentationTest' --tests '*Staff*Test' :app:assembleDebug --offline
--max-workers=2 --console=plain` passed in 1m28s: 87 tests, zero failures/skips. Subsequent
explicit named/fallback approval and staff label assertions are included in the full
build. Log app/build/device-name-tests.log; Android Studio JBR via JAVA_HOME.
Initial parallel `npm test --prefix supabase` had four photo-fixture before-hook 25P02
errors (70/74 passed); a diagnostic rerun passed with no production SQL changes.
The original unmodified suites then passed sequentially with
`node --test --test-concurrency=1 supabase/tests/*.test.mjs`: 74/74 passed, no skips
(86.9s). Log app/build/device-name-backend-final.log. Temporary diagnostic file removed.
Full `:app:assembleDebugAndroidTest build --offline --max-workers=2 --console=plain`
passed in 7m47s: debug346 passed, release337 passed +9 expected demo-only skips,
zero failures/errors. Lint zero errors/45 existing warnings. Instrumentation APK
compiled; no device instrumentation run claimed. Log app/build/device-name-build.log.
After adding the schema-cache NOTIFY, the final device-label SQL suite passed again:
3/3 (13.7s), log app/build/device-name-sql-final.log. Final `git diff --check` passed.
Exact next step: apply optional device-labels.sql to the approved environment, update
and restart reporter/reviewer apps, then perform controlled two-actor name/approval
acceptance. No backend application, APK install or real approval was performed. No working project deployment,
real approvals/accounts/sales or ADB operations in this iteration. Setup: DEVICE_NAMES.md.

## Drawer wording and outlet selector cleanup (2026-11-16)

Removed Menu aplikasi and Tutup menu plus their unused resources and swap vector.
The outlet title/role section now invokes the existing outlet selection callback as
an accessible 48dp-minimum Button, with current outlet/role state description. Existing
offline/validating restrictions remain; green header and access indicator are unchanged.
Drawer retains brand/account identity and navigation, with destination/outside/swipe/
Back dismissal. Added explicit Back handling and outside/Back regression checks.
The open drawer registers its Back callback only while open, taking priority over
feature/detail handlers in the underlying Scaffold composition.

Initial checks exposed test-only touch/Back API mismatches, then an ambiguous Kasir
selector after the role text became clickable; corrected available APIs and scoped
navigation assertions to selectable destinations. The new nested-feature Back regression
also caught a real priority issue; fixed callback registration and reran all 22 shell
tests successfully. Final `:app:testDebugUnitTest --tests '*AuthenticatedShellTest'
:app:assembleDebug :app:lintDebug --offline --max-workers=2 --console=plain` passed
in 1m44s: 22 tests, zero failures/errors/skips; lint zero errors and 45 existing
warnings. Log: `app/build/menu-cleanup-final-verified.log`. Final `git diff --check`
passed. No backend, business-state, permission or device changes. Next: idle-device drawer/selector
TalkBack and touch acceptance with existing outlet selection cancellation.

## Repeated device approval correction (2026-11-16)

Found that sign-in/sign-out erased enrollment proof and outlet changes replaced the
single cached device. Returning without a queued sale generated a new UUID; returning
with a queued sale reenrolled its UUID, rotating proof and resetting non-Owner approval.
The locked screen previously retried only manually/on network reconnect.

AuthCache now retains project/user/outlet enrollment records in existing Keystore
storage. Logout clears active session, membership/device and lease, retaining only
enrollment proof under a fresh key. Login/outlet switches reuse exact matching proof;
leases still validate live roles, membership, revocation and approval. Old active-device
caches upgrade without Room/schema/API changes. Online idle pending screens poll at
15-second intervals; busy/offline/unrelated lock states do not poll. No working-project
reset, backend deployment, APK installation or device checks performed.

Verification: initial sandboxed Gradle invocation could not access its external cache;
reran using SDK/cache access. Focused auth run passed 36 tests. Final
`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --max-workers=2
--console=plain` passed in 3m8s: all 354 debug tests passed (37 AuthRepositoryTest,
2 AuthViewModelTest), no failures/errors/skips. Lint: zero errors, 45 existing warnings.
Final log: `app/build/device-approval-final.log`; `git diff --check` passed.
Coverage includes staff logout/process restart, account/outlet isolation, legacy-cache
adoption, role change, server revocation without automatic replacement, logout offline
lockout and virtual-time polling guards. Release tests and physical/hosted approval
acceptance were not run. Exact next milestone:
controlled two-actor acceptance on updated phones, matching current pending UUIDs,
then reasoned revocation of obsolete duplicates if requested (retain receipt references).

## Double-Back root exit (2026-11-16)

Added a root Activity fallback: first Back shows “Tekan kembali sekali lagi untuk
keluar”; second within two seconds finishes the Activity. Monotonic timing is kept
in memory and reset on Stop, so returning/backgrounding does not retain confirmation.
Registered before Compose so existing navigation/detail/drawer/dialog Back behavior
keeps priority. Does not sign out or clear checkout data. Added timing/reset/priority
regressions. `:app:testDebugUnitTest --tests '*DoubleBackExitCallbackTest'
--tests '*AuthenticatedShellTest' :app:assembleDebug :app:lintDebug --offline
--max-workers=2 --console=plain` passed in 3m15s: 26 tests passed, zero failures/errors;
lint zero errors, 45 existing warnings. Log: `app/build/double-back-exit.log`.
`git diff --check` passed. No device installation or backend changes; release and
physical Back gesture/keyboard acceptance not run.
Next: physical Back-button/gesture acceptance with open drawer, detail and root.

## Bluetooth receipt plan (2026-11-16)

Created BLUETOOTH_PRINTING_PLAN.md based on current Receipt/ReceiptContent, history
and settings routes, manifest/toolchain and official Android Bluetooth documentation.
58 mm paper confirmed; model/protocol/printable width remain unresolved. Planned
paired-device settings, manual checkout/history printing, immutable snapshot renderer,
scoped transport, bounded attempt journal and explicit uncertain-output recovery.
No library/version, runtime code, backend/Room change or device action introduced.
Documentation review and diff whitespace check only; Gradle tests not run for this
plan-only change. Next milestone: PRINT-00 hardware compatibility confirmation, then
settings/test-print slice. Physical printer acceptance is required before compatibility
or successful paper output can be claimed.

## Printer setup and provisional text test (2026-11-16)

Implemented feature/printing domain/repository, DataStore selection per backend/user/
outlet, Hilt wiring, Settings card/permission/pairing/settings-launch recovery and
confirmed fictional 58 mm plain-ASCII test. Native secure RFCOMM uses SIG SerialPort
service; no reflection, insecure fallback, discovery/location permissions or new
library. Bluetooth is optional. A serialized IO sender closes on cancellation/deadline
and distinguishes pre-output failure from possibly partial/unknown output. Scope
changes cancel work and stale outcomes are generation-guarded; no automatic resend.

Generic thermal description does not verify model/protocol. UI clearly marks this
provisional; no receipt/sale payload, checkout integration, backend/Room change,
production record, physical printer operation or APK installation. Persistent receipt
print-attempt journal and receipt renderer/actions remain later milestones after
physical compatibility confirmation. Initial compile found a nonexistent cancel
resource; corrected to existing access_cancel. Initial full test run passed 371 but
lint identified 3 paired-device permission diagnostics and one KTX warning; added
explicit SecurityException mapping and used existing KTX URI conversion. No lint
suppression/baseline was added.

Final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline
--max-workers=2 --console=plain` passed in 3m10s: 371 tests, no failures/errors/skips,
including 13 new printer tests (real DataStore reopen/isolation, duplicate taps,
authorization/scope cancellation, permission retry, timeout/partial-write/cancellation,
ASCII/control safety and compact/tablet confirmation UI). Lint: zero errors and
45 existing warnings. Log `app/build/printer-setup-verified.log`. `git diff --check`
passed. Release and physical Bluetooth/permission/paper acceptance were not run.
Next: pair/test the real printer and verify 58 mm readability/protocol, then PRINT-02
renderer and PRINT-03 durable receipt attempt/integration in a separate iteration.
