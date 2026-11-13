-- Initial development schema, not a migration. Apply only to a fresh development project.
-- Supabase provides auth.users, auth.uid(), anon, authenticated and service_role.
begin;
revoke create on schema public from public,anon,authenticated;
alter default privileges in schema public revoke all on tables from anon,authenticated;
alter default privileges in schema public revoke execute on functions from public,anon,authenticated;
create schema private;
alter default privileges in schema private revoke execute on functions from public,anon,authenticated;
revoke all on schema private from public;
create table private.sync_generation(singleton boolean primary key default true check(singleton),epoch uuid not null default gen_random_uuid());
insert into private.sync_generation(singleton) values(true);
revoke all on private.sync_generation from public,anon,authenticated;
grant usage on schema private to authenticated;

create table public.brands (
 id uuid primary key default gen_random_uuid(), name text not null check(length(btrim(name)) between 1 and 120),
 active boolean not null default true, offline_access_enabled boolean not null default true
);
create table public.outlets (
 id uuid primary key default gen_random_uuid(), brand_id uuid not null references public.brands(id),
 name text not null check(length(btrim(name)) between 1 and 120), active boolean not null default true,
 unique(id,brand_id)
);
create table public.user_profiles (
 id uuid primary key references auth.users(id), full_name text not null default '' check(length(full_name)<=120), active boolean not null default true
);
create table public.brand_members (
 user_id uuid not null references public.user_profiles(id), brand_id uuid not null references public.brands(id),
 role text not null default 'OWNER' check(role='OWNER'), active boolean not null default true, primary key(user_id,brand_id)
);
create table public.outlet_members (
 user_id uuid not null references public.user_profiles(id), outlet_id uuid not null references public.outlets(id),
 role text not null check(role in ('MANAGER','CASHIER')), active boolean not null default true, version bigint not null default 1 check(version>0), primary key(user_id,outlet_id)
);
create index outlet_members_outlet on public.outlet_members(outlet_id);
create table public.devices (
 id uuid primary key, outlet_id uuid not null references public.outlets(id), enrolled_by uuid not null references public.user_profiles(id),
 secret_hash text not null check(secret_hash ~ '^[0-9a-f]{64}$'), enrolled_at timestamptz not null default now(),
 last_seen_at timestamptz not null default now(), revoked_at timestamptz, approved_at timestamptz,
 version bigint not null default 1 check(version>0), approved_by uuid references public.user_profiles(id), approved_role text check(approved_role in ('OWNER','MANAGER','CASHIER')), unique(id,outlet_id)
);
create table public.audit_logs (
 id uuid primary key default gen_random_uuid(), actor_user_id uuid not null references auth.users(id),
 brand_id uuid not null references public.brands(id), outlet_id uuid, action text not null,
 target_id uuid, metadata jsonb not null default '{}', created_at timestamptz not null default now(),
 foreign key(outlet_id,brand_id) references public.outlets(id,brand_id)
);
create index audit_actor_time on public.audit_logs(actor_user_id,created_at);

create function private.active_user(p_user uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.user_profiles where id=p_user and active);
$$;
create function private.owner(p_brand uuid,p_user uuid default auth.uid()) returns boolean language sql stable security definer set search_path='' as $$
 select private.active_user(p_user) and exists(select 1 from public.brand_members m join public.brands b on b.id=m.brand_id
 where m.user_id=p_user and m.brand_id=p_brand and m.active and b.active);
$$;
create function private.outlet_role(p_outlet uuid,p_user uuid default auth.uid()) returns text language sql stable security definer set search_path='' as $$
 select case when private.owner(o.brand_id,p_user) then 'OWNER' else
 (select m.role from public.outlet_members m where m.outlet_id=o.id and m.user_id=p_user and m.active) end
 from public.outlets o join public.brands b on b.id=o.brand_id where o.id=p_outlet and o.active and b.active and private.active_user(p_user);
$$;
create function private.read_brand(p_brand uuid) returns boolean language sql stable security definer set search_path='' as $$
 select private.owner(p_brand) or exists(select 1 from public.outlets o where o.brand_id=p_brand and private.outlet_role(o.id) is not null);
$$;
create function private.read_sale(p_brand uuid,p_outlet uuid,p_cashier uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.outlets o where o.id=p_outlet and o.brand_id=p_brand and
 (private.outlet_role(o.id) in ('OWNER','MANAGER') or (private.outlet_role(o.id)='CASHIER' and p_cashier=auth.uid())));
$$;
create table public.categories (id uuid primary key default gen_random_uuid(), brand_id uuid not null references public.brands(id),
 name text not null check(length(btrim(name)) between 1 and 120), active boolean not null default true,
 version bigint not null default 1 check(version>0), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), sort_order integer not null default 0 check(sort_order>=0), unique(id,brand_id));
create index categories_brand on public.categories(brand_id);

create table public.products (id uuid primary key default gen_random_uuid(), brand_id uuid not null references public.brands(id),
 name text not null check(length(btrim(name)) between 1 and 120), active boolean not null default true,
 version bigint not null default 1 check(version>0), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), category_id uuid not null, base_price_idr bigint not null check(base_price_idr>=0), unique(id,brand_id), foreign key(category_id,brand_id) references public.categories(id,brand_id));
create index products_brand on public.products(brand_id);

create table public.modifier_groups (id uuid primary key default gen_random_uuid(), brand_id uuid not null references public.brands(id),
 name text not null check(length(btrim(name)) between 1 and 120), active boolean not null default true,
 version bigint not null default 1 check(version>0), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), min_select integer not null check(min_select>=0), max_select integer not null check(max_select>0 and max_select>=min_select), unique(id,brand_id));
create index modifier_groups_brand on public.modifier_groups(brand_id);

create table public.modifier_options (id uuid primary key default gen_random_uuid(), brand_id uuid not null references public.brands(id),
 name text not null check(length(btrim(name)) between 1 and 120), active boolean not null default true,
 version bigint not null default 1 check(version>0), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), group_id uuid not null, price_delta_idr bigint not null check(price_delta_idr>=0), foreign key(group_id,brand_id) references public.modifier_groups(id,brand_id));
create index modifier_options_brand on public.modifier_options(brand_id);

create table public.product_modifier_groups (product_id uuid not null, group_id uuid not null, brand_id uuid not null,
 sort_order integer not null default 0 check(sort_order>=0), active boolean not null default true,
 version bigint not null default 1 check(version>0), updated_at timestamptz not null default now(),
 primary key(product_id,group_id), foreign key(product_id,brand_id) references public.products(id,brand_id),
 foreign key(group_id,brand_id) references public.modifier_groups(id,brand_id));
create index product_modifier_groups_brand on public.product_modifier_groups(brand_id);

