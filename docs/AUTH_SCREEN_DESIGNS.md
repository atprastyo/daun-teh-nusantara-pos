# Access flow — screen designs

15 November 2026 · First detailed flow · Asterfed

Design target for A-00–A-08 and OS boundary X-01 from
[the screen inventory](UI_SCREEN_PLAN.md). Open the
[interactive reference](design/access-flow.html) to compare phone/tablet and state
variants. This is a local design simulation with fictional accounts, not a login
client. No Android implementation or real approval is performed by these artifacts.

## 1. Behavioral constraints and source mapping

Use existing `AuthState` / `AuthUseCases` / `AuthViewModel` as the authority. Design
surface IDs do not require new AuthStage enum values or new endpoints.

| Existing value/action | Design use |
| --- | --- |
| LOADING / restore | A-00 session restoration |
| LOGIN, configured, message | A-01 form/configuration/errors |
| OUTLETS, choices, busy | A-02 memberships and A-03 in-flight selection |
| LOCKED, DEVICE_PENDING, pendingDeviceId | A-04 pending device; no offline shortcut |
| LOCKED / other message | A-05 restricted/unavailable access |
| canOffline, leaseExpiresAtMs | A-06 eligibility and bound expiry context |
| Android credential result + unlockOffline | X-01 then recheck before READY |
| READY, membership, offline | F-00 role-scoped handoff; still honor POS initial pull |
| loadDeviceRequests / approveDevice | A-07/A-08 inside authorized Staf route |
| signOut | Clear local authority, retain operational data; return to login |

Implementation anchors:
[Auth.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/auth/domain/Auth.kt),
[AuthScreen.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/auth/presentation/AuthScreen.kt),
[AuthViewModel.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/auth/presentation/AuthViewModel.kt),
[AuthRepositoryImpl.kt](../app/src/main/java/com/dauntehnusantara/pos/feature/auth/data/AuthRepositoryImpl.kt).
Server reviewer rules are in [schema.sql](../supabase/schema.sql).

Non-negotiable:

- Accounts/confirmation/roles are administrator-provisioned. No public signup,
  invitation, user-selected role, custom app PIN or new password-reset flow.
- Show only server-returned outlets/roles. New Owner self-enrollment is a server
  policy, not permission for the client to override a denied/revoked device.
- Offline entry needs existing valid authority and Android's secure device credential.
  First login, new device/outlet, invalid lease, reboot/clock problems or confirmed
  denial require the existing online validation path. Do not infer a specific reason
  unless state actually supplies it.
- Keep device secrets, tokens and passwords out of labels, logs, clipboards and saved
  UI state. A device UUID may be displayed; it is not the device secret.
- Existing one-calendar-month policy is a maximum. Display the actual lease deadline;
  never fabricate a fresh deadline or promise immediate offline revocation.

## 2. Shared layout and components

| Property | Compact <600 dp | Medium 600–839 dp | Expanded >=840 dp |
| --- | --- | --- | --- |
| Page | White, vertical, safe drawing + IME insets | White, centered single column | Quiet brand/context panel + active form pane |
| Content width | Available width, 16–24 dp gutters | Form max 480 dp, 24 dp gutters | Form max 480 dp; dialog max 560 dp |
| Branding | Original transparent logo 64–72 dp; app name | Same, comfortable top spacing | Original-color logo + short context on left |
| Main title | headlineMedium 28/36 | Same | Same; do not inflate every label |
| Controls | 48 dp minimum targets, primary 52–56 dp | Same | Same; no stretched full-tablet fields |
| Overflow | Vertical scroll; preserve focused field/CTA access | Vertical scroll as needed | Stack if panes/large text cannot fit; scroll form |
| Error/status | Inline near relevant action | Same | In form pane, not across the entire display |

At 200% text size, allow labels/rows to grow and switch to a single column if needed.
Use system sans, white canvas, primary #506B24, semantic status pairs and radii from
[UI_GUIDELINES.md](UI_GUIDELINES.md). On a white login canvas use the original-color
brand logo even if the device launcher is using its white night-mode variant.

Proposed reusable components: `AccessScaffold`, `AccessHeader`, `InlineStatus`,
`OutletChoiceRow`, `DeviceIdentity`, `AccessPrimaryAction`, `ApprovalReviewSheet`.
These are handoff names, not existing Kotlin APIs.

Static review captures: [phone login](design/access-phone.png) and
[tablet pending approval](design/access-tablet.png). These are browser-rendered
fictional examples, not Android screenshots; the interactive reference includes the
other states. Never copy its fixture role/approval/lease logic into the real auth layer.

## 3. Detailed screens

### A-00 — Restore and configuration

