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

## UI guideline and visual reference — 2026-11-14

- D071: Define a coherent light-first design direction before the next UI implementation
  pass: white canvas, semantic leaf-green emphasis, quieter surfaces, rounded shapes,
  meaningful spacing and an explicit sans-serif type scale. Preserve the requested
  white app default and independent dark-mode white launcher logo.
- D072: Target one Material Symbols Rounded icon style with distinct destination
  meanings. Current authenticated tabs reuse Info; replace them in the next pass.
  Asset identifiers in the guide are design targets, not promised installed Kotlin
  symbols. No new font/icon dependency or app behavior change in this documentation pass.
- D073: Keep the guide and an offline HTML component reference in docs, with fictional
  data and illustrative icons. Keep operational state semantics, role/outlet access,
  device/offline lease, idempotent retries and immutable paid snapshots as design
  constraints. Apply changes incrementally: shared foundations/navigation first,
  then catalog/POS, checkout/history, and management screens.

## Screen-level design handoff — 2026-11-15

- D074: Inventory screens, sheets, inline states and OS boundaries with stable design
  IDs, role/connectivity gates and existing implementation anchors. Design IDs do not
  automatically become navigation routes. Detail one connected flow per iteration.
- D075: Detail access first: restore/login/outlet choice, pending/locked/offline access,
  reviewer requests/confirmation and Android credential boundary. Reuse current
  AuthState/ViewModel actions and server hierarchy. No signup, client-selected role,
  custom app PIN or new password recovery/approval contract. Auth READY retains the
  separate initial-pull/recovery gate for cashier checkout.
- D076: Add a local phone/tablet state reference with read-only fictional credentials,
  externally labeled scenario controls and explicit separate reviewer context. It
  makes no auth/network/approval call. Keep current white/green tokens, preserve
  prior design work, and defer Android implementation to ACCESS-01–ACCESS-05 tasks.

## Access-flow Compose implementation — 2026-11-15

- D077: Implement the access handoff as callback-only Compose presentation over the
  existing AuthStage/actions. Use a scrolling 480dp form, a brand pane at >=840dp,
  and a single column at large font scales. Keep the white app canvas, original-color
  logo and server-authorized role/outlet navigation. No dependency versions changed;
  host Compose tests reuse the existing Compose BOM/test runner/JUnit versions.
- D078: Publish `lockedContext` solely as display context for pending/eligible offline
  screens; `membership` stays null while locked. Publish the original eligible lease
  expiry without renewing it. Add `deviceRequestsLoaded` so an unloaded/failed list
  cannot be rendered as a successful empty result. These memory-only fields are not
  persisted authority and change no RPC, lease validation or Room schema.
- D079: Reviewer approval requires a phone sheet/tablet dialog showing staff, outlet,
  role and public UUID. Block repeated submit/dismiss while pending and refresh via
  the existing repository action. Unknown/error results retain existing fail-closed
  handling; there is no new automatic approval retry/deduplication guarantee. Copy
  only public device UUIDs. Keep passwords memory-only and clear them on submission.

## Shared authenticated shell — 2026-11-15

- D080: Extract callback-only AuthenticatedShell with one outlet/role/access header,
  role-filtered destination content and distinct localized navigation. Below 600dp
  use bottom navigation; >=600dp use a scrolling rail and retain the >=840dp expanded
  content flag. At >=150% text or <480dp height use a scrolling labeled drawer.
  This presentation fallback preserves the same destinations/order and business gates.
  Keep the content composition location stable across navigation-form changes to
  preserve in-progress inputs when font/window/IME changes the layout.
- D081: Scope selected destination to account email/brand/outlet/role and validate a
  restored name against RoleNavigation before rendering. Header switch calls existing
  chooseOutlets through AuthViewModel, disabled offline/busy. Do not synthesize an
  outlet, offline switch, cancellation endpoint or approval. Settings retains sync,
  recovery, lease deadline, revalidation and sign-out; it no longer repeats the header.