create table public.sales (
 id uuid primary key, brand_id uuid not null, outlet_id uuid not null, device_id uuid not null,
 cashier_user_id uuid not null references public.user_profiles(id), sale_number text,
 subtotal_idr bigint not null check(subtotal_idr>=0), discount_idr bigint not null default 0 check(discount_idr>=0 and discount_idr<=subtotal_idr),
 total_idr bigint not null check(total_idr=subtotal_idr-discount_idr), status text not null check(status='COMPLETED'),
 created_at timestamptz not null, server_received_at timestamptz not null default now(), unique(outlet_id,sale_number),
 foreign key(outlet_id,brand_id) references public.outlets(id,brand_id), foreign key(device_id,outlet_id) references public.devices(id,outlet_id)
);
create index sales_scope_time on public.sales(outlet_id,cashier_user_id,created_at);
create table public.sale_items (
 id uuid primary key, sale_id uuid not null references public.sales(id), product_id uuid not null,
 product_name_snapshot text not null, unit_base_price_idr bigint not null check(unit_base_price_idr>=0),
 quantity integer not null check(quantity>0), line_total_idr bigint not null check(line_total_idr>=0)
);
create index sale_items_sale on public.sale_items(sale_id);
create table public.sale_item_modifiers (
 id uuid primary key, sale_item_id uuid not null references public.sale_items(id), option_id uuid not null,
 group_name_snapshot text not null, option_name_snapshot text not null, price_delta_idr bigint not null check(price_delta_idr>=0)
);
create index sale_modifiers_item on public.sale_item_modifiers(sale_item_id);
create table public.payments (
 id uuid primary key, sale_id uuid not null unique references public.sales(id), method text not null check(method in ('CASH','QRIS_MANUAL')),
 amount_idr bigint not null check(amount_idr>=0), tendered_idr bigint, change_idr bigint, verification_status text not null,
 check((method='CASH' and tendered_idr is not null and change_idr is not null and tendered_idr>=amount_idr and change_idr=tendered_idr-amount_idr and verification_status='CASH_RECEIVED')
 or (method='QRIS_MANUAL' and tendered_idr is null and change_idr is null and verification_status='CASHIER_CONFIRMED_MANUALLY'))
);
create table public.sync_operations (
 id uuid primary key, device_id uuid not null, outlet_id uuid not null, request_hash text not null check(request_hash ~ '^[0-9a-f]{64}$'),
 result_sale_id uuid not null references public.sales(id), processed_at timestamptz not null default now(),
 foreign key(device_id,outlet_id) references public.devices(id,outlet_id)
);
create table public.sync_changes (
 seq bigint generated always as identity primary key, brand_id uuid not null references public.brands(id), outlet_id uuid,
 entity_type text not null check(entity_type in ('CATALOG','SALE')), entity_id uuid not null, action text not null check(action in ('UPSERT','DELETE')),
 cashier_user_id uuid references public.user_profiles(id), payload jsonb not null, changed_at timestamptz not null default now(),
 check(entity_type!='SALE' or (outlet_id is not null and cashier_user_id is not null)),
 foreign key(outlet_id,brand_id) references public.outlets(id,brand_id)
);

create function private.immutable_sale() returns trigger language plpgsql set search_path='' as $$
 begin raise exception 'Completed sale records are append-only' using errcode='42501'; end;
$$;
create trigger immutable_sales before update or delete on public.sales for each row execute function private.immutable_sale();
create trigger immutable_sale_items before update or delete on public.sale_items for each row execute function private.immutable_sale();
create trigger immutable_sale_item_modifiers before update or delete on public.sale_item_modifiers for each row execute function private.immutable_sale();
create trigger immutable_payments before update or delete on public.payments for each row execute function private.immutable_sale();

-- Identity and authorization reads use live membership/profile state, never user_metadata.
create function public.get_access_context() returns table(brand_id uuid,brand_name text,outlet_id uuid,outlet_name text,role text)
 language sql stable security definer set search_path='' as $$
 select b.id,b.name,o.id,o.name,private.outlet_role(o.id) from public.outlets o join public.brands b on b.id=o.brand_id
 where private.outlet_role(o.id) is not null order by b.name,o.name,o.id;
$$;
create function public.enroll_device(p_device_id uuid,p_outlet_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
 declare v_user uuid:=auth.uid(); v_secret text; v_device public.devices; v_brand uuid;
 begin
 if private.outlet_role(p_outlet_id) is null then raise exception 'Forbidden' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_device_id::text,2));
 select * into v_device from public.devices where id=p_device_id for update;
 if found and (v_device.enrolled_by!=v_user or v_device.outlet_id!=p_outlet_id or v_device.revoked_at is not null) then raise exception 'Device denied' using errcode='42501'; end if;
 v_secret:=replace(gen_random_uuid()::text,'-','')||replace(gen_random_uuid()::text,'-','');
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_by,approved_role)
 values(p_device_id,p_outlet_id,v_user,encode(sha256(convert_to(v_secret,'UTF8')),'hex'),
 case when private.outlet_role(p_outlet_id)='OWNER' then now() end,
 case when private.outlet_role(p_outlet_id)='OWNER' then v_user end,
 case when private.outlet_role(p_outlet_id)='OWNER' then 'OWNER' end)
 on conflict(id) do update set secret_hash=excluded.secret_hash,last_seen_at=now(),
 approved_at=excluded.approved_at,approved_by=excluded.approved_by,approved_role=excluded.approved_role;
 select brand_id into v_brand from public.outlets where id=p_outlet_id;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id) values(v_user,v_brand,p_outlet_id,'DEVICE_ENROLL',p_device_id);
 return jsonb_build_object('device_id',p_device_id,'device_secret',v_secret);
 end;
$$;
create function public.issue_offline_lease(p_device_id uuid,p_device_secret text) returns jsonb language plpgsql security definer set search_path='' set timezone='UTC' as $$
 declare v_device public.devices; v_role text; v_brand public.brands; v_now timestamptz:=clock_timestamp(); v_expiry timestamptz;
 begin
 select * into v_device from public.devices where id=p_device_id and enrolled_by=auth.uid() and revoked_at is null for update;
 if not found or v_device.secret_hash!=encode(sha256(convert_to(coalesce(p_device_secret,''),'UTF8')),'hex') then raise exception 'Device denied' using errcode='42501'; end if;
 v_role:=private.outlet_role(v_device.outlet_id);
 if v_role is null then raise exception 'Forbidden' using errcode='42501'; end if;
 if v_device.approved_at is null or v_device.approved_role is distinct from v_role then raise sqlstate 'PT423' using message='Device approval required'; end if;
 select b.* into v_brand from public.brands b join public.outlets o on o.brand_id=b.id where o.id=v_device.outlet_id;
 v_expiry:=case when v_brand.offline_access_enabled then v_now+interval '1 month' else v_now end;
 update public.devices set last_seen_at=v_now where id=v_device.id;
 return jsonb_build_object('user_id',auth.uid(),'brand_id',v_brand.id,'outlet_id',v_device.outlet_id,'device_id',v_device.id,'role',v_role,
 'issued_at_ms',floor(extract(epoch from v_now)*1000)::bigint,'expires_at_ms',floor(extract(epoch from v_expiry)*1000)::bigint);
 end;
$$;

create function public.list_device_requests(p_outlet_id uuid) returns table(device_id uuid,user_id uuid,full_name text,email text,enrolled_at timestamptz,role text)
 language plpgsql security definer set search_path='' as $$
 begin
 if private.outlet_role(p_outlet_id) not in ('OWNER','MANAGER') or private.outlet_role(p_outlet_id) is null then raise exception 'Forbidden' using errcode='42501'; end if;
 return query select d.id,d.enrolled_by,p.full_name,u.email::text,d.enrolled_at,private.outlet_role(d.outlet_id,d.enrolled_by) from public.devices d
 join public.user_profiles p on p.id=d.enrolled_by join auth.users u on u.id=d.enrolled_by
 where d.outlet_id=p_outlet_id and (d.approved_at is null or d.approved_role is distinct from private.outlet_role(d.outlet_id,d.enrolled_by)) and d.revoked_at is null and
 (private.outlet_role(p_outlet_id)='OWNER' or private.outlet_role(p_outlet_id,d.enrolled_by)='CASHIER') and private.outlet_role(p_outlet_id,d.enrolled_by) is not null;
 end;
