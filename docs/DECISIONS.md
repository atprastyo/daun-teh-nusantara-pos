# Architecture decision log

- D001: Native Kotlin/Compose Android for phone/tablet.
- D002: Room-first offline sales, Supabase PostgreSQL shared cloud.
- D003: One brand initially: Daun Teh Nusantara; schema retains brand/outlet IDs.
- D004 (superseded by D047): Supabase Auth invitation-only; owner brand membership, manager/cashier outlet membership.
- D005: Stable UUIDs, integer IDR, append-only completed sales, idempotent sync.

## Pending decisions
- Operational rollout of the selected one-calendar-month offline lease and device approvals (duration resolved by user for M3).
- Outlet roster and assignment.
- Price-change conflict acceptance rules.
- Receipt numbering, cancellation/refund procedure.
- Cloud backup RPO/RTO and retention.

Append new entries with date, decision, alternatives and rationale.

## M0 — 2026-11-13

- D006: Use a single `app` module, namespace/application ID `id.asterfed.dauntehpos`,
  feature-first presentation/domain/data packages and shared `core/` packages.
  The foundation repository has no business use case yet; additional abstractions
  will be added only when required. Alternative: multiple modules now; deferred to
  keep MVP builds and dependency wiring simple.
- D007: Pin the compatible stable baseline below rather than adopt AGP 9's Kotlin
  integration changes during bootstrap. Build using Android Studio JBR 21.0.10,
  targeting Java/Kotlin JVM 17. The shell's default Java 8 cannot run this toolchain.
  Installed SDK Platform 36 and Build Tools 35.0.0 satisfy AGP requirements.

  | Component | Pinned version |
  | --- | --- |
  | Gradle / AGP | 8.13 / 8.13.2 |
  | Kotlin / Compose compiler plugin | 2.2.21 / 2.2.21 |
  | KSP (KSP2 default) | 2.2.21-2.0.4 |
  | Compose BOM | 2025.07.00 |
  | Hilt | 2.57.1 |
  | Room | 2.8.4 |
  | Navigation Compose | 2.9.5 |
  | Lifecycle | 2.9.4 |
  | Activity Compose / Core KTX | 1.9.3 / 1.17.0 |
  | Coroutines | 1.10.2 |
  | WorkManager / DataStore | 2.10.2 / 1.1.7 |
  | JUnit / AndroidX test JUnit / runner | 4.13.2 / 1.2.1 / 1.6.2 |
  | Android min / compile / target SDK | 26 / 36 / 36 |

  Versions are exact; these are a conservative baseline, not a claim of latest
  versions. Gradle wrapper includes the SHA-256 of the installed official 8.13 all
  distribution. Room/Hilt generation uses KSP rather than legacy kapt.
- D008: Room schema v1 contains only `app_metadata` infrastructure, initially empty.
  Observe it through the foundation repository to exercise database/DI wiring.
  Export and preserve the schema. M1 must increment the database version and add an
  explicit migration; never use destructive fallback. No product/sale/outbox
  tables or cloud schema are introduced in M0.
- D009: Use Material 3 navigation bar/rail with actual window constraints and the
  documented 600/840dp breakpoints; avoid device-type checks and orientation locks.
  Provide light/dark palettes, scrolling content and font-scale previews. All
  destinations are clearly nonfunctional placeholders; role enforcement starts M3.
- D010: No Supabase client or credentials in M0. For M3, the official Supabase
  Android quickstart currently gives Kotlin 2.2.21, supabase-kt BOM 3.5.0 and Ktor
  3.0.3 as a candidate combination. Reverify then; it is not installed or build
  validated here. Do not invent endpoints. Disable OS backup until a secure restore
  policy is defined. WorkManager/DataStore are dependencies only until needed.

