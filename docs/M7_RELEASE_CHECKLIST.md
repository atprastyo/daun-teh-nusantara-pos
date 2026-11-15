# M7 release acceptance

Local build verification is not production approval. Retain this checklist with the
release artifact and reviewed configuration. Do not clear retained sales to resolve
an upgrade or restore problem.

## Implemented controls

- Online Owner/Manager catalog editing uses `save_catalog_record`, approved device
  proof, live role, expected record version and the current feed generation. Catalog
  is shared by the whole brand; the UI states this explicitly. Cashiers cannot write.
- Each command has a stable UUID and canonical server request hash. Exact retries
  return the original acknowledgment; changed requests cannot reuse that UUID.
  Catalog, assignment tombstones, feed, dedupe and audit writes commit together.
- Operational Room catalog changes only through validated pull. The editor waits
  for its acknowledged feed sequence, supports retry of an uncertain request and
  disables editing offline. No physical deletion or automatic repricing of receipts.
- Option group identity is immutable. Product assignments and modifier requirements
  are validated server-side; unassigned groups can be prepared before linking them.
- Android disables cleartext traffic and backups. JSON input has a UTF-8 byte limit
  and a nesting limit before recursive parsing. Build configuration parses the
  legacy public JWT role exactly; an unrelated `anon` substring is insufficient.
- Release builds enable R8 and resource shrinking. WorkManager's persisted worker
  class name is retained. This does not configure signing or create a keystore.
- Device enrollment serializes requests for a common UUID before testing existence.

## Required before a production release

- [ ] Provision a separate reviewed Supabase environment using the fresh development
      schema; do not apply it over an existing production database. Verify real
      Auth, RLS, RPC error mappings, refresh/revocation and role/outlet isolation.
- [ ] Exercise concurrent enrollment, catalog edits, sale submission and retry on
      hosted PostgreSQL. Local PostgreSQL WASM tests run sequentially and cannot
      prove hosted locking or network behavior.
- [ ] Test phone and tablet: talkback labels/order, large font, keyboard, touch
      targets, dialogs, long catalog/roster, loading/error/offline/conflict screens.
- [ ] Test real-device process death, OS credential/Keystore, reboot/clock changes,
      airplane mode, a month lease boundary, two-device convergence and lost ACK.
- [ ] Test native CSV/JSON document providers, cancellation and actual export files.
- [ ] Profile cold start, large catalog scrolling, checkout and sync on representative
      low-end hardware; no performance budget is claimed from a JVM test.
- [ ] Verify minified signed build login, Hilt worker delivery after process death,
      Room, reports, catalog editing and export on API 26 and current target devices.
- [ ] Verify native library ELF/ZIP alignment and execute on a 16 KB page device.
      Alignment inspection alone is not a runtime test. Repair the incomplete local
      NDK before publishing native symbols; do not suppress missing toolchains.
- [ ] Configure release signing outside version control. Confirm correct app ID,
      public backend key/URL, version code, signing certificate and artifact SHA-256.
      Archive mapping.txt with the signed artifact. No server secret in the APK.
- [ ] Agree backup retention/RPO/RTO; rehearse hosted Auth/Storage/database recovery
      with authorized operators using BACKUP_RESTORE_RUNBOOK.md. Freeze writes,
      rotate feed generation and reconcile retained device receipts before reopening.
- [ ] Review a forward upgrade and rollback plan. Development uses initial Room v1
      and no migration history. A production upgrade requires an explicitly reviewed
      non-destructive migration; rolling back an APK cannot undo server writes.

## Known boundaries

Staff account creation and role assignment remain trusted administrator work; no
invite flow. Offline revocation is bounded by the device lease, not instantaneous.
Catalog form drafts and uncertain command IDs are in ViewModel memory, not a durable
catalog outbox; process recreation requires reloading the catalog before editing.
A recreated create form uses a new record ID: inspect existing records after an
uncertain create to avoid an accidental duplicate menu entry. Paid sale durability
and operation IDs use the separate Room sale outbox and are unaffected.

Restore generation protects pull and catalog edits. Existing sale submission and
staff/device management contracts are not all generation-bound; the operator write
freeze/reconciliation requirement remains mandatory. No automatic restore/import,
remote deployment, signed release or production acceptance is implied.

## Local evidence (2026-11-13)

- [x] Full Gradle build and unsigned optimized APK/AAB generation.
- [x] 99 debug JVM tests; 90 release JVM tests plus 9 expected demo-only skips.
- [x] 48 isolated PostgreSQL backend tests, including catalog role/version/dedupe and
      atomic rollback, alongside existing sale/RLS/restore tests.
- [x] 21 native instrumentation tests on Infinix X6873, API 36, 4 KB pages. The full
      suite passed after scrolling payment controls into view in two existing tests.
- [x] Lint zero errors; 18 existing toolchain/dependency version notices.
- [x] Public-key build guard regression with fake anon and escaped server-role input.
- [x] Release ZIP 16 KB alignment and all eight native libraries' ELF PT_LOAD alignment.

The phone tests use fixture screens and a scratch persistence database. They do not
replace the manual, live-backend, signed-build or other-device gates above. Full
commands, artifact hashes and earlier failed attempts are in PROGRESS.md.

## Current readiness pass — 15 November 2026

See [M7_DEVICE_ACCEPTANCE.md](M7_DEVICE_ACCEPTANCE.md) for the current artifact hashes,
normal debug startup observation, 52 freshly executed isolated backend tests and
repeatable ELF/ZIP verification. The current normal app reaches sign-in on Infinix
API36/4 KB; authenticated screen checks await device sign-in. The user confirmed the
brand's working project, so no fabricated sales/access changes/restore are exercised.

Latest prior Android build: debug 292 passed; release 283 passed +9 expected skips;
lint 0 errors/45 warnings; 9 component fixtures passed on the phone. Those are historical
build/component evidence, not newly executed normal-app auth/provider acceptance.
The release APK remains unsigned. No required production gate above is closed by
read-only artifact checks, component fixtures or the local PGlite suite alone.

### Signed-in follow-up and refresh fix — 15 November 2026

The user signed in directly. Read-only management navigation, normal sign-out/export
explanation Cancel and actual JSON document-picker Cancel were observed. A same-context
refresh bug that recreated the shell was fixed; manual validation and a >70s Settings
observation retained the destination afterwards. Full build: 295 debug passed, 286
release passed +9 expected skips, lint0 errors/45 warnings. Updated artifact fingerprints
and exact evidence/limits are in M7_DEVICE_ACCEPTANCE.md. Further filter/staff follow-up
was interrupted by phone use and is not counted. These bounded results do not close
completed-file/provider-failure, other-role, signed/minified, offline/restore or physical
accessibility/runtime gates. No business test records or access changes were submitted.
