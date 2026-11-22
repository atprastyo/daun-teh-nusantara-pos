# Per-outlet stock movement plan

17 November 2026 · original planning proposal. On19 November the user approved
whole-product counts editable by Owner/Manager. The narrower implemented STOCK-01
slice is documented in [OUTLET_STOCK.md](OUTLET_STOCK.md); that document supersedes
broader first-milestone proposals below. No hosted deployment has been executed.
Ingredient/recipe/automatic-consumption/transfer proposals remain planning only.

## Decisions to confirm

1. Track finished sellable products, ingredients/packaging, or both. User question
   is pending; do not assume one drink equals one tracked item or cup consumed.
2. Whether automatic deduction should warn or block when stock is insufficient.
   Recommended later offline policy: show warnings and allow explicitly authorized
   exceptions, with negative balances visible for reconciliation. This is a proposal,
   not an approved rule. Strict shared availability cannot be guaranteed offline.
3. For ingredients, recipe quantities for size and sugar/ice/topping modifiers,
   purchase/base units, required precision and the operational opening-balance date.

The manual-ledger foundation below does not depend on choosing sale-deduction rules.
Do not enable automatic deductions until item coverage, recipe/version handling and
pending/rejected-sale consumption policy have been agreed and tested.

## First milestone — STOCK-01

Add **Stok outlet** as a dedicated operational destination, not editable quantity on
the shared product catalog. First release is online Owner/Manager manual movement
entry plus authorized cached balance/history viewing under a valid offline lease.
Cashier gets selected-outlet stock read access; no manual adjustment authority.
Movement entry requires an approved device and current server authorization.

Scope: define tracked stock items, set one opening balance per item/outlet, record
restock, waste/damage and reasoned adjustments, and review movement history. A
finished-product stock item may reference a catalog product; ingredient/packaging
items remain separate from sellable drinks. Activating manual tracking does not imply
sale deductions, recipe consumption or automatic POS availability enforcement.

Exclude purchase orders, suppliers/payables, valuation/COGS/profit, expiry/batches,
barcode scanning, reservations, recipes, auto-deduction and transfers from STOCK-01.

## UX

### Stock overview

Show the selected outlet prominently. Owner may select authorized brand outlets;
Manager stays within assigned outlet context. Changing a report/stock filter never
silently switches the operational checkout outlet. Phone: searchable stock list;
tablet: list with balance/movement detail pane. Follow existing green theme, shared
form fields, outlined secondary actions and accessible 48dp touch targets.

Each row: item name, unit, confirmed balance, low-stock indicator, and as-of time.
Distinguish zero, negative, loading, stale, unavailable and untracked; missing data is
never rendered as zero. Low-stock threshold is per outlet/item; a negative balance
must remain visible, not clamped to zero. Read cached data only within existing role/
user/outlet/project/lease scope. Show offline/stale notice and disable manual writes.

### Movement entry

Owner/Manager selects type and item, enters quantity with visible unit, optional
external reference, and required reason for waste/adjustments. Opening is a separate
setup operation; later count corrections cannot rewrite it. Preview signed change
and known balance, then confirm a frozen operation. Show saving/confirmed/uncertain/
retry/conflict states. An offline attempt explains that manual entry requires online
access and retains only the unsubmitted form, not a claimed stock update.

Physical count entry accepts actual counted quantity and captures observed balance
revision. Server computes the difference under lock. If stock changed since the count
began, show conflict and require reconciliation/recount; do not overwrite intervening
sales or silently calculate a new adjustment from a stale screen.

### Movement detail/history

Filters: date/type/item; list movement time, actor, reason, signed quantity/unit,
reference and server status. Detail exposes movement/operation IDs and reversal links.
Never expose edit/delete on a posted movement. Correction is a new compensating
movement, requiring authorization/reason. A validated empty history differs from
network or permission errors. Dates display Asia/Jakarta; UTC is retained in storage.

## Proposed data model — not an existing API contract

| Record | Responsibility |
| --- | --- |
| stock_items | Brand identity, name, active flag, optional product link, immutable base unit and precision |
| outlet_stock_settings | Per-outlet/item tracking activation, low-stock threshold and version |
| stock_movements | Immutable UUID, stable operation UUID, brand/outlet/item, signed base quantity, movement type, actor/device, UTC occurrence/server times, reason/reference and compensation link |
| stock_balances | Transactional projection of ledger sum per outlet/item, revision and latest accepted sequence; not independently editable |
| stock_operations | Operation UUID, canonical request hash and durable result for idempotency |

