# Supabase development setup

This repository contains the current initial development schema and Android Auth,
push and pull integration. No hosted schema, accounts or deployments are applied by
these files. There is no development migration history; `schema.sql` expects a fresh
project and must not be run blindly over an existing/production database.

## Staff accounts: manual provisioning

The private brand uses administrator-created accounts. Follow
[the provisioning guide](../docs/MANUAL_STAFF_PROVISIONING.md):

1. In a fresh development project, review/apply `schema.sql` as a trusted SQL operator.
2. Disable public signup/anonymous login in Auth; retain password policy, email
   confirmation and rate limits. Confirm manually created accounts through trusted
   admin controls. `config.toml` supplies local CLI settings, not hosted settings.
3. Manually create the owner in Supabase Auth and configure/run the private copy of
   `bootstrap-owner.sql.example` with the actual brand/outlet roster.
4. Manually create Manager/Cashier accounts in Supabase Auth. Configure/run private
   copies of `provision-staff.sql.example` for their profile and each assigned outlet.
   It refuses implicit reactivation/role changes and records an audit entry.
5. Configure only `SUPABASE_URL` (HTTPS) and `SUPABASE_PUBLIC_KEY` (public anon or
   publishable key) in ignored `local.properties`, following the example; rebuild.
6. Staff sign in normally, select an outlet and obtain device approval. Owners may
   self-approve; active managers/cashiers automatically approve by default.

Auth users without active application profile/membership receive no POS access.
Passwords belong only to Supabase Auth. Never insert passwords/auth identities through
application SQL or put admin/service keys in Android. There is no invite/activation
UI, invitation table/RPC, email template, Edge function or SMTP setup requirement for
staff onboarding. Password recovery remains an admin/Auth operation, not a new app UI.

The removed invitation deployment is not automatically retired from any existing
hosted project. Review that cleanup separately. No live objects/data were dropped.

## Security and offline behavior

Expose only intended public schemas to the Data API; never expose `private`. All
public application tables use RLS and explicit grants. Android reads live role/outlet
context; user metadata cannot grant roles. Direct client membership/catalog/sale
writes are denied, even to signed-in owners. Trusted provisioning SQL is not a public
RPC. Catalog browsing is read-only until a vetted mobile mutation flow is added.

Device secrets are server generated and stored only as hashes server-side. Current
role-bound approval and secret proof are required for leases, submit and pull.
Re-enrollment rotates proof and uses the current automatic/manual policy; revoked devices
remain denied. Generic Data API RLS checks current user/outlet access; it is not
hardware attestation or a device-bound JWT. Disable profile/membership and revoke
devices for server denial; token logout alone does not revoke every issued JWT.

Offline access lasts up to one UTC calendar month (maximum 31 days), never renewed
offline. Secure OS lock, device-credential prompt, original boot count, monotonic/wall
anchors and matching user/outlet/device/role are required. Reboot, rollback, expiry,
corrupt context or insecure lock require online validation. Confirmed denial clears
local authority; a temporary outage preserves only the old valid deadline. Remote
revocation cannot be instantaneous while disconnected.

Tokens/device credentials/lease are Keystore AES-GCM encrypted in atomic no-backup
storage; passwords are not persisted. Completed old sessions remain valid; retired
partial activation caches fail closed to ordinary sign-in. Logout clears local secrets
while retained sale/outbox data stays intact for authorized recovery.

## Push, pull and catalog events

`submit_sale(p_payload text,p_device_secret text)` validates current authorization,
strict catalog prices/modifiers/payments and idempotent operation identity. Equal
retry/ack loss creates one graph/feed event; rejected rows stay for review, unchanged.

`pull_changes(uuid,text,bigint,text,integer)` returns bounded scoped pages. Catalog
triggers publish full rows and soft tombstones, and server updates increment versions.
Install the schema before importing catalog records. Feed readers/writers share lock
58301924001 to preserve committed cursor boundaries. Do not prune/reseed the feed
without reviewed rebootstrap. No Realtime setup is required.

See [sync protocol](../docs/SYNC_PROTOCOL.md), [M4 checks](../docs/M4_TEST_PLAN.md) and
[M5 checks](../docs/M5_TEST_PLAN.md). Demo operations never upload. Real cashier needs
a completed first catalog catch-up and approved scope; M6 management/recovery is described below.

## Checks

With Node 22.17+:

```sh
npm ci --prefix supabase --ignore-scripts
npm test --prefix supabase
```

Pinned test-only PGlite 0.5.16 runs the actual SQL/provisioning/RLS/push/pull in isolated
PostgreSQL WASM with test Auth/JWT fixtures. No Docker, credentials, real accounts or
email delivery is needed. This does not verify hosted GoTrue/PostgREST/JWT/Dashboard,
real concurrent hosted connections, Android Keystore or device prompts. Local CLI/
Docker services are not installed here. Android rejects plain HTTP; local CLI services
need an explicit HTTPS proxy or hosted development HTTPS endpoint.

Before production, execute the guide and M3–M5 live checks with actual test staff and
two devices: manual assignment, wrong/no outlet access, cashier own vs manager outlet
history, device approval/revocation, offline expiry/reboot, token refresh, ack loss,
page interruption and concurrent cursor boundaries. No production deployment is implied.


## M6 reports, staff and recovery

