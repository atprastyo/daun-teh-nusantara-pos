# Product stock per outlet

21 November 2026 · STOCK-01 and STOCK-02 implementation. The user selected **whole product
counts**, editable by Owner or assigned Manager. This narrows/supersedes the earlier
broader manual-ledger proposal in OUTLET_STOCK_PLAN.md. Ingredients, recipes, restock/waste-specific workflows, thresholds, transfers and
valuation remain deferred. STOCK-02 adds automatic finished-product deductions with
warn/allow checkout and separate financial/stock review.

## Enable the feature

1. Review/run [supabase/outlet-stock.sql](../supabase/outlet-stock.sql) **once** in
   the intended Supabase project's SQL Editor, after the existing base schema.
   Repeat separately for DEV/PROD. Do not rerun schema.sql or reset devices/data.
   This optional setup adds stock tables/RPCs without rewriting existing data.
2. For automatic deductions, review/run
   [stock-auto-deduction.sql](../supabase/stock-auto-deduction.sql) **once**, after
   outlet-stock.sql. Pause checkout/sync writers during this deployment. Upgrade all
   cashier/management clients and synchronize retained queues before opening stock.
   Keep original data; do not reset tables, devices, Room or cursors.
3. Install the updated app for that environment. No Edge Function change. Reconnect/
   sync each device, then verify/set current physical counts before resuming checkout.
4. Owner/Manager signs in and selects the operational outlet using the existing
   outlet selector. Open **Stok outlet** in the menu.
5. Select **Ubah jumlah**, enter the current whole-unit count and a 3–240 character
   reason, review the previous/new count and signed difference, then save.
6. Use **Riwayat produk** or all-outlet history to review changes. Counts apply
   only to this outlet. Cashier does not have this management destination/API.

No hosted SQL, working-brand stock entries, automatic opening counts or device
installation were executed by implementation. Existing products show **Belum dicatat** until first counted; a recorded zero is displayed as **0 unit**.

## Behavior

- One balance per catalog product/outlet; sizes, toppings and other modifiers do not
  create separate stock balances.
- Physical count input is a whole number from 0 through1,000,000,000. Automatic
  sales may make server balances negative (checked floor−1,000,000,000,000). Warn,
  show the debt and allow checkout. Initial zero is valid. With STOCK-02 a reasoned
  recount may confirm the same number and still create a new count generation.
- First count is the opening record. Later counts append a new event recording
  previous/new quantity, difference, count revision, reason, actor/name, product
  name snapshot and server UTC timestamp. Display uses Asia/Jakarta.
- Count history is append-only. Correct a mistake by posting another reasoned count.
  Product name/price changes never rewrite old movements or financial receipts.
- Completed paid sales consume the summed quantity per base product in that outlet.
  Cart edits, cancelled/failed payment and demo sales never consume stock. Variants/
  modifiers do not create separate stock items. Price-rejected paid sales still
  consume stock while their financial receipt remains in REVIEW.
- Phone uses one scrolling list; expanded windows use product/history panes. Search
  is literal text; products page by100, history by50 with an older-page cursor.
- Management lists use timestamped server snapshots. POS separately shows confirmed
  server quantity, this device’s unconfirmed quantity and an estimate, with warning
  rather than a checkout block. Other offline devices remain unknown.
- STOCK-02 uses a versioned stock-capable pull endpoint and outlet-shared durable
  STOCK/own-delivery events; balance, own-operation acknowledgement and cursor apply
  in one Room transaction. WorkManager’s existing one-hour interval is unchanged.
  Checkout/write completion requests prompt ordinary work. Realtime is not required.
- Valid authorized offline sessions can view exact cached query pages. Offline
  writes are disabled. Network failures show cache age/unavailability rather than
  inventing zero balances. Archived counted products remain visible but not editable.

## Consistency and recovery

Every command freezes a client UUID, server restore epoch, product, absolute count,
expected count revision and reason. The command is persisted in existing Room
app_metadata **before** sending. Only one unresolved command is allowed per full
project/brand/outlet/user/device/role scope. UI double taps are gated and repository
mutations serialized. There is no offline auto-submission queue.

After a missing acknowledgement, timeout, malformed response or process restart,
**Periksa / ulangi perubahan** submits the same frozen operation. The server returns
the original acknowledgement for an identical operation even after later counts.
Changing payload/actor/device under the UUID conflicts. Definitive count conflict or
validation rejection clears the pending journal and requires refresh/recount; an
uncertain result never creates a replacement command. A confirmed write remains
confirmed even if subsequent snapshot readback fails; a stale snapshot is not editable.

