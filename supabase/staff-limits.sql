-- Initial per-outlet staff-limit setup. Apply once after staff-provisioning.sql.
-- Limits are configured only through trusted Supabase admin tools; no defaults.
begin;
create table public.outlet_staff_limits (
 outlet_id uuid primary key references public.outlets(id),
 manager_limit integer not null check(manager_limit>=0),
 cashier_limit integer not null check(cashier_limit>=0),
 revision bigint not null default 1 check(revision>0)
);
alter table public.outlet_staff_limits enable row level security;
revoke all on public.outlet_staff_limits from public,anon,authenticated;
grant select on public.outlet_staff_limits to authenticated;
grant select,insert,update,delete on public.outlet_staff_limits to service_role;
create policy staff_limit_read on public.outlet_staff_limits for select to authenticated
 using(private.outlet_role(outlet_id) in ('OWNER','MANAGER'));

create function private.staff_slot_count(p_outlet uuid,p_role text) returns bigint
language sql stable security definer set search_path='' as $$
 select count(*) from (
  select user_id from public.outlet_members where outlet_id=p_outlet and role=p_role
  union
  select target_id from private.staff_provision_operations where outlet_id=p_outlet and role=p_role and completed_at is null
 ) occupants;
$$;
create function private.guard_staff_slot(p_outlet uuid,p_role text,p_target uuid) returns void
language plpgsql security definer set search_path='' as $$
declare cap bigint; reserved boolean;
begin
 -- A real row update serializes different actors, reservations, membership writes and
 -- admin edits. Repeatable-read conflicts abort rather than using an old count.
 update public.outlet_staff_limits set revision=revision+1 where outlet_id=p_outlet
 returning case when p_role='MANAGER' then manager_limit else cashier_limit end into cap;
 select exists(select 1 from public.outlet_members where outlet_id=p_outlet and role=p_role and user_id=p_target)
 or exists(select 1 from private.staff_provision_operations where outlet_id=p_outlet and role=p_role and target_id=p_target and completed_at is null) into reserved;
 -- Converting a reserved slot to a membership adds no usage, including after lowering a cap.
 if reserved then return;end if;
 if cap is null then raise sqlstate 'PT409' using message='STAFF_LIMITS_UNCONFIGURED';end if;
 if private.staff_slot_count(p_outlet,p_role)>=cap then raise sqlstate 'PT409' using message='STAFF_LIMIT_REACHED';end if;
end;$$;
create function private.enforce_staff_slot() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if TG_TABLE_NAME='staff_provision_operations' then
  perform private.guard_staff_slot(NEW.outlet_id,NEW.role,NEW.target_id);
 else
  if TG_OP='UPDATE' then
   if NEW.outlet_id=OLD.outlet_id and NEW.role=OLD.role and NEW.user_id=OLD.user_id then return NEW;end if;
  end if;
  perform private.guard_staff_slot(NEW.outlet_id,NEW.role,NEW.user_id);
 end if;
 return NEW;
end;$$;
create trigger reserve_staff_slot before insert on private.staff_provision_operations
 for each row execute function private.enforce_staff_slot();
create trigger membership_staff_slot before insert or update on public.outlet_members
 for each row execute function private.enforce_staff_slot();

create function private.staff_capacity(p_outlet uuid) returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('manager_used',private.staff_slot_count(p_outlet,'MANAGER'),
 'cashier_used',private.staff_slot_count(p_outlet,'CASHIER'),
 'manager_limit',(select manager_limit from public.outlet_staff_limits where outlet_id=p_outlet),
 'cashier_limit',(select cashier_limit from public.outlet_staff_limits where outlet_id=p_outlet));
$$;
revoke all on function private.staff_slot_count(uuid,text),private.guard_staff_slot(uuid,text,uuid),private.enforce_staff_slot(),private.staff_capacity(uuid) from public,anon,authenticated,service_role;

-- Preserve existing live-device/role-scoped roster and add authoritative capacity.
create or replace function public.list_staff(p_device_id uuid,p_device_secret text) returns jsonb language plpgsql security definer set search_path='' as $$
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
 return result || jsonb_build_object('capacity',private.staff_capacity(d.outlet_id));
end;$$;

commit;