`get_sales_report` and staff/access RPCs require current approved-device proof and
live owner/manager authority. Reports use historical accepted sale/payment/item
snapshots, Jakarta date boundaries and exact decimal strings. Owners may query their
brand/all outlets; managers the current assigned outlet only. Staff access controls
are versioned and audited, never create Auth accounts or change global profile/roles.
Disabling access revokes outlet devices; owner-only restoration returns to pending.

The private sync_generation epoch must be rotated through the reviewed example
**after an explicitly approved isolated restore and before reopening clients**.
`pull_changes` now accepts p_epoch and returns feed_epoch. Existing cursors require
that generation; mismatch returns HTTP 410 and preserves client work for review.
Current initial schema only, no live upgrade/destructive migration is implied.

See `../docs/BACKUP_RESTORE_RUNBOOK.md`, `../docs/M6_TEST_PLAN.md` and
`rotate-feed-generation.sql.example`. Backend tests actually dump/load an isolated
PGlite database and verify graph counts, totals, dedupe/RLS and rotation/audit. This
is not a hosted Supabase/Auth/Storage restore or a production recovery approval.

### M7 catalog command

`save_catalog_record(p_device_id uuid, p_device_secret text, p_epoch uuid,
p_change jsonb)` permits approved online Owner/Manager devices to edit their shared
brand catalog. No client table write grants are added. `p_change` contains stable
`operation_id`, record `id`, kind (`Category`, `Product`, `Group`, `Option`),
`expected_version` (0 for new), `name`, boolean `active`, and kind-specific
`sort_order`, `price_idr`, `parent_id`, `min_select`, `max_select`, `group_ids`.
Price values are whole nonnegative IDR. Option parent group is immutable.

The reply binds operation/record/kind/version to `through_seq` and `feed_epoch`.
Wait for scoped pull to reach that sequence before another edit. Duplicate commands
return the original acknowledgment; UUID reuse with different input fails 409.
Stale versions fail 409, invalid dependencies fail 422, and stale epoch fails 410.
Archive through `active=false`; product link removals emit tombstones. Private
`catalog_operations` is unexposed. Required-option validation, catalog writes,
operation dedupe, feed and `CATALOG_SAVE` audit commit atomically. No deployment or
migration history is introduced; this remains the fresh development schema.

### Optional product photos

Review/run `product-photos.sql` once after the base schema, with Supabase Storage
available. It adds photo metadata, its private operation ledger, a private JPEG
bucket/RLS and `set_product_photo`; existing price/sale tables are not rewritten.
Do not rerun the base schema to enable photos. See
[product photo guide](../docs/PRODUCT_PHOTOS.md). Install updated clients before
publishing photo feed rows. No hosted SQL or bucket changes were executed here.

## Optional in-app staff provisioning

See [IN_APP_STAFF_PROVISIONING.md](../docs/IN_APP_STAFF_PROVISIONING.md). Apply
staff-provisioning.sql once after the initial schema, then deploy functions/staff-admin.
This supersedes administrator-only account creation for Manager/Cashier roles, while
Owner bootstrap and recovery remain administrator operations. No deployment is performed
by building the app. Keep service-role secrets exclusively in the Edge environment.

## Per-outlet staff quota setup

Apply staff-limits.sql once after staff-provisioning.sql, then configure actual outlet
limits via trusted Supabase tools using configure-staff-limits.sql.example. No default
counts or Super Admin app user. Deploy updated staff-admin and refresh the staff list.
See [STAFF_LIMITS.md](../docs/STAFF_LIMITS.md) for counting, reservation/retry semantics
and required independent-connection hosted acceptance. Never reset the main schema
or use fabricated working-brand accounts to test limits.

## Device display names

Apply device-labels.sql once after the main schema, then update/restart staff apps and
refresh review lists. No Edge redeploy or device/secret/approval reset is needed.
See [DEVICE_NAMES.md](../docs/DEVICE_NAMES.md). Only owner-secret proof can write a
device label; reviewers read authorized outlet/role-scoped names. Do not trust labels
as authorization or collect hardware identifiers.

## Fresh production setup

Follow [PRODUCTION_SUPABASE_SETUP.md](../docs/PRODUCTION_SUPABASE_SETUP.md). It gives
the complete SQL order, DEV-only read-only catalog/limit export queries, fresh PROD
import/Owner queries and explicit-project staff-admin deployment. Never run the base
schema over existing app data. A full Auth/transaction migration is separate.

## Registered-staff automatic device approval

Fresh schema automatically approves active registered staff. Existing projects apply
`device-auto-approval.sql`, preserving devices and business data. Owner deactivation
revokes outlet devices; offline leases remain bounded rather than instantly revoked.
See [device policy](../docs/DEVICE_APPROVAL_ALLOWLIST.md) for setup, manual fallback,
optional scoped exceptions and audit. No Android/Edge redeploy is needed.

## Manual product counts per outlet

Review/run `outlet-stock.sql` once after the base schema, separately per environment.
Updated Android adds Owner/Manager **Stok outlet** with current counts, immutable
change history, online reasoned/versioned updates and offline cached snapshots.
No automatic sale deduction or Edge redeploy. See
[OUTLET_STOCK.md](../docs/OUTLET_STOCK.md) for setup/recovery/acceptance.

Automatic product deductions (STOCK-02): review/run `supabase/stock-auto-deduction.sql`
once after the manual outlet-stock setup, with checkout/sync writers paused and all
clients upgraded. No Edge Function redeploy. Preserve retained receipts and cursors;
verify current physical counts before resuming. See docs/OUTLET_STOCK.md for negative
stock, paid-price-review handling, explicit count reconciliation and backup/cutover.
