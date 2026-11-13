# Data model proposal

Use UUID PKs, `created_at`, `updated_at` (UTC) where appropriate, soft deletion/tombstones for synced catalog. `brand_id` and `outlet_id` scoped explicitly where applicable.

## Identity / access
- `brands(id, name, active)` initially one record: Daun Teh Nusantara.
- `outlets(id, brand_id FK, name, active)`.
- `user_profiles(id FK auth.users, full_name, active)`.
- `brand_members(user_id, brand_id, role OWNER, active, unique(user_id,brand_id))`.
- `outlet_members(user_id, outlet_id, role MANAGER|CASHIER, active, unique(user_id,outlet_id))`.
- `devices(id, outlet_id, enrolled_by, last_seen_at, revoked_at)`.

## Catalog
- `categories(id, brand_id, name, sort_order, active, version)`.
- `products(id, brand_id, category_id, name, base_price_idr BIGINT, active, version)`.
- `modifier_groups(id, brand_id, name, min_select, max_select, version)`.
- `modifier_options(id, group_id, name, price_delta_idr BIGINT, active, version)`.
- `product_modifier_groups(product_id, group_id, sort_order)`.
- Optional `outlet_product_availability(outlet_id,product_id,available,version)`.

## Sales
- `sales(id, brand_id, outlet_id, device_id, cashier_user_id, sale_number, subtotal_idr, discount_idr, total_idr, status, created_at, server_received_at)`.
- `sale_items(id, sale_id, product_id, product_name_snapshot, unit_base_price_idr, quantity, line_total_idr)`.
- `sale_item_modifiers(id, sale_item_id, option_id, group_name_snapshot, option_name_snapshot, price_delta_idr)`.
- `payments(id, sale_id, method CASH|QRIS_MANUAL, amount_idr, tendered_idr nullable, change_idr nullable, verification_status)`.
- `sale_adjustments(id, original_sale_id, kind VOID|REFUND, reason, approved_by, created_at)` later if needed; never silently overwrite a completed sale.

## Sync / audit
- Android `sync_outbox(id, operation_type, aggregate_id, payload_json, state, attempts, next_retry_at, created_at)`.
- Server `sync_operations(id, device_id, outlet_id, request_hash, result_sale_id, processed_at)` with UNIQUE(id) for idempotency; verify matching hash on retry.
- Server `sync_changes(seq BIGINT monotonic, brand_id, outlet_id nullable, entity_type, entity_id, action, payload/version, changed_at)`; scope feed by permissions.
- Android `sync_cursor(scope, last_seq)`.
- `audit_logs(id, actor_user_id, brand_id, outlet_id, action, target_id, metadata, created_at)`.

## Constraints
Use integer IDR and nonnegative checks; enforce FKs and role enum constraints; unique `(outlet_id,sale_number)` if human receipt numbering is used; client-generated UUID is primary deduplication key. Do not use timestamps as sync cursors. Database schema, Room entities, and migrations must be reviewed together.

## M1 Android catalog implementation (2026-11-13)

The initial development Room schema (version 1) contains `categories`, `products`,
`modifier_groups`, `modifier_options`, `product_modifier_groups` and `app_metadata`. Catalog records
have UUID text IDs, `brand_id`, active tombstones, local optimistic revisions and
`created_at_utc_ms`/`updated_at_utc_ms` integer UTC timestamps. Option rows and
product-group links also carry brand IDs to support composite foreign keys.
Category-product, group-option, and product/group-link references use RESTRICT;
no catalog record is physically deleted. Assignment replacement is transactional
and increments the product's revision. Required indexes and unique `(id,brand_id)`
keys support scoped reads and FKs. SQL validation triggers cover nonnegative
amounts/order, names, versions, boolean flags, timestamps and modifier bounds on
fresh creation. Application validation also enforces UUIDs.

No migrations are maintained during the current development phase, per user
direction. The current schema replaces the bootstrap schema; existing development
installs must be cleared or reinstalled after incompatible schema changes. No
automatic destructive fallback is configured. Keep the current schema export for
review. Before retaining released data, introduce reviewed migration paths; exported
Room JSON does not describe triggers. No identity/outlet, sales, outbox or server tables
are added yet. Demo data uses a local-only brand UUID, not a provisioned identity.

## M2 local checkout implementation (2026-11-13)

The current initial development schema v1 also includes `checkout_session`,
`sales`, `sale_items`, `sale_item_modifiers`, `payments` and `sync_outbox`. No
migration paths are added. Reset existing development installs explicitly.

- `checkout_session`: one durable demo-outlet draft, device UUID, sale/operation
  UUIDs, revision, immutable cart snapshot JSON, and last undismissed receipt ID.
- `sales`: brand/outlet/device/cashier UUIDs, unique operation UUID, local command
  hash, UTC timestamp, subtotal, zero discount, total and COMPLETED status.
- Sale items retain product/name/unit price/quantity/line total columns and a
  complete immutable snapshot JSON including selected modifier and catalog versions.
  Modifier rows independently retain group/option names and unit price deltas.
  Catalog FKs are deliberately absent on historical snapshots: changing/retiring
  the catalog must not change or erase a completed receipt.
- Payments are unique per sale; cash has tender/change with arithmetic checks,
  QRIS_MANUAL has no cash fields and requires CASHIER_CONFIRMED_MANUALLY.
