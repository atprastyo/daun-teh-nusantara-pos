# Manual staff provisioning

Current policy: a trusted administrator creates each account for this private brand.
The Android app provides email/password sign-in and device approval. There is no
public registration, invitation email, activation code or in-app account creation.

## Set up the backend

Use a fresh disposable development project with the current `supabase/schema.sql`.
Keep public signup and anonymous sign-in disabled in hosted Supabase Auth settings;
`supabase/config.toml` supplies the equivalent local development configuration.
Keep password policy/rate limits enabled. The app uses only the HTTPS URL and public
key from ignored `local.properties`, never an Auth admin/service-role key.

The initial owner is separate: manually create its Supabase Auth account, confirm
its email through trusted admin tools, then configure/run `bootstrap-owner.sql.example`
for the actual brand and outlet. Use real UUIDs and names in a private copy.

## Add a manager or cashier

1. In the intended project's Supabase Dashboard, open Authentication → Users and
   create the user's email/password account through the administrator create-user
   action. Confirm the account through trusted admin controls for the verified staff
   member. Do not use the invite action. Supabase's server-only Admin `createUser`
   also supports explicit email confirmation if your operator uses an admin tool.
2. Copy the Auth user UUID. Creating the Auth account alone grants no POS access:
   a separate active `user_profiles` row and outlet membership are required.
3. Copy `supabase/provision-staff.sql.example` to a private working file. Fill the
   staff Auth UUID, outlet UUID, same-brand owner Auth UUID, full name and role
   (`MANAGER` or `CASHIER`). Escape apostrophes in SQL text values. No password or key
   belongs in this SQL file, repository, screenshot or log.
4. Run that configured copy as a trusted SQL operator in the correct development
   project's SQL editor. The script atomically creates the profile/membership and
   writes a `STAFF_PROVISION` audit record. Repeat with another outlet UUID when the
   staff member is explicitly assigned to more than one outlet.
5. Communicate the account credentials privately to the staff member. The member
   signs in online in the Android app and selects an assigned outlet. No activation
   code or acceptance step is involved.
6. Approve the enrolled device: owner approves managers; owner or the assigned-outlet
   manager approves cashiers. Manual account creation does not approve devices or
   extend offline access. The existing one-calendar-month offline policy applies.

The assignment script is safe to rerun for the same active role/outlet. It refuses
missing/unconfirmed Auth users, inactive profiles/memberships, role changes, OWNER
assignments or a foreign-brand owner attribution. Existing assignments needing
reactivation/promotion must be explicitly reviewed rather than silently overwritten.
If the script fails inside its transaction, run `ROLLBACK` before correcting/retrying.
Do not run it as an Android/signed-in client; table write grants remain denied.

## Passwords, access changes and existing installs

Passwords are managed by Supabase Auth, never application tables. Password resets
are handled through trusted admin/Auth recovery tools; the app does not provide a
new password-reset or forced-first-login-password-change workflow in this iteration.
Keep each staff member's account separate. Disable application profile/memberships
and revoke devices when removing access; Auth token logout alone does not instantly
revoke every existing JWT. Disconnected revocation remains bounded by the lease.

Valid completed encrypted sessions remain compatible. An old unfinished activation
cache is rejected and requires ordinary sign-in. No Room migration/data reset is
needed for this onboarding change; existing queued sales are retained.

The repository now maintains only the current initial development schema. It does
not automatically remove invitation objects or old Edge deployments from an existing
hosted project. Review/retire any previously deployed invite function and privileges
separately; never apply the initial schema blindly over an existing/live database.
No accounts, emails, passwords, deployments or hosted SQL were executed by this work.

## Verify

`npm test --prefix supabase` executes the actual schema and configured test copies
of the assignment script in isolated PostgreSQL WASM. It verifies manual assignment,
idempotence, no implicit reactivation/escalation, absent invite endpoints, device
approval and retained RLS/push/pull behavior. `./gradlew build :app:assembleDebugAndroidTest`
checks Android auth/session behavior and compiles native UI tests. Hosted Dashboard/
GoTrue/PostgREST and actual Android devices still need live acceptance.

Sources: [Supabase user management](https://supabase.com/docs/guides/auth/managing-user-data),
[server-only Admin createUser and email confirmation](https://supabase.com/docs/reference/javascript/auth-admin-createuser).


M6 adds online, reasoned/versioned outlet enable/disable and device revocation in
Staf for authorized owner/manager reviewers. It does not create accounts, assign
roles or reactivate a globally inactive profile. Enabling an outlet does not restore
revoked devices; owner-only restoration returns them to pending before fresh approval.
See BACKUP_RESTORE_RUNBOOK for retained device work and explicit recovery boundaries.

## In-app alternative

After optional [in-app provisioning setup](IN_APP_STAFF_PROVISIONING.md), Owners can
create Manager/Cashier accounts and Managers can create assigned-outlet Cashiers.
This guide remains the trusted administrator fallback for existing identities,
Owner bootstrap and recovery. Do not run assignment scripts to bypass an unfinished
app-created account's password gate; inspect its reserved operation first.
