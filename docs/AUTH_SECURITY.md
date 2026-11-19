# Authentication and authorization

## Login / staff provisioning
- Disable public self-signup for the POS. Owner is provisioned by trusted admin setup.
- Owner bootstrapping remains trusted administration. With optional staff-provisioning setup, Owners create Manager/Cashier accounts and Managers create Cashiers for the selected assigned outlet through the protected staff-admin Edge Function. Manual administrator provisioning remains available.
- Staff sign in with email/password, without invitations or public signup. App-created accounts must replace their temporary password before server role access, device enrollment or offline leases. Auth account creation alone grants no application access.
- Fetch current membership and outlet permissions from server; if multiple outlets, explicitly select one.
- Never store passwords in application tables or hardcode service-role keys in client.

## Role policy
Owner: all outlets in assigned brand. Manager: assigned outlets, catalog/report/cashier management. Cashier: assigned outlet sales and own history. Define explicit permission checks for each operation; RLS policies on all exposed tables; RPC/Edge Functions independently validate caller and outlet scope.

## Offline policy
First login must be online; offline unlock allowed only for previously authenticated and enrolled device with bounded validity. Store sensitive auth material using Android Keystore-backed protection; prefer short local PIN for shift switching only after secure enrollment. Remote deactivation cannot take effect on disconnected devices until reconnect/lease expiration. Configure and document maximum offline access window.

## Security checklist
- Verify JWT server-side; never trust role or price from client.
- Validate sale totals using catalog snapshot/business rules, including offline catalog version policy.
- Audit membership changes, price edits, voids/refunds and device revocation.
- Rate limit login; secure account recovery; protect logs from PII/tokens.
- RLS test: cashier cross-outlet read/write denied, manager escalation denied, revoked membership denied, owner brand isolation.
- If client sync requires direct table reads, grant only necessary SELECT with RLS; writes to sales via validated RPC.
- Never rely on client UI visibility for authorization.

## M3 implementation and selected offline policy (2026-11-13)

The user selected **up to one calendar month offline**. The server computes expiry
in UTC with `interval '1 month'` (maximum 31 days); the client cannot extend it.
The lease binds user, brand, outlet, role and approved device UUID/secret. Android
requires a secure OS lock and device-credential confirmation for offline entry.
Boot count, monotonic elapsed time and wall-clock anchors are cached with the lease;
reboot, rollback, expiry, missing boot information, unsecured device or mismatch
requires online validation. Tokens/device credentials/context are Keystore AES-GCM
encrypted in atomic no-backup storage. Passwords are never cached. Corrupt or
cross-project caches fail closed. Valid completed caches remain compatible; retired
partial activation sessions are rejected and require fresh ordinary sign-in.

Active registered staff automatically receive role-bound device approval after
successful authentication and initial-password setup. No whitelist is required.
Owners can deactivate outlet memberships and revoke devices; those checks remain
authoritative online. Device secrets are server-generated; only SHA-256 hashes
are stored server-side. Revoked UUIDs remain denied. An active user can enroll a
new UUID, so disable membership to block the user. This is logical enrollment,
not hardware attestation or rooted-device resistance. SQL administrators may
restore manual approval with optional scoped allowlist exceptions; see
[device policy](DEVICE_APPROVAL_ALLOWLIST.md). Logout removes session/offline lease.

Online membership/profile/brand checks are authoritative on each RPC/RLS read.
The client revalidates on foreground, validated network reconnection and hourly while signed in;
an already-unlocked valid lease retains its original deadline on temporary service
failure, while a confirmed denial clears offline authority. Remote revocation is
not instantaneous on disconnected devices; it takes effect at the next successful
online validation or lease expiry. Generic table RLS remains user/outlet based;
M4 must also validate approved device-secret proof for sale submission. Auth refresh
logout alone does not invalidate every issued JWT: deactivate profile/membership
records for immediate server denial and revoke devices to stop renewed leases.

No public signup is exposed. `supabase/config.toml` and the hosted setup checklist
require Auth itself to disable public signup/anonymous login. Manual staff provisioning is a trusted administrator operation; no invitation table,
acceptance RPC, Edge function or template remains in the initial schema/app. The
assignment example requires a confirmed Auth identity, active same-brand owner and
outlet, and MANAGER/CASHIER only. It never silently reactivates disabled access or
changes an existing role. Android cannot execute this script or create accounts;
client table writes and privileged keys remain denied. See MANUAL_STAFF_PROVISIONING.