- D082: Label AuthState.offline as access mode (“Akses offline” / “Akses tervalidasi”),
  not a live network probe or synced/backup guarantee. Keep auth errors and in-flight
  validation visible; preserve cashier initial-pull/recovery gating in its feature.
- D083: Vendor twelve official Material Symbols Rounded 24px vectors at pinned source
  commit 10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7. Remove only root theme tint so Compose
  supplies semantic colors without AppCompat attrs. Retain license/attribution in
  docs and APK assets, path/source hashes in MATERIAL_SYMBOLS_SOURCES. No dependency
  version, Room schema, API or production deployment change.

## Catalog design handoff — 2026-11-15

- D084: Detail C-01–C-08 against existing editor/mutation/photo contracts before the
  native redesign. Preserve whole nonnegative IDR, immutable option parent, active
  dependency/minimum rules, independent photo versions, stable unknown-result retry
  identity and acknowledged-pull gating. No stock/barcode/discount/camera/crop features.
- D085: Propose localized product-first tabs, local name/status filtering, focused
  compact forms and a split editor only when available content width fits. Archive
  requires explicit brand-wide confirmation; dedicated archive preserves stored
  fields, while saving active=false explicitly includes form edits. Draft/result and
  observed-refresh correlation are native implementation work, not a new backend API.
- D086: Provide a local fictional interactive catalog reference plus static captures.
  Scenario/role/ACK/pull controls stay outside the app frame; picker is an illustration,
  not gallery/file access. No auth, HTTP, upload, real save or transaction occurs.
  Keep CATALOG_SCREEN_DESIGNS.md explicit about current-versus-proposed behavior and
  retrySync's lack of an acknowledged network-refresh result. Native CAT-UI tickets
  follow separately; other feature groups remain inventory-only.

## Native catalog redesign — 2026-11-15

- D087: Implement CAT-UI-01–04 using product-first localized tabs, cache-only name/
  active/archive filters, readable rows and a memory-only CatalogEditorSession owned
  by the activity ViewModel. Compact/large-text/short windows use a focused form,
  medium windows a bounded dialog, wide available content (>=1100dp) a 480dp editor
  beside the list. Preserve raw input/scroll on layout/rotation; block another edit
  while a form is open rather than silently discarding it. Scope changes/logout clear
  draft context; no SavedState/Room editor/photo queue is added.
- D088: Retain immutable catalog/photo retry commands and gate edits on their existing
  lifecycle. Conflict requires an observed newer successful pull marker and explicit
  form review; requesting WorkManager is not refresh completion. Requery the catalog
  after committed pull/ACK checkpoints before releasing local wait state, and close
  a submitted form only for its matching operation and visible newer target record.
  New corrected/reviewed submissions get new operation UUIDs; retries keep the old one.
- D089: Confirm archive and photo removal separately. Dedicated archive preserves
  stored fields; saving active=false explicitly includes current draft changes. Validate
  integer IDR, Int bounds, required options, active parents and immutable option parent
  locally without replacing server validation. Photo version/bytes stay separate from
  product price revision and sales. Missing-photo retry keeps a >=48dp target by removing
  the decorative icon that previously squeezed the button in a small thumbnail.
- D090: Clear old-scope display data before awaiting cancelled IO, then guard action/
  observation updates by scope; slow old requests cannot restore a prior outlet's
  screen. Existing backend, Room schema, device/auth policy, initial-pull/recovery and
  sale snapshot/outbox contracts remain unchanged. Use existing pinned dependencies;
  host regressions do not close real picker/IME/TalkBack/device/hosted acceptance.

- D091: Detail POS P-01–P-06 before changing native presentation. Use a fictional,
  local browser reference for compact/tablet interactions; it is not a payment,
  authorization, Room or server simulator suitable for production reuse.
- D092: Distinguish observed local receipt persistence from confirmed delivery.
  An uncertain save freezes the original command until scoped readback reconciles
  it; retry only the same verified unsaved command. Never ask for payment again
  because observation failed. Keep QRIS confirmation explicitly manual.
