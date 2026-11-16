# Bluetooth receipt printing — implementation plan

Status: planning only, 16 November 2026. User confirmed 58 mm paper. Printer
manufacturer/model, Bluetooth transport and command compatibility remain unconfirmed.
This is a newly requested scope beyond the original MVP printing exclusion.

## First release

- One selected printer per Android installation, with an outlet-specific selection
  so switching outlets cannot silently send a receipt to the wrong printer.
- Pair through Android Bluetooth settings; select an already paired device in
  Setelan → Printer struk. No in-app discovery or background printing initially.
- Manual Cetak struk on the committed checkout receipt. Cetak salinan on authorized
  history detail; copies clearly say SALINAN. Automatic checkout printing is deferred.
- Owner and Cashier print receipts their existing receipt-read scope permits.
  Manager currently has no receipt history/POS navigation: do not add access implicitly.
  All authenticated roles may configure/test the local printer using a fictional test
  slip; configuration never grants receipt access.
- Printing works without internet under valid existing offline authorization.
  Denied permissions, printer disconnection and paper failures never block a sale.
- 58 mm text receipt first. Logo raster, QR codes, automatic cutting, cash drawer,
  USB/LAN/BLE support and automatic printing are separate extensions unless the
  selected model makes a different transport necessary.

## Compatibility gate — PRINT-00

Obtain brand/model or manual/self-test printout. Confirm Classic Bluetooth RFCOMM/SPP
versus BLE/vendor protocol, service UUID, ESC/POS support or vendor SDK, printable dot
width, font/code-page support, and any reliable status/readback commands. Paper width
alone does not identify these. Do not assume all 58 mm printers are ESC/POS or use
384 dots/32 columns. Record a tested model/profile; do not promise universal support.

Provisional approach: Android platform Bluetooth transport with a small tested receipt
encoder if the model supports documented ESC/POS. Choose a library/vendor SDK only
once model documentation, licensing and current toolchain compatibility are verified.
No dependency/version is selected by this plan.

## UI — PRINT-01

Settings card: selected device/name, 58 mm profile, permission/Bluetooth status,
Pilih printer, Buka pengaturan Bluetooth, Cetak tes, and Lepaskan pilihan.
Missing/empty paired list explains pairing; Bluetooth unavailable/off, permission
rejected/permanently denied, connection timeout and unsupported model each have a
specific recovery action. Never request permission on app startup.

Receipt: add print action independently of next-sale and cloud-delivery actions.
If unconfigured, open printer setup and require a fresh explicit print tap afterward.
Show preparing/connecting/sending, then Data dikirim ke printer — periksa hasil cetak.
Do not claim paper output solely because socket writes returned successfully.
Show uncertain output as Hasil cetak belum dapat dipastikan; request confirmation
before another copy. Print errors do not say payment failed or request checkout retry.
Use existing Material icons/green theme, >=48dp targets, accessible announcements,
phone full-width controls and tablet bounded panels. Back closes setup normally.

## Receipt content — PRINT-02

Build a pure immutable print document from Receipt, not current product prices.
Include Daun Teh Nusantara, selected receipt outlet identity, original sale UUID,
Asia/Jakarta date/time, original products/modifiers, quantity × unit amount, line
amounts, total IDR, payment method, cash tender/change, and manual QRIS wording.
Keep the full UUID accessible/printable by wrapping. No invented receipt number,
transaction-time cashier name, tax, address or payment settlement claim.

Receipt currently contains brand/outlet/cashier IDs but no historical display names.
Resolve only a matching authorized outlet identity and mark display metadata as current;
use the original outlet UUID when its name cannot be resolved. Omit missing address/
cashier display name. Freeze display metadata/profile at print request; do not mutate
sale snapshots or introduce a sales-schema change just for printing. Reprints retain
original money/time/products; current display labels are not historical snapshots.

Sanitize ESC/control characters from every user/server text field before encoding;
wrap names/modifiers and align currency using the verified profile. Bound input/output
size, handle unsupported characters explicitly and test long names/maximum quantities.
Print-job status and cloud-sync status are independent; printed output never asserts
server acceptance. A REVIEW sale remains unchanged and reviewable after printing.

## Architecture and lifecycle — PRINT-03

Use feature/printing/{domain,data,presentation}, Hilt, ViewModel + StateFlow.
Domain: PrinterProfile, PrintDocument, PrintAttempt and PrinterRepository interface;
use cases prepare authorized receipt, select/test printer, print and explicit copy.
Data: Android Bluetooth transport, pure profile encoder, existing DataStore dependency
for local configuration and a dedicated bounded attempt journal. UI never owns sockets.
No Supabase RPC, sale/outbox transaction, Room schema or development migration change.

