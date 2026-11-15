# Staff and access screen designs

Design handoff for S-01–S-03, 15 November 2026. Delivered by Asterfed for Daun Teh
Nusantara. [Interactive reference](design/staff-flow.html) uses fictional records and
local simulation only. It does not change Android, accounts or backend permissions.
[Phone capture](design/staff-phone.png) and [tablet capture](design/staff-tablet.png)
show fictional browser layouts. Use UI_GUIDELINES and the existing shared shell/theme/icons. Native implementation
is the next milestone, STAFF-UI-01–04; this iteration adds no business capability.

## 1. Existing contracts and limits

Source anchors: feature/staff/domain/Staff.kt (StaffRules, ManageStaff), presentation/
StaffScreen.kt and StaffViewModel.kt; data/StaffJson.kt; AuthRepositoryImpl management
methods; DeviceRequestsPanel for A-07/A-08; supabase/schema.sql list_staff,
set_staff_access and set_device_access (538–598). Graph coverage is best-effort clean
for those Kotlin paths. SQL coverage is partial: the named management functions and
grants were read directly, not an exhaustive authorization audit. AUTH_SECURITY,
MANUAL_STAFF_PROVISIONING and M6_TEST_PLAN remain authoritative.

- Roster is **current operational outlet**, including for Owner. No brand-wide staff
  selector, cross-outlet roster, invitation, add-user form, password/role/global
  profile editor. Use existing shell outlet revalidation to manage another outlet.
- Members expose user ID, name, email, MANAGER/CASHIER role, membership active,
  profile active and positive expected version. Owners are excluded by list_staff.
- Devices expose UUID, enrolled user ID, nullable current role, revoked, approved and
  version. They have no returned hardware name, last-seen time or pending-sale count.
  Owner can see outlet devices; Manager receives only devices with live CASHIER role.
  Revoked takes precedence over approved when displaying the returned booleans.
- Owner receives up to 50 selected-outlet allowlisted audit events, newest first;
  Manager receives no audit. No pagination, full audit archive or export is implied.
- list_staff is bounded to 512 KiB. TOO_LARGE asks the operator for review; do not
  pretend that a truncated roster is complete or add unsupported pagination.
- Staff data is memory-only and online management only. No Room staff cache, offline
  mutation/outbox, optimistic toggle or instant disconnected revocation promise.
- Restoring/enabling does not grant new role, profile activation, device proof or a
  new lease. Approval remains the existing separate A-07/A-08 flow: Owner approves
  managers; Owner/assigned-outlet Manager approves cashiers. No reason/version field
  is added to the existing approval API by this handoff.

## 2. S-01 · Staf

One operational outlet/role/access header is supplied by the shell. Page content:

```text
Staf                                  [Perbarui]
Kelola akses untuk outlet yang dipilih.
[Anggota] [Perangkat] [Aktivitas akses · Owner only]

Anggota
Rani Putri                 Kasir
rani@example.invalid
[Akses outlet aktif]                [Nonaktifkan akses outlet]

Akun ditambahkan oleh administrator.
Hubungi administrator untuk akun, peran atau kata sandi.
```

No duplicate outlet switcher. Use headlineMedium page title, titleMedium names,
bodyMedium email/effects, labelLarge role/status/action labels. White canvas in light
mode, neutral grouped surfaces and restrained tea-green primary action. Shared group,
shield/store icons are decorative beside labels. Error color is for revocation/error,
not the entire page; status always includes text. Avoid one heavy outlined card per
line: rows, whitespace and dividers; named groups where they aid scanning.

Tabs are local presentation state over the returned roster, not extra server calls
or permission grants. Reuse localized names Pemilik/Pengelola/Kasir; never expose raw
role codes as the primary label. Member row distinguishes:

