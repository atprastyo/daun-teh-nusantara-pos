# Test plan

## Unit
- Integer IDR totals, quantities, modifier prices, discounts, cash change, rounding policy.
- Role permission matrix, invalid modifier selection, stale product version.

## Room integration
- Atomic sale+payment+outbox insertion; rollback on failure.
- Process restart retains pending operations; schema migrations preserve data.
- Pull page and cursor committed atomically.

## Sync integration
- No network; intermittent network; timeout after server commit; repeated operation id; different payload same id; out-of-order changes; duplicate pull pages; revoked user; stale auth; device revoked; pagination.

## RLS / backend
- Cashier cannot read other outlet; cashier cannot update price; manager cannot grant owner; manager limited to assigned outlets; owner cannot access unrelated brand; inactive user denied.

## UI
- Phone portrait; tablet landscape; split-screen; font scaling; no double submit; accessible error/status messages; offline indicator.

## Manual acceptance
- Two-device sale convergence, price update handling, daily totals, backup restore, device replacement, QRIS manual verification, shift change.

## M2 device acceptance (pending device availability)

Use a fresh/reset development debug install after the initial schema changes.
Load the demo menu through Settings, then test in airplane mode:

1. Add a product with required/optional modifiers; invalid counts/quantities cannot
   be accepted. Edit quantity/remove lines; force-stop/relaunch and verify the
   acknowledged cart and selected prices remain.
2. Complete cash with insufficient/exact/excess tender, and QRIS with and without
   explicit merchant-app confirmation. Only valid payments can be saved.
3. Tap submit repeatedly and rotate/background the app during completion. Reopen
   it; verify one receipt/one pending operation, correct change, and no duplicate
   customer charge or replacement sale. Saved receipt remains until dismissed.
4. Start a new transaction and inspect older receipts through local history.
   Receipt names/prices/modifiers are independent of later catalog edits.
5. Inspect compact phone, medium split screen, expanded tablet, landscape, large
   font, dark mode, keyboard visibility and screen-reader controls. Check errors,
   retry, empty/loading, conflict and busy states. No status claims cloud acceptance.

Local Robolectric tests execute transaction rollback/retry, concurrent submissions,
immutable snapshots, constraints and DB reopen. They do not replace real device
process-death, rendering, disk-full or OEM lifecycle validation.

## M3 Auth/server acceptance

Local automated coverage executes PostgreSQL/RLS/provisioning through PGlite and Android auth state-machine tests. Hosted Auth/JWT plumbing, manual Dashboard provisioning and native Keystore/credential prompts remain separate acceptance.
Use `supabase/README.md` on a fresh development project to perform:

- Disable signup in Auth itself, not just UI. Confirm unauthenticated table/RPC
  access is denied and no privileged key can be compiled into the Android app.
- Provision the initial owner using trusted Auth admin tools and the bootstrap
  example. Manually create Manager/Cashier Auth users and run configured copies of
  `supabase/provision-staff.sql.example`. Verify an unassigned Auth user gets no
  access, repeated assignment is idempotent, disabled access stays disabled and
  ordinary email/password sign-in/outlet selection works without any acceptance RPC.
- Reject direct cashier price/membership/sale writes, peer/other-outlet sales and
  nested payment/modifier rows. Test manager other-outlet denial, owner other-brand
  denial and scoped change-feed reads. Disable profile/membership/brand and verify
  denial without waiting for JWT expiry.
- Approve manager devices as owner and cashier devices as assigned manager/owner.
  Pending/revoked/new UUIDs cannot obtain leases without approval. Promote a cashier
  to manager: old cashier device approval must not carry over to the new role.
- Verify one-calendar-month lease, exact expiry, disabled offline policy, secure
  device prompt cancellation/success, reboot/clock rollback/context mismatch,
  AES-GCM corruption, process restart, changed project and offline logout.
- Reconnect from a valid unlocked offline state: renew only after server approval,
  keep the old deadline if service remains unavailable, and invalidate authority on
  confirmed server denial. Inspect role-appropriate compact/expanded navigation.

No real account, SMTP email, hosted schema application or native device test has
been executed during this iteration. Do not upload M2 demo operations as real sales.

## M7 release hardening evidence

See M7_RELEASE_CHECKLIST.md and PROGRESS.md. Local regression completed: 48 backend,
99 debug JVM, 90 release JVM (+9 expected demo-only skips), and 21 native tests on
Infinix X6873/API 36. Two existing checkout tests now explicitly scroll the save
control into view before tapping, retaining their payment/duplicate-submit assertions.
Real tablet/API 26/16 KB runtime, signed minified app, accessibility/performance,
real Auth, hosted concurrency and hosted recovery remain release acceptance work.
