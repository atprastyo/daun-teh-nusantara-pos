# Authentication and authorization

## Login / invitation
- Disable public self-signup for the POS. Owner is provisioned by trusted admin setup.
- Owner invites manager; owner/manager invites cashier within authorized scope through a trusted server endpoint using privileged key only on server.
- User accepts email invitation, sets password, signs in with Supabase Auth.
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
- Rate limit login/invites; secure account recovery; protect logs from PII/tokens.
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
cross-project caches fail closed. An interrupted one-time invite/password setup can
resume from its encrypted pending session without reusing the consumed invite code.

Owners can self-enroll; managers require owner device approval; cashiers require
owner or assigned-outlet manager approval. Device secrets are server-generated and
only SHA-256 hashes are stored server-side. New device IDs cannot bypass approval
for cashier/manager users. This is logical enrollment, not hardware attestation or
rooted-device resistance. Logout invalidates the local encrypted authority/key.

Online membership/profile/brand checks are authoritative on each RPC/RLS read.
The client revalidates on foreground, validated network reconnection and periodically;
an already-unlocked valid lease retains its original deadline on temporary service
failure, while a confirmed denial clears offline authority. Remote revocation is
not instantaneous on disconnected devices; it takes effect at the next successful
online validation or lease expiry. Generic table RLS remains user/outlet based;
M4 must also validate approved device-secret proof for sale submission. Auth refresh
logout alone does not invalidate every issued JWT: deactivate profile/membership
records for immediate server denial and revoke devices to stop renewed leases.

No public signup is exposed. `supabase/config.toml` and the hosted setup checklist
require Auth itself to disable public signup/anonymous login. Staff invitations are
server-authorized, expiring intents and may grant MANAGER/CASHIER only. A confirmed
recipient email plus a still-authorized inviter is required at acceptance; inactive
profiles cannot self-reactivate. Service-role keys stay exclusively in the Edge
function environment. Client build/runtime reject privileged keys.

All backend table writes are denied to authenticated clients at M3, including
catalog and sales. Future vetted catalog RPCs must enforce optimistic versions;
M4 owns validated sale submission, M5 owns durable feed/pull. Real cashier writes
remain disabled. M2 demo data is accessible only through an explicit debug entry
and cannot be substituted for authenticated live data. See `supabase/README.md`
for setup and the live Auth/SMTP/Keystore/device acceptance checks still required.