- D093: Receipt delivery labels require a separate scoped read projection from
  existing outbox/remote proof, not aggregate counts or changes to immutable Receipt.
  Missing proof stays unknown. REVIEW retains original prices/payment and provides
  no automatic reprice/resubmit; readiness gates still protect new mutations.

- D094: Implement P-01–P-06 as one native presentation milestone, retaining pinned
  dependencies and existing sale/auth/sync contracts. Collapse the persistent cart
  below 900dp available width or at >=150% text; use bounded scrolling modifier
  selection and focused payment with fixed actions. Keep cash whole integer IDR and
  manual QRIS confirmation memory-only, reset on method/draft revision.
- D095: Freeze original authenticated checkout command and draft while its local
  result is uncertain. Reconcile via read-only scoped Room transaction and original
  command metadata; show a matching original receipt or allow only an identical
  verified-unsaved retry. Scope/action/observation generations prevent cancelled old
  work restoring a prior outlet or consumed cart. No new durable payment queue.
- D096: Put receipt delivery in CheckoutState's separate read projection, sourced
  from scoped outbox state or validated current-scope matching feed receipts.
  Missing proof remains unknown. Do not alter immutable Receipt/sale JSON/schema,
  infer delivery from aggregate counts, reset REVIEW, reprice or reissue paid sales.
- D097: Display an authorized retained receipt before checkout-readiness gates, with
  recovery instructions and disabled next sale. Repository guards remain unchanged.
  Add closes only on observed success; failed selections remain editable. Native
  physical-device/hosted/IME/TalkBack/signing acceptance remains separate from host
  regressions; no device was connected for this iteration's ADB check.

- D098: Detail H-01/H-02 before native history redesign. Filters search only the
  bounded authorized receipt cache; say “up to 100 cached receipts,” not a complete
  or globally latest-100 archive. Remote input is capped by feed sequence before
  merge; no load-more/server-search endpoint or revenue total is invented. Use
  original-name/public UUID search, Jakarta day boundaries and proven status.
- D099: History detail is read-only and uses “Struk tersedia di perangkat” for both
  created and downloaded cached receipts; Receipt has no provenance/name/number field
  supporting invented origin badges. Missing delivery proof stays unknown; review
  preserves original prices/payment. Closing detail clears UI selection only, never
  checkout's last-receipt pointer, pending command, cart or sale/outbox.
- D100: Preserve same-scope snapshots/filters across refresh and responsive layouts,
  distinguish local read from observed successful pull, and clear visible state on
  lock/scope change before late IO completes. Cache eviction clears selection with
  explanation; it cannot delete a sale. No new Owner/Manager history route, schema,
  migration, RPC, payment, refund/void or printing scope is introduced by this design.

- D101: Give native history its own scoped HistoryViewModel and in-memory session,
  independent of POS pending commands and receipt dismissal. Preserve bounded cache
  selection/order, original-name/UUID search and Jakarta day/status filters. Clear
  account/outlet/role/lock state before cancelled IO completes; validate backend/device
  scope after reads and guard late UI callbacks with the session generation.
- D102: Refresh reads local state and awaits existing bounded pull completion before
  reporting observed success; timestamp equality is not failure when the owned pull
  completed. Offline/recovery remains local-only; partial pulls request existing
  continuation and cannot claim archive completeness. Retain only authorized same-
  scope snapshots on read/network errors. No new RPC, schema or outbox retry mutation.
- D103: Use responsive focused/split history and read-only shared receipt content.
  History availability includes downloaded cache and does not invent device origin.
  UUID is selectable; Back/Close never changes checkout or shows next-sale/queue
  actions. Manual QRIS copy refers to bank verification separately from server ACK.
