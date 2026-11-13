# M4 push acceptance

Automated:
- `npm test --prefix supabase`: actual initial SQL, manual provisioning and RLS in PGlite.
  Exercise repeated operation/ack loss, canonical JSON key order, changed payload,
  reused sale, changed catalog before upload vs after acceptance, device proof,
  membership/revocation, role promotion, totals, modifiers, payment, full rollback,
  audit and one feed append. No real credentials or external emails.
- `./gradlew build :app:assembleDebugAndroidTest`: JVM domain/auth/Room behavior,
  lint, both app variants and native UI test compilation. Push tests cover atomic
  graph/outbox, restart, lost ack, cancellation after commit, persisted due time,
  account scoping, review immutability, refresh, exact ack hash and ID validation,
  re-login device recovery, rollback and bounded backoff.
- `./gradlew :app:connectedDebugAndroidTest`: requires a device/emulator. Sync UI
  tests cover empty/retry and review with preserved-receipt wording.

Live development acceptance (not executed without configured backend/device):
1. Review/apply current initial schema to a fresh disposable development project.
   Provision real staff and configure HTTPS/public key via ignored local.properties.
2. Sign in, select outlet, obtain required device approval. Confirm non-owner
   re-enrollment needs fresh approval and revoked IDs cannot be reused.
3. M5 will supply synced catalog/real checkout UI. Until then use a trusted test
   harness calling `SyncRepository.record` with a real matching catalog/receipt;
   do not upload DEMO_SALE or change demo identities.
4. Complete cash and manually verified QRIS sales offline within the lease. Kill
   the process before enqueue, restart offline, then reconnect. Verify one full
   graph/operation/feed row and matching acknowledgement; no partial writes.
5. Drop the accepted RPC response and reconnect/retry. Verify one sale, same IDs,
   same canonical hash and unchanged original receipt. Repeat concurrent requests
   on separate database connections to validate advisory-lock behavior in hosted PG.
6. Change price/version before a new upload. Verify REVIEW + audit, original local
   receipt unchanged, no server sale; manual Retry must not resubmit reviewed rows.
7. Expire access token, deny membership/device, rate-limit and interrupt HTTP. Check
   refresh, paused auth or retry status as appropriate. Do not claim synced on error.
8. Sign out, sign in with another user: no reading/submitting the former queue.
   Re-login as original user and reapprove recovered device; verify retained work.
9. Check Settings on compact phone and expanded tablet, accessibility/text scaling,
   background network and process recreation. WorkManager timing is OS best effort.

Limitations: no hosted Supabase/PostgREST/GoTrue, physical device scheduling/Keystore,
concurrent hosted DB sessions or visual/native acceptance is claimed by local tests.
M5 pull/cursors/convergence and explicit review/revoked-device recovery remain future work.