$$;
create function public.approve_device(p_device_id uuid) returns void language plpgsql security definer set search_path='' as $$
 declare v_device public.devices; v_actor text; v_target text; v_brand uuid;
 begin
 select * into v_device from public.devices where id=p_device_id and revoked_at is null for update;
 if not found then raise exception 'Device denied' using errcode='42501'; end if;
 v_actor:=private.outlet_role(v_device.outlet_id); v_target:=private.outlet_role(v_device.outlet_id,v_device.enrolled_by);
 if v_actor is null or v_target is null or not (v_actor='OWNER' or v_actor='MANAGER' and v_target='CASHIER') then raise exception 'Forbidden' using errcode='42501'; end if;
 update public.devices set approved_at=now(),approved_by=auth.uid(),approved_role=v_target where id=p_device_id;
 select brand_id into v_brand from public.outlets where id=v_device.outlet_id;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id) values(auth.uid(),v_brand,v_device.outlet_id,'DEVICE_APPROVE',p_device_id);
 end;
$$;

-- RLS on every exposed table. No client writes to catalog/sales/sync/admin state.
alter table public.brands enable row level security;
revoke all on public.brands from anon,authenticated;
alter table public.outlets enable row level security;
revoke all on public.outlets from anon,authenticated;
alter table public.user_profiles enable row level security;
revoke all on public.user_profiles from anon,authenticated;
alter table public.brand_members enable row level security;
revoke all on public.brand_members from anon,authenticated;
alter table public.outlet_members enable row level security;
revoke all on public.outlet_members from anon,authenticated;
alter table public.devices enable row level security;
revoke all on public.devices from anon,authenticated;
alter table public.audit_logs enable row level security;
revoke all on public.audit_logs from anon,authenticated;
alter table public.categories enable row level security;
revoke all on public.categories from anon,authenticated;
alter table public.products enable row level security;
revoke all on public.products from anon,authenticated;
alter table public.modifier_groups enable row level security;
revoke all on public.modifier_groups from anon,authenticated;
alter table public.modifier_options enable row level security;
revoke all on public.modifier_options from anon,authenticated;
alter table public.product_modifier_groups enable row level security;
revoke all on public.product_modifier_groups from anon,authenticated;
alter table public.sales enable row level security;
revoke all on public.sales from anon,authenticated;
alter table public.sale_items enable row level security;
revoke all on public.sale_items from anon,authenticated;
alter table public.sale_item_modifiers enable row level security;
revoke all on public.sale_item_modifiers from anon,authenticated;
alter table public.payments enable row level security;
revoke all on public.payments from anon,authenticated;
alter table public.sync_operations enable row level security;
revoke all on public.sync_operations from anon,authenticated;
alter table public.sync_changes enable row level security;
revoke all on public.sync_changes from anon,authenticated;
create policy read_scoped on public.brands for select to authenticated using (private.read_brand(id));
grant select on public.brands to authenticated;
create policy read_scoped on public.outlets for select to authenticated using (private.outlet_role(id) is not null);
grant select on public.outlets to authenticated;
create policy read_scoped on public.user_profiles for select to authenticated using (private.active_user(auth.uid()) and (id=auth.uid() or exists(select 1 from public.outlet_members m where m.user_id=id and private.outlet_role(m.outlet_id) in ('OWNER','MANAGER'))));
grant select on public.user_profiles to authenticated;
create policy read_scoped on public.brand_members for select to authenticated using (private.owner(brand_id) or (user_id=auth.uid() and private.read_brand(brand_id)));
grant select on public.brand_members to authenticated;
create policy read_scoped on public.outlet_members for select to authenticated using (private.outlet_role(outlet_id) in ('OWNER','MANAGER') or (user_id=auth.uid() and private.outlet_role(outlet_id) is not null));
grant select on public.outlet_members to authenticated;
create policy read_scoped on public.devices for select to authenticated using (private.outlet_role(outlet_id) is not null and (enrolled_by=auth.uid() or private.outlet_role(outlet_id) in ('OWNER','MANAGER')));
grant select(id,outlet_id,enrolled_by,enrolled_at,last_seen_at,revoked_at,approved_at,approved_by,approved_role) on public.devices to authenticated;
create policy read_scoped on public.audit_logs for select to authenticated using (private.owner(brand_id));
grant select on public.audit_logs to authenticated;
create policy read_scoped on public.sales for select to authenticated using (private.read_sale(brand_id,outlet_id,cashier_user_id));
grant select on public.sales to authenticated;
create policy read_scoped on public.sale_items for select to authenticated using (exists(select 1 from public.sales s where s.id=sale_id and private.read_sale(s.brand_id,s.outlet_id,s.cashier_user_id)));
grant select on public.sale_items to authenticated;
create policy read_scoped on public.sale_item_modifiers for select to authenticated using (exists(select 1 from public.sale_items i join public.sales s on s.id=i.sale_id where i.id=sale_item_id and private.read_sale(s.brand_id,s.outlet_id,s.cashier_user_id)));
grant select on public.sale_item_modifiers to authenticated;
create policy read_scoped on public.payments for select to authenticated using (exists(select 1 from public.sales s where s.id=sale_id and private.read_sale(s.brand_id,s.outlet_id,s.cashier_user_id)));
grant select on public.payments to authenticated;
create policy read_scoped on public.sync_operations for select to authenticated using (exists(select 1 from public.devices d where d.id=device_id and (private.outlet_role(d.outlet_id) in ('OWNER','MANAGER') or (private.outlet_role(d.outlet_id)='CASHIER' and d.enrolled_by=auth.uid()))));
grant select on public.sync_operations to authenticated;
create policy read_scoped on public.sync_changes for select to authenticated using ((entity_type='CATALOG' and private.read_brand(brand_id) and (outlet_id is null or private.outlet_role(outlet_id) is not null)) or (entity_type='SALE' and private.read_sale(brand_id,outlet_id,cashier_user_id)));
grant select on public.sync_changes to authenticated;
create policy read_scoped on public.categories for select to authenticated using (private.read_brand(brand_id));
grant select on public.categories to authenticated;
create policy read_scoped on public.products for select to authenticated using (private.read_brand(brand_id));
grant select on public.products to authenticated;
create policy read_scoped on public.modifier_groups for select to authenticated using (private.read_brand(brand_id));
grant select on public.modifier_groups to authenticated;
create policy read_scoped on public.modifier_options for select to authenticated using (private.read_brand(brand_id));
grant select on public.modifier_options to authenticated;
create policy read_scoped on public.product_modifier_groups for select to authenticated using (private.read_brand(brand_id));
grant select on public.product_modifier_groups to authenticated;

-- PUBLIC execute is unsafe for SECURITY DEFINER functions. Explicit allowlist only.
revoke all on all functions in schema private from public,anon,authenticated;
grant execute on function private.active_user(uuid),private.owner(uuid,uuid),private.outlet_role(uuid,uuid),private.read_brand(uuid),private.read_sale(uuid,uuid,uuid) to authenticated;
revoke all on function public.get_access_context(),public.enroll_device(uuid,uuid),public.issue_offline_lease(uuid,text),public.list_device_requests(uuid),public.approve_device(uuid) from public,anon,authenticated;
grant execute on function public.get_access_context(),public.enroll_device(uuid,uuid),public.issue_offline_lease(uuid,text),public.list_device_requests(uuid),public.approve_device(uuid) to authenticated;
grant usage on schema public to authenticated;
-- No authenticated table INSERT/UPDATE/DELETE grants. Catalog management and sale
-- submission will be authorized RPCs with optimistic/idempotent validation later.
-- Atomic push submission (initial development schema).
create function public.submit_sale(p_payload text,p_device_secret text) returns jsonb
language plpgsql security definer set search_path='' set timezone='UTC' as $$
declare
 v_payload jsonb:=p_payload::jsonb;
 v_user uuid:=auth.uid(); v_device public.devices; v_role text; v_brand uuid;
 v_op uuid; v_sale uuid; v_hash text; v_existing public.sync_operations;
 v_line jsonb; v_mod jsonb; v_group jsonb; v_payment jsonb;
 v_product public.products; v_category public.categories; v_option public.modifier_options; v_g public.modifier_groups;
 v_unit bigint; v_total bigint:=0; v_qty integer; v_count integer; v_when timestamptz;
 v_code text; v_ack jsonb;
