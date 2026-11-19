# Device automatic approval and optional allowlist

17 November 2026. **Active registered staff automatically receive device approval.**
No whitelist entry is required. This policy supersedes the previous whitelist-only
exception: Owner-provisioned staff still authenticate, complete initial-password
setup and have active membership for the selected outlet. Actual server roles are
retained; the policy does not grant outlet access or elevate a role.

## Deploy to an existing project

1. Open the intended Supabase project → SQL Editor. Review and run
   [device-auto-approval.sql](../supabase/device-auto-approval.sql).
2. Existing pending users tap **Cek persetujuan**, or wait for the online pending
   screen poll (15 seconds). Their existing device UUID and proof are preserved.
3. New staff sign in normally. No Android rebuild or staff-admin Edge redeploy is needed.

Fresh `schema.sql` already includes this policy. **Do not rerun the base schema on
an existing project.** The standalone script preserves staff, devices, sales and
allowlist records. It replaces only enrollment/lease functions and its own private
policy audit triggers. No hosted deployment was performed by this implementation.
DEV and PROD must be configured separately.

Installation creates the singleton policy with `auto_approve_registered=true` if
absent. Reapplication preserves an explicitly selected policy. To enable automatic
approval if an administrator previously selected manual mode, run:

```sql
update private.device_approval_policy
set auto_approve_registered=true, reason='Owner-provisioned active staff'
where singleton;
```

## Deactivate a user

Owner → **Staf** → select the user → disable outlet access with a reason.
The existing staff action disables that outlet membership and revokes its devices.
Enrollment, lease refresh and operational requests then fail online. To block a user
across outlets, disable each membership, or have trusted Supabase administration
disable the user profile. Reenabling membership does not un-revoke existing devices;
Owner must restore a device or the active user must enroll a new device.

An already issued offline lease cannot be revoked instantly while disconnected;
access ends on online revalidation or local lease expiry (up to one month).
Revoking one device blocks that UUID but does not prevent an active staff member
from enrolling another. Disable the membership to block the person.

## Optional manual mode

```sql
update private.device_approval_policy
set auto_approve_registered=false, reason='Require manual device review'
where singleton;
```

Existing stored approvals remain valid. Future pending/new approvals require normal
Owner/assigned-Manager review, or an optional matching entry in
`private.device_approval_allowlist`. Use
[configure-device-approval-allowlist.sql.example](../supabase/configure-device-approval-allowlist.sql.example)
only for that manual-mode exception. Entries match Auth UUID + outlet + actual
Manager/Cashier role, enabled state and optional expiry. Removing/expiring entries
affects future grants, not existing device approvals. Owners retain self-enrollment.
The old `device-approval-allowlist.sql` is a compatibility installer for the current
policy; it does not restore manual mode or overwrite existing settings.

## Enforcement and audit

Every path retains live active user/brand/outlet/membership and password checks.
Lease approval additionally requires matching user, unrevoked UUID and correct
secret. Approval is stored with the actual role; downstream submit/pull/report/
photo/staff checks are unchanged. Role changes are revalidated and may automatically
receive the new actual role's stored approval, never a client-requested role.

Only trusted SQL administration can manage private policy tables. App Owner,
Manager, Cashier, anon and service_role have no management privileges. Keep private
out of exposed Data API schemas. Global policy changes have private audit entries
with database-session actor, nullable Auth actor and before/after values; automatic
device approvals use ordinary app audit events with `REGISTERED_STAFF_POLICY`
(or `ADMIN_ALLOWLIST` for manual-mode exceptions). No secret is recorded in audit.

Host tests cover default automatic enrollment, pending retry, deactivation, proof,
role and account gates, revoked IDs, private policy access and upgrade/reapplication.
Hosted JWT/concurrency and actual device acceptance remain to be checked.