- D104: Run Android component/Room fixture tests with ComponentTestRunner and a plain
  Application, so production PosApplication startup cannot enqueue authenticated sync
  during those tests. This runner does not support Hilt MainActivity/end-to-end auth
  acceptance; use a separate normal-application setup for those tests. Preserve the
  connected device's application data; never clear/uninstall to bypass install failure.

- D105: Detail R-01–R-03 / report X-03 before native redesign. Overview and reports
  share the applied accepted-server snapshot; retain existing count/total/cash/manual
  QRIS and daily/outlet/top20 snapshot product fields. No profit/trend/bank/inventory
  metric, complete product-mix claim, new endpoint or chart dependency is introduced.
- D106: Separate editable filters from applied report identity. Validate actual
  Jakarta dates and the existing <=365 difference (366 inclusive dates), allow future
  dates under current contract, keep Manager fixed and Owner choices authorized.
  Same-query refresh can retain a labeled valid snapshot; changed queries need their
  exact cache/response. Guard late scope/request/error/finally results and OS callbacks.
- D107: Capture the original SalesReport/source before CreateDocument, clear prepared
  intent on cancel/scope change and report success only after provider write/close.
  Preserve UTF-8/CRLF/integer/formula-safe CSV format. No real download occurs in the
  browser illustration; native cancellation/provider/partial-file and auth checks
  remain implementation acceptance, not promises made by the prototype.

- D108: Implement RPT-UI-01–04 using one ViewModel-owned report session for Ringkasan
  and Laporan. Filters are memory-only drafts until Apply; Cancel performs no IO.
  Jakarta presets read the clock at selection, strict ISO dates retain the <=365
  difference bound, Owner choices include authorized inactive history, Manager is
  fixed, and unresolved outlet IDs are never labeled All. No dependency or schema
  version changes are needed.
- D109: Clear scoped report/draft/export state before cancelling old work. Guard
  report results, errors and finally by scope generation and request generation;
  recheck current reporting authority around cache/remote/export operations. Retain
  only the exact-query valid snapshot on refresh failure. Reject duplicate product
  ID + original-name rows before rendering keyed lists; keep existing financial and
  exact Long/CSV validation. Offline cache absence is not a zero-sales report.
- D110: Correlate CreateDocument with an opaque UUID ticket; keep the captured
  immutable report/source in memory and only the callback ID in saved presentation
  state. Cancel and launch failure consume prepared intent; a stale callback cannot
  write a new scope's report. Write/close on IO, recheck authority before/after, and
  claim success only on completed write under the original scope. Navigation away
  cancels pending intent; configuration recreation preserves the pending callback.
  Already-written provider bytes cannot be retracted after authority changes;
  failed/partial files must be discarded and a new destination selected manually.

- D111: Detail S-01–S-03 before the native staff redesign. Roster/devices and Owner-only
  <=50 audit rows are for the operational outlet, including for Owner. Keep membership,
  global profile, device revocation and separate A-07/A-08 approval distinct; no new
  account/role/password/invitation workflow or roster pagination is introduced.
- D112: Capture source/target/version and the intended access boolean in confirmation.
  Member self/Owner/foreign targets and current-device actions retain existing rules.
  Enabling membership leaves devices revoked; Owner restoration returns pending and
  requires live membership plus fresh approval. Disconnected revocation remains
  bounded by the original lease. Reasons use current trimmed 3–240 length semantics.
- D113: Staff management remains online-only with memory-only scoped state. Native
  tickets must guard late reads/acks/errors/finally, invalidate changed confirmations
  and separate mutation acknowledgement from readback. Existing expected-version
  RPCs have no operation UUID: uncertain responses require refresh/review, not blind
  replay or inversion of a newly refreshed row. Process/scope loss never restores a
  destructive intent. Browser simulation is design evidence, not Android/SQL signoff.

- D114: Implement STAFF-UI-01–03 with a ViewModel-owned memory-only StaffSession for
  tab/target/reason. Capture the immutable original target and source; derive desired
  access from that capture, then preflight a fresh same-scope roster/version before
  invoking the existing expected-version RPC. No role/account/password/schema or
  dependency changes. Offline/scope loss invalidates and clears sensitive state without
  waiting for cancelled IO.