- D011 (2026-11-13): Explicitly exclude all app storage domains from Android 12+
  cloud backup and device transfer, in addition to disabling legacy backup.
  `allowBackup=false` alone can leave manufacturer device-transfer behavior enabled.
  [Android backup rules reference](https://developer.android.com/identity/data/autobackup).
  Add a code-native vector launcher mark and declare Indonesian as the default
  resource language; no generated bitmap or external asset is required.

### Official references checked

- [AGP 8.13 compatibility (Gradle 8.13, JDK 17, SDK support)](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom) (2025.07.00 also present in the local Maven cache)
- [Compose compiler with Kotlin 2+](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [KSP release](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4)
- [Hilt setup / 2.57.1](https://developer.android.com/training/dependency-injection/hilt-android)
- [Room stable release history](https://developer.android.com/jetpack/androidx/releases/room)
- [Navigation releases](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work)
- [DataStore releases](https://developer.android.com/jetpack/androidx/releases/datastore)
- [Supabase Kotlin quickstart (future candidate only)](https://supabase.com/docs/guides/getting-started/quickstarts/kotlin)

## M1 — 2026-11-13

- D012: Add Room catalog schema v2 via an explicit additive `AutoMigration(1,2)`
  referencing both exported schemas. Preserve metadata and avoid duplicating Room's
  table/index SQL by hand. A concrete `CatalogMigration` spec installs validation
  triggers; the same callback installs them for fresh databases. No destructive
  migration or production cloud migration is involved.
- D013: Catalog repository operations use Room transactions, canonical UUIDs,
  integer Long IDR, UTC epoch milliseconds from an injectable Clock, immutable
  creation timestamps, local optimistic revisions and soft-deletion tombstones.
  Composite brand foreign keys prevent cross-brand category/group relationships.
  A stale or cross-brand write rejects rather than overwrites. Local revisions
  protect local mutations only; M3/M5 must define server versions and authorized
  catalog mutation delivery before enabling shared catalog editing.
- D014: M1 exposes a read-only adaptive local demo browser through Settings. Full
  CRUD is implemented/tested through the repository, not an unauthenticated
  management screen. Role-aware editing is deferred until M3. No checkout/cart,
  auth, outlet provisioning, cloud schema or sync jobs are added.
- D015: Demo menu is explicitly loaded in debug builds only, uses deterministic
  client UUIDs in a separate local brand scope, and inserts all rows plus a seed
  marker in one transaction. Retrying never overwrites edits or resets deletions;
  an existing unmarked demo scope fails safely rather than guessing ownership.
  Prices are examples and must not be treated as the brand's official menu.
- D016: Keep all M0 runtime versions. Add
  Robolectric **4.17** to execute real local Android/Room tests without an emulator.
  Tests simulate API 28 with a plain Application and native SQLite. The migration
  fixture reads archived v1 JSON from test-only classpath resources and opens it
  with real Room v2, invoking generated migration and schema validation. This provides
  executable migration/DAO coverage, not proof of device UI or OEM behavior.
  Add Java 17+ opens and Android resources/assets per the official setup guide.

Official references: [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions),
[Room relationships](https://developer.android.com/training/data-storage/room/relationships),
[Robolectric setup](https://robolectric.org/getting-started/),
[Robolectric releases](https://github.com/robolectric/robolectric/releases).

## Development schema policy — 2026-11-13

- D017: Per user direction, do not maintain Room migrations during the current
  development phase. This supersedes the migration requirements in D008/D012 and
  the migration-test portion of D016. The complete catalog is now initial schema
  version 1; remove the bootstrap-only schema history, AutoMigration/spec and its
  test fixture. Keep fresh-create validation triggers and catalog persistence tests.
  No automatic destructive fallback is added. Existing development installs need
  an explicit app-data clear or reinstall after incompatible schema changes; no
  existing app data was cleared by this change. Establish data-preserving migration
  paths before releases that require retained data.

## Application identity — 2026-11-13

- D018: Per user direction, use `com.dauntehnusantara.pos` for applicationId,
  namespace, application/test Kotlin packages and the Room database class/schema
  export directory. Supersedes the package identity in D006. Development schema
  stays version 1 with no migrations. Android treats the new application ID as a
  separate installed app; existing installations/data are not moved or removed.

## M2 — 2026-11-13

- D019: Implement M2 in an explicitly labelled debug-only demo scope until M3
  provides authenticated outlet/staff membership and enrolled device context.
  Persist a generated local device UUID, with dedicated demo outlet/cashier UUIDs.
  Guard the checkout repository as well as UI in release. These are test identities,
  not authorization, and must not become production credentials or real sales.
- D020: Persist every accepted cart edit in Room with immutable line snapshots,
  client sale/operation UUIDs and optimistic draft revision. Commit sale graph,
  payment, outbox and consumed-draft/receipt state in one transaction. On restart,
  recover the current draft or undismissed receipt. Durable pending data survives
  DB reopen; actual OS process-kill acceptance remains a device check.
- D021: Use append-only sale/payment/item/modifier rows, RESTRICT FKs and immutable
  outbox identity/payload. Unique sale operation/outbox aggregate constraints plus
  transaction serialization prevent duplicate local sales. Matching operation and
  command hash return the original receipt; a changed command rejects. M4 must hash
  and validate the actual immutable server payload; do not reuse this local command
  hash as a server security check. No remote idempotency guarantee is claimed yet.
- D022: Revalidate product/category/group/selected-option revisions, availability,
  names and prices before adding/completing. Reject stale cart snapshots and ask
  for removal/reselection; never silently change accepted prices. Historical sale
  snapshots remain independent of later catalog edits. Shared offline price
  acceptance policy with the owner remains open for M3/M4.
- D023: All arithmetic uses Long IDR with checked addition/multiplication. Cash
  requires sufficient integer tender; manual QRIS requires explicit operator
  confirmation and has no cash change. No gateway verification, QR generation,
  discounts, tax editing or split tender in the demo. Subtotal equals item sum,
  discount is zero, total equals subtotal. Defensive limits: 100 lines, 1–999 units
  per line. Use sale UUID until receipt numbering is agreed. Display UTC times in
  Asia/Jakarta. No new libraries or runtime version changes.
- D024: Separate demo outbox rows using DEMO_SALE/PENDING_DEMO and a `demo: true`
  versioned local payload. Do not send these rows through future real-sale workers.
  Status means saved locally and not sent; never synced, server-confirmed or backed
  up. M4 owns durable submission and server response states.
- D025: Compact/medium cart in a sheet with sticky summary, expanded persistent
  cart and focused checkout form. Persisted receipts are accessible through local
  history (latest 100 display rows; older records retained). This is M2 receipt
  recovery, not role-scoped reporting or cloud history. Loading/error/retry,
  validation/conflict and busy states are explicit. Retain the user-requested
  development initial schema version 1; no migrations/destructive fallback.

## M3 — 2026-11-13

- D026: User explicitly chose **up to one month offline**. Use one UTC calendar
  month server-side, with a 31-day maximum client sanity cap. Bind leases to current
  user/brand/outlet/role/device and a server-issued secret. Require secure OS device
  credentials, same boot, and monotonic/wall-clock bounds. No lease renewal offline;
  reboot/rollback/expiry requires online validation. This resolves the duration
  decision, not instant disconnected revocation or rooted-device resistance.
- D027: Follow the existing development policy for both Android and backend: keep
  the current initial Room v1 and provide `supabase/schema.sql` as a fresh initial
  development schema, not migrations. No data reset, hosted SQL application,
  deployment, real account creation or emails were performed by the agent.
- D028: Use the documented Supabase Auth HTTPS REST API and RPCs authored in this
  repository, avoiding an unnecessary unverified Kotlin/Ktor client dependency.
  HTTPS only, no credential-bearing redirects or HTTP caching, bounded response
  sizes/timeouts, no raw provider errors or token logs. Runtime versions unchanged.
  Public configuration is optional for builds; reject secret/service-role keys at
  build time and runtime. No key is embedded in repository examples.
- D029: Every exposed server table has RLS and explicit grants. Helpers are private,
  SECURITY DEFINER with fixed empty search paths, revoked PUBLIC execution and
  narrow grants. Exclude private from Data API exposure. Server checks live profile,
  brand, membership/outlet; cashier sales/nested rows are own-only, managers assigned
  outlets, owners one brand. No authenticated direct table writes. Catalog edits,
  actual sale submission and feed generation await vetted later RPCs.
- D030: Invitation Edge handler verifies Auth identity and SQL authorization before
  using a server-only admin key. SQL intents expire after seven days, limit requests
  to five per actor/ten minutes, forbid owner grants and manager escalation, and
  recheck verified recipient email and current inviter rights at acceptance.
  Uncertain email delivery is not treated as success or permission activation.
  Invite activation uses the official TokenHash email variable, verified directly
  in-app; no implicit URL-fragment/deep-link token handling is introduced.
- D031: Persist session/rotated refresh tokens, device credentials, selected context
  and lease together using Android Keystore AES-GCM, authenticated format, AtomicFile
  and no-backup storage; passwords stay transient. Cache is project scoped. Clear
  local key/authority on logout/confirmed denial even without remote logout success.
  Preserve an interrupted password-set session so invite retry does not reuse a
  consumed one-time code. Hardware-backed behavior still requires device validation.
- D032: Owners may self-enroll. Manager devices need owner approval; cashier devices
  need an owner/assigned-outlet manager. Secret proof plus approval is required for
  leases. New cashier device UUIDs stay pending after revocation until approved. Approvals
  bind the granted role; a promotion from cashier to manager requires fresh owner
  approval rather than inheriting a manager-approved cashier device grant.
  Device revocation does not itself revoke arbitrary existing Auth JWTs; use live
  profile/membership denial and require device proof in M4 operations. Keep the
  compatible OS credential intent path for API 26 support; native prompt acceptance
  is not claimed without a device.
- D033: Add a real authenticated role/outlet portal with first-online enrollment,
  invitation and device approval, plus safe locked/error/retry/offline states.
  Owners/managers start at overview; cashier at Kasir. The M2 demo remains explicit
  debug-only and isolated. Do not enable real checkout until M4/M5 supplies the
  validated server sale and synced catalog contracts. No cloud sync is implemented.
- D034: Verify server authorization locally using pinned test-only PGlite **0.5.16**
  (version checked through npm), PostgreSQL WASM with a test Auth context, plus
  Node 22.17.1 tests of the Edge handler with mocked HTTP. This verifies actual RLS/
  SQL behavior but not hosted Supabase JWT plumbing, GoTrue/SMTP, Deno hosting or
  Android Keystore/device prompts. Android runtime dependency versions unchanged.

Official sources checked for M3:
- [Supabase Auth REST endpoints](https://github.com/supabase/auth/blob/master/README.md)
- [Invite TokenHash templates](https://supabase.com/docs/guides/auth/auth-email-templates)
- [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Database function security](https://supabase.com/docs/guides/database/functions)
- [Supabase CLI configuration](https://supabase.com/docs/guides/local-development/cli/config)
- [PGlite setup/API](https://pglite.dev/docs/)


## M4 push decisions — 2026-11-13

- D035: User explicitly selected **reject changed catalog prices for review**.
  Validate current active product/category/group/option versions, snapshot names,
  prices, modifier limits and exact arithmetic. Never reprice an existing receipt.
  Deduplicate accepted operations before catalog/time checks, after live auth checks.
- D036: Submit through a vetted PostgreSQL RPC with user JWT and secret proof;
  no new Edge runtime dependency or invented external service. Hash canonical JSONB
  on server for semantic dedupe and exact incoming UTF-8 for client ack validation.
  Operation/sale identity reuse is audited and quarantined; full graph, operation
  and SALE feed append are atomic. Serialize feed sequence allocation for M5.
- D037: Keep Room schema version 1 during development; extend outbox metadata only.
  Persist attempts/due time before IO, keep payload/identity immutable, terminal
  review/synced states, no transient claimed state. At-least-once delivery plus
  effectively-once sale creation; no exactly-once transport claim.
- D038: Reuse pinned WorkManager 2.10.2 and Hilt 2.57.1 with an injected WorkerFactory
  and Configuration.Provider. Remove only the WorkManager Startup initializer.
  Connected work, unique KEEP with explicit manual replacement, periodic sweep and bounded backoff/jitter
  cover process death/ack loss without adding another dependency/version.
- D039: Credentials remain exclusively in auth's encrypted cache. Background delivery
  restores only matching account/outlet/device context and refreshes tokens under
  the auth mutex. Re-login recovers a retained public device UUID from Room and
  rotates proof on server; non-owner re-enrollment invalidates approval to prevent
  a known UUID plus password from inheriting an old device approval. Revoked IDs
  remain denied. Sign-out still deletes all local credentials and offline authority.
- D040: Add a separately scoped atomic authenticated receipt producer and Settings
  status/retry UI. Do not convert M2 demo data or pretend a real menu exists before
  M5. Real checkout UI and catalog/sales pull are explicitly still gated.

Official sources checked for M4:
- [WorkManager custom configuration and initializer removal](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)
- [Supabase database function security and privileges](https://supabase.com/docs/guides/database/functions)

## M5 pull and real checkout — 2026-11-13

- D041: Use vetted `pull_changes` RPC with live user/outlet role and approved-device
  secret proof. Catalog triggers append full versioned rows/tombstones in the same
  transaction; updates increment versions on the server. Physical catalog deletion
  is denied. Feed readers take the shared M4 sequence lock and advance over hidden
  gaps only to a stable committed high-water mark. No Realtime dependency is needed.
- D042: Scope cursors by backend URL, brand, outlet, user, device and role. A role
  change starts its own cursor so promotion can backfill older newly visible sales;
  demotion never queries the broader cached history. Catalog versions are tracked
  across scopes to prevent older role replay from reverting newer menu records.
  Reject foreign scopes, unknown schemas, reversed sequences and immutable sale
  conflicts without advancing the page cursor. Do not silently reset a bad cursor.
- D043: Apply page data, dedupe/ack echo and cursor in one Room transaction. Retain
  downloaded immutable receipts in separately scoped `remote_sales`, merge by sale
  UUID for history, and preserve every unsent local sale/outbox. Equal durable sale
  echoes can resolve a lost acknowledgement; conflicting receipts stop the page.
  Keep initial Room version 1 (now 14 tables), no migrations or destructive fallback.
- D044: Reuse pinned WorkManager/HTTP stack. Push then pull with bounded pages per
  pass and connected periodic/manual retries. Cap page payloads at 512 KiB and
  client responses at 1 MiB; this accommodates one valid M4 receipt. Record progress
  and errors durably. No dependency version changes or hosted deployments.
- D045: Enable authenticated cashier POS/history with the existing adaptive UI,
  genuine enrolled scope and durable per-account/outlet/device/role drafts. A first
  completed catalog catch-up is required before checkout. Consume draft and write
  complete sale/outbox/command dedupe in one transaction; no repricing paid receipts.
  Validate auth before entering Room transactions to avoid auth/DB lock inversion.
  Keep demo isolated and label real receipts/history without demo or backup claims.
- D046: Authenticated catalog browsing is read-only in this pull milestone. Trusted
  server catalog writes produce updates; a vetted role-authorized mobile catalog
  editor remains outstanding MVP work. Do not connect the demo local editor to real
  shared catalog state. Reports/staff/recovery stay M6; Realtime remains optional.

Official sources checked for M5:
- [PostgreSQL trigger transaction behavior](https://www.postgresql.org/docs/current/trigger-definition.html)
- [Supabase database functions/security](https://supabase.com/docs/guides/database/functions)


## Manual staff provisioning revision — 2026-11-13

- D047: User explicitly replaced invitation onboarding with trusted administrator
  creation of Owner/Manager/Cashier Supabase Auth accounts for this private brand.
  Staff use ordinary email/password sign-in; no public signup, in-app account
  creation, invitation email or activation code. This supersedes D004/D030 and the
  invitation/activation-specific portions of D031/D033; other security rules remain.
- D048: Remove invite/activation methods/state/UI and the login acceptance RPC call.
  Login only reads preassigned live context; it never creates/reactivates a profile
  or membership. Preserve completed encrypted sessions/device proof; reject legacy
  unfinished activation caches and require fresh sign-in. No Room change/migration.
- D049: Current initial development SQL omits staff_invitations and its helpers/RPCs/
  policies; delete the invite Edge handler/config/template and associated HTTP tests.
  Add a trusted-only provisioning SQL example that atomically creates profile/outlet
  membership and audit, permits MANAGER/CASHIER only, and refuses implicit reactivation,
  role changes, missing/unconfirmed identities or foreign owner attribution. Client
  table-write grants stay denied. Owner bootstrap remains separate. No hosted DROP,
  deployment or account creation is performed; existing live cleanup needs review.
- D050: Preserve all documentation files and historical milestone evidence; update
  current setup/security/product plans and add MANUAL_STAFF_PROVISIONING.md. Replace
  obsolete invitation tests with actual-script/manual access tests and auth/UI
  regression coverage. Device approval, role/outlet RLS, one-calendar-month offline
  lease and M4/M5 durable sync remain unchanged. Next milestone stays M6.

Official sources checked:
- [Supabase account/user management](https://supabase.com/docs/guides/auth/managing-user-data)
- [Server-only Auth Admin createUser and email confirmation](https://supabase.com/docs/reference/javascript/auth-admin-createuser)


## M6 reports, staff and recovery — 2026-11-13

- D051: Reports use accepted server sale/payment/item snapshots only, never merge
  pending/review local totals into cloud receipts. Jakarta inclusive date range
  (maximum 366 days), owner brand/all or one outlet (including historical closed
  outlets), manager current active assigned outlet, cashier denied. Return decimal
  strings for exact Long IDR, reconcile totals client-side, and bound payload size.
  Daily/outlet partitions and top 20 product snapshot names/amounts are explicit.
- D052: Cache the last validated report per project/user/device/role/outlet/range in
  Room metadata; show timestamp/cached status on offline/failed refresh. CSV uses
  integer values, quoting and formula-prefix escaping. Capture the selected snapshot
  before the document picker so a later filter change cannot export a different report.
- D053: Keep account creation/role assignment manual. Staff RPCs use live approved
  device proof and expected membership/device versions plus reasons. Owner manages
  manager/cashier outlet access; manager cashier only; no self/owner/foreign target.
  Disable revokes the user's outlet devices. Reenable never restores devices;
  owner-only restoration clears approval and requires fresh review. Inactive global
  profiles require administrator review. Audit writes share mutations; owner can
  see the latest 50 selected-outlet access audit entries, manager cannot read audits.
- D054: Add no migration; initial Room v1 retains 14 tables with cursor serverEpoch.
  Initial server schema retains 18 public tables, adds version columns and private
  sync_generation. A restored branch must rotate epoch under the feed lock; old
  clients receive HTTP 410 and gate checkout while preserving all work. No automatic
  cursor reset, data deletion, repricing, replay or device reassignment.
- D055: Export scoped original real sale/outbox/draft/command records, including
  retained SYNCED rows, as a versioned checksummed local archive through Android SAF.
  Exclude credentials, demo and other users/outlets; bound to 16 MiB, fail rather
  than truncate. Checksum is integrity only, not authenticity. No automatic importer
  or production recovery harness is claimed. CSV/JSON do not replace server backups.
- D056: Verify actual isolated PostgreSQL dump/load with PGlite's supported API and
  execute the configured epoch-rotation example in that restored instance. Verify
  original operation dedupe/hash, graph/RLS and old-epoch rejection. This is local
  rehearsal evidence only; hosted Supabase/Auth/Storage/device restore and business
  retention/RPO/RTO still require operator signoff. Do not deploy/restore live data.

Official sources checked for M6:
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Supabase backups and restore boundaries](https://supabase.com/docs/guides/platform/backups)
- [PGlite dumpDataDir/loadDataDir API](https://pglite.dev/docs/api)

## M7 local release hardening

- D057: Add online Owner/Manager editing for the shared brand catalog. All writes
  use approved device proof, live role, expected version and feed epoch. No offline
  catalog queue or direct real-catalog DAO editing. UI waits for the acknowledged
  feed sequence; preserve original sale snapshots and strict upload price rejection.
- D058: Deduplicate catalog operations in a private server ledger. A stable operation
  UUID binds actor/device/canonical request; write catalog, links, feed and audit in
  one transaction. Soft archive only, immutable option group and validated required
  modifier availability. Brand command lock and dependency locks precede feed locks.
- D059: Bound JSON nesting before parsing, parse legacy public-key roles exactly,
  serialize absent-row device enrollment, disable cleartext and enable release
  shrinking. Keep persisted WorkManager names. No dependency version changes.
- D060: Keep M7 production acceptance open until hosted/device/signing/restore gates
  are executed. Catalog forms/uncertain command state remain in ViewModel memory;
  after process loss reload and inspect existing catalog before recreating an entry.
  The durable paid-sale outbox is unchanged. No destructive development migration.

Official Android references checked for M7:
- https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- https://developer.android.com/guide/practices/page-sizes

## Brand icon and green theme (2026-11-14)

- D061: Use the user-supplied Daun Teh Nusantara logo unchanged as the launcher
  artwork, converted to a square PNG with white padding while preserving aspect
  ratio. Adaptive icons use a white background and 20% foreground inset so the full
  logo remains within the mask-safe area; regular and round icons share the artwork.
- D062: Use a fixed leaf-green Material 3 palette in both light and dark modes,
  including primary, secondary, tertiary, containers and neutral surfaces. Darker
  light-mode action colors retain readable white labels. Match native startup
  backgrounds/accent colors to Compose and provide night-mode native styling.
  No dependencies, business behavior, database schema or migrations change.

## Transparent logo and explicit white appearance (2026-11-14)

- D063: Replace the logo bitmap with an alpha-transparent background extraction of
  the supplied artwork, generated with the imagegen skill and packaged at 560×560.
  Preserve the brand lettering/leaves/ring, keep generated originals outside the
  project, and use the extracted PNG in the existing launcher foreground.
- D064: Default the app to light mode independently of the phone's night setting,
  following the user's report that debug appeared dark only. Keep the explicit
  `darkTheme` argument/dark palette for future opt-in, but do not invent a theme
  settings feature. White background/surface/containers and transparent surface tint
  replace the green-tinted off-white surfaces. Native day/night startup styles and
  system-bar icon appearance match the white UI; automatic view force-dark is off.
  No dependency, API, database or business behavior changes.
- D065: Remove the adaptive launcher's explicit white background as requested.
  Both the foreground bitmap's empty pixels and background layer are transparent;
  launcher masks/backdrops remain OS-dependent. Use AndroidX SystemBarStyle for
  system icon compatibility and guard the native force-dark opt-out at API 29;
  keep min SDK 26 without suppressing API lint checks.

- D066 (2026-11-14): Use a `drawable-night` launcher foreground with white SRC_IN
  tint when the device is in night mode. Reuse the transparent logo bitmap and its
  alpha silhouette; light-mode artwork retains its original colors. This is a native
  drawable tint, not a second redrawn logo, and does not change the app's explicitly
  white light UI. Launcher refresh/cache behavior remains device-dependent.

## Product photo preparation — 2026-11-14

- D067: Add optional online product photos using the existing Activity Photo Picker,
  framework image/EXIF tools and HTTPS stack; add no dependency versions. Automatically
  center-crop/orient, strip metadata and encode bounded 512px JPEGs. No offline photo
  upload queue, camera capture or interactive crop editor.
- D068: Use optional `product-photos.sql` after the existing base schema: private JPEG
  bucket with limits, approved Owner/Manager JWT upload RLS, published-brand reads,
  immutable paths and no client object update/delete. Publish/detach through a
  proof/role/epoch/version/idempotency-checked RPC with atomic audit/feed. No deployment.
- D069: Keep photo revisions in their own table/feed rows so pictures cannot invalidate
  prices/paid snapshots. Persist those rows in existing Room catalog metadata, in the
  same page/cursor transaction; no Room schema, version or migration change. Upgrade
  old clients before emitting the new feed table; never skip unsupported rows.
- D070: Scope a bounded atomic private cache to the complete authorized context;
  images are best-effort offline assets, not operational checkout dependencies.
  Keep binary IO outside the auth mutex. Retain uncertain photo commands in memory
  for stable retries across same-context revalidation. Retain orphaned/replaced
  Storage objects until reviewed operator cleanup; no automatic deletion.