begin
 if v_payload is null or octet_length(v_payload::text)>262144 then raise exception 'Invalid payload' using errcode='22023'; end if;
 -- Authenticate before dedupe: revoked users/devices cannot use acknowledgements as a bypass.
 select * into v_device from public.devices where id=(v_payload->>'device_id')::uuid for update;
 if not found or v_user is null or v_device.enrolled_by is distinct from v_user or v_device.revoked_at is not null or
 v_device.secret_hash is distinct from encode(sha256(convert_to(coalesce(p_device_secret,''),'UTF8')),'hex') then
 raise exception 'Device denied' using errcode='42501'; end if;
 v_role:=private.outlet_role(v_device.outlet_id);
 if v_role is null then raise exception 'Forbidden' using errcode='42501'; end if;
 if v_device.approved_at is null or v_device.approved_role is distinct from v_role then raise sqlstate 'PT423' using message='Device approval required'; end if;
 select brand_id into v_brand from public.outlets where id=v_device.outlet_id;
 if (v_payload->>'cashier_user_id')::uuid is distinct from v_user or
 (v_payload->>'outlet_id')::uuid is distinct from v_device.outlet_id or
 (v_payload->>'brand_id')::uuid is distinct from v_brand then raise exception 'Scope denied' using errcode='42501'; end if;
 v_op:=(v_payload->>'operation_id')::uuid; v_sale:=(v_payload->>'sale_id')::uuid;
 if v_op is null or v_sale is null then raise exception 'Missing identity' using errcode='22023'; end if;
 -- Canonical JSONB hash is computed here; never trust a client-supplied digest.
 v_hash:=encode(sha256(convert_to(v_payload::text,'UTF8')),'hex');
 perform pg_advisory_xact_lock(hashtextextended(v_op::text,1));
 select * into v_existing from public.sync_operations where id=v_op;
 v_ack:=jsonb_build_object('operation_id',v_op,'sale_id',v_sale,'request_hash',v_hash,'payload_hash',encode(sha256(convert_to(p_payload,'UTF8')),'hex'));
 if found then
 if v_existing.device_id=v_device.id and v_existing.outlet_id=v_device.outlet_id and v_existing.result_sale_id=v_sale and v_existing.request_hash=v_hash then
 return v_ack||jsonb_build_object('status','ACCEPTED'); end if;
 v_code:='OPERATION_CONFLICT';
 elsif exists(select 1 from public.sales where id=v_sale) then v_code:='SALE_CONFLICT';
 else
 begin
 if v_payload->'schema_version' is distinct from '1'::jsonb or v_payload->'demo' is distinct from 'false'::jsonb or
 jsonb_typeof(v_payload->'items') is distinct from 'array' or jsonb_array_length(v_payload->'items') not between 1 and 100 or
 v_payload->'discount_idr' is distinct from '0'::jsonb then raise sqlstate 'P1001'; end if;
 v_when:=to_timestamp((v_payload->>'created_at_utc_ms')::bigint/1000.0);
 -- Admission age is a queue/review limit, not evidence of offline authorization.
 if v_when is null or v_when>clock_timestamp()+interval '5 minutes' or v_when<clock_timestamp()-interval '1 month' or v_when<v_device.enrolled_at then raise sqlstate 'P1001'; end if;
 for v_line in select value from jsonb_array_elements(v_payload->'items') loop
 if jsonb_typeof(v_line->'modifiers') is distinct from 'array' or jsonb_typeof(v_line->'groups') is distinct from 'array' then raise sqlstate 'P1001'; end if;
 v_qty:=(v_line->>'quantity')::integer;
 if v_qty is null or v_qty not between 1 and 999 or v_line->'quantity' is distinct from to_jsonb(v_qty) then raise sqlstate 'P1001'; end if;
 select * into v_product from public.products where id=(v_line->>'product_id')::uuid and brand_id=v_brand for share;
 if not found or not v_product.active or v_product.version is distinct from (v_line->>'product_version')::bigint or
 v_product.name is distinct from v_line->>'product_name' or v_product.base_price_idr is distinct from (v_line->>'unit_base_price_idr')::bigint then raise sqlstate 'P1002'; end if;
 select * into v_category from public.categories where id=v_product.category_id and brand_id=v_brand for share;
 if not found or not v_category.active or v_category.version is distinct from (v_line->>'category_version')::bigint then raise sqlstate 'P1002'; end if;
 -- Lock assignments and check the exact current active group set (including zero-selection groups).
 perform 1 from public.product_modifier_groups where product_id=v_product.id for share;
 if jsonb_array_length(v_line->'groups')<>(select count(*) from public.product_modifier_groups l join public.modifier_groups g on g.id=l.group_id where l.product_id=v_product.id and l.active and g.active) or
 (select count(distinct value->>'id') from jsonb_array_elements(v_line->'groups'))<>jsonb_array_length(v_line->'groups') then raise sqlstate 'P1002'; end if;
 for v_group in select value from jsonb_array_elements(v_line->'groups') loop
 select g.* into v_g from public.modifier_groups g join public.product_modifier_groups l on l.group_id=g.id
 where l.product_id=v_product.id and l.active and g.active and g.brand_id=v_brand and g.id=(v_group->>'id')::uuid for share of g;
 if not found or v_g.version is distinct from (v_group->>'version')::bigint then raise sqlstate 'P1002'; end if;
 select count(*) into v_count from jsonb_array_elements(v_line->'modifiers') where value->>'group_id'=v_g.id::text;
 if v_count<v_g.min_select or v_count>v_g.max_select then raise sqlstate 'P1002'; end if;
 end loop;
 if (select count(distinct value->>'option_id') from jsonb_array_elements(v_line->'modifiers'))<>jsonb_array_length(v_line->'modifiers') then raise sqlstate 'P1001'; end if;
 v_unit:=v_product.base_price_idr;
 for v_mod in select value from jsonb_array_elements(v_line->'modifiers') loop
 select * into v_option from public.modifier_options where id=(v_mod->>'option_id')::uuid and brand_id=v_brand for share;
 if not found or not v_option.active or v_option.group_id is distinct from (v_mod->>'group_id')::uuid or
 v_option.version is distinct from (v_mod->>'option_version')::bigint or v_option.name is distinct from v_mod->>'option_name' or
 v_option.price_delta_idr is distinct from (v_mod->>'price_delta_idr')::bigint or not exists(
 select 1 from jsonb_array_elements(v_line->'groups') where value->>'id'=v_option.group_id::text) then raise sqlstate 'P1002'; end if;
 if not exists(select 1 from public.modifier_groups where id=v_option.group_id and name=v_mod->>'group_name') then raise sqlstate 'P1002'; end if;
 v_unit:=v_unit+v_option.price_delta_idr;
 end loop;
 if v_unit*v_qty is distinct from (v_line->>'line_total_idr')::bigint then raise sqlstate 'P1001'; end if;
 v_total:=v_total+v_unit*v_qty;
 end loop;
 if v_total is distinct from (v_payload->>'subtotal_idr')::bigint or v_total is distinct from (v_payload->>'total_idr')::bigint then raise sqlstate 'P1001'; end if;
 v_payment:=v_payload->'payment';
 if (v_payment->>'amount_idr')::bigint is distinct from v_total then raise sqlstate 'P1001'; end if;
 -- All graph rows, dedupe result and change feed share this transaction/subtransaction.
 insert into public.sales(id,brand_id,outlet_id,device_id,cashier_user_id,subtotal_idr,total_idr,status,created_at)
 values(v_sale,v_brand,v_device.outlet_id,v_device.id,v_user,v_total,v_total,'COMPLETED',v_when);
 for v_line in select value from jsonb_array_elements(v_payload->'items') loop
 insert into public.sale_items values((v_line->>'id')::uuid,v_sale,(v_line->>'product_id')::uuid,v_line->>'product_name',
 (v_line->>'unit_base_price_idr')::bigint,(v_line->>'quantity')::integer,(v_line->>'line_total_idr')::bigint);
 for v_mod in select value from jsonb_array_elements(v_line->'modifiers') loop
 -- Stable identities derived from immutable line + option; payment ID is the sale ID in its own table.
 insert into public.sale_item_modifiers values(md5((v_line->>'id')||':'||(v_mod->>'option_id'))::uuid,(v_line->>'id')::uuid,
 (v_mod->>'option_id')::uuid,v_mod->>'group_name',v_mod->>'option_name',(v_mod->>'price_delta_idr')::bigint);
 end loop;
 end loop;
 insert into public.payments values(v_sale,v_sale,v_payment->>'method',(v_payment->>'amount_idr')::bigint,
 (v_payment->>'tendered_idr')::bigint,(v_payment->>'change_idr')::bigint,v_payment->>'verification');
 insert into public.sync_operations(id,device_id,outlet_id,request_hash,result_sale_id) values(v_op,v_device.id,v_device.outlet_id,v_hash,v_sale);
 -- All future feed writers must acquire this transaction lock before allocating seq.
 perform pg_advisory_xact_lock(58301924001);
 insert into public.sync_changes(brand_id,outlet_id,entity_type,entity_id,action,cashier_user_id,payload)
 values(v_brand,v_device.outlet_id,'SALE',v_sale,'UPSERT',v_user,v_payload);
 return v_ack||jsonb_build_object('status','ACCEPTED');
 exception
 when sqlstate 'P1002' then v_code:='CATALOG_CHANGED';
 when sqlstate 'P1001' or data_exception or integrity_constraint_violation then v_code:='INVALID_SALE';
 end;
 end if;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(v_user,v_brand,v_device.outlet_id,'SALE_REVIEW',v_op,jsonb_build_object('code',v_code,'request_hash',v_hash,'payload_hash',encode(sha256(convert_to(p_payload,'UTF8')),'hex')));
 return v_ack||jsonb_build_object('status','REVIEW','code',v_code);