- D115: Separate mutation acknowledgement from readback. Lost response or cancellation
  after dispatch blocks new actions as UNCERTAIN; known ack with readback failure is
  ACK_REFRESH_FAILED. Refresh observes current state without attributing an uncertain
  commit to this actor; no automatic replay or inverse toggle. Guard old result/error/
  finally with generation/request checks and revalidate full authority around IO.
- D116: Use adaptive member/device/Owner-audit tabs, virtualized rows, a wide-window
  guidance pane and bounded scrolling confirmations with fixed actions. Preserve
  existing profile/membership/device distinctions, full identities and audit bound.
  Integrate A-07/A-08 once in Perangkat with separate read/result state; staff writes
  and reviewer actions disable one another. Component tests preserve normal app data
  and skip PosApplication startup sync; they do not establish hosted/Hilt acceptance.

- D117: Detail T-01–T-04/sign-out/recovery X-03 before native settings redesign. Keep
  access validation, scoped push status, last successful pull and local recovery
  artifacts distinct. Queue scheduling is not delivery or backup; missing counts
  are not zero. No reset/import/restore, admin configuration or new settings capability.
- D118: Native recovery export should capture/validate/freeze the existing <=16 MiB
  UTF-8 archive under original authority before CreateDocument, instead of current
  scope-only preparation/capture after picker. This changes capture timing only;
  preserve original payloads, archive format, exact IDs and integrity checks. Use
  memory-only bytes/authority, opaque callback ID, cancel/scope/process guards and
  post-write validation. Provider partial files cannot be retracted; no auto-overwrite.
- D119: Sign-out gets a scoped confirmation explaining retained local work and loss
  of local authority, including unknown queue status. Permit explicit sign-out without
  requiring export; never clear Room or claim remote revocation of all JWTs. Recovery
  required remains after login/export and directs authorized export/operator review,
  with no automatic epoch reset or data deletion. Browser fixtures are not native or
  hosted recovery/Keystore/document-provider acceptance.

- D120: Implement SETTINGS-UI-01–03 with a scoped SettingsViewModel/route and guarded
  existing SyncStatusViewModel. Bind push/pull by full PullScope, clear obsolete
  observations/errors/retries and only report scheduling after scoped repository
  retry returns. Keep missing counts distinct from zero and aggregate Int counts in
  Long. Recovery-required blocks retry; authorized offline export remains available.
- D121: Add RecoverySnapshot metadata to the domain/data boundary, preserving capture
  String compatibility and the existing archive format/transaction/integrity rules.
  Capture/size validation runs on IO before picker launch; private memory holds frozen
  bytes and authority, with only an opaque ID saved for callback correlation. Null,
  launch/navigation/scope/process failure consumes intent; provider writes/close on IO
  and post-write authority gates success. Confirmed capture denial clears visible scope.
- D122: Settings sign-out confirmation warns about retained/unknown work and never
  deletes Room or requires export. Current auth stage determines leaving the shell;
  remote logout remains best effort after local authority clears. Export and session
  actions have independent guarded outcomes; foreground auth validation is awaited
  before provider access, never bypassed. Keep schema, credentials, endpoint and
  dependency policies unchanged; normal-app/provider/M7 acceptance remains separate.

- D123: Run the M7 normal-app pass against the user's confirmed working-brand project
  with read-only navigation and cancellation boundaries. No fabricated financial,
  catalog/staff/device/restore operation or actual sign-out is used for acceptance.
  Credentials are entered on the phone only; authenticated checks wait for that session.
- D124: Record artifact fingerprints/signature/manifest/ELF/ZIP checks separately from
  runtime and signed-release approval. Current unsigned optimized APK and a 4 KB phone
  cannot establish signed or 16 KB-device acceptance. Add a read-only repeatable
  artifact verifier with malformed/4 KB/header-boundary parser regressions.