Re-read current authorized receipt scope before starting and validate frozen account/
outlet/receipt/profile identity immediately before the first write. On scope change or
sign-out cancel/close outstanding transport; bytes already sent cannot be recalled.
Never display another account's attempt journal. Do not store JWTs, device secrets or
full receipt payloads in printer preferences/journal; bound retention and omit Bluetooth
addresses/receipt contents from logs. Backend approval device IDs are unrelated to
printer Bluetooth addresses.

Serialize device-wide jobs so bytes cannot interleave and repeated taps create only
one in-flight attempt. Perform socket connect/write on IO threads with bounded
connection/write deadlines; close the socket on cancellation/timeout to unblock calls.
Persist attempt identity/scope before any write; mark SENDING durably before output.
If journal writes fail, do not start output. Journal records IDs/status/profile/hash,
not a new sale or payload copy; reconstruct documents through authorized receipt reads.

Failures before output are retryable by user action. Partial writes, timeout after
write, cancellation during send or interrupted SENDING after process death are UNKNOWN.
Never automatically resend on reconnect, recomposition, rotation, startup or WorkManager.
Any further attempt is an explicitly confirmed copy. Local attempt history cannot
prove whether this receipt was printed on another device; do not promise exactly-once
physical printing, global copy counts, or confirmed paper status without model support.

## Android permissions

For already-paired communication, request BLUETOOTH_CONNECT at runtime on Android 12+;
legacy BLUETOOTH permission is limited to API30 and below. Mark Bluetooth hardware
optional so checkout remains usable on unsupported devices. No discovery/advertising/
location permissions in the paired-only baseline. Do not call discovery-management
APIs requiring SCAN unless that capability is explicitly introduced and justified.
Use the system UI to enable/pair Bluetooth. Verify API26–30 and API31–36 behavior.

Primary references, reviewed 16 November 2026:
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android Bluetooth connection/threading](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)

## Verification and rollout — PRINT-04

1. Encoder tests: saved cash/manual QRIS, zero/large IDR, long names/modifiers,
   Unicode fallback, control-byte injection, wrapping, full UUID and SALINAN.
2. Repository/lifecycle tests: double tap, scope changes/revocation, offline valid vs
   expired, journal failure, partial output, timeout/cancellation, rotation/process
   death, and explicit copy. Assert sale/cart/outbox identities/counts never change.
3. Compose tests: compact/tablet, text scaling, missing printer, permissions, loading,
   errors, unknown outcome and recovery, with shared checkout/history actions.
4. Gradle build/unit tests/lint; verify release manifest permission boundaries.
5. Physical acceptance with actual 58 mm printer: Android permission versions,
   pairing, power loss/out-of-range, reconnect, paper out, print readability and
   repeated copies. Run fictional test slips first; obtain authorization before
   creating any working-brand sale for end-to-end testing.

Implementation order: PRINT-00 compatibility confirmation, PRINT-01 settings,
PRINT-02 renderer, PRINT-03 transport/journal and receipt integration, PRINT-04 hardening.
Each is a reviewable iteration with tests and progress/decision updates. The next
implementation milestone is PRINT-00 plus the smallest verified settings/test-print
slice; do not integrate live receipt printing before that hardware path is verified.

## First implementation slice (16 November 2026)

User described the device as a generic 58 mm thermal printer; exact model remains
unknown. Implemented paired-device configuration and an explicitly unverified Serial
Port plain-ASCII test path, without ESC/POS initialization/profile/logo/cut commands.
This is a compatibility probe with fictional text, not proof of receipt compatibility.
The confirmation explains Bluetooth Classic/Serial Port requirements before output.
No live Receipt integration or persistent receipt print-attempt journal is enabled.

Use Setelan → Printer struk → Buka pengaturan Bluetooth to pair the printer, return
and Muat printer berpasangan (grant Nearby devices when requested), select the
printer by name/address, then Cetak tes → Kirim teks tes. Check physical output.
For permanent permission denial use Buka izin aplikasi. Lepaskan pilihan removes
only the account/outlet printer preference, not the Android Bluetooth bond.

Configuration is DataStore-backed per project/user/outlet. The singleton transport
serializes tests, uses secure RFCOMM SPP, closes on cancellation/12-second deadline,
and reports unknown outcome after possible partial writes. Navigation/account scope
changes cancel the test; no startup/reconnect/resume resends. Test slips contain no
sale/account identifiers, money transactions or credentials; process death simply
leaves the user to inspect paper before another explicitly confirmed fictional test.
Receipt attempts still require PRINT-02/03's durable journal before live integration.

Version choices: existing Kotlin/Compose/Hilt/DataStore dependencies and Android API
26–36 only; no third-party printer library. Protocol source: Bluetooth SIG assigns
SerialPort service 0x1101 in its
[Assigned Numbers](https://www.bluetooth.com/specifications/assigned-numbers/).
BLE-only devices and unsupported models require a later verified adapter. Pairing,
permission behavior on real phones and readable paper output remain unverified.