end;
$$;
revoke all on function public.submit_sale(text,text) from public,anon,authenticated;
grant execute on function public.submit_sale(text,text) to authenticated;

-- Catalog events retain complete row tombstones. Apply this initial schema before seeding.
create function private.catalog_version() returns trigger language plpgsql set search_path='' as $$
begin
 if TG_OP='DELETE' then raise exception 'Use active=false tombstones' using errcode='42501'; end if;
 if TG_OP='UPDATE' then
 if NEW.brand_id is distinct from OLD.brand_id then raise exception 'Immutable catalog scope'; end if;
 if TG_TABLE_NAME='product_modifier_groups' then
 if NEW.product_id<>OLD.product_id or NEW.group_id<>OLD.group_id then raise exception 'Immutable assignment identity'; end if;
 elsif NEW.id<>OLD.id then raise exception 'Immutable catalog identity'; end if;
 NEW.version:=OLD.version+1; NEW.updated_at:=clock_timestamp();
 end if;
 return NEW;
end;
$$;
create function private.catalog_change() returns trigger language plpgsql security definer set search_path='' as $$
declare v_id uuid;
begin
 perform pg_advisory_xact_lock(58301924001);
 v_id:=coalesce(to_jsonb(NEW)->>'id',to_jsonb(NEW)->>'product_id')::uuid;
 insert into public.sync_changes(brand_id,entity_type,entity_id,action,payload)
 values(NEW.brand_id,'CATALOG',v_id,case when NEW.active then 'UPSERT' else 'DELETE' end,
 jsonb_build_object('schema_version',1,'table',TG_TABLE_NAME,'record',to_jsonb(NEW)));
 return NEW;
end;
$$;
do $$declare t text; begin
 foreach t in array array['categories','products','modifier_groups','modifier_options','product_modifier_groups'] loop
 execute format('create trigger catalog_version before update or delete on public.%I for each row execute function private.catalog_version()',t);
 execute format('create trigger catalog_change after insert or update on public.%I for each row execute function private.catalog_change()',t);
 end loop;
end;$$;
create index sync_changes_scope_seq on public.sync_changes(brand_id,seq);

