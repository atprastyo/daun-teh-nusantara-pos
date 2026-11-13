-- Initial development schema, not a migration. Apply only to a fresh development project.
-- Supabase provides auth.users, auth.uid(), anon, authenticated and service_role.
begin;
revoke create on schema public from public,anon,authenticated;
alter default privileges in schema public revoke all on tables from anon,authenticated;
alter default privileges in schema public revoke execute on functions from public,anon,authenticated;
create schema private;
alter default privileges in schema private revoke execute on functions from public,anon,authenticated;
revoke all on schema private from public;
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
 role text not null check(role in ('MANAGER','CASHIER')), active boolean not null default true, primary key(user_id,outlet_id)
);
create index outlet_members_outlet on public.outlet_members(outlet_id);
create table public.devices (
 id uuid primary key, outlet_id uuid not null references public.outlets(id), enrolled_by uuid not null references public.user_profiles(id),
 secret_hash text not null check(secret_hash ~ '^[0-9a-f]{64}$'), enrolled_at timestamptz not null default now(),
 last_seen_at timestamptz not null default now(), revoked_at timestamptz, approved_at timestamptz,
 approved_by uuid references public.user_profiles(id), unique(id,outlet_id)
);
create table public.staff_invitations (
 id uuid primary key default gen_random_uuid(), email text not null check(email=lower(btrim(email)) and length(email)<=254 and email like '%@%'),
 outlet_id uuid not null references public.outlets(id), role text not null check(role in ('MANAGER','CASHIER')),
 invited_by uuid not null references public.user_profiles(id), created_at timestamptz not null default now(),
 expires_at timestamptz not null default now()+interval '7 days', accepted_at timestamptz, accepted_by uuid references auth.users(id), cancelled_at timestamptz
);
create unique index pending_staff_invitation on public.staff_invitations(email,outlet_id) where accepted_at is null and cancelled_at is null;
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
create function private.can_invite(p_actor uuid,p_outlet uuid,p_role text) returns boolean language sql stable security definer set search_path='' as $$
 select coalesce(p_role in ('MANAGER','CASHIER') and
 (private.outlet_role(p_outlet,p_actor)='OWNER' or (private.outlet_role(p_outlet,p_actor)='MANAGER' and p_role='CASHIER')),false);
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
create function public.create_staff_invitation(p_email text,p_outlet_id uuid,p_role text) returns jsonb
 language plpgsql security definer set search_path='' as $$
 declare v_actor uuid:=auth.uid(); v_brand uuid; v_email text:=lower(btrim(p_email)); v_id uuid; v_target uuid;
 begin
 if not private.can_invite(v_actor,p_outlet_id,p_role) then raise exception 'Forbidden' using errcode='42501'; end if;
 if v_email is null or length(v_email)>254 or v_email not like '%@%' then raise exception 'Invalid invitation' using errcode='22023'; end if;
 select brand_id into v_brand from public.outlets where id=p_outlet_id;
 select id into v_target from auth.users where lower(email)=v_email;
 if v_target is not null and (private.owner(v_brand,v_target) or
 (private.outlet_role(p_outlet_id,v_actor)='MANAGER' and exists(select 1 from public.outlet_members where user_id=v_target and outlet_id=p_outlet_id and role='MANAGER'))) then
 raise exception 'Forbidden target' using errcode='42501'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_actor::text,0));
 if (select count(*) from public.audit_logs where actor_user_id=v_actor and action='INVITE_REQUEST' and created_at>now()-interval '10 minutes')>=5 then
 raise sqlstate 'PT429' using message='Invitation rate limit'; end if;
 update public.staff_invitations set cancelled_at=now() where email=v_email and outlet_id=p_outlet_id and expires_at<=now() and accepted_at is null and cancelled_at is null;
 select id into v_id from public.staff_invitations where email=v_email and outlet_id=p_outlet_id and accepted_at is null and cancelled_at is null;
 if v_id is null then
 insert into public.staff_invitations(email,outlet_id,role,invited_by) values(v_email,p_outlet_id,p_role,v_actor) returning id into v_id;
 elsif not exists(select 1 from public.staff_invitations where id=v_id and role=p_role and invited_by=v_actor) then
 raise exception 'Invitation conflict' using errcode='23505'; end if;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(v_actor,v_brand,p_outlet_id,'INVITE_REQUEST',v_id,jsonb_build_object('role',p_role));
 return jsonb_build_object('id',v_id,'email',v_email);
 end;