- `sync_outbox` uses operation UUID PK and unique sale aggregate FK. It holds a
  versioned immutable DEMO_SALE payload, PENDING_DEMO state, zero attempts and UTC
  creation time. Payload/identity/deletion guards preserve pending work. No worker
  submits it. It is not the server M4 request contract or proof of cloud acceptance.

One Room transaction validates current catalog, writes the full sale graph and
outbox, rotates the consumed draft IDs and retains the receipt pointer. A matching
operation/request hash returns the stored receipt before looking at today's cart
or catalog; different commands sharing an operation reject. Mutations use draft
revision checks. SQL triggers enforce nonnegative amounts, cash/QRIS invariants,
append-only sales and immutable outbox identity/payload. Foreign keys are RESTRICT.
The local command hash covers draft ID/revision, operation ID and payment, not a
future server payload hash. M3/M4 must add authenticated context and vetted protocol
validation before enabling real sale submission. All identities are demo-scoped.

## M3 initial server foundation (2026-11-13)

`supabase/schema.sql` defines the initial development PostgreSQL schema (no migration
history): brands/outlets, profiles and owner/outlet memberships; device approval and
hashed secret state; manually assigned staff profiles/memberships; catalog graph; immutable
sales/items/modifiers/payments; unique server operation identities, scoped monotonic
change feed and audit logs. It is not applied to a hosted database by this iteration.

Every exposed table uses RLS with explicit SELECT grants, including nested sale
scope and own-history constraints for cashiers. Authenticated direct writes are
not enabled; device approval/enrollment/lease issuance are
narrow server functions. No service key or password is stored in
application tables. Devices expose only non-secret columns; only their secret hash
is stored server-side. Offline expiry is computed as one UTC calendar month.

Android Room remains unchanged at initial schema v1; M3 auth state is separately
Keystore-encrypted in no-backup storage. Demo sale IDs/outbox payloads remain isolated
and never acquire real user membership merely because someone logs in. M4/M5 must
validate actual sale context/device proof and introduce vetted mutation/pull APIs.

## M4 outbox extension (initial Room v1)

`sync_outbox` adds `nextAttemptAtMs`, `lastErrorCode`, `acknowledgedAtMs`.
Initial real state PENDING and operation SUBMIT_SALE; allowed transitions persist
attempts and due time, then RETRY/AUTH_REQUIRED or terminal REVIEW/SYNCED.
Identity, payload and creation time remain immutable; deletion remains forbidden.
Demo operation/state remain DEMO_SALE/PENDING_DEMO. No migration, version bump,
destructive fallback or data reset is performed automatically.

Server `sync_operations` stores canonical JSONB request hash and accepted sale ID.
`submit_sale` is the only authenticated graph writer, checking current user/device/
outlet authorization and strict catalog policy. It appends one SALE feed record
in the same transaction. See SYNC_PROTOCOL for stable derived child IDs, feed lock
ordering, rejection/audit and exact acknowledgement hashes.

## M5 local pull state (initial Room v1, 14 tables)

- `sync_cursor(scope PK,lastSeq,initialized,lastSuccessMs,errorCode)` scopes the
  cursor to backend/brand/outlet/user/device/role. Initial catch-up becomes usable
  only after a complete page sequence. No implicit cursor reset on server errors.
- `remote_sales(scope,saleId PK,operationId,seq,payload)` retains immutable downloaded
  receipts separately from locally created sale graphs/outbox. History merges UUIDs.
- `app_metadata` retains backend binding, cross-scope catalog version/payload guards
  and local completed checkout command dedupe. Draft sessions use the complete scope
  key, not the shared demo outlet. Sale/outbox and consumed draft commit together.
- Catalog and sale snapshot tables remain compatible with M4; no migration or
  destructive fallback is added. Development installs need explicit reset/reinstall
  for the changed initial schema; no app data was cleared by this iteration.

Server catalog triggers produce full-row versioned tombstones in `sync_changes`.
`pull_changes` is the approved-device, live-role, byte/record-bounded read API. Feed
sequence allocation/read boundaries share the M4 advisory lock; see SYNC_PROTOCOL.


Manual provisioning revision (2026-11-13): the current initial server schema has
18 public application tables. Removed staff_invitations and its create/accept RPCs;
no live DROP/migration was executed. Auth identities are created only by trusted
Supabase admin tools; `provision-staff.sql.example` adds profile/outlet membership
and STAFF_PROVISION audit in one trusted transaction. Login reads existing access
without creating/reactivating profiles. Room remains v1, 14 tables, unchanged.


## M6 reports, access versions and restore generation

Initial Room stays v1 with 14 tables. `sync_cursor.serverEpoch` binds each committed
cursor to a server feed branch; restored generation mismatch preserves data/cursor,
records REBOOTSTRAP_REQUIRED and gates checkout. Report cache entries in app_metadata
are scoped by project/brand/outlet/user/device/role/date range; no receipt graph is
rewritten for reports. Recovery export preserves original real payloads and command
metadata without exporting auth/device credentials. No migration or fallback added.

The initial server schema keeps 18 public RLS tables. `outlet_members.version` and
`devices.version` support optimistic management. Device version changes only with
proof/approval/revocation, not ordinary last-seen updates. Private sync_generation
contains one epoch UUID; it is not exposed or granted to clients. Report/staff/access
RPCs use approved-device and live role checks; access mutations and audits commit
atomically. A trusted post-restore epoch rotation records FEED_RESTORE_BOUNDARY.
