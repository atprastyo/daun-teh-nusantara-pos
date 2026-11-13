# M3 Supabase foundation

This is an initial development schema and native Android auth integration. Nothing
has been deployed or applied to a hosted project by Codex. There is no migration
history during the current development phase, per project direction. `schema.sql`
expects a fresh project and does not drop existing objects or data.

## Local executable security checks

Requires Node 22.17+ (the installed 22.17.1 supports TypeScript stripping):

```sh
npm ci --prefix supabase --ignore-scripts
npm test --prefix supabase
```

The pinned test-only `@electric-sql/pglite` 0.5.16 runs PostgreSQL in WASM. Tests
create an isolated Auth/users/JWT-context fixture, then execute the actual schema,
RLS and RPCs as `authenticated`/`anon`, with explicit role switches. Edge handler
tests use mocked HTTP. They send no emails and touch no hosted database. This does
not exercise Supabase GoTrue, PostgREST JWT verification, SMTP, Deno hosting, TLS,
Android Keystore or actual device prompts. Run the live checks below separately.

## Configure a development project

1. Use a fresh development Supabase project. Apply `schema.sql` with a trusted SQL
   operator. It creates identity/outlet, catalog, sale/payment, sync-operation,
   scoped change-feed, audit and invitation/device tables. Never apply this initial
   schema blindly to an existing/production database. No production deployment is
   authorized by these instructions.
2. In hosted Auth settings, disable public email signups and anonymous sign-in,
   require email confirmation and set the password minimum to at least 12. Retain
   provider rate limits. `config.toml` supplies the signup/confirmation settings for
   local CLI services; it does not change a hosted project's dashboard settings.
   The CLI and Docker are not installed in the current environment, so local service
   startup/config parsing has not been verified here.
3. Keep only intended schemas exposed to the Data API; `private` must not be an
   exposed schema. Private helpers have a fixed empty search path. All exposed
   tables have RLS and explicit grants; no authenticated direct table writes are
   enabled, including catalog/sales/memberships. Owner/manager catalog mutation
   must use a vetted optimistic RPC later, rather than enable last-write-wins REST
   updates. M4 owns sale submission and dedupe; M5 owns feed generation/pull.
4. Provision the first owner with trusted Supabase Auth admin tools. Copy the
   verified Auth UUID into a configured copy of `bootstrap-owner.sql.example` and
   supply the real outlet roster/name. No real accounts, UUIDs or credentials are
   invented in this repository. OWNER membership is not granted through staff
   invitation endpoints.
5. Configure SMTP and replace the Auth invite email template with
   `templates/invite.html`. It displays the supported `TokenHash` variable. The
   native app accepts this one-time hash directly; it does not consume URL fragments
   or log deep-link tokens. The recipient verifies the invitation, sets a password,
   then accepts only pending invitations matching their confirmed Auth email.
6. Deploy `functions/invite-staff` only to the intended development project after
   review. It uses the server-provided `SUPABASE_URL`, `SUPABASE_ANON_KEY` and
   `SUPABASE_SERVICE_ROLE_KEY`; none belong in Android/local.properties. The handler
   verifies the caller through Auth, creates an authorized SQL invitation intent
   with the caller JWT, and only then invokes Auth Admin invite. An uncertain email
   delivery leaves a scoped, expiring pending intent. Verified existing users can
   accept such intents on login; the app never claims membership was granted just
   because email delivery was requested. Inviter permissions are rechecked at
   acceptance. SQL limits invitation attempts to five per actor per ten minutes.
7. Configure **only** the HTTPS project URL and public anon/publishable key in
   ignored `local.properties`, using `local.properties.example`. Rebuild Android.
   Build-time checks reject secret/service-role keys; runtime checks also reject
   privileged keys and non-HTTPS URLs. The adapter uses the documented Auth REST
   endpoints and the RPCs defined in this schema; there is no Supabase SDK/Ktor
   version dependency to guess. No keys are required to build the unconfigured app.

