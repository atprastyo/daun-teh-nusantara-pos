# Fresh production Supabase setup

17 November 2026. Operator-run instructions only; no hosted deployment was performed.
This path copies reviewed schema/functions plus selected business configuration into
an empty production project. It does not clone development users, passwords, devices,
leases, sales, payments, sync cursors or private operation ledgers.

The SQL source files represent this repository's current implementation. Reconcile
any dashboard-only development schema/function edits with source before proceeding.
A full project/data migration is a separate operation, not this fresh setup.

## 1. Create and identify production

Create a separate Supabase project. Keep its database password and server credentials
private. Record its project reference (the identifier in its project URL), and label
Dashboard tabs clearly DEV vs PROD. Do not change development credentials or link a
CLI command implicitly to a previously linked development project.

In PROD Auth settings, disable public signup and anonymous sign-in; retain appropriate
password requirements/rate limits. Create accounts through trusted admin controls or
the protected staff flow. Do not expose the private schema through the Data API.
Local supabase/config.toml Auth settings do not configure hosted Auth automatically.

## 2. PROD SQL Editor — install the fresh backend

First run this read-only check on PROD:

```sql
select to_regnamespace('private') as existing_private_schema,
       to_regclass('public.brands') as existing_app_table;
```

Both should be null for this initial installation. If existing app objects are
present, stop and inspect; these scripts are not rerunnable migrations. Do not drop
or reset an existing project to make them run.

Run the complete contents of these repository files, ONE AT A TIME, in this order:

1. [schema.sql](../supabase/schema.sql)
2. [staff-provisioning.sql](../supabase/staff-provisioning.sql)
3. [staff-limits.sql](../supabase/staff-limits.sql)
4. [device-labels.sql](../supabase/device-labels.sql)
5. [product-photos.sql](../supabase/product-photos.sql)

Each file supplies its own transaction. Confirm success before continuing. The base
schema installs the SQL functions for authentication, approved devices, sale push,
pull, reports, catalog and staff access. Later files install staff provisioning,
limits, display names and private photo Storage policies. No stock tables are added.
Supabase Storage must be available for the photo setup.

After all files, run on PROD:

```sql
notify pgrst, 'reload schema';

select
 to_regprocedure('public.get_access_context()') is not null as access_ready,
 to_regprocedure('public.submit_sale(text,text)') is not null as sale_ready,
 to_regprocedure('public.pull_changes(uuid,text,bigint,text,integer,uuid)') is not null as pull_ready,
 to_regprocedure('public.get_sales_report(uuid,text,date,date,uuid)') is not null as reports_ready,
 to_regclass('public.outlet_staff_limits') is not null as quotas_ready,
 to_regclass('public.device_labels') is not null as device_names_ready,
 to_regclass('public.product_photos') is not null as photos_ready;
```

All readiness booleans should be true. This checks installation, not hosted permission
or end-to-end acceptance. No `supabase db push` is used here because these initial
SQL files are not a migrations directory.

## 3. DEV SQL Editor — export selected brand/outlets/catalog

Identify the intended brand on DEV:

```sql
select id, name, active, offline_access_enabled from public.brands order by name;
```

Open [export-prod-catalog.sql.example](../supabase/export-prod-catalog.sql.example),
replace `<brand-uuid>` with that verified DEV brand UUID, then run the query on DEV.
This is read-only. It returns one text cell named `prod_catalog_import_sql` containing
an already quoted, transactional import script.

Copy the complete raw text cell, not a JSON-escaped representation or truncated
preview. Review its business data before importing. For a larger catalog, download
the result as CSV and extract the single cell locally, for example:

```python
import csv
from pathlib import Path
with open('catalog-export.csv', newline='', encoding='utf-8-sig') as f:
    sql = next(csv.DictReader(f))['prod_catalog_import_sql']
Path('prod-catalog-import.sql').write_text(sql, encoding='utf-8')
```

Keep real export files outside Git (for example under ignored supabase/.temp/).
Do not hand-edit SQL quoting or paste catalog JSON into a dollar-quoted DO block.
The exporter safely quotes both JSON and the complete DO body, including names with
apostrophes/backslashes/dollar delimiters.