- D125: Fresh PGlite test evidence remains isolated sequential database evidence,
  never proof of hosted Auth/Storage/concurrency or a live restore. Maintain an explicit
  normal-app matrix and leave production gates open until their actual evidence exists;
  do not promote prior component fixtures into end-to-end acceptance.

- D126: Fix a normal-app acceptance finding: same-context periodic/foreground refresh
  must not emit OUTLETS and destroy the authenticated shell/pending document intent.
  Preserve READY with busy only after newly fetched choices confirm identical brand,
  outlet, role, account and enrolled user. Changed scope/role or confirmed denial
  continues to clear old authority; live lease validation and offline policy remain.

- D127: Set the WorkManager periodic background push/pull sweep to one hour, per user.
  Keep the existing unique work name and use ExistingPeriodicWorkPolicy.UPDATE so
  persisted 15-minute requests adopt the new interval in place on the next startup,
  without cancelling an active run or creating another sweep. Verified semantics in
  installed WorkManager2.10.2 source. Network/backoff, event/manual/startup nudges and
  the separate 60-second access-validation policy remain. No dependency/schema change.

- D128: Use a fixed opaque white adaptive-launcher background and the original green
  foreground in both day/night configurations for consistent launcher treatment.
  Remove the launcher-only white night tint so the mark remains visible on white.
  Keep the transparent brand bitmap and in-app branding/theme untouched. Verify local
  resource packaging; device checks/install remain paused.

- D129: Extract repeated presentation into core/designsystem/components: notices,
  errors, headers, sections/rows/empty/identity, fields/buttons and adaptive confirmation.
  Adopt across catalog/POS/reports/staff/settings while features retain permission,
  target/version/reason/draft/result and business callbacks. No generic domain-driven
  form engine, dependency or theme palette change.
- D130: MoneyInputField displays Rp and dot grouping over raw whole-IDR text. Accept
  valid Indonesian grouped/Rp pastes; preserve invalid/decimal/negative/overflow drafts
  for existing validation rather than stripping them into a different amount. Parse
  only ASCII digits to Long; empty is not zero, no floating point. Price/option delta/
  cash tender use money formatting; quantity and bounds retain integer semantics.

- D131: Cash checkout offers fixed Rp5.000/Rp10.000/Rp20.000/Rp50.000/Rp100.000
  tender shortcuts. Each replaces the input; it does not add banknotes or submit a
  payment. Retain exact-total/manual entry, existing insufficient-cash validation and
  change calculation. Wrapping 48dp buttons remain cash-only and disabled while saving.

- D132: Add online in-app Owner→Manager/Cashier and assigned-outlet Manager→Cashier
  provisioning through staff-admin Edge Function, with live role/approved-device checks
  before Auth creation and final assignment. Selected outlet is explicit; Owner bootstrap
  stays trusted. No existing email attachment, password reset, role change or invitation.
- D133: Reserve a private operation and target Auth UUID, bind email/name/role/actor/device,
  cap new reservations at 10/hour/actor, verify protected Auth operation metadata and
  atomically assign profile/membership/audit. Retry never changes the original password
  or reactivates a completed account. Partial Auth/DB failures stay fail-closed and
  require same-command retry or administrator review after process loss.
- D134: Gate new accounts at private.active_user until temporary password replacement
  via verified/re-authenticated Edge action, then require fresh sign-in and normal device
  approval. Credentials are scoped memory-only and redacted; no client admin key, Room
  change, migrations or deployment. Existing manual accounts are unaffected.

- D135: Owners may operate Kasir and Riwayat without impersonating a Cashier or
  changing membership. Keep Overview as default and preserve Owner user/device/outlet
  identity in immutable receipts/outbox. Existing checkout/lease/sync guards and server
  authorization remain; Manager navigation is unchanged. Compact Owner navigation uses
  the existing drawer for seven readable destinations; larger windows use the rail.
  Owner history uses the existing authorized selected-outlet cache and is labeled as
  outlet-wide; Cashier history stays personal. No schema/RPC/dependency/deployment.

