-- Compatibility installer; current default is automatic approval for registered staff.
-- Apply after the existing schema. Preserves devices, staff and explicit policy settings.
begin;
-- Active registered staff auto-approve by default; SQL administrators may restore manual mode.
create table if not exists private.device_approval_policy (
 singleton boolean primary key default true check(singleton),
 auto_approve_registered boolean not null default true,
 reason text not null check(length(btrim(reason)) between 3 and 240)
);
revoke all on private.device_approval_policy from public,anon,authenticated,service_role;
create table if not exists private.device_approval_policy_audit (
 id uuid primary key default gen_random_uuid(),
 changed_at timestamptz not null default clock_timestamp(),
 database_actor text not null,
 auth_actor uuid,
 action text not null,
 metadata jsonb not null
);
revoke all on private.device_approval_policy_audit from public,anon,authenticated,service_role;
create or replace function private.audit_device_approval_policy() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 insert into private.device_approval_policy_audit(database_actor,auth_actor,action,metadata)
 values(session_user,auth.uid(),'DEVICE_POLICY_'||TG_OP,
 jsonb_build_object('before',case when TG_OP='INSERT' then null else to_jsonb(OLD) end,
 'after',case when TG_OP='DELETE' then null else to_jsonb(NEW) end));
 if TG_OP='DELETE' then return OLD;else return NEW;end if;
end;$$;
revoke all on function private.audit_device_approval_policy() from public,anon,authenticated,service_role;
drop trigger if exists device_approval_policy_audit on private.device_approval_policy;
create trigger device_approval_policy_audit after insert or update or delete on private.device_approval_policy
for each row execute function private.audit_device_approval_policy();
insert into private.device_approval_policy(singleton,reason) values(true,'Owner-provisioned active staff') on conflict do nothing;
-- Administrative auto-approval policy. No client/API role may manage this table.
create table if not exists private.device_approval_allowlist (
 user_id uuid not null,
 outlet_id uuid not null,
 role text not null check(role in ('MANAGER','CASHIER')),
 enabled boolean not null default true,
 reason text not null check(length(btrim(reason)) between 3 and 240),
 expires_at timestamptz,
 created_at timestamptz not null default now(),
 primary key(user_id,outlet_id,role),
 foreign key(user_id,outlet_id) references public.outlet_members(user_id,outlet_id)
);
revoke all on private.device_approval_allowlist from public,anon,authenticated,service_role;
-- SQL Editor has no Auth uid; retain database-session attribution separately.
create table if not exists private.device_approval_allowlist_audit (
 id uuid primary key default gen_random_uuid(),
 changed_at timestamptz not null default clock_timestamp(),
 database_actor text not null,
 auth_actor uuid,
 brand_id uuid not null,
 outlet_id uuid not null,
 target_user_id uuid not null,
 action text not null,
 metadata jsonb not null
);
revoke all on private.device_approval_allowlist_audit from public,anon,authenticated,service_role;
create or replace function private.device_approval_allowlisted(p_user uuid,p_outlet uuid,p_role text)
returns boolean language sql security definer set search_path='' as $$
 select p_role in ('OWNER','MANAGER','CASHIER') and private.outlet_role(p_outlet,p_user)=p_role
 and (coalesce((select auto_approve_registered from private.device_approval_policy where singleton),false) or exists(select 1 from private.device_approval_allowlist a where a.user_id=p_user
 and a.outlet_id=p_outlet and a.role=p_role and a.enabled
 and (a.expires_at is null or a.expires_at>clock_timestamp())));
$$;
revoke all on function private.device_approval_allowlisted(uuid,uuid,text) from public,anon,authenticated,service_role;
create or replace function private.audit_device_approval_allowlist() returns trigger
language plpgsql security definer set search_path='' as $$
declare a private.device_approval_allowlist;b uuid;
begin
 if TG_OP='DELETE' then a:=OLD;else a:=NEW;end if;
 select brand_id into b from public.outlets where id=a.outlet_id;
 insert into private.device_approval_allowlist_audit(database_actor,auth_actor,brand_id,outlet_id,action,target_user_id,metadata)
 values(session_user,auth.uid(),b,a.outlet_id,'DEVICE_ALLOWLIST_'||TG_OP,a.user_id,
 jsonb_build_object('reason',a.reason,'role',a.role,'enabled',a.enabled,'expires_at',a.expires_at,
 'before',case when TG_OP='INSERT' then null else to_jsonb(OLD) end,
 'after',case when TG_OP='DELETE' then null else to_jsonb(NEW) end));
 if TG_OP='DELETE' then return OLD;else return NEW;end if;
end;$$;
revoke all on function private.audit_device_approval_allowlist() from public,anon,authenticated,service_role;
drop trigger if exists device_approval_allowlist_audit on private.device_approval_allowlist;
create trigger device_approval_allowlist_audit after insert or update or delete on private.device_approval_allowlist
for each row execute function private.audit_device_approval_allowlist();