Purpose: decide the next authorized state without flashing the wrong account/scope.

- Original-color logo, app name, restrained progress and “Memeriksa sesi…”.
- No arbitrary splash delay or fake progress percentage. No authenticated data yet.
- Fresh account → A-01. Stored session → existing restore/revalidation; previous
  outlet may be restored only by that existing path, not by UI preference alone.
- Configuration missing → A-01 configuration variant with disabled submit and
  “Konfigurasi aplikasi belum siap. Hubungi pengelola aplikasi.” No URL/admin-key inputs.
- Corrupt/mismatched cache → existing safe login/locked outcome; never offer app-data reset.

### A-01 — Login

Hierarchy, top to bottom:

```text
[original logo]  Daun Teh Nusantara
Masuk
Gunakan akun yang diberikan pengelola.
[inline configuration / sign-in error, when present]
Email
[email field]
Kata sandi
[masked password field                      visibility]
[                         Masuk                         ]
Lupa kata sandi? Hubungi pengelola untuk pemulihan akun.
```

- Email uses email keyboard/Next; password uses password keyboard/Done. Persistent
  labels, supporting error text and minimum 48 dp visibility-button target.
- Email may be trimmed on submit as today; do not trim or transform the password.
- Client validation should match existing constraints, not invent a new password policy.
- Password hidden by default, memory-only; clear on submission/leaving the form. A
  proposed visibility toggle is local UI state, never persisted as a stored password.
- Submit requires configured backend, required inputs and not busy. Show “Memeriksa
  akun…” while in flight; disable repeated submission, keep visible progress text.
- No remember-password checkbox, role selector, sign-up, magic link or recovery email
  button without a separately reviewed implementation. Recovery help is plain text.
- Debug demo entry, if retained, stays visually separate and debug-only.

Variants:

| Variant | Copy / treatment | Action |
| --- | --- | --- |
| Initial | Helper text and enabled required fields | Masuk when valid |
| Invalid/missing input | Specific local field helper, retain email | Correct the input |
| Rejected credentials/denial | “Belum bisa masuk. Periksa akun atau hubungi pengelola.” | Retry without exposing account existence |
| Temporary network issue | “Belum bisa terhubung. Periksa koneksi lalu coba lagi.” | Existing online retry path |
| Rate-limited message | “Terlalu banyak percobaan. Coba lagi setelah beberapa saat.” | No invented countdown; verify backend classification before wiring |
| Configuration missing | Configuration message above | Submit disabled; contact app operator |

The RATE_LIMIT enum/rendering exists, but the current repository's HTTP 429 mapping
must be reviewed/tested before claiming this variant is reached automatically. Do
not change auth failure classification just to make a design preview work.

### A-02 — Outlet selection

- Title “Pilih outlet”, helper “Peran Anda ditentukan oleh pengelola.”
- Each row: storefront icon, outlet name (primary), brand (secondary), read-only
  role badge mapped to Pemilik/Pengelola/Kasir, and clear selection affordance.
- Tapping a row invokes the existing select(outletId) action immediately; it is not
  a client-side role choice and requires no new backend endpoint.
- Even one outlet may appear on fresh sign-in, matching current behavior. A verified
  previous outlet can resume automatically through restore/refresh.
- Busy selection: prevent another row action and display A-03 context. Preserve which
  outlet is being checked instead of showing an unexplained blank spinner.
- No choices after successful identity check → A-05 no-access state. Do not create a
  membership or show a fake empty outlet for the user to self-assign.
- Connection loss cannot enroll/switch to a new outlet offline. Any eligible offline
  entry remains tied to the original cached outlet/device, never the newly tapped row.
- Secondary “Keluar” returns to login through the existing sign-out action.

### A-03 — Access verification

Use a transient busy presentation, not a new persistent navigation route/AuthStage.
Show “Memeriksa akses perangkat…” plus the selected outlet when known. Do not show
individual checklist steps as complete before server confirmation, or expose proofs.

Outcomes: server-authorized READY → F-00; pending → A-04; no access/unavailable/invalid
cached authority → corresponding A-05 variant. Cashier then needs P-01's initial pull
before a new sale. READY must not be drawn as “all products are synced”.

### A-04 — Device awaiting approval

```text
[device/lock icon]
Menunggu persetujuan
Perangkat ini perlu disetujui sebelum digunakan.
Outlet contoh · Kasir
ID perangkat
[public UUID, wrapping]                    [Salin ID]
[                  Cek persetujuan                  ]
Minta pemilik atau pengelola outlet meninjau ID perangkat ini.
Keluar
```

- For Manager, helper names the Owner; for Cashier, Owner or assigned-outlet Manager.
- Copy only the public UUID, never a device secret or JWT. Full ID remains available
  for matching; do not replace it with an unmatchable decorative short code.