- D136 (2026-11-16): Configure per-outlet Manager/Cashier maximums only via trusted
  Supabase admin tools. No Super Admin app role/editor or automatic numeric defaults.
  Missing configuration/zero slots block creation; disabled memberships and pending
  reservations count. Deduplicate reservation-to-membership conversion; retain existing
  users/reservations when lowering caps. Multiple Cashiers are allowed up to the cap.
- D137 (2026-11-16): Serialize reservation and membership slot increases on an actual
  outlet-limit row update, independent of actor throttling, and enforce manual insert/
  role changes with triggers. Add read-only capacity to scoped roster and fail-closed
  UI gates; return bounded allowlisted quota error codes. No Room migration, production
  deployment or working-brand values applied. Hosted parallel-connection acceptance
  remains separate from local PGlite tests.

- D138 (2026-11-16): Add verified membership brand name and existing logo to the
  drawer header, followed by signed-in email, actual role and operational outlet.
  Do not invent a profile name or fetch new identity data for this presentation change.
  Tint the drawer logo white on dark surfaces. Simplify the app bar to outlet/role,
  existing navigation/outlet actions and a labeled 48dp status icon (validated,
  validating or offline). Tap exposes access/offline details separately from sync.

- D139 (2026-11-16): Place outlet switching beside the outlet title/role block and
  use branded green app-bar surfaces with contrasting content in Light/Dark themes.
  Preserve offline/busy switch guards and access-status details.
- D140 (2026-11-16): Add device-local Light/Dark appearance preference in Settings,
  persisted with the existing DataStore dependency. Default Light; apply at the app
  root without activity recreation or changing navigation keys. Keep selection through
  logout/restart, expose read/write failure and retry, update system-bar icon contrast,
  and use white in-app branding on dark surfaces. No auth, backend or Room changes.

- D141 (2026-11-16): Add manufacturer/model display metadata through a separate
  device_labels table and narrow own-secret/report + role/outlet-read RPCs. Display in
  pending/review/directory/access confirmations with UUID and fallback; no hardware
  identifiers, extra permissions, auth-version/approval mutation or Room changes.
  Report once per selected device/session and tolerate old/missing label APIs. Labels
  are untrusted display information, excluded from authorization-target comparisons.

- D142 (2026-11-16): Remove redundant drawer heading/close button; retain brand/account
  header and destination list. Close through destination selection, outside tap, swipe
  or Back. Replace the swap icon with the entire outlet title/role section as an
  accessible Button target (at least 48dp), labeled Pilih outlet lain with current
  outlet/role state. Preserve offline/busy guards, existing callback and access indicator.

- D143 (2026-11-16): Retain Keystore-protected enrollment UUID/proof per project,
  user and outlet across sign-out/re-login and outlet/account switching. Remove
  active tokens/membership/lease at sign-out; require fresh online authentication
  and live lease checks before reuse. Supersedes reset-on-every-login enrollment
  behavior without weakening server role/revocation checks or changing backend/Room.
  Poll only online, idle DEVICE_PENDING access every 15 seconds. Keep obsolete device
  records for audit; do not reset the working database to fix approval prompts.

- D144 (2026-11-16): Register double-Back exit as the Activity's root fallback before
  Compose callbacks. First root Back shows a localized Toast; a second within two
  seconds finishes the Activity. Use monotonic elapsed time and reset on Stop.
  Navigation/detail/drawer/dialog handlers retain priority. No sign-out/data reset.

- D145 (2026-11-16): Plan Bluetooth receipts for confirmed 58 mm paper. Model/protocol
  confirmation gates transport/encoder choice. First release uses paired-device
  setup and manual receipt/copy actions, independent of checkout/sync; unknown physical
  output requires explicit copy, never automatic resend. See BLUETOOTH_PRINTING_PLAN.md.