create function public.pull_changes(p_device_id uuid,p_device_secret text,p_after_seq bigint,p_role text,p_limit integer default 100,p_epoch uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_device public.devices; v_role text; v_brand uuid; v_high bigint; v_next bigint:=p_after_seq;
 v_rows jsonb:='[]'; v_row jsonb; v_change public.sync_changes; v_size integer:=0; v_count integer:=0; v_more boolean; v_epoch uuid;
begin
 if p_after_seq is null or p_after_seq<0 or p_limit is null or p_limit not between 1 and 100 then raise exception 'Invalid cursor' using errcode='22023'; end if;
 select * into v_device from public.devices where id=p_device_id and enrolled_by=auth.uid() and revoked_at is null for share;
 if not found or v_device.secret_hash is distinct from encode(sha256(convert_to(coalesce(p_device_secret,''),'UTF8')),'hex') then raise exception 'Device denied' using errcode='42501'; end if;
 v_role:=private.outlet_role(v_device.outlet_id);
 if v_role is null then raise exception 'Forbidden' using errcode='42501'; end if;
 if v_device.approved_at is null or v_device.approved_role is distinct from v_role then raise sqlstate 'PT423' using message='Device approval required'; end if;
 if p_role is distinct from v_role then raise sqlstate 'PT409' using message='Scope changed; revalidate access'; end if;
 select brand_id into v_brand from public.outlets where id=v_device.outlet_id;
 -- Shared lock gives a stable committed high-water boundary; all feed writers use the exclusive lock.
 perform pg_advisory_xact_lock_shared(58301924001);
 select epoch into v_epoch from private.sync_generation where singleton;
 if (p_after_seq>0 or p_epoch is not null) and p_epoch is distinct from v_epoch then raise sqlstate 'PT410' using message='Feed generation changed; reviewed recovery required'; end if;
 select coalesce(max(seq),0) into v_high from public.sync_changes;
 if p_after_seq>v_high then raise sqlstate 'PT409' using message='Cursor ahead; explicit reset required'; end if;
 for v_change in select * from public.sync_changes c where c.seq>p_after_seq and c.seq<=v_high and c.brand_id=v_brand and
 ((c.entity_type='CATALOG' and c.outlet_id is null) or (c.entity_type='SALE' and c.outlet_id=v_device.outlet_id and
 (v_role in ('OWNER','MANAGER') or c.cashier_user_id=auth.uid()))) order by c.seq limit p_limit+1 loop
 v_row:=jsonb_build_object('seq',v_change.seq,'entity_type',v_change.entity_type,'entity_id',v_change.entity_id,'action',v_change.action,'payload',v_change.payload);
 if v_count>=p_limit or (v_count>0 and v_size+octet_length(v_row::text)>524288) then exit; end if;
 if octet_length(v_row::text)>524288 then raise exception 'Unsupported oversized event' using errcode='22023'; end if;
 v_rows:=v_rows||jsonb_build_array(v_row); v_next:=v_change.seq; v_count:=v_count+1; v_size:=v_size+octet_length(v_row::text);
 end loop;
 select exists(select 1 from public.sync_changes c where c.seq>v_next and c.seq<=v_high and c.brand_id=v_brand and
 ((c.entity_type='CATALOG' and c.outlet_id is null) or (c.entity_type='SALE' and c.outlet_id=v_device.outlet_id and
 (v_role in ('OWNER','MANAGER') or c.cashier_user_id=auth.uid())))) into v_more;
 if not v_more then v_next:=v_high; end if;
 return jsonb_build_object('schema_version',1,'brand_id',v_brand,'outlet_id',v_device.outlet_id,'device_id',v_device.id,
 'user_id',auth.uid(),'role',v_role,'after_seq',p_after_seq,'next_seq',v_next,'has_more',v_more,'changes',v_rows,'feed_epoch',v_epoch);
end;
$$;
revoke all on function private.catalog_version(),private.catalog_change() from public,anon,authenticated;
revoke all on function public.pull_changes(uuid,text,bigint,text,integer,uuid) from public,anon,authenticated;
grant execute on function public.pull_changes(uuid,text,bigint,text,integer,uuid) to authenticated;

-- M6 management reads/writes always require a currently approved device and live role.
create function private.approved_device(p_device uuid,p_secret text) returns public.devices
language plpgsql security definer set search_path='' as $$
declare d public.devices; r text;
begin
 select * into d from public.devices where id=p_device and enrolled_by=auth.uid() and revoked_at is null for share;
 if not found or d.secret_hash is distinct from encode(sha256(convert_to(coalesce(p_secret,''),'UTF8')),'hex') then raise exception 'Device denied' using errcode='42501'; end if;
 r:=private.outlet_role(d.outlet_id);
 if r is null then raise exception 'Forbidden' using errcode='42501'; end if;
 if d.approved_at is null or d.approved_role is distinct from r then raise sqlstate 'PT423' using message='Device approval required'; end if;
 return d;
end;$$;
create function private.access_version() returns trigger language plpgsql set search_path='' as $$
begin
 if TG_TABLE_NAME='devices' then
 if NEW.revoked_at is distinct from OLD.revoked_at or NEW.approved_at is distinct from OLD.approved_at or NEW.approved_role is distinct from OLD.approved_role or NEW.secret_hash is distinct from OLD.secret_hash then NEW.version:=OLD.version+1; else NEW.version:=OLD.version; end if;
 else NEW.version:=OLD.version+1;
 end if;
 return NEW;
end;$$;
create trigger access_version before update on public.devices for each row execute function private.access_version();
create trigger access_version before update on public.outlet_members for each row execute function private.access_version();

create function public.get_sales_report(p_device_id uuid,p_device_secret text,p_from_date date,p_to_date date,p_outlet_id uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices; r text; b uuid; scope_outlet uuid; start_utc timestamptz; end_utc timestamptz; result jsonb;
begin
 d:=private.approved_device(p_device_id,p_device_secret); r:=private.outlet_role(d.outlet_id);
 if r not in ('OWNER','MANAGER') then raise exception 'Reports forbidden' using errcode='42501'; end if;
 if p_from_date is null or p_to_date is null or p_to_date<p_from_date or p_to_date-p_from_date>365 then raise sqlstate 'PT422' using message='Invalid report range'; end if;
 select brand_id into b from public.outlets where id=d.outlet_id;
 scope_outlet:=case when r='MANAGER' then d.outlet_id else p_outlet_id end;
 if (r='MANAGER' and p_outlet_id is not null and p_outlet_id<>d.outlet_id) or
 (scope_outlet is not null and not exists(select 1 from public.outlets where id=scope_outlet and brand_id=b)) then raise exception 'Outlet denied' using errcode='42501'; end if;
 start_utc:=p_from_date::timestamp at time zone 'Asia/Jakarta'; end_utc:=(p_to_date+1)::timestamp at time zone 'Asia/Jakarta';
 if exists(select 1 from public.sales s left join public.payments p on p.sale_id=s.id where s.brand_id=b and (scope_outlet is null or s.outlet_id=scope_outlet) and s.created_at>=start_utc and s.created_at<end_utc and
 (p.id is null or p.amount_idr<>s.total_idr or (select coalesce(sum(i.line_total_idr),-1) from public.sale_items i where i.sale_id=s.id)<>s.subtotal_idr)) then raise sqlstate 'PT422' using message='Incomplete sale graph'; end if;
 with scoped as (
 select s.*,p.method from public.sales s join public.payments p on p.sale_id=s.id where s.brand_id=b and (scope_outlet is null or s.outlet_id=scope_outlet) and s.created_at>=start_utc and s.created_at<end_utc
 ), summary as (
 select count(*)::text sale_count,coalesce(sum(subtotal_idr),0)::bigint::text subtotal_idr,coalesce(sum(discount_idr),0)::bigint::text discount_idr,
 coalesce(sum(total_idr),0)::bigint::text total_idr,coalesce(sum(total_idr) filter(where method='CASH'),0)::bigint::text cash_idr,
 coalesce(sum(total_idr) filter(where method='QRIS_MANUAL'),0)::bigint::text qris_idr from scoped
 ), days as (
 select (created_at at time zone 'Asia/Jakarta')::date report_day,count(*)::text sale_count,sum(total_idr)::bigint::text total_idr from scoped group by 1
 ), outlets as (
 select s.outlet_id,o.name,count(*)::text sale_count,sum(s.total_idr)::bigint::text total_idr from scoped s join public.outlets o on o.id=s.outlet_id group by s.outlet_id,o.name
 ), products as (
 select i.product_id,i.product_name_snapshot name,sum(i.quantity)::bigint::text quantity,sum(i.line_total_idr)::bigint gross from public.sale_items i join scoped s on s.id=i.sale_id group by i.product_id,i.product_name_snapshot order by gross desc,i.product_id,i.product_name_snapshot limit 20
 )
 select jsonb_build_object('schema_version',1,'brand_id',b,'user_id',auth.uid(),'device_id',d.id,'role',r,'source_outlet_id',d.outlet_id,'outlet_id',scope_outlet,
 'from_date',p_from_date,'to_date',p_to_date,'as_of_ms',floor(extract(epoch from clock_timestamp())*1000)::bigint,
 'summary',(select to_jsonb(summary) from summary),
 'days',coalesce((select jsonb_agg(jsonb_build_object('day',report_day,'sale_count',sale_count,'total_idr',total_idr) order by report_day) from days),'[]'),
 'outlets',coalesce((select jsonb_agg(to_jsonb(outlets) order by outlet_id) from outlets),'[]'),
 'products',coalesce((select jsonb_agg(jsonb_build_object('product_id',product_id,'name',name,'quantity',quantity,'gross_idr',gross::text) order by gross desc,product_id,name) from products),'[]'),
 'choices',(select jsonb_agg(jsonb_build_object('id',id,'name',name,'active',active) order by name,id) from public.outlets where brand_id=b and (r='OWNER' or id=d.outlet_id))) into result;
 if octet_length(result::text)>524288 then raise sqlstate 'PT413' using message='Narrow report range'; end if;
 return result;
end;$$;

create function public.list_staff(p_device_id uuid,p_device_secret text) returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices; r text; b uuid; result jsonb;
begin
 d:=private.approved_device(p_device_id,p_device_secret);r:=private.outlet_role(d.outlet_id);
 if r not in ('OWNER','MANAGER') then raise exception 'Staff forbidden' using errcode='42501'; end if;
 select brand_id into b from public.outlets where id=d.outlet_id;
 select jsonb_build_object('schema_version',1,'brand_id',b,'outlet_id',d.outlet_id,'user_id',auth.uid(),'device_id',d.id,'role',r,
 'members',coalesce((select jsonb_agg(jsonb_build_object('user_id',m.user_id,'name',p.full_name,'email',u.email,'role',m.role,'active',m.active,'profile_active',p.active,'version',m.version) order by p.full_name,m.user_id)
 from public.outlet_members m join public.user_profiles p on p.id=m.user_id join auth.users u on u.id=m.user_id where m.outlet_id=d.outlet_id and not private.owner(b,m.user_id)),'[]'),
 'devices',coalesce((select jsonb_agg(jsonb_build_object('id',x.id,'user_id',x.enrolled_by,'role',private.outlet_role(x.outlet_id,x.enrolled_by),'revoked',x.revoked_at is not null,'approved',x.approved_at is not null and x.approved_role is not distinct from private.outlet_role(x.outlet_id,x.enrolled_by),'version',x.version) order by x.enrolled_at,x.id)
 from public.devices x where x.outlet_id=d.outlet_id and (r='OWNER' or private.outlet_role(x.outlet_id,x.enrolled_by)='CASHIER')),'[]'),
 'audit',case when r='OWNER' then coalesce((select jsonb_agg(entry order by created_at desc,id desc) from (
 select a.id,a.created_at,jsonb_build_object('id',a.id,'actor_id',a.actor_user_id,'target_id',a.target_id,'action',a.action,
 'created_at_ms',floor(extract(epoch from a.created_at)*1000)::bigint,'reason',a.metadata->>'reason') entry
 from public.audit_logs a where a.brand_id=b and a.outlet_id=d.outlet_id and a.action in ('STAFF_PROVISION','STAFF_ENABLE','STAFF_DISABLE','DEVICE_REVOKE','DEVICE_RESTORE_PENDING','DEVICE_APPROVE')
 order by a.created_at desc,a.id desc limit 50) recent),'[]') else '[]'::jsonb end) into result;
 if octet_length(result::text)>524288 then raise sqlstate 'PT413' using message='Staff roster requires operator review'; end if;
 return result;
end;$$;
create function public.set_staff_access(p_device_id uuid,p_device_secret text,p_user_id uuid,p_active boolean,p_expected_version bigint,p_reason text)
returns void language plpgsql security definer set search_path='' as $$
declare d public.devices; r text; b uuid; m public.outlet_members; revoked jsonb;
begin
 d:=private.approved_device(p_device_id,p_device_secret);r:=private.outlet_role(d.outlet_id);select brand_id into b from public.outlets where id=d.outlet_id;
 if r not in ('OWNER','MANAGER') or p_user_id=auth.uid() or private.owner(b,p_user_id) then raise exception 'Staff target forbidden' using errcode='42501'; end if;
 if p_active is null or p_reason is null or length(btrim(p_reason)) not between 3 and 240 then raise sqlstate 'PT422' using message='Reason required'; end if;
 select * into m from public.outlet_members where user_id=p_user_id and outlet_id=d.outlet_id for update;
 if not found or (r='MANAGER' and m.role<>'CASHIER') then raise exception 'Staff target forbidden' using errcode='42501'; end if;
 if p_expected_version is distinct from m.version then raise sqlstate 'PT409' using message='Staff changed; reload'; end if;
 if p_active and not private.active_user(p_user_id) then raise sqlstate 'PT422' using message='Inactive profile needs administrator review'; end if;
 if m.active=p_active then return; end if;
 update public.outlet_members set active=p_active where user_id=m.user_id and outlet_id=m.outlet_id;
 if not p_active then
 with changed as (update public.devices set revoked_at=clock_timestamp() where enrolled_by=m.user_id and outlet_id=m.outlet_id and revoked_at is null returning id)
 select coalesce(jsonb_agg(id),'[]') into revoked from changed;
 end if;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata) values(auth.uid(),b,m.outlet_id,
 case when p_active then 'STAFF_ENABLE' else 'STAFF_DISABLE' end,m.user_id,jsonb_build_object('reason',btrim(p_reason),'version',m.version,'revoked_device_ids',coalesce(revoked,'[]')));
end;$$;
create function public.set_device_access(p_device_id uuid,p_device_secret text,p_target_id uuid,p_revoked boolean,p_expected_version bigint,p_reason text)
returns void language plpgsql security definer set search_path='' as $$
declare d public.devices; target public.devices; r text; target_role text; b uuid;
begin
 d:=private.approved_device(p_device_id,p_device_secret);r:=private.outlet_role(d.outlet_id);
 select brand_id into b from public.outlets where id=d.outlet_id;
 if r not in ('OWNER','MANAGER') or p_target_id=d.id then raise exception 'Device target forbidden' using errcode='42501'; end if;
 if p_revoked is null or p_reason is null or length(btrim(p_reason)) not between 3 and 240 then raise sqlstate 'PT422' using message='Reason required'; end if;
 select * into target from public.devices where id=p_target_id and outlet_id=d.outlet_id for update;
 if not found then raise exception 'Device target forbidden' using errcode='42501'; end if;
 target_role:=private.outlet_role(target.outlet_id,target.enrolled_by);
 if (r='MANAGER' and (target_role is distinct from 'CASHIER' or not p_revoked)) then raise exception 'Device target forbidden' using errcode='42501'; end if;
 if p_expected_version is distinct from target.version then raise sqlstate 'PT409' using message='Device changed; reload'; end if;
 if not p_revoked and target_role is null then raise sqlstate 'PT422' using message='Restore membership first'; end if;
 if p_revoked=(target.revoked_at is not null) then return; end if;
 update public.devices set revoked_at=case when p_revoked then clock_timestamp() end,approved_at=null,approved_by=null,approved_role=null where id=target.id;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata) values(auth.uid(),b,d.outlet_id,
 case when p_revoked then 'DEVICE_REVOKE' else 'DEVICE_RESTORE_PENDING' end,target.id,jsonb_build_object('reason',btrim(p_reason),'version',target.version));