- “Cek persetujuan” refreshes existing context online. A still-pending result stays
  here with “Persetujuan belum diterima.” No fake polling countdown or guaranteed push.
- No local “Saya sudah disetujui” bypass, no offline unlock action while canOffline is false.
- Keep the same device identity on retry; do not suggest repeated sign-out/re-enrollment
  as the approval-check action, because that can rotate proof/reset approval.

### A-05 — Restricted/unavailable access

Use a meaningful heading and next step for the **known** state:

| Condition | Heading and action |
| --- | --- |
| NO_ACCESS | “Akun belum memiliki akses outlet.” Ask operator to check profile/membership; online retry or sign out |
| Confirmed denial | “Akses perlu diperiksa.” Return to the repository-selected login/locked state; no cached-authority override |
| UNAVAILABLE with canOffline false | “Belum bisa terhubung.” Retry online; explain that valid offline access is unavailable |
| Invalid/expired offline authority | “Validasi online diperlukan.” Connect and revalidate; do not claim reboot/clock/expiry reason unless supplied |
| UNAVAILABLE with canOffline true | A-06 alternative is available for the original bound scope |

Preserve retained Room sales/outbox. No clear-data/reset/reprice/reassign button in
an access-error screen. Do not reveal other accounts, outlet memberships or old roles.

### A-06 / X-01 — Offline unlock and system boundary

Show only eligible offline entry, driven by `canOffline`, with bound outlet/role and
actual expiry if available. Title “Buka akses offline”, primary “Konfirmasi kunci
layar”, secondary online retry, and concise “Persetujuan ulang tetap diperlukan saat
masa akses berakhir atau validasi perangkat tidak sesuai.”

- Launch the existing Android device-credential confirmation intent.
- PIN/pattern/device password is collected **by Android**, not by an app text field.
- Cancellation → keep locked, “Konfirmasi dibatalkan. Coba lagi saat siap.”
- OS success → invoke existing unlockOffline, which checks the lease again. If it
  expired/changed during the prompt, remain locked and require online validation.
- A design illustration of the OS dialog is not an exact promise of the OEM's UI,
  biometric support or available authentication methods.
- A successful offline entry cannot switch user/outlet or renew a lease. Role navigation
  and report/catalog availability still follow their existing offline behavior.

### A-07 — Reviewer device requests (inside Staf)

On an already authorized reviewer session: “Permintaan perangkat”, current outlet,
short approval policy, refresh, and rows with staff name, role, public device UUID.
Use a device symbol and “Tinjau” action. Empty: “Tidak ada permintaan perangkat.”
Failures expose retry without inventing an empty-success roster.

- Only server-returned permitted targets are shown. Manager cannot approve another
  manager; Owner is required for manager requests.
- No user-provided role selector, device secret, password or account-creation form.
- Tinjau opens A-08. This is another actor/device, not a button allowing a pending
  staff session to approve itself.

### A-08 — Approval confirmation

The reference isolates confirmation content for review. Production hosts it as a
phone sheet/tablet dialog over A-07, with focus/back/dismiss handling and busy guards,
not a new full-screen route. Show staff/role/outlet/full
public UUID and “Pastikan identitas staf dan perangkat sesuai.”

Primary “Setujui perangkat”; secondary “Batal”. Disable repeat action while busy.
Call existing approve(id), then refresh requests and display its result. Do not
claim a version/operation UUID parameter that this existing approval RPC does not have.
If the approval acknowledgment is lost, refresh request/status before offering another
explicit attempt; do not invent automatic retries or claim RPC deduplication.
If the target is revoked, access changes, or the server rejects the request, show
failure and refresh; never locally mark it approved. The staff device separately
checks approval and obtains its own authorized context/lease.

## 4. Action contract

| UI action | Existing owner | Important guard |
| --- | --- | --- |
| Masuk | AuthViewModel.signIn | Online identity + active application membership |
| Outlet row | AuthViewModel.select | Server choices; enrollment/role-bound device rules |
| Cek persetujuan / retry online | AuthViewModel.refresh | Live revalidation; preserve existing device context |
| Confirm offline, after OS success | AuthViewModel.offline | Domain rechecks canOffline/lease; UI cannot grant it |
| Keluar | AuthViewModel.signOut | Clear local authority, keep operational data |
| Refresh requests | AuthViewModel.devices | Live authorized reviewer/outlet |
| Setujui perangkat | AuthViewModel.approve | Server checks reviewer/target role/outlet/revocation |

Local UI state may hold password visibility, focused field, confirmation visibility
and a pending visual selection. It cannot hold authoritative role, lease validity,
backend approval or a replacement secret. Existing callbacks remain the integration seam.

