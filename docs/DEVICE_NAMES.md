# Device names in staff approval

Staf → Perangkat shows the reported Android manufacturer/model in pending approval
cards, review confirmations and the existing device directory. Staff account details,
role, outlet and public device UUID remain visible. Example: Samsung SM-M146B; this
is the OS model identifier, not a marketing-model lookup or a custom phone nickname.
Missing names show “Nama perangkat belum tersedia”.

## Setup

1. Apply `supabase/device-labels.sql` once in Supabase SQL Editor after the existing
   main schema. It is compatible with staff provisioning/limits setup in either order.
   Do not rerun schema.sql, reset data or alter existing devices/credentials.
2. Install the updated Android app on the staff device. Restart the app after SQL
   setup, then validate access online (or use normal pending-approval refresh/sign-in).
   Existing cached devices report metadata without reenrolling or resetting approval.
3. On the reviewing Owner/Manager device, refresh Staf → Perangkat / approval requests.
   Names appear for devices that have reported them; old apps keep the fallback.
   Manager reviewers see names only for Cashier devices in an assigned outlet; Owners
   see devices within the selected authorized outlet, matching current review policy.

This change needs SQL RPC setup, not a staff-admin Edge Function redeployment. No
hosted SQL/deployment or working-brand device/approval mutation is performed by local
builds or tests. Missing/unavailable label endpoints do not block normal authorization.

## Security and behavior

- Metadata is display-only and can be spoofed. Verify the public device UUID with
  the staff member. Names never grant role, outlet access, approval or offline leases.
- Only the live enrolled user holding that device's current secret may report its
  manufacturer/model. Pending devices may report, revoked devices may not. Password
  replacement and existing active-profile/membership checks still apply.
- Reports use a separate RLS-protected device_labels table and narrow RPCs. They never
  change device access version, secret, approval, offline lease, sale or Room schema.
- Each manufacturer/model field is bounded to 80 characters; controls are rejected
  server-side and control/format characters removed for Android display. UI labels
  are bounded to 160 characters and wrap normally. No IMEI, serial, Android ID,
  contacts, location or additional permissions are requested.
- The app attempts reporting once per selected device per auth session/process. A
  missing/failed optional endpoint shows fallback; restart/sign in after setup to
  retry. Names are fetched with roster/approval reads and are not an offline directory.
- Display metadata is excluded from captured device access comparisons: a model-label
  update alone must not create an optimistic access conflict. Actual device version,
  user, role, approval/revocation and original action identity still matter.

Tests use synthetic devices in isolated PGlite and Android host fixtures. Real device
model rendering, two-actor hosted approval, large-text/TalkBack and rollout to older
apps remain manual acceptance. Deploy only through the intended project's normal
review/approval process.