create or replace function public.enroll_device(p_device_id uuid,p_outlet_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
 declare v_user uuid:=auth.uid(); v_secret text; v_device public.devices; v_brand uuid; v_role text; v_allowlisted boolean;
 begin
 v_role:=private.outlet_role(p_outlet_id);
 if v_role is null then raise exception 'Forbidden' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_device_id::text,2));
 select * into v_device from public.devices where id=p_device_id for update;
 if found and (v_device.enrolled_by!=v_user or v_device.outlet_id!=p_outlet_id or v_device.revoked_at is not null) then raise exception 'Device denied' using errcode='42501'; end if;
 v_allowlisted:=private.device_approval_allowlisted(v_user,p_outlet_id,v_role);
 v_secret:=replace(gen_random_uuid()::text,'-','')||replace(gen_random_uuid()::text,'-','');
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_by,approved_role)
 values(p_device_id,p_outlet_id,v_user,encode(sha256(convert_to(v_secret,'UTF8')),'hex'),
 case when v_role='OWNER' or v_allowlisted then now() end,
 case when v_role='OWNER' or v_allowlisted then v_user end,
 case when v_role='OWNER' or v_allowlisted then v_role end)
 on conflict(id) do update set secret_hash=excluded.secret_hash,last_seen_at=now(),
 approved_at=excluded.approved_at,approved_by=excluded.approved_by,approved_role=excluded.approved_role;
 select brand_id into v_brand from public.outlets where id=p_outlet_id;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id) values(v_user,v_brand,p_outlet_id,'DEVICE_ENROLL',p_device_id);
 if v_allowlisted then
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(v_user,v_brand,p_outlet_id,'DEVICE_APPROVE',p_device_id,
 jsonb_build_object('source',case when (select auto_approve_registered from private.device_approval_policy where singleton) then 'REGISTERED_STAFF_POLICY' else 'ADMIN_ALLOWLIST' end,'role',v_role));
 end if;
 return jsonb_build_object('device_id',p_device_id,'device_secret',v_secret);
 end;
$$;
create or replace function public.issue_offline_lease(p_device_id uuid,p_device_secret text) returns jsonb language plpgsql security definer set search_path='' set timezone='UTC' as $$
 declare v_device public.devices; v_role text; v_brand public.brands; v_now timestamptz:=clock_timestamp(); v_expiry timestamptz;
 begin
 select * into v_device from public.devices where id=p_device_id and enrolled_by=auth.uid() and revoked_at is null for update;
 if not found or v_device.secret_hash!=encode(sha256(convert_to(coalesce(p_device_secret,''),'UTF8')),'hex') then raise exception 'Device denied' using errcode='42501'; end if;
 v_role:=private.outlet_role(v_device.outlet_id);
 if v_role is null then raise exception 'Forbidden' using errcode='42501'; end if;
 if (v_device.approved_at is null or v_device.approved_role is distinct from v_role)
 and private.device_approval_allowlisted(auth.uid(),v_device.outlet_id,v_role) then
 update public.devices set approved_at=v_now,approved_by=auth.uid(),approved_role=v_role
 where id=v_device.id returning * into v_device;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 select auth.uid(),o.brand_id,o.id,'DEVICE_APPROVE',v_device.id,
 jsonb_build_object('source',case when (select auto_approve_registered from private.device_approval_policy where singleton) then 'REGISTERED_STAFF_POLICY' else 'ADMIN_ALLOWLIST' end,'role',v_role)
 from public.outlets o where o.id=v_device.outlet_id;
 end if;
 if v_device.approved_at is null or v_device.approved_role is distinct from v_role then raise sqlstate 'PT423' using message='Device approval required'; end if;
 select b.* into v_brand from public.brands b join public.outlets o on o.brand_id=b.id where o.id=v_device.outlet_id;
 v_expiry:=case when v_brand.offline_access_enabled then v_now+interval '1 month' else v_now end;
 update public.devices set last_seen_at=v_now where id=v_device.id;
 return jsonb_build_object('user_id',auth.uid(),'brand_id',v_brand.id,'outlet_id',v_device.outlet_id,'device_id',v_device.id,'role',v_role,
 'issued_at_ms',floor(extract(epoch from v_now)*1000)::bigint,'expires_at_ms',floor(extract(epoch from v_expiry)*1000)::bigint);
 end;
$$;
revoke all on function public.enroll_device(uuid,uuid),public.issue_offline_lease(uuid,text) from public,anon,authenticated;
grant execute on function public.enroll_device(uuid,uuid),public.issue_offline_lease(uuid,text) to authenticated;
notify pgrst,'reload schema';
commit;