## Sign-in, roles and device enrollment

The app starts at login/configuration, with an explicitly separate debug-only M2
local demo button. Real role/outlet screens use `get_access_context`, not JWT user
metadata or user-entered roles. Cashiers see Kasir/Riwayat/Pengaturan. Owners and
managers enter Ringkasan and see Katalog/Laporan/Staf/Pengaturan. These real-data
screens remain placeholders until the corresponding production data flow is ready;
they do not expose the local demo catalog or submit demo sales.

After online sign-in/activation, select an outlet. Enrollment creates a random
client device UUID and returns a server-generated device secret; only its SHA-256
hash is stored server-side and no client can SELECT that hash. Owners may approve
their own enrollment. Manager devices require owner approval. Cashier devices
require owner or assigned-outlet manager approval. In Staf, authorized reviewers
load pending device requests, verify the user/device UUID and approve. A pending
cashier/manager stays locked and retries online after approval. Generating another
UUID after revocation creates another pending device, not approved offline access.

Server RLS checks live profile, brand, membership and outlet state on every read.
Cashiers see only their own sales, including nested item/payment rows; managers
see assigned outlets and owners only their brand. The schema reserves immutable
sales and scoped sync data but implements no submission transport. Device-secret
proof is required for lease issuance; M4 must require current approved device proof
for sale submission as well. Generic Data API RLS is user/outlet based, not hardware
attestation or a claim that revoking a device alone revokes every existing Auth JWT.
For immediate account denial, disable the profile/memberships as well as devices;
Auth-only refresh-token logout does not invalidate an already-issued stateless JWT.

## Offline policy: one calendar month

The user selected **up to one month offline**. `issue_offline_lease` uses one UTC
calendar month, not a fixed thirty days (maximum 31 days). It checks the current
user, membership, approved/non-revoked device and device secret. Owners can disable
future offline leases through the trusted `brands.offline_access_enabled` setting.
Existing disconnected leases cannot be remotely recalled instantly.

The app encrypts tokens, device credentials and lease/context together using an
Android Keystore AES-GCM key, authenticated format, AtomicFile and no-backup storage.
Passwords are never persisted. Cache entries are project scoped. Refresh token
rotation is persisted immediately. Logout clears local authority/key even when
remote logout cannot be reached. Confirmed online denial clears the cached lease.

Offline entry requires a secure OS device lock and the system credential prompt.
The lease binds user/brand/outlet/role/device and the original boot count plus
monotonic/wall-clock anchors captured before the issuance request. Request latency
cannot extend validity. Expiry, reboot, clock rollback, missing boot metadata,
unsecured lock, corruption or a context mismatch fails closed and requires online
validation. The UI checks offline expiry every second while alive and revalidates
online context on foreground, validated-network reconnection and periodically.
A temporary service outage preserves an already-unlocked valid lease without
extending it; a confirmed denial invalidates it. This is logical device binding,
not rooted-device resistance or hardware attestation. A phone reboot requires an
online check even if the calendar month has not ended.

## Live acceptance still required

Use actual development users on two outlets and a second brand fixture. Confirm:

- Public signup is rejected by Auth itself; no UI-only signup restriction.
- Invite delivery/template, one-time activation, password creation and login work.
- Cashier cannot read peers/another outlet or change prices through direct REST;
  manager cannot escalate role or invite another outlet; owner cannot cross brands.
- Pending devices cannot obtain leases; approvals/revocation work. Deactivating a
  profile/membership denies current requests without waiting for JWT expiry.
- Hardware Keystore persistence/integrity, device credential cancellation/success,
  restart, reboot/clock rollback, exact expiry and disabled offline leases work.
- Lost connectivity/token refresh and interrupted password setup recover safely.

Do not enable real cashier writes before M4/M5 establishes validated sale submission
and catalog synchronization. Do not upload `DEMO_SALE` operations. No production
schema application, account provisioning or email delivery was performed here.