$$;
create function public.accept_staff_invitations() returns integer language plpgsql security definer set search_path='' as $$
 declare v_user uuid:=auth.uid(); v_email text; v_inv public.staff_invitations; v_count integer:=0; v_brand uuid;
 begin
 select lower(email) into v_email from auth.users where id=v_user and email_confirmed_at is not null;
 if v_email is null then raise exception 'Verified user required' using errcode='42501'; end if;
 insert into public.user_profiles(id) values(v_user) on conflict(id) do nothing;
 if not private.active_user(v_user) then raise exception 'Inactive profile' using errcode='42501'; end if;
 for v_inv in select * from public.staff_invitations where email=v_email and accepted_at is null and cancelled_at is null and expires_at>now() for update loop
 if not private.can_invite(v_inv.invited_by,v_inv.outlet_id,v_inv.role) then continue; end if;
 select brand_id into v_brand from public.outlets where id=v_inv.outlet_id;
 if private.owner(v_brand,v_user) or (private.outlet_role(v_inv.outlet_id,v_inv.invited_by)='MANAGER' and exists(
 select 1 from public.outlet_members where user_id=v_user and outlet_id=v_inv.outlet_id and role='MANAGER')) then continue; end if;
 insert into public.outlet_members(user_id,outlet_id,role,active) values(v_user,v_inv.outlet_id,v_inv.role,true)
 on conflict(user_id,outlet_id) do update set role=excluded.role,active=true;
 update public.staff_invitations set accepted_at=now(),accepted_by=v_user where id=v_inv.id;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id) values(v_user,v_brand,v_inv.outlet_id,'INVITE_ACCEPT',v_inv.id);
 v_count:=v_count+1;
 end loop;
 return v_count;
 end;
$$;
create function public.enroll_device(p_device_id uuid,p_outlet_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
 declare v_user uuid:=auth.uid(); v_secret text; v_device public.devices; v_brand uuid;
 begin
 if private.outlet_role(p_outlet_id) is null then raise exception 'Forbidden' using errcode='42501'; end if;
 select * into v_device from public.devices where id=p_device_id for update;
 if found and (v_device.enrolled_by!=v_user or v_device.outlet_id!=p_outlet_id or v_device.revoked_at is not null) then raise exception 'Device denied' using errcode='42501'; end if;
 v_secret:=replace(gen_random_uuid()::text,'-','')||replace(gen_random_uuid()::text,'-','');
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_by)
 values(p_device_id,p_outlet_id,v_user,encode(sha256(convert_to(v_secret,'UTF8')),'hex'),
 case when private.outlet_role(p_outlet_id)='OWNER' then now() end,
 case when private.outlet_role(p_outlet_id)='OWNER' then v_user end)
 on conflict(id) do update set secret_hash=excluded.secret_hash,last_seen_at=now();
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
 if v_device.approved_at is null then raise sqlstate 'PT423' using message='Device approval required'; end if;
 v_role:=private.outlet_role(v_device.outlet_id);
 if v_role is null then raise exception 'Forbidden' using errcode='42501'; end if;
 select b.* into v_brand from public.brands b join public.outlets o on o.brand_id=b.id where o.id=v_device.outlet_id;
 v_expiry:=case when v_brand.offline_access_enabled then v_now+interval '1 month' else v_now end;
 update public.devices set last_seen_at=v_now where id=v_device.id;
 return jsonb_build_object('user_id',auth.uid(),'brand_id',v_brand.id,'outlet_id',v_device.outlet_id,'device_id',v_device.id,'role',v_role,
 'issued_at_ms',floor(extract(epoch from v_now)*1000)::bigint,'expires_at_ms',floor(extract(epoch from v_expiry)*1000)::bigint);
 end;
$$;

create function public.list_device_requests(p_outlet_id uuid) returns table(device_id uuid,user_id uuid,full_name text,email text,enrolled_at timestamptz)
 language plpgsql security definer set search_path='' as $$
 begin
 if private.outlet_role(p_outlet_id) not in ('OWNER','MANAGER') or private.outlet_role(p_outlet_id) is null then raise exception 'Forbidden' using errcode='42501'; end if;
 return query select d.id,d.enrolled_by,p.full_name,u.email::text,d.enrolled_at from public.devices d
 join public.user_profiles p on p.id=d.enrolled_by join auth.users u on u.id=d.enrolled_by
 where d.outlet_id=p_outlet_id and d.approved_at is null and d.revoked_at is null and
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
 update public.devices set approved_at=now(),approved_by=auth.uid() where id=p_device_id;
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
alter table public.staff_invitations enable row level security;
revoke all on public.staff_invitations from anon,authenticated;
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
grant select(id,outlet_id,enrolled_by,enrolled_at,last_seen_at,revoked_at,approved_at,approved_by) on public.devices to authenticated;
create policy read_scoped on public.staff_invitations for select to authenticated using (private.outlet_role(outlet_id) in ('OWNER','MANAGER'));
grant select on public.staff_invitations to authenticated;
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
revoke all on function public.get_access_context(),public.create_staff_invitation(text,uuid,text),public.accept_staff_invitations(),public.enroll_device(uuid,uuid),public.issue_offline_lease(uuid,text),public.list_device_requests(uuid),public.approve_device(uuid) from public,anon,authenticated;
grant execute on function public.get_access_context(),public.create_staff_invitation(text,uuid,text),public.accept_staff_invitations(),public.enroll_device(uuid,uuid),public.issue_offline_lease(uuid,text),public.list_device_requests(uuid),public.approve_device(uuid) to authenticated;
grant usage on schema public to authenticated;
-- No authenticated table INSERT/UPDATE/DELETE grants. Catalog management and sale
-- submission will be authorized RPCs with optimistic/idempotent validation later.
notify pgrst,'reload schema';
commit;