end;$$;
revoke all on function private.approved_device(uuid,text),private.access_version() from public,anon,authenticated;
revoke all on function public.get_sales_report(uuid,text,date,date,uuid),public.list_staff(uuid,text),public.set_staff_access(uuid,text,uuid,boolean,bigint,text),public.set_device_access(uuid,text,uuid,boolean,bigint,text) from public,anon,authenticated;
grant execute on function public.get_sales_report(uuid,text,date,date,uuid),public.list_staff(uuid,text),public.set_staff_access(uuid,text,uuid,boolean,bigint,text),public.set_device_access(uuid,text,uuid,boolean,bigint,text) to authenticated;

-- Catalog edits are online, role/device scoped, versioned and idempotent.
create table private.catalog_operations(id uuid primary key,device_id uuid not null references public.devices(id),actor_id uuid not null references auth.users(id),request_hash text not null,result jsonb not null);
revoke all on private.catalog_operations from public,anon,authenticated;
create function public.save_catalog_record(p_device_id uuid,p_device_secret text,p_epoch uuid,p_change jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices; r text; b uuid; op uuid; record_id uuid; kind text; expected bigint; current_version bigint; new_version bigint;
 name_value text; active_value boolean; price bigint; parent uuid; min_value integer; max_value integer; order_value integer;
 groups uuid[]; g uuid; item public.modifier_groups; existing private.catalog_operations; hash_value text; result jsonb; through_seq bigint;
begin
 d:=private.approved_device(p_device_id,p_device_secret);r:=private.outlet_role(d.outlet_id);
 if r not in ('OWNER','MANAGER') then raise exception 'Catalog forbidden' using errcode='42501'; end if;
 if p_epoch is distinct from (select epoch from private.sync_generation where singleton) then raise sqlstate 'PT410' using message='Feed generation changed'; end if;
 select brand_id into b from public.outlets where id=d.outlet_id;
 if p_change is null or jsonb_typeof(p_change)<>'object' or octet_length(p_change::text)>32768 then raise sqlstate 'PT422' using message='Invalid catalog request'; end if;
 op:=(p_change->>'operation_id')::uuid;record_id:=(p_change->>'id')::uuid;kind:=p_change->>'kind';expected:=(p_change->>'expected_version')::bigint;
 if op is null or record_id is null or kind is null or kind not in ('Category','Product','Group','Option') or expected is null or expected<0 then raise sqlstate 'PT422' using message='Invalid catalog identity'; end if;
 perform pg_advisory_xact_lock(hashtextextended(op::text,3));
 hash_value:=encode(sha256(convert_to(p_change::text,'UTF8')),'hex');
 select * into existing from private.catalog_operations where id=op;
 if found then
 if existing.device_id<>d.id or existing.actor_id<>auth.uid() or existing.request_hash<>hash_value then raise sqlstate 'PT409' using message='Operation conflict'; end if;
 return existing.result;
 end if;
 -- Serialize brand catalog commands; target/dependency locks precede trigger feed allocation.
 perform pg_advisory_xact_lock(hashtextextended(b::text,4));
 name_value:=btrim(p_change->>'name');active_value:=(p_change->>'active')::boolean;
 if name_value is null or length(name_value) not between 1 and 120 or jsonb_typeof(p_change->'active') is distinct from 'boolean' then raise sqlstate 'PT422' using message='Invalid catalog fields'; end if;
 if kind='Category' then
 select version into current_version from public.categories where id=record_id and brand_id=b for update;
 elsif kind='Product' then
 select version into current_version from public.products where id=record_id and brand_id=b for update;
 elsif kind='Group' then
 select version into current_version from public.modifier_groups where id=record_id and brand_id=b for update;
 else
 select version into current_version from public.modifier_options where id=record_id and brand_id=b for update;
 end if;
 if (current_version is null and expected<>0) or (current_version is not null and current_version<>expected) then raise sqlstate 'PT409' using message='Catalog changed; reload'; end if;
 if expected=0 and (exists(select 1 from public.categories where id=record_id) or exists(select 1 from public.products where id=record_id) or exists(select 1 from public.modifier_groups where id=record_id) or exists(select 1 from public.modifier_options where id=record_id)) then raise sqlstate 'PT409' using message='Catalog identity already exists'; end if;
 if kind in ('Product','Option') then
 price:=(p_change->>'price_idr')::bigint;parent:=(p_change->>'parent_id')::uuid;
 if price is null or price<0 or parent is null then raise sqlstate 'PT422' using message='Invalid catalog price or parent'; end if;
 end if;
 if kind='Category' then
 order_value:=(p_change->>'sort_order')::integer;
 if order_value is null or order_value<0 then raise sqlstate 'PT422' using message='Invalid sort order'; end if;
 insert into public.categories(id,brand_id,name,active,sort_order) values(record_id,b,name_value,active_value,order_value)
 on conflict(id) do update set name=excluded.name,active=excluded.active,sort_order=excluded.sort_order returning version into new_version;
 elsif kind='Group' then
 min_value:=(p_change->>'min_select')::integer;max_value:=(p_change->>'max_select')::integer;
 if min_value is null or max_value is null or min_value<0 or max_value<1 or min_value>max_value then raise sqlstate 'PT422' using message='Invalid modifier bounds'; end if;
 perform 1 from public.modifier_options where group_id=record_id for share;
 if active_value and exists(select 1 from public.product_modifier_groups l join public.products p on p.id=l.product_id where l.group_id=record_id and l.active and p.active) and
 (select count(*) from public.modifier_options where group_id=record_id and active)<min_value then raise sqlstate 'PT422' using message='Assigned group lacks required options'; end if;
 insert into public.modifier_groups(id,brand_id,name,active,min_select,max_select) values(record_id,b,name_value,active_value,min_value,max_value)
 on conflict(id) do update set name=excluded.name,active=excluded.active,min_select=excluded.min_select,max_select=excluded.max_select returning version into new_version;
 elsif kind='Option' then
 select * into item from public.modifier_groups where id=parent and brand_id=b for share;
 if not found or (active_value and not item.active) then raise sqlstate 'PT422' using message='Unavailable modifier group'; end if;
 if exists(select 1 from public.modifier_options where id=record_id and group_id<>parent) then raise sqlstate 'PT422' using message='Option group is immutable'; end if;
 if not active_value and item.active and exists(select 1 from public.product_modifier_groups l join public.products p on p.id=l.product_id where l.group_id=parent and l.active and p.active) and
 (select count(*) from public.modifier_options where group_id=parent and active and id<>record_id)<item.min_select then raise sqlstate 'PT422' using message='Assigned group needs this option'; end if;
 insert into public.modifier_options(id,brand_id,name,active,group_id,price_delta_idr) values(record_id,b,name_value,active_value,parent,price)
 on conflict(id) do update set name=excluded.name,active=excluded.active,price_delta_idr=excluded.price_delta_idr returning version into new_version;
 else
 perform 1 from public.categories where id=parent and brand_id=b and (active or not active_value) for share;
 if not found then raise sqlstate 'PT422' using message='Unavailable category'; end if;
 if jsonb_typeof(p_change->'group_ids') is distinct from 'array' or jsonb_array_length(p_change->'group_ids')>100 then raise sqlstate 'PT422' using message='Invalid group assignment'; end if;
 select coalesce(array_agg(value::uuid order by ord),'{}') into groups from jsonb_array_elements_text(p_change->'group_ids') with ordinality a(value,ord);
 if cardinality(groups)<>(select count(distinct x) from unnest(groups) x) then raise sqlstate 'PT422' using message='Duplicate group'; end if;
 foreach g in array groups loop
 select * into item from public.modifier_groups where id=g and brand_id=b for share;
 if not found or (not item.active and not exists(select 1 from public.product_modifier_groups where product_id=record_id and group_id=g and active)) then raise sqlstate 'PT422' using message='Unavailable assigned group'; end if;
 perform 1 from public.modifier_options where group_id=g for share;
 if active_value and item.active and (select count(*) from public.modifier_options where group_id=g and active)<item.min_select then raise sqlstate 'PT422' using message='Assigned group lacks required options'; end if;
 end loop;
 perform 1 from public.product_modifier_groups where product_id=record_id for update;
 insert into public.products(id,brand_id,name,active,category_id,base_price_idr) values(record_id,b,name_value,active_value,parent,price)
 on conflict(id) do update set name=excluded.name,active=excluded.active,category_id=excluded.category_id,base_price_idr=excluded.base_price_idr returning version into new_version;
 update public.product_modifier_groups set active=false where product_id=record_id and active and not(group_id=any(groups));
 insert into public.product_modifier_groups(product_id,group_id,brand_id,sort_order,active)
 select record_id,x,b,(ord-1)::integer,true from unnest(groups) with ordinality a(x,ord)
 on conflict(product_id,group_id) do update set active=true,sort_order=excluded.sort_order
 where not product_modifier_groups.active or product_modifier_groups.sort_order<>excluded.sort_order;
 end if;
 select coalesce(max(seq),0) into through_seq from public.sync_changes where brand_id=b;
 result:=jsonb_build_object('operation_id',op,'id',record_id,'kind',kind,'version',new_version,'through_seq',through_seq,'feed_epoch',p_epoch);
 insert into private.catalog_operations values(op,d.id,auth.uid(),hash_value,result);
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(auth.uid(),b,d.outlet_id,'CATALOG_SAVE',record_id,jsonb_build_object('operation_id',op,'kind',kind,'version',new_version));
 return result;
end;$$;
revoke all on function public.save_catalog_record(uuid,text,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.save_catalog_record(uuid,text,uuid,jsonb) to authenticated;

notify pgrst,'reload schema';
commit;
