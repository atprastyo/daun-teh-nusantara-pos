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