All backend table writes are denied to authenticated clients at M3, including
catalog and sales. Future vetted catalog RPCs must enforce optimistic versions;
M4 owns validated sale submission, M5 owns durable feed/pull. M4/M5 now enable authenticated cashier writes and scoped pull after first catch-up. M2 demo data is accessible only through an explicit debug entry
and cannot be substituted for authenticated live data. See `supabase/README.md`
for setup and the live Auth/manual provisioning/Keystore/device acceptance checks still required.


M4 submission requires current JWT user, live membership, original device/outlet,
secret proof and approval bound to the current role before any dedupe response.
An offline lease never overrides a live denial. Sign-out removes login tokens and
offline authority. The enrollment-retention revision below supersedes the earlier
behavior that rotated device proof and reset non-owner approval on every re-login. A revoked original device cannot upload; retained sales need
explicit later recovery, never silent reassignment to another device/cashier.


## M5 scoped pull and authenticated cashier

Authenticated cashier checkout/history is now connected to approved enrollment and
server catalog catch-up. A complete first pull is required; offline use retains the
unchanged M3 one-month/device/boot policy. Sale submission still rechecks live access
and strict prices. Catalog browsing is read-only; no real local-only price editor.

Pull requires current membership/profile/outlet/brand plus approved device secret
proof, not an offline lease alone. The expected role is checked each page; changes
lock local offline authority until online validation. Cursors/downloaded histories
are separately keyed by project, brand, outlet, user, device and role. Cached broader
manager/owner history is not queried after demotion or account switching. A backend
URL mismatch with retained real data fails closed rather than mixing it. Offline
revocation remains delayed until successful online validation or expiry.


## M6 staff/report management

Reports require OWNER/MANAGER and current approved device proof; manager queries
stay on the active assigned outlet, owner queries stay within its brand. Cached
report reads/exports bind the current authorized context and never grant access to
another account, role or backend. Online-only staff mutations use expected versions
and reasons; managers can change cashier outlet access only, owners manager/cashier.
No self/owner/foreign target or Auth/global-profile/role creation is exposed.

Disabling membership revokes that user's outlet devices atomically with audit.
Reenable leaves devices revoked. Owner-only restoration returns a device to pending;
role-appropriate approval and secret proof are still required. Offline revocation is
not instant. Owner audit reads are selected-outlet and bounded; staff data is not
persisted for offline management. Manual account/password provisioning is unchanged.

Local recovery export requires current authorized scope, excludes credentials and
never auto-imports or overrides authorization. Restored feed epochs gate checkout
without deleting retained work. Live restore and production backup setup need explicit
operator approval/signoff; no production restore was authorized/performed here.

## M7 controls

Online catalog writes require live Owner/Manager membership and approved device
proof for an outlet belonging to the shared brand; cashier and cross-brand parents
are denied server-side. The request also requires the current feed generation.
Version conflicts cannot silently overwrite changes, and catalog operations are
idempotent/audited. Catalog modification affects all brand outlets, as labeled in UI.
Android cleartext traffic and backups are disabled; JSON is byte/depth bounded before
parsing. Release builds optimize code/resources; this is not signing configuration.
See M7_SECURITY_REVIEW.md and M7_RELEASE_CHECKLIST.md for evidence and open gates.

## Optional product photos

The private Storage bucket permits new immutable JPEG objects only to live
Owner/Manager users with an approved role-bound device in the brand; published-photo
reads use current brand access. Publication/removal additionally requires the current
device secret, live role and feed generation through a narrow versioned RPC. Storage
JWT/RLS does not bind every binary request to a particular device secret. Android
validates the current scope before/after IO, bounds/decodes images, strips source EXIF,
and scopes cached bytes to project/account/device/role/outlet under the normal lease.
There is no public bucket, signed URL in Room, client overwrite/delete policy or
photo data inside paid receipts. No hosted Storage deployment/acceptance is claimed.

## In-app provisioning revision (15 November 2026)

