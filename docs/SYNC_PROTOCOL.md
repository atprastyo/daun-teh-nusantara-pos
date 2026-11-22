# Offline synchronization protocol

## Core guarantees
At-least-once transport, effectively-once sale creation through idempotent server processing. Never promise exactly-once delivery. Client Room is authoritative for unsynced local work; Supabase is authoritative for accepted shared records.

## Push flow
1. User completes sale; atomically persist sale, items, modifiers, payment, and immutable outbox operation in Room.
2. WorkManager observes network and sends pending operations with `operation_id`, `device_id`, `outlet_id`, `sale_id`, payload, and hash.
3. Server authenticates user/device, validates membership, catalog/price policy, amount arithmetic, and payload. In ONE PostgreSQL transaction, dedupe operation, insert sale graph, and append change-feed records.
4. On repeat with same id/hash return same acknowledgment; on same id/different hash reject and audit.
5. Client marks synced only after durable acknowledgment. On timeout, retry same operation id with exponential backoff and jitter.

## Pull flow
1. Request changes since last `sync_changes.seq` for permitted brand/outlet scopes, with pagination.
2. Server returns ordered changes and next cursor; no missing sequence assumption for filtered feeds.
3. Apply page and persist cursor in ONE Room transaction; then request next page.
4. Handle tombstones, catalog versions, permission changes, and revoked device access.
5. Realtime can trigger an extra pull, never replace cursor-based catch-up.

## Conflict policy
- Sales: immutable; void/refund via separate authorized operation.
- Catalog: server version/optimistic concurrency; on mismatch fetch authoritative version and surface conflict.
- Stock: do not claim strict real-time stock when offline; future inventory uses movement ledger and reconciliation.
- Price mismatch: user selected strict rejection for review. A new upload must match current active catalog prices, names, versions and modifier constraints; preserve the completed local receipt. An already accepted operation returns its original acknowledgement even if the catalog later changes.

## Failure matrix
No network -> pending; app killed -> persisted outbox survives; ack lost -> idempotent retry; token expired -> pause sync until refresh; membership revoked -> reject and show admin action; schema mismatch -> quarantine operation and report; duplicate device request -> same server result; pull interrupted -> resume last committed cursor.

## Backup vs sync
Set independent database backup retention, secure offsite storage, restore drill, and export path. Sync alone propagates mistakes and deletions.

## M4 concrete push contract

`POST /rest/v1/rpc/submit_sale` uses the signed-in user JWT and public API key.
Body: `p_payload` is the immutable UTF-8 JSON **string**, `p_device_secret` the
Keystore-protected enrollment secret. The string contains schema version 1,
`demo:false`, UUID operation/sale/brand/outlet/device/cashier IDs, UTC epoch ms,
integer IDR totals (discount zero), checkout line/version/modifier snapshots and
one cash or cashier-confirmed manual QRIS payment. Maximum payload 262144 bytes.
No credential belongs in Room/outbox or WorkManager input/output.

The SECURITY DEFINER function fixes search_path, denies anonymous execution, checks
live membership and role-bound device approval/proof before dedupe, hashes parsed
canonical JSONB server-side, and serializes each operation. Equal operation+payload
returns ACCEPTED; changed identity/payload returns REVIEW and an audit code. A
successful graph, operation and SALE change feed append share one transaction.
Payment ID is sale ID in its own table; modifier IDs derive deterministically from
line ID and option ID. Feed writers must take transaction advisory lock 58301924001
before allocating seq, so committed feed order remains safe for future M5 cursors.

Ack: operation_id, sale_id, request_hash (canonical JSONB SHA-256), payload_hash
(exact incoming UTF-8 SHA-256), status ACCEPTED or REVIEW; review also has an
allowlisted code. Android checks both IDs and exact payload_hash before SYNCED.
No response body, receipt or credential is logged. Catalog mismatch, invalid sale,
operation conflict and reused sale identity stay immutable in REVIEW. HTTP 401/403/
423 pauses that scope for reauthentication/approval; 429/5xx/IO retry with bounded
exponential backoff and stable jitter. Unknown/malformed acknowledgements quarantine
for review rather than claiming success. No automatic repricing or reissue.

Room states: PENDING, RETRY, AUTH_REQUIRED, REVIEW, SYNCED; demo stays PENDING_DEMO.
Attempts and next due time persist before IO. Cancellation/process death cannot
strand an IN_FLIGHT flag because no such state is used. Connected unique KEEP one-time
work plus an hourly periodic sweep and startup nudges recover commit/enqueue gaps; OS scheduling
is best effort. Worker processes 20 rows per pass, scoped to the encrypted session's
original account, outlet and enrolled device, refreshing expired tokens privately.
Manual retry resets due times only for deliverable rows, never REVIEW/SYNCED, and replaces a backoff-delayed worker so the request can run immediately when connected.

New uploads older than one server calendar month or earlier than device enrollment
are reviewed. This is an admission bound, not proof of offline checkout time: there
is no device attestation. Local M3 monotonic, same-boot authorization still applies;
server live authorization wins at submission, including after offline revocation.
M5 catalog pull and real cashier integration remain separate.

## M5 concrete pull contract

`POST /rest/v1/rpc/pull_changes`: user JWT/public key, `p_device_id`,
`p_device_secret`, `p_after_seq` (nonnegative bigint), `p_role` (expected live role),
`p_limit` (1–100). Secret stays in encrypted auth storage, not Room or worker input.
Response schema_version 1 binds brand/outlet/user/device/role and includes after_seq,
next_seq, has_more, and ordered changes {seq,entity_type,entity_id,action,payload}.
Pages stop at 100 records or 512 KiB; client bounds responses at 1 MiB.

