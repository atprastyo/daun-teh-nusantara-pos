# In-app staff account creation

Owners can create Manager or Cashier accounts; Managers can create Cashier accounts.
Creation is online-only for the **selected operational outlet**. Owners select the
intended outlet using the existing outlet switcher before opening Staf → Tambah staf.
Managers cannot assign another outlet or create another Manager/Owner. Owner accounts
and password recovery stay with trusted administrators. No invitations/public signup.

## Setup (not deployed by implementation)

1. Keep public/anonymous signup disabled. Apply `supabase/staff-provisioning.sql`
   **once after schema.sql** in the intended environment. This is optional initial
   setup, not a migration history or a reason to rerun/reset the main schema.
   It adds a private operation ledger and narrow RPCs, and replaces active_user to
   block temporary-password accounts until their password has been replaced.
   It does not modify Room, existing profiles, sales or passwords.
2. Apply `supabase/staff-limits.sql` once, then configure per-outlet Manager/Cashier
   maximums through Supabase admin tools. No configured row blocks new creation.
   See [STAFF_LIMITS.md](STAFF_LIMITS.md) and configure-staff-limits.sql.example.
3. Deploy `supabase/functions/staff-admin` as the `staff-admin` Edge Function using
   your installed/approved Supabase CLI or dashboard. Keep gateway JWT verification
   enabled. The function additionally validates the caller against Auth `/user`.
   Supabase must provide server-only SUPABASE_URL, SUPABASE_ANON_KEY and
   SUPABASE_SERVICE_ROLE_KEY. Never place the service key in Android/local.properties.
   No new JavaScript SDK dependency: the handler uses native fetch; index.ts uses
   the Supabase Deno runtime. See official references below.
4. Build/install the Android app. Staf → Tambah staf asks for name, email, permitted
   role, temporary password and confirmation. Passwords must be 12–72 characters and
   at most 72 UTF-8 bytes; hosted Auth policies may be stricter. Share credentials
   privately. Email is administrator-attested and auto-confirmed, not mailbox-verified.
5. The new staff member signs in online, replaces the temporary password with a
   different personal password, then signs in again. Existing role-bound device
   enrollment and approval still apply. With the current default server policy,
   active registered staff automatically receive approval after password setup.
   Owner can disable outlet access to revoke devices. See
   [device policy](DEVICE_APPROVAL_ALLOWLIST.md) for the standalone server update
   and optional manual-approval mode. Creating the Auth account alone grants no access.

## Failure and retry contract

The Android command freezes its UUID/name/email/role/password after submission and
retains it only in scoped ViewModel memory for an uncertain retry. Credentials are
masked; no SavedStateHandle, Room, DataStore, export, audit password or application
logging is used. Process/account/outlet loss discards this draft. An existing Auth
email cannot be attached, reactivated, reset or promoted through this feature.

The server reserves an operation/target UUID under actor and operation locks, with
unique email and at most 10 new reservations per actor/hour. Reserved targets exist
before Auth IO. Auth admin creation uses that target UUID and a protected app_metadata
operation marker. Repeating a command reads its exact identity and never replaces
its password. If Auth explicitly rejects the password and the reserved target is
still absent, the form permits a corrected password with the same operation/public
details; unknown responses keep the original command frozen. Finish revalidates role/device, checks confirmed Auth ID/email/marker,
and inserts profile + outlet membership + STAFF_PROVISION audit in one transaction.
A replay returns the same result without reenabling later-disabled membership.
Password bytes are absent from the ledger and audit; operation identity binds public
account details, not a stored reusable password digest.

Auth and PostgreSQL are separate transactions. A timeout/revocation can leave a
reserved Auth identity with no usable POS profile, or a completed account whose UI
acknowledgement was lost. Retry the **same operation** while the form is retained;
do not generate replacement accounts. No automatic delete/rollback of Auth users.
After process loss, email collision needs trusted administrator review of the private
ledger and Auth target. The UI does not expose a general existing-user assignment or
password-reset path. An acknowledged create followed by roster failure offers list
refresh, not account creation again.

New targets are denied by active_user across RLS, role/context, enrollment and lease
RPCs while their password gate is set. The Edge Function reauthenticates the current
password, rejects an identical replacement, updates Auth password, then clears the
gate through a service-only RPC. If clearing the gate fails after Auth updated the
password, sign in with the new password and replace it again, or contact the trusted
administrator. This fails closed; a temporary-password session never receives an
offline lease. Password recovery and malicious/stolen session management are separate
operational concerns; this is not MFA or hardware attestation.

Old hosted projects without setup report creation unavailable. A 404 on the password
status RPC retains legacy login compatibility; those servers have no app-created
temporary accounts. Once setup is applied the gate is enforced by server authorization,
not only the Android screen.

## Acceptance

Automated tests use synthetic users in isolated PGlite and mocked HTTP, plus Android
host Compose/ViewModel/auth tests. Validate in a disposable hosted project before the
working brand project: Owner→Manager/Cashier; Manager→Cashier; deny escalation/foreign
outlet/revoked devices; duplicate email; lost response/retry; first password replacement;
automatic approval only for active memberships; Owner deactivation; no offline access before replacement. Never use working
brand accounts for fabricated creation tests. Deno deployment/real Auth behavior,
rate limits, keyboard/TalkBack and physical tablet acceptance remain separate gates.

Official references checked 15 November 2026:
- [Supabase admin createUser](https://supabase.com/docs/reference/javascript/auth-admin-createuser)
- [Securing Edge Functions](https://supabase.com/docs/guides/functions/auth)
- [Auth getUser verification](https://supabase.com/docs/reference/javascript/auth-getuser)
- [Auth server admin API source, including caller-specified UUID](https://github.com/supabase/auth/blob/master/internal/api/admin.go)
