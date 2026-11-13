# Product requirements (MVP)

## Objective
An Android POS for Daun Teh Nusantara, built by Asterfed, for one brand with potentially multiple outlets. Cashiers must sell drinks even with unreliable connectivity; cloud stores synced records for cross-device reporting and recovery.

## Users and permissions
- Owner: all outlets, products, prices, reports, employee accounts, store configuration, void approvals.
- Manager: assigned outlets, products/prices, outlet reports, cashier account management, void approvals.
- Cashier: assigned outlet sales, own transaction history, own profile; no price editing, employee management, or global reports.
- Server permission enforcement is mandatory. Explicit brand membership for owner, outlet membership for manager/cashier.

## MVP features
1. Email/password sign-in and invite-only account activation; role/outlet selection when needed.
2. Manage drink catalog, categories, size/sugar/ice/topping modifier groups and option prices.
3. Cart with quantity, line modifiers, total, cash tender/change, manually verified QRIS payment.
4. Offline sale persistence and outbox, durable push/pull sync, sync status UI.
5. Sales history, own history for cashier, outlet and brand reporting based on role.
6. Manage staff membership and disable access; audit sensitive actions.
7. Local export/backup guidance and cloud backup/restore runbook.

## Non-goals for MVP
- Public business signup, SaaS subscriptions, multi-brand account switching, payment gateway QRIS verification, inventory ingredient depletion, automated tax compliance, online ordering, loyalty, complex refunds, thermal printer integration (later milestone).

## Acceptance criteria
- Offline cashier can record cash sale, restart app, and see pending sale intact.
- After reconnect, sale syncs once; retries and duplicate requests do not duplicate sale.
- Two devices in same outlet converge on sales and product updates using pull cursor.
- Cashier cannot change prices or read another outlet via direct API.
- Owner sees brand-wide reports; manager only assigned outlet reports.
- Deactivated online users are denied by server immediately; offline policy is time-bounded.
- UI usable in phone portrait and tablet landscape/split screen.

## Open business decisions
- Initial outlet list and manager assignment.
- Whether cashiers can access multiple outlets and how outlet switching works.
- Refund/void policy and audit retention.
- Offline duration selected: up to one calendar month. Owner/manager device approval is implemented; confirm operational rollout/enrollment procedures before production.
- QRIS confirmation process and backup retention targets.