## 5. Acceptance scenarios for implementation

| Case | Expected behavior |
| --- | --- |
| Fresh account, no internet | No first-login/offline grant; explain online requirement |
| Missing backend config | Clear configuration message; no submit/API call or secret-entry form |
| Valid credentials, zero memberships | No-access state; no self-service enrollment/outlet creation |
| One/multiple memberships | Correct server outlets/roles; selected action cannot fire twice |
| Manager new device | Pending; approval by Owner; no offline route |
| Cashier new device | Pending; Owner/assigned Manager review; no peer/self approval |
| Owner new device | Server policy may self-enroll; still reject revoked/denied access |
| Pending then check twice | Same enrolled identity; remain pending until actual authorization |
| Reviewer empty/error/stale target | Distinct states; no false success, refresh after result |
| Eligible offline, OS cancel | Remain locked; do not mutate lease or enter shell |
| Eligible offline, OS success | Recheck current lease before bound-scope READY |
| Lease expires/boot changes while prompt open | No offline entry after invalidation |
| Denial/profile/outlet/device revoked | Follow current authority clearing/locking; no bypass |
| Cashier READY before first pull | POS initial-sync gate remains; no enabled new checkout |
| Large font/IME/rotation/split screen | Inputs/actions readable and reachable; password not saved across recreation |
| Account/outlet/role switch | No stale scope-sensitive content or cached broader authority |

Existing regression anchors: AuthRepositoryTest, OfflineLeaseTest, AuthScreenTest and
RoleNavigation. Extend them when implementing the new presentation; design/browser
checks do not replace Android credential, real Auth or physical-device acceptance.

## 6. Implementation tickets

1. **ACCESS-01 — Shared access components/tokens:** use the existing guideline palette,
   typography/shapes, text-field/button/progress semantics and responsive scaffold.
2. **ACCESS-02 — Login/outlet selection:** A-00/A-01/A-02/A-03 with current callbacks;
   local input errors, IME/focus, busy state and read-only role labels.
3. **ACCESS-03 — Pending/locked/offline:** A-04/A-05/A-06 and X-01 result handling,
   correct authority gates, UUID display and existing-context retry.
4. **ACCESS-04 — Reviewer UI:** A-07/A-08 within Staf; confirmation and live result,
   preserving server hierarchy. Do not silently add new approval backend guarantees.
5. **ACCESS-05 — Regression/device verification:** compact/expanded/200% text/rotation,
   TalkBack, keyboard, OS credential cancellation/success and real test-user flow.

Then implement F-00/F-01 shared navigation/context using the same tokens. Remaining
screen groups still require their own detailed designs; this flow does not close M7
hosted/security/signing/recovery acceptance or introduce new business features.

## 7. Native implementation status — 15 November 2026

ACCESS-01–ACCESS-04 presentation is implemented in `AccessComponents.kt`,
`AccessScreen.kt`, `DeviceRequestsPanel.kt` and the existing AuthRoot integration.
Shared palette/type/shape tokens are centralized in Theme.kt. `lockedContext` is
read-only display context, never a replacement for READY membership; request-list
loading is explicit. OS credential results still invoke the existing domain recheck.
ACCESS-05 host regression coverage is added; physical-device, TalkBack, real IME/OS
credential and hosted two-actor acceptance remain required. The browser reference
and its screenshots remain fictional design artifacts, not native screenshots.

Manual device acceptance for this iteration:

1. Open a configured debug build at phone width. Check masked password, visibility,
   email Next/password Done, error recovery and keyboard access to Masuk. Rotate with
   a typed password: it must clear; no invitation/signup/role picker should appear.
2. Sign in with a provisioned test account. Select only a server-listed outlet;
   repeat taps during loading must not change context or enroll a second device.
3. For a new Manager/Cashier device, match the full public UUID with a separate
   authorized reviewer's Staf → Permintaan perangkat list. Cancel review once, then
   confirm it; repeated submits/dismissal while busy must be blocked. The requester
   must independently use Cek persetujuan. Manager targets require an Owner.
4. With an eligible enrolled device, disconnect and reopen: verify original outlet,
   role and actual expiry. Cancel Android's lock prompt, then retry successfully.
   Repeat with expired/reboot-invalid authority: no offline entry is available.
5. At >=840dp, review the split access layout and tablet approval dialog. At 200%
   system font size and in split screen, verify scrolling, readable UUIDs/actions,
   focus/TalkBack labels and safe system/IME insets. Keep real credentials out of captures.
6. Verify cashier READY still waits for initial catalog catch-up; auth redesign must
   not enable checkout through a pending sync/recovery or restored-feed gate.
