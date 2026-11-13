# M5 pull and two-device acceptance

Automated checks:
- `npm test --prefix supabase`: initial SQL, catalog trigger/version/tombstone and
  rollback, byte/record-bounded ordered pull, role/device proof and membership denial,
  same-user two-device convergence, cashier own vs manager outlet history, hidden gaps.
- `./gradlew build :app:assembleDebugAndroidTest`: JVM/Room tests of separate device
  databases, offline sales/restart/ack loss, cursor/page atomicity, interrupted bootstrap,
  role backfill/demotion, immutable prices, tombstones, malformed/cross-scope pages,
  consumed draft rollback, backend binding, conflicting sale identity, retry and equal durable echo dedupe. Native UI sources compile.
- `./gradlew :app:connectedDebugAndroidTest`: execute only with a connected device.
  Includes initial-pull checkout gate and real receipt labels without demo claims.

Live development acceptance (requires fresh configured backend and actual devices):
1. Apply the reviewed current initial schema to a fresh disposable project before
   seeding real brands/outlets/catalog. Use actual authorized staff/device approval;
   never change demo IDs to make demo rows look like real sales.
2. Use two devices for the same cashier/outlet. Complete first pull on each, check
   product/modifier versions. Disconnect both; create different sales; restart apps.
3. Reconnect. Drop an accepted submit response on one device. Run sync repeatedly.
   Confirm both histories converge to two receipts, one operation/feed append each,
   equal original prices and no duplicate local display after pull echo/restart.
4. With different cashier accounts, verify each sees only own receipts. A manager
   assigned to that outlet sees both via scoped feed; another outlet/brand is denied.
5. Update server catalog as a trusted operator. Confirm automatic higher versions,
   price changes and assignment/inactive tombstones converge on both devices. Check
   unpaid stale carts require correction and paid snapshots remain unchanged.
6. Interrupt HTTP between pages and kill process after a committed page. Resume from
   stored cursor; malformed/foreign/unknown pages must roll back data + cursor.
7. Promote with fresh device approval: older newly visible history backfills under
   the new role cursor. Demote/revoke and refresh: broader cached history is not shown.
8. Check compact phone, tablet/split screen, large text, modifiers, cash, QRIS manual
   confirmation, receipts, Settings status/errors and manual retry. No demo/backup
   label should appear on real receipts. Catalog is read-only in this milestone.
9. Run concurrent writer/pull requests on separate hosted PG connections to validate
   committed cursor boundaries/advisory locking. PGlite tests are not this verification.

Not yet verified locally: live Supabase/PostgREST/JWT, real concurrent sessions,
physical background scheduling/network, Keystore/offline OS prompts and visual tests.
Realtime hints are deliberately optional/omitted. Feed pruning, production backfill,
restored/reseeded database recovery and a real authorized mobile catalog editor need
explicit additional work. Do not clear retained work to resolve these silently.