- D146 (2026-11-16): Begin printing with scoped paired-device settings and a fictional
  plain-ASCII SPP test only. User's generic 58 mm device has no confirmed model; mark
  compatibility unverified and confirm before sending. Use existing DataStore/Hilt
  and native secure RFCOMM, optional Bluetooth hardware, CONNECT permission on demand,
  bounded serialized transport, cancellation-close and explicit unknown outcomes.
  Do not add live receipt actions, ESC/POS commands, Room changes or a sales journal
  until model/profile and physical test are verified. No auto-retry/background print.

- D147 (2026-11-16): Align every input with the login outlined-field baseline using
  AppTextField: Material default padding, min56dp, bodyLarge16sp/24sp, medium12dp shape.
  Add icon/IME/multiline slots and migrate remaining raw fields, including login.
  Preserve formatting, validation, keyboard actions, multiline growth and font scaling;
  no fixed height for supporting errors. No business/backend/schema changes.

- D148 (2026-11-16): Expose Kasir/Riwayat to Manager with Overview default. Preserve
  actual Manager user/role, approved role-bound device and assigned selected outlet.
  Manager history uses existing outlet-wide read authorization and labels it clearly;
  Cashier stays own-only. Existing checkout/submit paths already support Managers,
  so no backend/schema change or privilege expansion is needed.

- D149 (2026-11-16): Add native Compose charts over the existing validated accepted-
  server snapshot: categorical daily bars with exact selection, top-five bars within
  returned product groups, and cash/manual-QRIS value split. Retain exact tables/CSV,
  current scope/cache/as-of truth, zero handling and Long financial values. Expose
  Jakarta quick periods through the existing scoped load path without changing outlet.
  No missing-date interpolation, global unit ranking, growth/profit/hourly metric,
  new dependency, Room/RPC change or live-data operation.

- D150 (2026-11-16): Use primaryContainer/onPrimaryContainer for report metric cards
  in both themes. Light mode gains a visible pale-green container with dark-green
  labels/values; dark mode uses the matching dark-green container and light content.
  Keep existing elevation, spacing, typography and adaptive metric layout.

- D151 (2026-11-16): Replace report TextButtons with OutlinedButtons for daily/product
  table toggles, sheet period presets and Cancel. Retain callbacks, labels and layout
  placement while making each action boundary visible.

- D152 (2026-11-17): Plan per-outlet stock as an immutable signed movement ledger
  with an atomic balance projection, stable operation identities and scoped sharing,
  separate from the brand catalog. Recommend online-only manual entry first; tracking
  type/unit and negative-stock policy remain unconfirmed. Auto-consumption and
  transfers are later milestones, gated on recipes/cutover and rejected-sale physical
  consumption reconciliation. See OUTLET_STOCK_PLAN.md; no implementation/deployment.

- D153 (2026-11-17): Change READY access revalidation timer from60 seconds to one
  hour, matching the existing hourly background sweep. Preserve event-driven login/
  foreground/reconnection/manual validation, local lease expiry enforcement and the
  separate15-second DEVICE_PENDING approval poll. Server operations still validate
  live authorization; no instantaneous offline revocation is implied.

- D154 (2026-11-17): Add environment dev/prod flavors with separate public Supabase
  properties. Preserve legacy unprefixed config as dev-only fallback; require valid
  explicit PROD values only when building prod. Keep production ID/namespace, append
  .dev to the development install ID and distinguish its launcher label. No runtime
  project switch, data transfer, credentials in source or backend deployment.

- D155 (2026-11-17): Provide fresh-production setup from reviewed SQL/Edge source,
  plus read-only DEV exports of selected brand/outlet/catalog and reviewed quotas.
  Generated catalog SQL safely quotes its JSON and full DO body, refuses nonempty
  targets, preserves catalog UUIDs/versions, and generates a new target feed/epoch.
  Do not clone users/passwords/device secrets/financial records/private ledgers or
  photo metadata without objects. Create a new production Owner and enroll devices.
  Runbook only; no hosted deployment is performed.
