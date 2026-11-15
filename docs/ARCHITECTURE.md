# Architecture

## Android stack
Kotlin + Compose Material 3 + adaptive layouts; MVVM, StateFlow; Hilt; Room; WorkManager; DataStore; Kotlin coroutines. Use a Supabase Kotlin-compatible client version verified against project Kotlin/Gradle/Android versions; direct HTTPS to vetted Edge Functions/RPC is also acceptable.

## Layers
`feature/{auth,pos,checkout,catalog,history,reports,staff,settings,sync}/{presentation,domain,data}` and `core/{database,network,di,designsystem,security,common}`.
UI -> ViewModel -> use case -> repository -> Room/remote datasource. No direct network calls from composables.

## Local-first flow
Checkout -> validate cart and cash/change -> Room transaction (sale, lines, payment, outbox) -> immediate receipt view -> WorkManager push -> Supabase transactional endpoint -> ack -> mark synced -> pull changes -> apply Room transaction -> update UI via Flow.

## Cloud components
Supabase Auth for identity; PostgreSQL for tenant/outlet data and sales; RLS for table access; SQL RPCs for validated atomic sale submission, device authorization and sync change feed; protected staff-admin Edge Function provisions new Manager/Cashier Auth accounts and scoped SQL assignments; trusted administrator tools bootstrap Owners and handle recovery; Realtime optional as refresh hint.

## Operational rules
- Network unavailable is normal; handle process death, retries, stale sessions and conflicts.
- Keep separate statuses for local save, cloud pending, synced, rejected, and attention required.
- Do not treat a push as successful until the server acknowledges the operation.
- Cloud sync is not backup. Set up independent point-in-time or scheduled backups and test restores.
- Use migrations with explicit schema versioning; no destructive fallback on production databases.
