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