Server authorization verifies live identity, initial-password gate, current role,
outlet and approved device/secret before reads, dedupe or writes. Direct REST table
access, anonymous and Cashier access are denied. The enrolled device determines the
outlet; clients cannot supply another target outlet. Owner changes outlet through
normal authorization. Existing user deactivation/revocation checks continue to apply.

An operation lock deduplicates requests; an outlet transaction lock serializes counts
and makes read snapshots coherent. Product/balance locks and expected revision prevent
last-write-wins. Movement, balance projection, durable operation acknowledgement and
STOCK_COUNT audit commit together. First-count races also conflict instead of silently
overwriting. Stock is not a new sale/price revision and does not change old receipts.
Hosted independent-connection contention still requires acceptance testing.

Room stays at version1/14 tables: no migrations, schema reset, destructive fallback
or data clearing. Snapshot and pending keys use the existing metadata store with
full authorization scope. Normal recovery export now includes a validated optional
stock_pending command, without passwords/tokens/device secrets. It is evidence for
operator review, not an import/replay tool or a full stock backup. Server database
backups must include stock tables and private.stock_operations. Restore epoch changes
block retained old stock commands; export and reconcile against server movement UUIDs
before any deliberate replacement. Role/outlet/device changes retain the old journal
under its original scope; do not clear data to bypass an unresolved operation.

## Acceptance before working-brand use

Use a disposable hosted project and controlled stock records: Owner and Manager
update the same product/outlet from two independent devices, verify stale conflict
and lost-response retry, restart during submission, deactivate staff and verify
server denial, switch outlet/account/offline lease and verify cache boundaries.
Check phone/tablet, large text, keyboard, TalkBack and pagination against real JWTs.
Automated checks and their actual outcomes are recorded in PROGRESS.md. No claim of
real-device/hosted concurrency acceptance is made by host tests.

## Automatic deductions, review and cutover

The paid receipt and immutable stock intent commit alongside the original outbox and
consumed draft in one Room transaction. Stock intent records product totals, restore
epoch and the physical-count generation observed at checkout. Retries keep both
financial payload and stock intent unchanged under the same operation UUID. There is
no HTTP deduction at cart edit or a separate unprotected post-upload subtraction.

The SQL stock submission wrapper owns financial admission plus stock movement,
balance, dedupe result and durable feed in one server transaction. An accepted sale
is deducted once. A price-rejected paid sale gets normal stock consumption while its
original receipt/prices/payment stay in financial REVIEW. Independent receipt math/
payment checks prevent a malformed price-rejected payload consuming goods. Receipt
UI distinguishes financial delivery from stock confirmation/review. Repeated upload
returns its original acknowledgement, even after prices/counts later change.

A physical count changes a separate generation. Late offline sales carrying an older
generation **do not blindly subtract from that new count**, because those goods may
already be included. Owner/Manager sees **Tinjau** under Stok outlet and chooses:

- **Sudah termasuk dalam hitungan**: acknowledge without a further subtraction.
- **Belum termasuk**: deduct now against the displayed count revision.

Both choices require a reason, frozen operation UUID and server authorization; stale
versions conflict. Uncertain resolution keeps the existing stock_pending recovery
journal and retries the same command. This never approves/reprices a financial sale.
Untracked goods, legacy payloads without intent, or other valid paid receipts outside
financial admission policy also require explicit stock reconciliation. Missing stock
is never interpreted as zero; establish an opening count before choosing deduction.

Existing accepted server receipts are **not backfilled**. Existing queued payloads
remain immutable: their financial admission may continue but stock is explicitly
reviewed rather than silently inferred. Upgrade all devices before rollout; app
version downgrades during active inventory operation are unsupported. The first
stock-capable pull seeds current outlet balances once per enrolled device, so an old
financial cursor can continue without a reset. Private financial admission is revoked
from client roles; the legacy submit RPC routes through stock handling rather than
allowing a direct inventory bypass. Legacy pull remains compatible and omits stock
entities; new clients use pull_changes_stock with explicit stock_enabled capability.

Normal recovery export includes each retained receipt’s original stock_intent as well
as manual/resolution stock_pending. It excludes credentials and is for evidence and
operator reconciliation, not an automatic replay/import tool. Pending consumption
may be financially SYNCED or REVIEW; do not discard it based only on outbox status.
Keep private.stock_sale_operations, private.stock_sale_lines, stock movement/balance
records and stock feed in database backups, along with existing dedupe/restore state.

Next milestone: controlled hosted/two-device and physical accessibility acceptance,
then deliberate SQL deployment/install. Automatic ingredients, transfers and costing
remain outside this slice.
