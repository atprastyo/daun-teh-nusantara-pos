# M6 reports, staff and recovery acceptance

## Automated

- `npm test --prefix supabase`: actual initial SQL and management RPCs with test Auth
  substrate; Jakarta midnight boundaries, owner brand/all-outlet and selected-outlet
  reports, manager assigned-outlet restriction, cashier/foreign/proof denial, empty/
  invalid ranges and immutable historical prices. Staff/device controls verify reason,
  versions/conflicts, self/owner/other-outlet denial, disable+revoke, reenable without
  device restoration, owner-only pending restoration, profile restrictions and audits.
- The same suite dumps/restores an isolated PGlite database, dedupes the same accepted
  sale and validates RLS and the reviewed feed-generation rotation script/HTTP 410.
  This is actual local database restore evidence, not hosted Supabase acceptance.
- `./gradlew build :app:assembleDebugAndroidTest`: Long IDR arithmetic/reconciliation,
  Jakarta date conversion, CSV precision/formula/quote escaping, role/target rules,
  persisted report cache with failed refresh and cross-account/backend isolation,
  offline management denial and conflict handling. Existing Room tests additionally
  exercise archive integrity/original payloads/no credentials, scope protection and
  restored-feed gating with retained pending work. Native UI sources compile.
- `./gradlew :app:connectedDebugAndroidTest`: requires a physical device/emulator.
  Includes report basis/empty cached state, manager restrictions and reason confirmation.

## Live development acceptance (not yet executed)

1. Fresh configured disposable backend, manually provisioned owner/manager/cashiers,
   approved devices and real sample catalog/sales. No public signup/invites.
2. Compare owner all-brand/individual outlet and manager selected outlet totals directly
   against scoped server sales/payment/item queries in a Jakarta date range. Include
   midnight, zero-price sale, manual QRIS and changed historical catalog names/prices.
   Pending/review device sales must not appear as accepted server report revenue.
3. Disconnect: owner/manager can read the exact previously fetched report snapshot,
   with timestamp and cached label, but cannot refresh/mutate staff. Try another
   account/outlet/role/project; old reports must not appear under the new scope.
4. Change staff access online with a reason and stale-version race. Manager cannot
   change peer managers/owners, self or other outlets. Disable denies current server
   requests and revokes affected devices; disconnected clients remain bounded by lease.
   Enabling membership must not silently un-revoke devices; owner restoration returns
   pending, then requires fresh approval. Inspect owner-only selected-outlet audit.
5. Export CSV and local JSON through actual Android document providers on compact/
   expanded layouts. Cancel, rotate, lose process/provider/network and switch accounts
   during the picker. Never report success for partial writes or export a different
   current report than the snapshot selected before opening the picker.
6. Follow BACKUP_RESTORE_RUNBOOK in an isolated hosted target with explicit approval.
   Verify Auth/JWT, roles, totals, graph/dedupe, epoch rotation and retained device
   data before any approved reset/rebootstrap. Exercise two real devices and record
   actual RPO/RTO/retention/rollback evidence. Local test fixtures are not this signoff.

## Boundaries

Manual account creation and role assignment stay administrator operations. This
milestone changes outlet access and device state, not Auth passwords/global profiles
or roles. Owner report totals can include closed outlets in the same active brand;
manager access stays the current active assigned outlet. Reports are gross sale
receipts, not profit, tax filings or bank-verified QRIS. No refund/void policy is
invented. Real mobile catalog editing remains an explicit outstanding MVP feature.
Recovery archives are not encrypted/signed full backups and have no automatic import
UI; keep them private and use an approved reviewed recovery plan. Hosted backup setup,
concurrent restore testing, large roster pagination and release hardening remain.
