# Codex Instructions: Daun Teh Nusantara POS

## Mission
Build a production-minded, offline-first native Android POS for **Daun Teh Nusantara**, delivered by **Asterfed**. Current scope is one brand, multiple outlets, Android phones and tablets. Do not build a generic SaaS onboarding/billing platform.

## Mandatory reading order
1. `docs/PRODUCT_REQUIREMENTS.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DATA_MODEL.md`
4. `docs/AUTH_SECURITY.md`
5. `docs/SYNC_PROTOCOL.md`
6. `docs/UI_UX.md`
7. `docs/IMPLEMENTATION_PLAN.md`

## Agent workflow
- Inspect repository and toolchain first. If no Android project exists, scaffold one.
- Work on ONE milestone per request. Provide a brief plan, implement, run feasible checks, report results and limitations.
- Do not invent API endpoints, library versions, or credentials. Check installed tools and current official docs when available. Record version choices.
- Never silently broaden scope. Prefer simple, testable solutions over abstractions.
- Keep code compilable after each milestone; add tests for behavior and edge cases.
- Never claim a test passed unless executed. If SDK/emulator/network is unavailable, state what was not verified.
- No real credentials in repository, logs, screenshots, or tests. Provide `.env.example`/`local.properties.example` only.
- Do not apply destructive DB migrations or deploy to production without explicit approval.
- For each milestone update `docs/DECISIONS.md` and `docs/PROGRESS.md`.

## Technical conventions
- Kotlin, Jetpack Compose, Material 3 adaptive UI, ViewModel + StateFlow, Coroutines/Flow, Room, Hilt, Navigation Compose, DataStore, WorkManager.
- Single Gradle app module with feature-first packages for MVP; `core/` for cross-cutting concerns.
- Repository interfaces belong to domain; implementations in data. UI does not directly access DAO or Supabase.
- Local Room is the operational source of truth for cashier UX; server is authoritative for shared synced data and authorization.
- Currency: `Long` integer IDR. Persist immutable transaction price/name/modifier snapshots.
- IDs: client-generated UUID; store all timestamps in UTC; format in Asia/Jakarta for display.
- All sale writes and outbox writes must share one Room transaction.
- Remote submission must be idempotent with stable operation UUID and database unique constraints.
- Never use last-write-wins for sales or inventory. Sales are append-only with compensating void/refund records.
- Enforce authorization in Supabase RLS and server functions; hiding UI is not security.
- Offline authorization is bounded and device-scoped; do not imply instant revocation while offline.
- Treat Realtime as a refresh hint, not durable sync delivery.

## Definition of done
- Feature works on compact phone and expanded tablet layouts; supports offline use where applicable.
- Success, loading, error, retry, empty, and conflict states covered.
- Unit tests for domain calculations; DAO and sync tests for persistence; relevant Compose UI tests.
- No duplicate sales after retry; no data loss on process death; correct role/outlet scoping.
- README instructions and progress updated; build/test commands and outcomes reported.

## Deliverables per iteration
Summarize changed files, decisions, commands executed, tests passed/failed, risks, and exact next milestone.
