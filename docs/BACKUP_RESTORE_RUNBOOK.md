# Backup, export and restore runbook

M6 implements report CSV export, scoped local recovery archives and an isolated
PostgreSQL restore rehearsal. It does not configure hosted backups, restore a live
project or automatically import archives into the app. Production restore needs an
explicit owner/operator decision, current verified backups and a rehearsal record.

## What each artifact contains

- Report CSV: the selected accepted-server report snapshot, Jakarta date range,
  scope, totals and fetch timestamp. It excludes unsent/rejected sales and is not a
  database backup. Snapshot names/prices are used; CSV text is quoted/formula escaped.
- Local recovery JSON: original immutable payload strings/IDs, delivery states,
  command dedupe metadata and current draft for the signed-in account, device,
  outlet and role. Includes retained SYNCED rows as well as pending/review work so
  an operator can compare a restored server with retained device records. It excludes
  demo operations, other accounts/outlets, tokens, device secrets and Auth credentials.
- Server database backup: the independent authoritative database, including accepted
  sale graphs, dedupe operations, memberships, audit and sync metadata. Host/provider
  recovery is separate from Android export and must include the actual Auth substrate.

Local JSON has a SHA-256 integrity checksum, not a digital signature or proof of
who created it. Do not trust an edited file merely because its checksum matches.
Exports contain business data; choose a private destination and protect off-device
copies. Do not include credentials in incident notes, screenshots or repository files.
Exports above 16 MiB fail explicitly rather than silently omit records; large cases
require a reviewed operator-led device extraction. Failed/partial files are not usable.

## Configure and rehearse backups before release

The owner/operator must record the chosen backup schedule/retention, off-site copy
location, recovery point objective, recovery time objective and authorized restorers.
These business targets are not configured by this repository. Verify the actual
hosted plan and backup availability rather than assuming a backup exists.

Supabase documents database backup/PITR options and their restore procedures. Database
backups do not include Storage API object contents; preserve any separately used
objects and project configuration independently. See the [official backup guide](https://supabase.com/docs/guides/platform/backups).
Do not paste provider access tokens into shell history or this document.

For each rehearsal, record: source/isolated target project identifiers, UTC backup
point, artifact checksum, pre/post counts and integer IDR totals by outlet/day,
foreign-key/graph checks, dedupe result, RLS/role results, operator/owner signoff,
and measured elapsed time. Record omissions, unavailable Auth/Storage/config pieces
and whether actual devices/GoTrue/PostgREST were exercised. Never label a simulated
Auth fixture as a hosted Supabase restore test.

## Capture device recovery artifacts

1. While access/lease is still valid, open Settings → export recovery JSON. Preserve
   every affected account/outlet/device separately; do not clear app data or sign out
   just to repair sync. Report CSV is supplementary, not a replacement for the JSON.
2. Copy the completed file off-device to a protected location and verify it can be
   read. Preserve an untouched original with its checksum and capture time.
3. Verify schema/kind/checksum, scope and original sale/operation IDs. The test-covered
   `RecoveryArchive.inspect` validates receipt arithmetic, scope and payload hashes;
   it does not authorize importing or replaying a file.
4. Reconcile pending, rejected and accepted rows separately. Reviewed sales retain
   their original paid prices; never edit snapshots to make an upload pass.

If device/account access is already revoked, use an explicit owner-approved recovery
procedure. Restore the membership first if appropriate. Only an owner can move a
revoked device back to pending; it then needs fresh role-appropriate device approval.
Reenrollment rotates proof and still needs approval for non-owners. Never silently
assign retained receipts to a new device/user. Do not use an admin/service key in Android.

## Restore rehearsal / incident sequence

1. Obtain explicit authorization for the intended restore and isolate the target.
   Keep normal clients disconnected and pause new sales uploads/admin writes while
   taking inventory. Preserve the current server backup and device artifacts first.
2. Follow the provider's supported restore procedure into a disposable isolated target.
   Verify schema, Auth identities/config, device/proof state, role grants, immutable
   graphs, original UUIDs, sync_operations, audit and catalog data. Record the recovery
   point and any accepted sales that occurred after it.
3. Inspect the restored `private.sync_generation` epoch as a trusted operator. Configure
   a private copy of `supabase/rotate-feed-generation.sql.example` with the expected
   restored epoch, active owner/brand UUIDs and incident reference. Review/run it in
   the isolated target. It changes the epoch and records FEED_RESTORE_BOUNDARY atomically;
   it never deletes local/server receipts. Do this before reopening a restored feed.
4. Verify clients retaining the old epoch receive HTTP 410/REBOOTSTRAP_REQUIRED even
   when their numeric cursor is still within the restored sequence range. The app
   preserves cursor, catalog and outbox, gates checkout and permits authorized local
   export; it never auto-wipes/replays a restored branch. New empty clients can bootstrap.
5. Compare server accepted IDs/hashes/totals to protected device archives. A paid
   sale may have been accepted after the chosen backup point but be absent from the
   restored server. A SYNCED local state alone does not prove it survived the restore.
6. Under an approved recovery plan, replay only original verified operation/sale UUIDs
   and exact payloads through the normal authorized submit_sale contract with the
   original user's approved device proof. Matching existing operations dedupe. Missing
   operations must still satisfy live access, age and strict price policy; reject/review
   disagreements rather than override constraints or rewrite receipts. There is no
   automatic archive import/replay UI in M6. A production recovery harness and its
   authorization/reconciliation must be reviewed before real recovery operations.
7. Export/reconcile and verify all retained work before any operator-approved local
   reset/rebootstrap. No reset is performed automatically by this milestone. Re-pull
   under the new epoch and check counts/totals/history on two authorized devices.
8. Resume traffic only after owner/operator signoff and tested rollback criteria.
   Keep pre-restore backups/artifacts until the agreed incident retention expires.

Rollback means retaining isolation and returning to the approved pre-restore backup
or target; it never means deleting conflicting paid records or releasing an unchecked
feed. A changed project URL also requires explicit reconciliation/rebootstrap; caches
from different projects must not be mixed.

## Executed local rehearsal and limits

`npm test --prefix supabase` includes an isolated test that runs the actual schema,
creates a validated sale, dumps PGlite's data directory, starts a fresh PGlite instance
from that dump and verifies sale uniqueness, stable dedupe hash and RLS. It runs the
configured generation-rotation example, checks its audit record and verifies old
cursors stop while fresh bootstrap works. The TAP diagnostic reports artifact size
and key checks. The temporary snapshot is test-only and is discarded afterwards.

PGlite's dump/load API is designed for PGlite data directories, not as a production
Supabase/PostgreSQL backup format. See [PGlite API](https://pglite.dev/docs/api#dumpdatadir).
This rehearsal does not verify hosted PITR, GoTrue/JWT, external Storage, SMTP,
concurrent network clients, physical Android file providers or a live restore.
Those remain explicit release prerequisites, alongside chosen RPO/RTO/retention and
a reviewed production recovery harness. No live data or account was restored here.
