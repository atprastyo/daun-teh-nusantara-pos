# Settings, sync and recovery screen designs

T-01–T-04, sign-out confirmation and recovery X-03 handoff, 15 November 2026.
[Interactive reference](design/settings-flow.html), [phone](design/settings-phone.png)
and [tablet](design/settings-tablet.png) are fictional browser artifacts, not Android
screenshots. Native implementation is next: SETTINGS-UI-01–04. Preserve UI_GUIDELINES,
shared shell, existing Auth/Room/sync contracts and BACKUP_RESTORE_RUNBOOK.

## 1. Source and boundaries

Verified anchors: AuthScreen.AccessPortal, AuthState/AuthViewModel.signOut and
AuthRepositoryImpl.signOut/checkoutScope/pullScope; SyncStatusPanel/ViewModel and
SyncStatus/PullStatus; RecoveryPanel/ViewModel, ExportRecovery, RoomRecoveryRepository
and RecoveryArchive. Graph search/trace/snippets and direct reads had clean best-effort
coverage metadata for relied-on Kotlin paths. This is a task-directed review, not a
full security audit. No new account, password, device approval or server endpoint.

All roles see Settings for their current authorized account, operational outlet and
enrolled device. No broader Owner/Manager export: archive scope is exact project,
brand/outlet/user/device/role. No backend URL editor, role selector, admin key, PIN,
public account creation, restore/import/reset/rebootstrap/delete-data button. Account
and password management remains the trusted administrator procedure. No new theme
preference, printer, payment gateway or backup scheduling capability is introduced.

AuthState exposes email, membership, access mode and lease expiry. Device UUID must
come from the matching current authorized PullScope, not pendingDeviceId or a made-up
hardware name. Never display/cache tokens, secrets, passwords, Keystore material or
raw project configuration. Reuse the shell's outlet/role/access header once.

## 2. T-01 · Setelan

```text
[shared operational outlet / role / access context]
Setelan
Akun & akses
  cashier@example.invalid
  Izin offline berlaku hingga <actual lease expiry · WIB>
  [Validasi akses online]

Sinkronisasi perangkat                    [Lihat rincian]
  Menunggu · Perlu akses · Perlu ditinjau · Diterima server
  Unduhan berhasil terakhir <actual stored timestamp or unavailable>
  [Coba pengiriman ulang]

Data pemulihan perangkat
  Arsip untuk akun, outlet dan perangkat ini.
  [Ekspor data perangkat]

Sesi akun
  [Keluar dari akun]
```

Group with whitespace, restrained neutral containers and semantic headings; use shared
shield/settings/sync concepts with labels, tea-green primary action, white light
canvas and existing dark theme tokens. Danger/sign-out stays away from retry/export.
No success-colored whole page or claim that online access proves queued work synced.
Account identity, device ID and actual lease expiry are selectable/readable details;
missing data says unavailable, never an invented expiry or current device model.

Validasi akses online calls existing refresh only when online/not busy. Explain
one-calendar-month maximum lease without implying a sliding month from this screen.
A valid lease still needs the established boot/clock/device/OS credential rules.
Offline mode is access authority, not a network monitor or a server-acceptance status.
Confirmed denial clears access through existing auth flow; transient validation failure
never manufactures an extended lease. Outlet switching remains the shell action.

## 3. T-02 · Status sinkronisasi perangkat

Use a compact summary with optional inline expanded detail, not a new navigation
permission. Observe the full current scope; outlet name alone is insufficient for
identity. Show push/local delivery separately from downloaded shared data:

| Available field | Label / implication |
| --- | --- |
| pending + retrying | Menunggu pengiriman; never accepted revenue |
| authRequired | Perlu validasi akses/perangkat; not retry success |
| review | Perlu ditinjau; original paid snapshots preserved |
| synced | Diterima server, based on local proven delivery; not a server backup |
| PullStatus.initialized | Initial catalog/history catch-up usable only when complete |
| PullStatus.lastSuccessMs | Unduhan berhasil terakhir · WIB; not last upload/backup |
| PullStatus.error | Honest pull failure/recovery state, never fabricate zero queue |

Null/unreadable counts show loading/unavailable, not “Semua tersinkron”. Zero is valid
only after a successful current-scope read. Avoid arithmetic overflow when aggregating
Int counts (sum in Long for presentation). Do not add progress percentages, estimated
completion, per-device server health, hardware status or exact scheduling guarantees.

