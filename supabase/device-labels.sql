-- Optional device display metadata. Apply once after schema.sql; no existing device
-- credentials, approval, access versions or sales are changed.
begin;
create table public.device_labels (
 device_id uuid primary key references public.devices(id),
 manufacturer text not null default '' check(length(manufacturer)<=80 and manufacturer !~ '[[:cntrl:]]'),
 model text not null default '' check(length(model)<=80 and model !~ '[[:cntrl:]]'),
 updated_at timestamptz not null default now()
);
alter table public.device_labels enable row level security;
revoke all on public.device_labels from public,anon,authenticated;
create function public.set_device_label(p_device_id uuid,p_device_secret text,p_manufacturer text,p_model text) returns void
language plpgsql security definer set search_path='' as $$
declare d public.devices;m text:=btrim(coalesce(p_manufacturer,''));n text:=btrim(coalesce(p_model,''));
begin
 select * into d from public.devices where id=p_device_id and enrolled_by=auth.uid() and revoked_at is null for share;
 if not found or d.secret_hash is distinct from encode(sha256(convert_to(coalesce(p_device_secret,''),'UTF8')),'hex') or private.outlet_role(d.outlet_id) is null then raise exception 'Device denied' using errcode='42501';end if;
 if length(m)>80 or length(n)>80 or m ~ '[[:cntrl:]]' or n ~ '[[:cntrl:]]' then raise sqlstate 'PT422' using message='Invalid device label';end if;
 insert into public.device_labels(device_id,manufacturer,model) values(d.id,m,n)
 on conflict(device_id) do update set manufacturer=excluded.manufacturer,model=excluded.model,updated_at=now();
end;$$;
create function public.get_device_labels(p_outlet_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare r text:=private.outlet_role(p_outlet_id);
begin
 if r is null or r not in ('OWNER','MANAGER') then raise exception 'Forbidden' using errcode='42501';end if;
 return coalesce((select jsonb_agg(jsonb_build_object('device_id',d.id,'device_name',nullif(btrim(case
  when l.manufacturer='' then l.model
  when l.model='' then l.manufacturer
  when left(lower(l.model),length(l.manufacturer))=lower(l.manufacturer) then l.model
  else l.manufacturer||' '||l.model end),'')))
 from public.devices d join public.device_labels l on l.device_id=d.id where d.outlet_id=p_outlet_id
 and (r='OWNER' or private.outlet_role(d.outlet_id,d.enrolled_by)='CASHIER')),'[]'::jsonb);
end;$$;
revoke all on function public.set_device_label(uuid,text,text,text),public.get_device_labels(uuid) from public,anon,authenticated;
grant execute on function public.set_device_label(uuid,text,text,text),public.get_device_labels(uuid) to authenticated;
notify pgrst,'reload schema';
commit;