Use checked Long quantities in a defined smallest unit, never Float/Double. For
finished-product pieces, require whole units. For fractional materials, choose
precision/base unit before use (e.g. grams or milligrams), then keep that definition
immutable once movements exist. Display conversions must not alter historical
quantities; multi-unit purchasing/conversion UI is deferred initially.

Movement signs: opening/restock positive, waste negative, reasoned adjustment signed;
reversal negates its referenced movement. A reversal does not delete the original
entry or revoke a sale/payment. Enforce one opening operation per tracked item/outlet,
unique operation identity, nonzero movement quantity, valid same-brand relationships,
checked arithmetic and limits on reversal totals. Freeze the policy for movements
that would go negative before implementation; do not quietly choose it in SQL.

## Consistency, permissions and delivery

Server transaction: validate live identity/role/outlet/device, dedupe stable UUID/hash,
lock balance row, validate expected revisions/policy, insert immutable movement,
update balance projection and append durable stock feed/audit records atomically.
Retries with identical UUID/hash return the original result; different payloads under
one UUID conflict. Last-write-wins balance assignment is prohibited.

Owner may act in authorized brand outlets. Manager may act in assigned outlets;
Cashier may only read selected-outlet operational quantities/history allowed by the
final policy. Enforce RLS and narrow RPCs, not just hidden buttons. Keep credentials
in existing encrypted auth storage, never in stock rows, jobs or diagnostics.

Online command identity must be durable locally before submission so process death
or lost acknowledgement can be reconciled without duplicate movement. Distinguish
server confirmation from local readback; unavailable readback is not a failed write.
Apply movement/balance feed pages and cursor atomically in Room. All permitted outlet
stock movements are shared across staff devices: do not reuse the cashier-own-sales
feed predicate for stock entities. Realtime is only a refresh hint.

Manual STOCK-01 commands do not queue for later automatic submission when offline.
A journaled online operation with an unknown outcome is reconciled by its original
UUID; editing its payload or creating another movement requires resolving it first.
Recovery tooling must include new operational records without exporting secrets.

## Later milestones

### STOCK-02 — Automatic consumption

For finished products, snapshot the tracked-item mapping and sold base quantity.
For ingredients/packaging, add versioned recipe requirements covering size/modifiers
and explicit stock consumption units. Do not guess recipes from drink names.

Persist local sale, immutable consumption intent and required outbox records in one
Room transaction. When online sale admission succeeds, post sale and consumption
ledger effects atomically/idempotently server-side. Old client compatibility, tracking
activation/cutover and existing retained sales must be planned: never silently apply
current recipes to old sales or allow old clients to omit required consumption.

Existing price-change policy rejects uploads for review even though a local payment
was recorded and physical materials may already be consumed. Keep those consumption
intents visible as unresolved; do not discard them, count them twice or silently put
stock back. Require a dedicated stock-only reconciliation path linked to the original
operation when the financial sale is not admitted. This policy is a release gate for
automatic deduction, not an implementation detail to postpone after launch.

Show confirmed server balance separately from local unconfirmed consumption. Incoming
accepted movements replace the matching local effect by operation identity. Do not
sum an accepted movement and its pending intent twice. Other offline devices remain
unknown; any combined number is explicitly an estimate. Do not claim strict real-time
stock or global prevention of overselling. Availability toggles and tracked stock
have separate meanings until their enforcement policy is explicitly agreed.

### STOCK-03 — Transfers

Introduce a transfer document with requested/approved/dispatched/partially received/
received/cancelled states and immutable line quantities. Source dispatch decreases
source stock into in-transit stock; destination receipt increases only the quantity
actually received. Both movements reference stable transfer-line identities. Bound
cumulative received/returned quantities; retries cannot post twice. Handle losses,
returns and cancellation after dispatch with compensating movements, never deletion.

Owners approve cross-outlet transfers within their brand. Assigned Managers operate
only their own dispatch/receipt side of an approved transfer; access to the other
outlet's balances is not implicit. Do not model an immediate source decrement plus
destination increment as proof of physical receipt.