| Returned state | Label | Action |
| --- | --- | --- |
| Profile and membership active | Akses outlet aktif | Disable if permitted |
| Profile active, membership inactive | Akses outlet nonaktif | Enable if permitted |
| Profile inactive, membership active | Profil nonaktif · tinjau administrator | May disable existing membership if permitted |
| Profile and membership inactive | Profil nonaktif · tinjau administrator | No enable action; explain admin review |
| Self / peer Manager under Manager | Read-only role/access | Explain role restriction; no mutation |

Keep membership/profile labels distinct, even if a global inactive profile already
denies use. Show IDs selectable in detail/confirmation, not mysterious shortened
identities on the destructive action. Email can wrap; no fabricated avatars/contact
channels or network images.

Perangkat shows owner/name or returned user UUID if unresolved, full device UUID,
current-device label where IDs match, current role if returned and state:
Dicabut / Menunggu persetujuan / Disetujui. Unknown role says “Akses anggota perlu
ditinjau”, not Kasir. Pending is not approved; approved alone is not proof of current
online/offline permission. Display the existing approval request panel as its own
section with its own load/error/result state; do not infer requests from roster flags
or claim a roster refresh also refreshed requests. Preserve A-07/A-08 identity review.

Aktivitas akses (Owner only) shows readable event label, Jakarta date/time, reason
when present, actor and target IDs. Resolve names only from matching returned rows;
otherwise retain UUID. Label “Hingga 50 aktivitas akses outlet yang dikembalikan”.
Unknown action fallback: “Aktivitas akses”, not a successful known operation. An empty
list means no returned events, not proof that no access changes ever occurred.

## 3. Permissions and effects

UI mirrors StaffRules; RPC/live proof and RLS remain authoritative. Fresh server role,
member/device version and current scope must still be checked before dispatch.

| Operation | Owner | Manager |
| --- | --- | --- |
| Member disable/enable | Other Manager/Cashier in this outlet | Other Cashier in this outlet |
| Member self / Owner / other outlet | Denied | Denied |
| Revoke device | Other device in this outlet, including unknown role | Other live Cashier device in this outlet |
| Revoke current device | Denied | Denied |
| Restore revoked device | Other device with live returned role | Denied |
| Restore when role null | Restore member/profile access first; no action | Denied |
| View access audit | Returned selected-outlet <=50 | Unavailable |

The current-device exclusion is by device UUID; it does not prohibit an Owner from
revoking another device of their own account. Do not broaden membership self-block
into an invented device API rule. Cashier has no staff destination. Client gating
never establishes server authorization; no mutation can be submitted from hidden UI.

## 4. S-02 · Outlet membership confirmation

Use a focused compact sheet; >=600dp readable window can use a centered max560dp
dialog. At large text/short window use a bounded scrolling body and fixed actions,
IME padding, visible reason field and labeled Cancel/Confirm. Back/outside dismissal
before dispatch is Cancel and makes no call. Capture member ID, original access,
desired active boolean, expected version and complete source scope on opening.

Title: “Nonaktifkan akses outlet?” or “Aktifkan akses outlet?”
Body: name/email/role, full target ID, outlet, current → requested membership state.

Disable copy: “Akses anggota di outlet ini dinonaktifkan dan perangkat outlet milik
anggota dicabut. Perangkat yang terputus dapat tetap memakai izin offline yang masih
berlaku hingga validasi online atau masa izin berakhir.” Existing offline bound is
one server calendar month; do not suggest a new month starts at revocation.

Enable copy: “Akses outlet diaktifkan. Perangkat yang dicabut tetap dicabut. Pemilik
perlu memulihkan perangkat, lalu persetujuan baru tetap diperlukan.” This does not
activate a global profile, change another outlet or reset a password.

Persistent label “Alasan perubahan”, helper “3–240 karakter setelah spasi tepi
dihapus”. Match StaffRules trim/length semantics and server validation; whitespace-only,
2 and 241 reject, 3 and 240 accept. Do not silently truncate a pasted reason and
submit changed text. Store reason only in scoped VM memory until dispatch, never
logs/saved-state/disk. Reason is audited; tell users to avoid passwords or secrets.