It includes brand/outlets, categories, products, modifier groups/options and product
links, preserving their UUIDs, versions, active/archive flags and catalog timestamps.
Archived parents are included for referential integrity. This is a point-in-time
configuration snapshot; later development edits are not replicated into production. It deliberately omits users,
financial records, devices, image metadata/blobs and synchronization state.

## 4. PROD SQL Editor — import the reviewed catalog script

Run the complete `prod_catalog_import_sql` text from step3 on PROD. It refuses
nonempty business tables; there is no overwrite/upsert/reset behavior. Do not run
bootstrap-owner.sql.example afterward, since that would try to create duplicate
brand/outlet records; link the new Auth owner with step6 instead.

Keep catalog triggers enabled. They create a fresh production catalog change feed;
do not copy development sync_changes or private.sync_generation. Run this count
query on DEV and PROD and compare results for the selected brand:

```sql
select 'outlets' as entity, count(*) from public.outlets where brand_id='<brand-uuid>'::uuid
union all select 'categories',count(*) from public.categories where brand_id='<brand-uuid>'::uuid
union all select 'products',count(*) from public.products where brand_id='<brand-uuid>'::uuid
union all select 'modifier_groups',count(*) from public.modifier_groups where brand_id='<brand-uuid>'::uuid
union all select 'modifier_options',count(*) from public.modifier_options where brand_id='<brand-uuid>'::uuid
union all select 'product_modifier_groups',count(*) from public.product_modifier_groups where brand_id='<brand-uuid>'::uuid;
```

## 5. Configure production staff limits

To reuse reviewed development capacity values, run
[export-prod-staff-limits.sql.example](../supabase/export-prod-staff-limits.sql.example)
on DEV with the same brand UUID. Review each returned `prod_limit_sql` row and run
those INSERT statements on PROD, together inside `begin; ... commit;`.

Only per-outlet Manager/Cashier maxima are copied; users, usage counts and pending
reservations are not. An outlet with no development limit row produces no output.
For such an outlet, configure actual limits using a private copy of
[configure-staff-limits.sql.example](../supabase/configure-staff-limits.sql.example).
The example counts are not production defaults; choose operational values.

Verify on PROD (replace the brand UUID):

```sql
select o.id,o.name,l.manager_limit,l.cashier_limit
from public.outlets o left join public.outlet_staff_limits l on l.outlet_id=o.id
where o.brand_id='<brand-uuid>'::uuid order by o.name;
```

Null limits block new staff creation; zero blocks creation for that role. The Owner
membership is not a Manager/Cashier quota slot.

## 6. Create the production Owner Auth account, then assign membership

In PROD Authentication → Users, create the real Owner using email/password admin
controls and confirm that account. Copy its NEW production Auth UUID. Do not copy
auth.users, encrypted passwords or the development Owner UUID into production.
Creating the same email in a different project is a separate account.

Run this configured query on PROD (replace BOTH UUID placeholders):

```sql
begin;
do $$
declare
 v_owner uuid := '<prod-owner-auth-uuid>'::uuid;
 v_brand uuid := '<imported-brand-uuid>'::uuid;
begin
 if not exists(select 1 from auth.users where id=v_owner and email_confirmed_at is not null) then
  raise exception 'Create and confirm the production Auth account first';
 end if;
 if not exists(select 1 from public.brands where id=v_brand and active) then
  raise exception 'Expected imported active brand';
 end if;
 insert into public.user_profiles(id,full_name,active)
 values(v_owner,'Pemilik Daun Teh Nusantara',true);
 insert into public.brand_members(user_id,brand_id,role,active)
 values(v_owner,v_brand,'OWNER',true);
end;
$$;
commit;
```

This is fresh bootstrap, not an existing-user role reset. Duplicate records fail;
inspect rather than adding an unreviewed upsert. An Owner gets active brand outlets
through brand_members; no outlet_members row is required. user_metadata.role does
not grant app access. Use the app to create real Managers/Cashiers after step8.

## 7. Deploy the staff-admin Edge Function to PROD