### STOCK-04 — Operational insight

Per-outlet low-stock list, movement-type totals, usage/waste trends, stock-count
variance and in-transit exceptions. Physical value/profit metrics need separately
approved costing data and policy. Keep confirmed, pending and unresolved consumption
separate rather than using a decorative graph as proof of current availability.

## Implementation and acceptance

1. Confirm tracking unit/type and insufficient-stock policy; detail STOCK-01 screens.
2. Implement server ledger/projection, role/device/RLS boundaries and isolated tests.
3. Add local read cache, durable operation reconciliation and scoped stock feed.
4. Implement adaptive overview/entry/history and shared quantity formatting.
5. Verify sums/reversals, quantity precision/overflow, one opening, concurrent edits,
   idempotent retry/lost ack, process restart, cursor replay, stale UI conflicts,
   cross-outlet denial, Manager/Cashier scoping, offline expiry and recovery export.
6. Run Gradle/unit/DAO/UI tests plus isolated backend tests and controlled physical
   two-device acceptance before any working-brand opening balances or real entries.

No deployment or stock reset is authorized by this document. Development migration
preference remains in force; implementation must explicitly address installed retained
sales/cache before any incompatible Room change. Never silently clear a working app
or production database to introduce stock tables. Each milestone records schema,
command/feed version compatibility, test outcomes and rollout/recovery limits.

## Automatic product deduction request — 21 November 2026

The user requested automatic deduction against each outlet's product stock. This is
now the next requested STOCK-02 scope, rather than an optional inventory proposal.
The existing STOCK-01 manual count implementation remains unchanged until the two
checkout/rejected-sale policies below are answered. No ingredient recipes, modifier
stock, transfer or costing scope is implied.

Confirmed integration points from source review:

- AuthenticatedCheckoutRepository.complete commits payment/sale, outbox and consumed
  draft in one Room transaction. Persist immutable product consumption intent there,
  grouped by product ID and sold quantity, alongside the existing sale operation UUID.
  Cart edits and failed/cancelled payment must not consume stock.
- RoomSyncRepository retains original sale payload/operation on retry and records
  REVIEW separately from SYNCED. Stock delivery/review state must remain distinct:
  a financially rejected paid sale is not proof that goods were returned.
- submit_sale already atomically admits accepted sales, operation dedupe and the
  SALE change feed. Extend its accepted/review paths only after the stock policy is
  selected; a separate fire-and-forget deduction after upload is insufficient.
- Keep outlet stock confirmed balance separate from local unconfirmed consumption.
  Add narrowly scoped Cashier stock read access and durable outlet-shared delivery;
  current management-only snapshot RPCs cannot support offline cashier availability.
- Count corrections made while other devices have offline sales require a separate
  count generation/reconciliation rule. Expected balance revision alone cannot tell
  whether a physical count already included those goods; do not silently double-deduct
  late uploads against a newer count baseline.
- Define explicit cutover/old-client handling. Do not retroactively apply the new
  feature to historical receipts or silently infer consumption for existing payloads.

Pending business rules (questions sent to the user):

1. Insufficient recorded stock: warn/allow with visible negative balances, or block
   checkout against the device's available estimate. Strict cross-device availability
   cannot be guaranteed offline. The existing nonnegative stock constraints must not
   be relaxed without the selected policy.
2. Changed-price rejected paid sales: consume stock while financial records await
   review, or keep consumption unresolved until Owner/Manager reconciliation. Preserve
   the user's existing strict financial rejection policy in either case.

Next implementation begins after these answers, including protocol compatibility,
local journal/cache/feed, manual count baseline handling, immutable sale-linked stock
movements and offline/idempotency/rejection regressions. No runtime/SQL deployment,
new deduction, data reset or Gradle check was performed by this review.

## STOCK-02 policies selected and implemented —21 November2026

The user selected warn/allow insufficient stock (negative balances visible) and
continued stock consumption for paid changed-price rejected receipts, with financial
review retained separately. These answers supersede the pending-business-rules block
above. Implementation and rollout details are now in OUTLET_STOCK.md. Count-generation
changes/untracked/legacy receipts require explicit reconciliation to avoid double
consumption. Automatic ingredient recipes, transfers and costing remain unimplemented.
