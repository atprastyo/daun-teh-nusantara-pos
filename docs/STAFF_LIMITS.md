# Outlet staff limits

Each outlet can have multiple Managers and Cashiers, with separate configured maximums.
Owners create either role; Managers create Cashiers only in the selected assigned
outlet. Limit configuration is a trusted Supabase administration operation. There is
no Super Admin app role, quota editor, billing or public signup.

## Configure using Supabase admin tools

1. Apply the normal schema and staff-provisioning setup to a new development project.
   For a project that already has them, do not rerun/reset those scripts.
2. Apply `supabase/staff-limits.sql` **once after staff-provisioning.sql**. It adds the
   RLS-protected `public.outlet_staff_limits` table, private capacity functions,
   reservation/membership guards and capacity in the scoped staff roster response.
   Existing memberships are not rewritten or removed. Room stays unchanged.
3. Deploy the updated staff-admin Edge Function, which forwards only the known quota
   error codes; Android displays them separately from email conflicts or hourly limits.
4. Configure one row per outlet with Supabase SQL Editor or trusted admin Table Editor.
   Use `supabase/configure-staff-limits.sql.example`, replacing the outlet UUID and
   example counts with your actual operating limits. For example, Manager limit 2 and
   Cashier limit 10 permits up to two Manager memberships and ten Cashier memberships
   at that outlet. These are examples, not values applied to the working brand.
5. In the app, refresh Staf → Anggota. Owners see Manager and Cashier used/maximum;
   Managers see Cashier used/maximum. The creation form repeats these counts and
   disables unavailable role choices. Counts are a fetched snapshot; the server always
   makes the final decision. No quota data is retained for offline management.

No row means creation is unconfigured and blocked. Zero blocks new users of that role.
The app cannot write the limits table or invoke private quota helpers. Supabase admin
credentials must never appear in Android. No working-project setup/deployment is done
by building the app or running isolated tests.

## What counts

- Every `outlet_members` row for that outlet/role, including disabled memberships and
  inactive global profiles. A trusted-admin manual membership counts too. Owner brand
  membership does not count; a redundant Manager/Cashier outlet membership does.
- Every unfinished staff-provision operation reserves one slot for its target user.
- A pending reservation and its just-inserted matching membership count once, not twice.
  Finalizing a reservation converts it to a membership atomically.
- Replaying the same operation neither adds usage nor resets passwords/reactivates users.
- Each role and outlet has a separate maximum. The same person assigned to two outlets
  consumes one membership slot at each outlet. This feature does not attach existing
  emails to new outlets; existing identity assignment remains trusted administration.

Lowering a maximum below usage does not delete or disable users. Existing reservations
can still finish because they add no usage; additional creation is blocked. Missing
configuration also prevents new reservations. Reserved operations remain recoverable.
Disabling/reenabling a membership does not change usage, preventing quota bypass.
The existing 10-new-reservations/hour/actor throttle remains independent.

Reservations do not automatically expire. Auth and database operations span separate
transactions, so an uncertain or failed Auth request may still have created an identity.
After process loss, trusted administrators inspect the operation's Auth target and
profile/membership state before resolving a stalled reservation. Never blindly delete
completed operation rows: they also enforce first-password replacement. Do not remove
unresolved reservations merely to make room while another request may still finalize.

## Atomic enforcement

New reservations and membership insert/role/outlet/user changes use the same private
quota guard. It updates the outlet limits revision row before checking deduplicated
usage. Different actors and devices serialize on that shared row; higher isolation
levels abort conflicting updates instead of accepting a stale count. Trusted admin
limit edits use the same row. Membership activation-only updates consume no new slot.
Clients have no direct membership/table write grants; triggers also cover trusted
manual assignment. Administrators must not disable triggers to bypass operational caps.

Implementation reasoning follows PostgreSQL's documented [transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html),
[application consistency checks](https://www.postgresql.org/docs/15/applevel-consistency.html)
and [function snapshot behavior](https://www.postgresql.org/docs/17/xfunc-volatility.html).
These sources describe the locking/snapshot contract; they are not a claim that hosted
concurrency was tested here.

## Acceptance and limits of local checks

Isolated PGlite tests cover multiple Cashiers, separate role/outlet caps, disabled and
pending usage, reservation conversion/replay, missing/zero/lowered limits, direct
membership insert/role updates, atomic multirow overflow rollback and RLS/admin-only
writes. Mocked Edge tests verify quota errors stop before Auth creation. Android tests
cover capacity parsing, missing/full UI and reserved retry behavior.

Before working-project rollout, use a disposable hosted project with **two independent
connections** to race different Owner/Manager operations for the last slot, race admin
limit edits and reservation completion, and verify one additional slot cannot be used
twice. No native PostgreSQL server/psql/Docker or Supabase/Deno CLI is installed in the
current toolchain; PGlite serial execution does not replace this hosted concurrency
acceptance. Real Auth/Edge, keyboard/TalkBack/tablet acceptance remains separate.
