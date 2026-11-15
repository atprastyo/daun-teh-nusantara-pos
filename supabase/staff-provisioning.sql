-- Initial optional staff-account setup, run once after schema.sql. Not a migration.
-- No Auth password or password hash is stored in application tables.
begin;
create table private.staff_provision_operations (
 id uuid primary key, actor_id uuid not null references auth.users(id), device_id uuid not null references public.devices(id),
 brand_id uuid not null references public.brands(id), outlet_id uuid not null references public.outlets(id),
 target_id uuid not null unique default gen_random_uuid(), email text not null unique,
 full_name text not null, role text not null check(role in ('MANAGER','CASHIER')),
 created_at timestamptz not null default now(), completed_at timestamptz, password_changed_at timestamptz
);
revoke all on private.staff_provision_operations from public,anon,authenticated,service_role;
-- Gate both RLS and every live membership/device/lease RPC until password replacement.
create or replace function private.active_user(p_user uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.user_profiles where id=p_user and active)
 and not exists(select 1 from private.staff_provision_operations where target_id=p_user and password_changed_at is null);
$$;
create function public.staff_password_change_required() returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.staff_provision_operations where target_id=auth.uid() and password_changed_at is null);
$$;
create function public.prepare_staff_account(p_device_id uuid,p_device_secret text,p_operation_id uuid,p_email text,p_full_name text,p_role text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices;r text;b uuid;j private.staff_provision_operations;e text:=lower(btrim(p_email));n text:=btrim(p_full_name);
begin
 d:=private.approved_device(p_device_id,p_device_secret);r:=private.outlet_role(d.outlet_id);
 if p_role is null or not (r='OWNER' and p_role in ('MANAGER','CASHIER') or r='MANAGER' and p_role='CASHIER') then raise exception 'Staff creation forbidden' using errcode='42501';end if;
 if p_operation_id is null or e is null or length(e)>254 or e !~ '^[a-zA-Z0-9.!#$%&''*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(\.[a-zA-Z0-9-]+)+$' or n is null or length(n) not between 1 and 120 then raise sqlstate 'PT422' using message='Invalid staff details';end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text,77));
 perform pg_advisory_xact_lock(hashtextextended(p_operation_id::text,78));
 select * into j from private.staff_provision_operations where id=p_operation_id;
 if found then
  if j.actor_id<>auth.uid() or j.device_id<>d.id or j.outlet_id<>d.outlet_id or j.email<>e or j.full_name<>n or j.role<>p_role then raise sqlstate 'PT409' using message='Operation conflict';end if;
 else
  if (select count(*) from private.staff_provision_operations where actor_id=auth.uid() and created_at>now()-interval '1 hour')>=10 then raise sqlstate 'PT429' using message='Staff creation rate limited';end if;
  if exists(select 1 from auth.users where lower(email)=e) or exists(select 1 from private.staff_provision_operations where email=e) then raise sqlstate 'PT409' using message='Email unavailable; administrator review required';end if;
  select brand_id into b from public.outlets where id=d.outlet_id;
  insert into private.staff_provision_operations(id,actor_id,device_id,brand_id,outlet_id,email,full_name,role)
  values(p_operation_id,auth.uid(),d.id,b,d.outlet_id,e,n,p_role) returning * into j;
 end if;
 return jsonb_build_object('operation_id',j.id,'user_id',j.target_id,'email',j.email,'completed',j.completed_at is not null);
end;$$;
create function public.finish_staff_account(p_device_id uuid,p_device_secret text,p_operation_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare d public.devices;r text;j private.staff_provision_operations;u auth.users;
begin
 d:=private.approved_device(p_device_id,p_device_secret);r:=private.outlet_role(d.outlet_id);
 select * into j from private.staff_provision_operations where id=p_operation_id for update;
 if not found or j.actor_id<>auth.uid() or j.device_id<>d.id or j.outlet_id<>d.outlet_id or not (r='OWNER' and j.role in ('MANAGER','CASHIER') or r='MANAGER' and j.role='CASHIER') then raise exception 'Staff creation forbidden' using errcode='42501';end if;
 if j.completed_at is null then
  select * into u from auth.users where id=j.target_id;
  if not found or lower(u.email) is distinct from j.email or u.email_confirmed_at is null or u.raw_app_meta_data->>'pos_provision_operation' is distinct from j.id::text then raise sqlstate 'PT409' using message='Auth identity not confirmed';end if;
  -- Never attach, reactivate or overwrite an existing profile/membership.
  if exists(select 1 from public.user_profiles where id=j.target_id) then raise sqlstate 'PT409' using message='Profile conflict';end if;
  insert into public.user_profiles(id,full_name) values(j.target_id,j.full_name);
  insert into public.outlet_members(user_id,outlet_id,role) values(j.target_id,j.outlet_id,j.role);
  insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
  values(j.actor_id,j.brand_id,j.outlet_id,'STAFF_PROVISION',j.target_id,jsonb_build_object('operation_id',j.id,'role',j.role,'reason','Staff account created in app'));
  update private.staff_provision_operations set completed_at=now() where id=j.id;
 end if;
 return jsonb_build_object('operation_id',j.id,'user_id',j.target_id,'outlet_id',j.outlet_id,'role',j.role,'status','CREATED');
end;$$;
-- Only the Edge Function, after successfully replacing Auth password, may clear the gate.
create function public.complete_staff_password_change(p_user_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 update private.staff_provision_operations set password_changed_at=now() where target_id=p_user_id and completed_at is not null and password_changed_at is null;
end;$$;
revoke all on function public.staff_password_change_required(),public.prepare_staff_account(uuid,text,uuid,text,text,text),public.finish_staff_account(uuid,text,uuid),public.complete_staff_password_change(uuid) from public,anon,authenticated,service_role;
grant execute on function public.staff_password_change_required(),public.prepare_staff_account(uuid,text,uuid,text,text,text),public.finish_staff_account(uuid,text,uuid) to authenticated;
grant usage on schema public to service_role;
grant execute on function public.complete_staff_password_change(uuid) to service_role;
commit;