Primary button names the effect (Nonaktifkan/Aktifkan akses outlet), never ambiguous
OK. Cancel is separate and neutral. One dispatch at a time; no auto-dismiss before
acknowledgement. Do not implement mutation by toggling a newly refreshed row: send
the captured desired boolean with its expected version through the existing gateway.

## 5. S-03 · Device confirmation

Same layout/reason/cancel rules, with enrolled user, current returned role, outlet,
full device UUID, current state → requested state. No made-up model/last-seen data.

- Revoke: title/button “Cabut akses perangkat”. Explain that online checks will deny
  the revoked device; disconnected access is bounded by the existing lease. Do not
  claim remote wipe, immediate offline logout, deletion of receipts or lost-sale
  recovery. Retained queued work needs the existing reviewed recovery procedure.
- Restore: “Pulihkan ke menunggu persetujuan”. Owner-only, revoked and live role
  required. Explain that restoration clears approval and does not activate the
  device. Fresh role-appropriate approval remains separate. Enabling membership
  first does not itself restore devices.
- Do not add restore on active devices or a revoke action for the current device.
  Role-null restoration explains membership/profile review first. Show restrictions
  as explanatory copy, without inviting a forbidden retry.

## 6. State and lifecycle contracts for native implementation

| State | Presentation / action |
| --- | --- |
| Initial/loading | Text progress, no fake empty list or editable target |
| Loaded empty | Distinct no returned members/devices/events; manual account guidance |
| Refresh | Same-scope previous roster can be read as “Memperbarui · data sebelumnya”; disable mutations until validated |
| Offline | Clear roster/requests and pending drafts; say “Pengelolaan staf memerlukan koneksi”. No offline staff directory or queued change |
| Error / TOO_LARGE | Honest retry/operator guidance, no success or fabricated zero counts |
| Permission loss | Clear all roster/draft/result before waiting for old jobs; revalidate |
| Version CONFLICT | Explain data changed; refresh and review target/effects/reason anew; no force overwrite |
| Dispatching | One request, named busy text; no repeat/dismiss-as-undo |
| Ack received, reload fails | “Perubahan diterima; daftar belum diperbarui”. Keep mutation ack distinct from read failure; refresh only |
| Timeout/cancel/process loss after dispatch | “Hasil belum dapat dipastikan. Perbarui dan periksa akses sebelum mencoba lagi.” No blind replay |
| Refreshed desired state after uncertain write | Say current observed state; do not attribute it to this operator without ack/audit evidence |
| Scope/account/role/device/backend change | Clear sensitive roster, local tab, target, reason, pending intent and transient outcomes; ignore old result/error/finally |

Keep source and request-generation guards around scope lookup, roster IO, mutation
ack/readback, errors and finally, including non-cancellable IO. Separate roster and
approval lifecycles; block conflicting reviewer operations without mixing success
messages. Recheck that the captured target/version still matches visible authorized
state before dispatch; a newly read version invalidates an open confirmation.

Membership/device RPCs have expected versions but no operation UUID ledger. A lost
response can mean committed state; repeating an old version can return CONFLICT.
Never retry an inverse toggle from a refreshed row as if it were the same command.
A cancel of a coroutine or Back after dispatch cannot undo a server commit. Keep a
same-scope uncertain result while still authorized online; on offline/scope loss
clear sensitive UI and require fresh load before new actions. Process restart loads
fresh state, never restores a destructive intent/reason or automatically retries.
UI must not mark success from list refresh alone. No endpoint changes are required.

Current StaffViewModel clears roster on reload, combines write/read success and has
unguarded late error/finally paths; StaffScreen stores selected object/reason in
composition. STAFF-UI-01/03 must address those implementation gaps; the prototype
is the intended contract, not a claim that current Android already behaves this way.

## 7. Adaptation and accessibility

<600dp: vertical list, local tabs can wrap/scroll, contextual action below identity.
600–839dp: existing rail; readable list, confirmations bounded. >=840dp: roster list
and selected read-only identity/effects panel when both fit (list min320dp, detail
min320dp); otherwise focus one pane. Tablet toolbar never gives broader authority.
At 200% text collapse to one pane, wrap names/emails/UUIDs, retain full labels and
scrolling confirmation body; never shrink type to fit. Current tab/selection should
survive window changes in scoped memory without retaining authority across scope.