Retry uses existing scoped SyncRepository.retry and SyncScheduler. It requests work,
not proof that network or upload succeeded. Show “Pengiriman dijadwalkan; tunggu status
berubah” only after scheduling returns; update totals from observed Room/pull state.
No retry operation may rewrite REVIEW/SYNCED payloads, reset feed generation or change
stable operation IDs. AUTH_REQUIRED directs online validation/approval. An unreadable
status uses “Muat ulang status” for a fresh read, not an unknown-scope enqueue.
Offline network-dependent actions are disabled with explanation; existing OS workers
remain best effort. Repeated taps are blocked while the request is being scheduled.

## 4. T-03 / X-03 · Local recovery JSON

Purpose: private operator reconciliation of retained real work. It is different from
accepted-server report CSV and hosted backups. Existing JSON contains original real
operation/sale UUIDs and exact immutable payload strings, delivery states/attempts,
command metadata, and the exact scoped draft/last-sale pointer. Includes retained
SYNCED rows for restore comparison; excludes demo, foreign scope, credentials and
server/Storage contents. It is not a complete downloaded-history/catalog backup.
Keep the existing envelope schema_version 1 / LOCAL_RECOVERY, SHA-256 checksum,
canonical payload, integrity inspection, exact Long values and **16 MiB UTF-8** bound.
Checksum is integrity only, not encryption, signature, authenticity or permission.

Proposed lifecycle (explicit native work; current prepare only stores a scope):

1. “Ekspor data perangkat” opens an explanation/confirmation with current email,
   outlet, device UUID, private-destination warning and existing contents/limits.
   Cancel does no capture, picker or write. Online or valid unlocked offline authority
   may export; bootstrap/recovery data gate alone must not prohibit authorized export.
2. Capture through ExportRecovery/RoomRecoveryRepository off Main under the complete
   original scope. Preserve the existing one-Room-transaction snapshot and repository
   pre/post authority/project checks; no sale/outbox writes or retries. Validate artifact
   size/integrity. During preparation show named progress and block duplicate capture.
3. Freeze the resulting bytes and original capture time/scope in memory **before**
   launching CreateDocument(application/json). This is an intentional presentation
   timing change from current capture-after-picker, not an archive format change.
   Copy says when captured; never imply it contains sales made while the picker was open.
4. Correlate the pending result by an opaque unique ticket. Only that callback ID may
   be saved for configuration recreation; no scope authority/archive/URI in saved
   state or disk. Null result, launch failure, scope change, navigation away or process
   loss clears prepared intent. Process loss cannot recreate authority from an old ID.
5. Before opening the provider, recheck current checkout scope and full pull scope,
   including role/project/device and valid access. Write/close on IO; check again after
   write. Cached valid offline authority is allowed; no new online grant is invented.
   A stale callback must never write a new scope's artifact. One picker/write at a time.
6. Completion means provider write/close finished while original authority remained
   valid. Say “Arsip selesai di lokasi yang dipilih. Lindungi salinannya.” Not “cadangan
   server selesai” or a guarantee the destination is private. Android providers can
   create an empty/partial file before cancellation/failure/authority loss; discard it.
   Retry chooses a new destination explicitly, never automatic overwrite/share/upload.

Too large: no truncated/silent export, tell owner/operator to follow reviewed device
extraction. Invalid archive: do not write partial/unvalidated bytes, preserve originals.
No import/replay, filesystem manager, share sheet, raw JSON viewer or restoration UI.
Operator instructions remain documentation, not buttons that perform live recovery.

## 5. T-04 · Restored server / recovery-required state

Use explicit warning heading “Data server perlu direkonsiliasi”. Explain that cursor,
catalog, local receipts and outbox are retained; new checkout stays gated on the
unchecked branch. A numeric cursor or previous SYNCED label does not prove that a
sale survived a server restore. Do not silently change epochs, clear application data,
reprice/reissue, mark pending as accepted or retry REVIEW/unknown restored branches.

Primary permitted action: authorized local recovery export. Secondary: readable
“Hubungi pemilik/operator. Ikuti panduan pemulihan yang ditinjau.” No fabricated phone,
email or incident service. No reset/rebootstrap/rotate-epoch button, new-sale action or
‘retry fixes restore’ copy. If access expired/revoked, export is unavailable and the
operator-approved access recovery procedure is required; retained work is still not
deleted. Show original immutable history where current existing authorization permits.
Successful login/online refresh or file export does not itself clear recovery-required.