Only selected-outlet sales and shared brand catalog are returned. Cashiers receive
own sales across devices; managers/owners receive selected-outlet sales. Owners
switch outlets for other outlet histories; brand reports are M6. A role mismatch or
future cursor returns 409 and requires online context validation. Current membership,
active profile/outlet/brand and approved role/device proof are checked on every page.

Catalog INSERT/UPDATE triggers emit payload {schema_version:1,table,record}; soft
inactive rows use DELETE action but retain the complete record. Assignment tombstones
remove only the local link. Other catalog tombstones retain identity/FKs. Server
UPDATE increments version and timestamp; physical deletion is blocked. Install the
fresh initial schema before importing real catalog data so initial rows have feed
entries; no legacy production backfill or migration is implied.

Feed writers use exclusive transaction lock 58301924001, readers shared lock. The
reader captures max committed seq, filters current scope, and may advance across
invisible gaps after exhausting visible rows. Never require contiguous sequence IDs.
All future feed writers must use the same lock; otherwise cursor safety is invalid.
Do not prune/reseed the feed without a reviewed snapshot/rebootstrap protocol.
Hosted concurrent-session acceptance and restore/recovery are still required.

Android cursor key includes project URL/brand/outlet/user/device/role. Each page
applies catalog rows, immutable scoped sale receipts and cursor in one Room
transaction. Unknown schema, malformed sequence/scope or conflicting immutable sale
rolls the page back, retaining previous cursor and recording a retry/error code.
Partial bootstrap cannot enable checkout. Replays compare catalog versions globally
within the backend so an old role cursor cannot revert newer menu state. Role
promotion replays from its own cursor; demotion uses only the narrow cached scope.

Downloaded receipts are separate from local operational sales/outbox and are merged
by UUID for scoped history. A matching durable feed echo may mark an outbox SYNCED;
no local receipt/payload is changed. Pull never deletes pending work or rewrites paid
snapshots. HTTP/auth/page errors retain cached usable data within M3 authorization.
A backend URL change with existing real sync data fails closed; use an explicitly
reviewed export/reset/rebootstrap rather than mixing databases.

The worker pushes then pulls up to five pages per pass, retrying if more remain.
Periodic, startup, foreground/network and explicit retry nudges converge without
Realtime. Timings are best effort. Real cashier uses downloaded catalog and durable
scope-specific drafts; menu changes invalidate unpaid stale carts instead of silently
repricing, while paid stale sales follow the user's M4 review policy.


## M6 restored-feed generation

The current initial pull RPC also accepts `p_epoch uuid` (null only at first bootstrap)
and returns `feed_epoch`. Persist it with the cursor in the page transaction. For an
existing cursor, server/client generation mismatch yields HTTP 410/REBOOTSTRAP_REQUIRED;
Room retains cursor/catalog/sales/outbox and marks initial catch-up unavailable to
prevent new checkout on an unchecked branch. Export remains possible under valid
authority. Old epoch is never silently replaced or the cursor reset automatically.

After an explicitly authorized isolated restore, the trusted generation-rotation
example acquires the feed lock, compares the restored epoch, rotates it and writes
an audit event. Reconcile retained device work before any approved rebootstrap.
A numeric cursor alone cannot detect a restored branch with reused sequence values.
See BACKUP_RESTORE_RUNBOOK for actual local rehearsal evidence and live prerequisites.

## M7 online catalog editing

Owner/Manager commands use live approved device proof and current feed epoch through
`save_catalog_record`. Catalog records are brand shared. Each command carries an
expected version and stable UUID; the private server ledger binds actor/device and
canonical request hash. A matching retry returns its original acknowledgment;
a different request under that UUID is a conflict. Writes, link tombstones, dedupe,
audit and feed share one transaction. The Android editor never writes Room directly:
it requests pull and waits until the acknowledged `through_seq` is applied. Offline
catalog editing is disabled. Form/uncertain command state is memory-only across
navigation/outlet reselection; after process loss inspect the refreshed catalog
before recreating a record. This does not change durable sale delivery semantics.

## Optional product-photo events

`product-photos.sql` adds `CATALOG` rows with table `product_photos`. Their record ID
is the product UUID, with brand, nullable image_path, active and independent version.
Active=false removes the reference but not historical Storage bytes. Updated clients
validate brand/product/path and persist the row's metadata with the cursor atomically;
photo events never bump product price revisions or change original paid snapshots.
Upgrade all clients before publishing this optional table; old clients must not skip
unknown events. Binary files are fetched separately on display, not in change-feed or
sale outbox payloads. `set_product_photo` uses live device proof/role/epoch, expected
photo version and stable operation UUID; photo reference, dedupe, feed and audit commit
in one transaction. Upload itself precedes this transaction and can leave an orphan
on interruption. See PRODUCT_PHOTOS.md for private cache and cleanup boundaries.

## Optional STOCK-02 protocol (21 November2026)

New clients try pull_changes_stock;404 falls back to the existing financial/catalog
feed without enabling inventory. Stock-enabled pages carry an explicit capability;
STOCK balance and operation acknowledgement apply with cursor in one Room transaction.
Legacy cursors gain fresh current-balance events on the device’s first stock-capable
pull. Stock estimates retain local unconfirmed consumption until its own stock
acknowledgement, even when finance has already become SYNCED or REVIEW.

New checkout commits an immutable stock intent beside the unchanged financial outbox.
Submission passes that intent to submit_sale_stock under the original sale UUID;
accepted finance and stock are atomic, while paid changed-price review still records
stock. An uncertain stock acknowledgement retries without rewriting financial data.
Count-generation mismatch, legacy/untracked consumption and other paid financial
exceptions enter outlet-scoped reconciliation. Never sum a confirmed own-operation
stock event and its pending estimate twice. See OUTLET_STOCK.md for cutover/recovery.