The optional implementation in [IN_APP_STAFF_PROVISIONING.md](IN_APP_STAFF_PROVISIONING.md)
supersedes earlier statements that only administrators can create Manager/Cashier accounts.
Live Owner→Manager/Cashier and Manager→Cashier rules are checked before Auth creation
and again before atomic profile/membership/audit assignment. Admin keys are Edge-only.
Existing emails never grant an implicit attach/reset/reactivation. The private ledger
provides retry identity, rate limiting and the server-enforced initial-password gate.
No backend deployment or working-brand user creation was performed.

## Owner cashier access (15 November 2026)

Owner navigation now exposes Kasir and Riwayat for the selected operational outlet.
This retains Role.OWNER, actual auth user UUID and approved Owner-bound device proof;
it does not impersonate a Cashier, create a membership or bypass initial catalog sync
or offline lease checks. Existing submit_sale already accepts a live Owner with
matching device/user/outlet/brand and unchanged payment/idempotency rules. History
uses the existing authorized selected-outlet cache (Owner can see all available outlet
receipts; Cashier remains own-only). Manager navigation is unchanged.

## Staff quota administration (16 November 2026)

Only trusted Supabase administration configures per-outlet Manager/Cashier maxima;
no app role can edit quotas. Live-role/approved-device creation rules remain. Missing
limits fail closed; disabled memberships and pending reservations count. Shared row
serialization and membership/reservation triggers enforce caps across actors and admin
assignment, separately from the hourly throttle. See STAFF_LIMITS.md for setup and
remaining hosted concurrency/Edge acceptance.

## Device display labels (16 November 2026)

Manufacturer/model labels assist staff review but are not attestation. Approval still
uses the original UUID, live roles/outlet and device proof. Separate optional metadata
updates cannot rotate credentials, approve devices, change versions or extend leases.
No IMEI/serial/Android ID or additional Android permissions. See DEVICE_NAMES.md.

## Enrollment retention and approval refresh (16 November 2026)

Keep device UUID/secret per backend project, authenticated user and outlet in the
existing Keystore-encrypted, no-backup store. Signing out removes session tokens,
active membership/device and the offline lease, then saves only enrollment records
under a fresh encryption key. An enrollment record alone cannot unlock the app,
restore a session or authorize offline checkout. Fresh online sign-in, current
membership and a server-issued lease are required before reuse. Confirmed online
denial clears session/lease authority but retains enrollment identity; revoked
proof is never silently replaced with a new enrollment. Corrupt caches, project
changes, app-data clearing or reinstall discard local enrollment records. Legacy
active-device caches upgrade without reenrollment or a Room migration.

Returning to an account/outlet reuses its proof instead of calling enroll_device;
server role-bound approval, membership and revocation checks are unchanged. Lost
proof still requires reenrollment and current-policy device approval. Pending screens
check again every 15 seconds while network validation is available and no auth
action is busy; other locked failures are not automatically polled.

Do not reset/delete the working project's device table to resolve repeated prompts.
Approve the UUID shown on the current pending screen for the matching outlet/role.
Use existing reasoned, versioned device revocation for obsolete duplicate records;
records referenced by immutable receipts must remain for audit and recovery.

## Manager cashier access (16 November 2026)

Manager navigation now includes Kasir and Riwayat, with Ringkasan retained as the
default. Managers sell under their actual user UUID and Role.MANAGER using their
approved Manager-bound device in the selected assigned outlet. They do not acquire
Owner privileges or impersonate Cashiers. Initial catalog sync, offline leases,
immutable sale/outbox identity, strict price review and live RPC checks remain.
History shows available authorized receipts for that selected outlet, matching the
existing Manager repository/server read policy; Cashier history remains own-only.
This supersedes earlier notes that Manager navigation is unchanged/management-only.
No server schema/function deployment or membership change is required.

## Registered-staff automatic approval (17 November 2026)

Default automatic approval supersedes the earlier manual/whitelist-only policy.
Stored approval/proof/live-membership checks remain required for operational APIs.
Owner deactivation revokes outlet devices, but cannot instantly cancel an already
issued offline lease on a disconnected device (maximum one month). Full deployment,
manual-mode fallback and audit details: [device policy](DEVICE_APPROVAL_ALLOWLIST.md).