## 6. Sign-out confirmation

All roles can leave their own session, including offline. An in-memory confirmation
names the current account and operational outlet. Present:

“Keluar menghapus izin masuk lokal. Struk dan antrean lokal tetap disimpan, tetapi
pengiriman memerlukan masuk kembali dengan akun dan perangkat yang berwenang.
Keluar bukan cara memperbaiki sinkronisasi atau data server yang dipulihkan.”

If pending/retry/auth/review counts are known, show them separately. If status is
loading/unavailable, say “Keadaan antrean belum dapat dipastikan”, not zero. Offer
Cancel and an optional return to export (closes sign-out dialog; does not silently
export). Do not require successful export to sign out or add an acknowledgement form.
User may explicitly confirm “Keluar dari akun” after the warning. Do not delete Room,
reassign retained work or claim remote revocation of every issued JWT/device.

Existing AuthRepository.signOut clears local encrypted authority and calls remote
logout best effort; remote failure must not keep UI falsely authorized. Navigate based
on actual AuthState stage, not a toast or tapped button. One sign-out request, busy
text, scope-bound identity; Back/Cancel before send has no side effect. Disable it
while capture/picker/write is active; external auth loss still cancels export intent.
After process recreation do not restore an unfinished destructive confirmation.

## 7. States, scope and adaptive behavior

Use generation guards for scope resolution, observation, retry scheduling, capture,
provider callback, sign-out and error/finally. Identity includes account, brand, outlet,
device, role and project, not just outlet string. Invalidate/clear old state before
waiting for cancelled IO. Independent access-validation, queue retry and export states
must not masquerade as one success. Missing scope/denial clears sensitive state and
requires auth validation. Refresh/offline toggle cannot extend the lease.

Current implementation gaps: settings is inline AccessPortal content; SyncStatusViewModel
loads by outlet and has unguarded late observations/errors/retry scheduling; recovery
prepare is scope-only, null picker result leaves prepared state, and write success/
finally lacks full generation/post-provider checks. Sign-out has no confirmation.
These are native implementation tickets, not claims the current app already has the
handoff behavior. Preserve data/repository and auth contracts rather than replacing them.

Compact: one scrolling page, full-width useful actions, focused sheets. Medium: rail
and bounded groups. Expanded: account/access and sync left, recovery/session right
when each pane >=320dp; otherwise one column. Forms/dialogs max560dp; 200% text/short
window collapses and scrolls bodies with fixed actions. Use actual window width.
Preserve scoped local expansion across rotation; not destructive intent after process
loss. Full IDs wrap/select, named progress/errors use live-region semantics, 48dp
targets, readable green contrast and existing dark theme. Restore focus on cancel;
Android owns picker and OS credentials. Browser checks do not prove TalkBack/provider.

## 8. Native tickets and acceptance

1. **SETTINGS-UI-01:** explicit scoped settings route/read model and adaptive grouped
   content replacing inline AccessPortal settings; preserve shell and auth callbacks,
   localized identity/lease/sync/recovery states, current public device UUID only.
2. **SETTINGS-UI-02:** generation-safe push/pull observation and bounded retry outcome;
   loading/error/zero/review/auth/recovery labels and no scheduled=delivered claim.
3. **SETTINGS-UI-03:** captured, validated in-memory archive/ticket and guarded picker/
   cancel/provider lifecycle; sign-out confirmation, scope/process loss and busy gates.
4. **SETTINGS-UI-04:** unit/Room/Compose regressions for scopes, last-pull versus push,
   unavailable counts, scheduling versus delivery, original payload/draft, 16 MiB bound,
   integrity/no credentials, zero/cancel/launch/provider/mid-write authority failure,
   process/rotation, sign-out retaining local work and recovery branch/no reset. Full
   Gradle build and feasible isolated phone fixtures; no real recovery mutation.

Remaining normal-app acceptance: actual document providers/large artifacts, Hilt/Auth/
Keystore/lease, concurrent WorkManager, scoped sign-out/re-login, physical tablet/
split-window/IME/TalkBack, hosted RLS/restore and signing. No live restore, production
SQL, backup setup or device data deletion is authorized by these design artifacts.

Exact next milestone: native SETTINGS-UI-01–04 presentation implementation.