48dp actions, semantic headings, visible focus, non-color status, labeled shield/
group/device icons using existing rounded symbol system; decorative icons have no
extra announcement. Announce loading/errors/ack/uncertain outcomes, restore focus to
the originating row after Cancel. Dialog traps focus; Escape/Back before send cancels.
No browser modal/animation is proof of Android TalkBack/IME/rotation acceptance.

## 8. Implementation tickets and acceptance

1. **STAFF-UI-01:** scoped in-memory session/tab/selection/reason, complete source and
   request guards; online-only read and separate load/write/readback outcomes. Preserve
   current authorization, fields and expected-version gateway; no new persistence/API.
2. **STAFF-UI-02:** localized adaptive S-01 rows/detail/tabs, profile vs membership vs
   device labels, honest audit limit and manual provisioning help. Integrate existing
   A-07/A-08 once, with independent request lifecycle; preserve shared shell context.
3. **STAFF-UI-03:** captured S-02/S-03 target/version/desired effect, strict reason,
   fresh-state validation, cancel/submit/ack/uncertain/conflict handling and no replay.
4. **STAFF-UI-04:** domain/VM/Compose regressions for permissions, profile inactive,
   role-null/current device, revoked/approved precedence, 3/240 boundaries, duplicate
   taps, conflict, ack+read failure, lost response/process loss, stale scope/error/
   finally, offline clearing and independent approval; full Gradle build and feasible
   isolated phone checks. Existing SQL tests remain evidence, not newly executed here.

Browser acceptance covers fictional Owner/Manager/Cashier, selected outlet changes,
membership and device distinct effects, disabled profiles, Owner-only restore/audit,
reason/cancel, conflict/uncertain/ack+read failure and stale callback rejection.
Normal-app acceptance still needs hosted RLS/proof/two-actor races, device approval,
Keystore/lease/offline revocation, IME/rotation/TalkBack and physical tablet. No live
accounts, SQL, deployments or real access mutations are authorized by this artifact.

Exact next milestone: native STAFF-UI-01–04 presentation implementation.


## 9. Native implementation — 15 November 2026

STAFF-UI-01–03 now use StaffSession and guarded StaffViewModel IO, with explicit
SAVED/ACK_REFRESH_FAILED/UNCERTAIN/CONFLICT outcomes and refresh-only recovery. A fresh
roster preflight compares the captured target/version before dispatch. Expected desired
access always derives from the original immutable target, never a newly toggled row.
Cancellation after dispatch is uncertain unless acknowledgement already arrived.

Compose now provides member/device/Owner-audit tabs, virtualized separated rows,
localized roles, distinct profile/membership/device statuses, full selectable IDs and
readable bounded audit labels. Wide windows use list plus guidance pane, following the
reference; identities/effects stay in the focused confirmation rather than adding a
second editable pane. Large text collapses to a single list. Dialog/sheet bodies scroll
with fixed Cancel/Confirm and IME padding; Cancel restores originating action focus.
Reasons remain untruncated scoped memory and use the existing trimmed 3–240 rule.

The existing approval component remains independent inside Perangkat. A new optional
presentation gate blocks it during staff load/write/confirmation; auth reviewer busy
blocks roster reload/write. No API/reason/version/approval semantics are changed.
Scope/offline loss removes the roster and approval surface before old IO can publish.
No new Room cache, pending access outbox, account form, role editor or RPC was added.

Executed checks and limitations are in PROGRESS.md. Normal-app hosted authorization,
concurrent operators, real approval/Keystore/offline lease and physical tablet/IME/
TalkBack remain acceptance work; component fixtures cannot establish these gates.
Exact next milestone: detail T-01–T-04 settings/recovery/sign-out and existing X-03
recovery export screens before their native presentation redesign.
