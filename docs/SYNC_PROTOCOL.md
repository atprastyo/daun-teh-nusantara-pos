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
- Price mismatch: define explicit offline price acceptance policy with owner before production; never silently change a customer's completed sale.

## Failure matrix
No network -> pending; app killed -> persisted outbox survives; ack lost -> idempotent retry; token expired -> pause sync until refresh; membership revoked -> reject and show admin action; schema mismatch -> quarantine operation and report; duplicate device request -> same server result; pull interrupted -> resume last committed cursor.

## Backup vs sync
Set independent database backup retention, secure offsite storage, restore drill, and export path. Sync alone propagates mistakes and deletions.