Only `staff-admin` is an Edge Function in this repository. The other application
functions are SQL RPCs, already installed by step2. Deploy the repository source so
index.ts and handler.mjs stay together; do not copy a possibly stale dashboard stub.

Install/use the [official Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started),
then from this repository root:

```sh
supabase --version
supabase login
supabase functions deploy staff-admin --project-ref YOUR_PROD_PROJECT_REF
```

Use the verified PROD reference explicitly. No persistent `supabase link` or database
reset is required for this command. Keep config.toml's verify_jwt=true; do not add
--no-verify-jwt. Android sends the signed-in user's access JWT as Authorization,
with the public project key in apikey. The handler also verifies the caller using
Auth /user and role/device-protected SQL RPCs.

The current index.ts reads runtime SUPABASE_URL, SUPABASE_ANON_KEY and
SUPABASE_SERVICE_ROLE_KEY. Hosted Supabase supplies those project-specific defaults.
Verify them in the production function environment if a configuration error occurs;
do not copy development defaults or put the service-role key in Android/local.properties.
The Android app may use a publishable key while this handler uses the hosted legacy
runtime keys. If legacy keys have been disabled in PROD, the Edge adapter needs a
separately tested update for new server-key headers; do not paste an sb_secret key
into the legacy variable or weaken JWT verification as a workaround. Custom secrets would use production values via Dashboard/CLI, but this
handler does not require an extra custom secret file.

Official references checked 17 November 2026:
- [Deploy functions](https://supabase.com/docs/guides/functions/deploy)
- [Runtime secrets/defaults](https://supabase.com/docs/guides/functions/secrets)
- [User JWT and API key headers](https://supabase.com/docs/guides/functions/auth-headers)

## 8. Configure/build the Android production flavor

Set only the production public client URL/key locally:

```properties
PROD_SUPABASE_URL=https://YOUR_PROD_PROJECT_REF.supabase.co
PROD_SUPABASE_PUBLIC_KEY=YOUR_PROD_PUBLISHABLE_OR_ANON_KEY
```

Select prodDebug or prodRelease in Android Studio, or build:

```sh
./gradlew :app:assembleProdDebug
# Release signing remains separate:
./gradlew :app:bundleProdRelease
```

Previous flavor-verification APKs used synthetic credentials; rebuild with actual
values before use. See [BUILD_ENVIRONMENTS.md](BUILD_ENVIRONMENTS.md) before replacing
the old unflavored installation: preserve/export unsynced work first, or use a clean
production device/profile. Do not copy Room databases or device enrollment between
projects. Real production users/devices must enroll again; Owner enrollment can
self-approve, Manager/Cashier approval remains role-scoped.

Sign in with the production Owner. Complete initial catalog pull and check actual
outlet/catalog/prices/modifiers before normal operations. Create real staff through
Staf, complete their initial-password flow and approve their new production devices.
Do not create fabricated working-brand transactions merely to check connectivity.

## 9. Photos and final checks

The catalog export does not copy Storage images or product_photos references. Reupload
photos through the production Owner/Manager app after product import; its publication
flow validates that the production Storage object exists. Never copy storage.objects
rows or make the bucket public as a transfer shortcut. A separate image-copy operation
would need object transfer plus authorized publication, not just SQL metadata.

On PROD before normal sales (these queries are read-only):

```sql
select b.name,p.full_name,m.role,m.active
from public.brand_members m join public.user_profiles p on p.id=m.user_id
join public.brands b on b.id=m.brand_id;

select count(*) as existing_sales from public.sales;
select count(*) as existing_sync_operations from public.sync_operations;
select id,name,public,file_size_limit from storage.buckets where id='product-photos';

select n.nspname,c.relname
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relkind='r' and not c.relrowsecurity;
```

Initially financial counts should be zero; the bucket should be private with the
configured 204800-byte limit, and the last query should return no public app tables.
Owner profile/membership should be active. Zero RLS omissions is not a complete policy
security test; finish the existing hosted role/device/first-password/outlet checks
and production backup/signing readiness before launch. Never drop/recreate schemas
or replay bootstrap exports over a project that has begun operations.
