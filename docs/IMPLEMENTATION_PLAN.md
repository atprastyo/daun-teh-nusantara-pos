# Implementation milestones

Do not implement all milestones in one Codex turn. Complete each with build/tests and a progress report.

## M0 Toolchain & skeleton
Verify Android SDK/Gradle/JDK, choose compatible pinned versions, scaffold Kotlin Compose app, adaptive theme, Hilt, Room, navigation, CI-ready build. Acceptance: debug build succeeds or blockers documented.

## M1 Local schema & catalog
Room schema, products/categories/modifiers, seed demo menu, catalog repository, unit/DAO tests. Acceptance: CRUD and modifiers work offline.

Development policy: maintain only the current initial Room schema during this development phase; no migration paths or migration tests are required yet. Reset development app data explicitly after incompatible schema changes. Introduce reviewed data-preserving migrations before releases that require retained data.

## M2 POS & checkout offline
Phone/tablet adaptive POS, cart, modifier selection, cash/manual QRIS, local atomic sale+outbox, receipts, tests. Acceptance: offline sale survives restart.

## M3 Supabase foundation & security
SQL migrations for brands/outlets/members/catalog/sales/sync/audit, RLS policies and tests, invite/login, role-specific navigation. Acceptance: unauthorized cross-outlet access denied server-side.

## M4 Push sync
Secure transactional `submit_sale`, operation dedupe, WorkManager retry/status. Acceptance: retry/ack-loss tests prove no duplicates.

## M5 Pull sync & multi-device
Monotonic change feed, scoped cursors, catalog and sales updates, Realtime hints optional. Acceptance: two devices converge after offline period.

## M6 Reports, staff, recovery
Role-scoped reports, staff management, backup/restore runbook, export, device revocation, audit trail. Acceptance: correct outlet scoping and verified restore rehearsal.

## M7 Release hardening
Security review, end-to-end offline/online scenarios, UI accessibility, performance, migration and rollback plan, signed release checklist.

## Every milestone report
Files changed; commands/tests actually run; unresolved blockers; assumptions/decisions; suggested next step.
