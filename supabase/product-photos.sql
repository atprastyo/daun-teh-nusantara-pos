-- Optional initial product-photo setup. Run ONCE after schema.sql in development.
-- Requires Supabase Storage. No existing application table/receipt is rewritten.
begin;
create table public.product_photos (
 id uuid primary key, brand_id uuid not null, image_path text,
 active boolean not null default true, version bigint not null default 1 check(version>0),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 foreign key(id,brand_id) references public.products(id,brand_id),
 check ((active and image_path is not null) or (not active and image_path is null))
);
alter table public.product_photos enable row level security;
revoke all on public.product_photos from public,anon,authenticated;
grant select on public.product_photos to authenticated;
create policy photo_read on public.product_photos for select to authenticated using(private.read_brand(brand_id));
create trigger catalog_version before update or delete on public.product_photos for each row execute function private.catalog_version();
create trigger catalog_change after insert or update on public.product_photos for each row execute function private.catalog_change();
create table private.photo_operations(id uuid primary key,device_id uuid not null references public.devices(id),actor_id uuid not null references auth.users(id),request_hash text not null,result jsonb not null);
revoke all on private.photo_operations from public,anon,authenticated;

create function private.photo_path(p_path text,p_brand uuid,p_product uuid) returns boolean
language sql immutable set search_path='' as $$
 select coalesce(p_path ~ ('^'||p_brand::text||'/'||p_product::text||'/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg$'),false);
$$;
create function private.photo_manager(p_brand uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.devices d join public.outlets o on o.id=d.outlet_id
 where o.brand_id=p_brand and d.enrolled_by=auth.uid() and d.revoked_at is null and d.approved_at is not null
 and private.outlet_role(d.outlet_id) in ('OWNER','MANAGER') and d.approved_role=private.outlet_role(d.outlet_id));
$$;
create function private.photo_storage_access(p_name text,p_write boolean) returns boolean
language plpgsql stable security definer set search_path='' as $$
declare parts text[];b uuid;p uuid;
begin
 if p_name is null or p_name !~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\.jpg$' then return false;end if;
 begin parts:=string_to_array(p_name,'/');b:=parts[1]::uuid;p:=parts[2]::uuid;
 exception when invalid_text_representation then return false;end;
 if not private.photo_path(p_name,b,p) then return false;end if;
 if p_write then return private.photo_manager(b) and exists(select 1 from public.products where id=p and brand_id=b);end if;
 return private.read_brand(b) and exists(select 1 from public.product_photos where id=p and brand_id=b and active and image_path=p_name);
end;$$;
revoke all on function private.photo_path(text,uuid,uuid),private.photo_manager(uuid),private.photo_storage_access(text,boolean) from public,anon,authenticated;
grant execute on function private.photo_storage_access(text,boolean) to authenticated;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('product-photos','product-photos',false,204800,array['image/jpeg']);
create policy pos_photo_insert on storage.objects for insert to authenticated
with check(bucket_id='product-photos' and private.photo_storage_access(name,true));
create policy pos_photo_read on storage.objects for select to authenticated
using(bucket_id='product-photos' and private.photo_storage_access(name,false));
-- No UPDATE/DELETE policies: each replacement uses a fresh immutable object path.

create function public.set_product_photo(p_device_id uuid,p_device_secret text,p_epoch uuid,p_product_id uuid,p_expected_version bigint,p_path text,p_operation_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices;b uuid;v bigint;current_v bigint;hash text;result jsonb;previous private.photo_operations;seq bigint;
begin
 d:=private.approved_device(p_device_id,p_device_secret);
 if private.outlet_role(d.outlet_id) not in ('OWNER','MANAGER') then raise exception 'Photos forbidden' using errcode='42501';end if;
 if p_epoch is distinct from (select epoch from private.sync_generation where singleton) then raise sqlstate 'PT410' using message='Feed generation changed';end if;
 select brand_id into b from public.outlets where id=d.outlet_id;
 if p_operation_id is null or p_product_id is null or p_expected_version is null or p_expected_version<0 or p_expected_version=9223372036854775807 then raise sqlstate 'PT422' using message='Invalid photo command';end if;
 perform pg_advisory_xact_lock(hashtextextended(p_operation_id::text,5));
 hash:=encode(sha256(convert_to(jsonb_build_array(p_product_id,p_expected_version,p_path)::text,'UTF8')),'hex');
 select * into previous from private.photo_operations where id=p_operation_id;
 if found then
 if previous.device_id<>d.id or previous.actor_id<>auth.uid() or previous.request_hash<>hash then raise sqlstate 'PT409' using message='Photo operation conflict';end if;
 return previous.result;end if;
 -- Serialize a product's photo before locking dependency rows and allocating feed.
 perform pg_advisory_xact_lock(hashtextextended(p_product_id::text,6));
 perform 1 from public.products where id=p_product_id and brand_id=b for share;
 if not found then raise sqlstate 'PT422' using message='Product denied';end if;
 select version into current_v from public.product_photos where id=p_product_id for update;
 if coalesce(current_v,0)<>p_expected_version then raise sqlstate 'PT409' using message='Photo changed; reload';end if;
 if p_path is not null then
 if not private.photo_path(p_path,b,p_product_id) then raise sqlstate 'PT422' using message='Invalid photo path';end if;
 perform 1 from storage.objects where bucket_id='product-photos' and name=p_path
 and metadata->>'mimetype'='image/jpeg' and (metadata->>'size')::bigint between 1 and 204800 for share;
 if not found then raise sqlstate 'PT422' using message='Upload photo first';end if;
 end if;
 insert into public.product_photos(id,brand_id,image_path,active) values(p_product_id,b,p_path,p_path is not null)
 on conflict(id) do update set image_path=excluded.image_path,active=excluded.active returning version into v;
 select max(s.seq) into seq from public.sync_changes s where s.brand_id=b;
 result:=jsonb_build_object('operation_id',p_operation_id,'product_id',p_product_id,'version',v,'through_seq',seq,'feed_epoch',p_epoch);
 insert into private.photo_operations values(p_operation_id,d.id,auth.uid(),hash,result);
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(auth.uid(),b,d.outlet_id,'PRODUCT_PHOTO_SET',p_product_id,jsonb_build_object('version',v,'removed',p_path is null,'operation_id',p_operation_id));
 return result;
end;$$;
revoke all on function public.set_product_photo(uuid,text,uuid,uuid,bigint,text,uuid) from public,anon,authenticated;
grant execute on function public.set_product_photo(uuid,text,uuid,uuid,bigint,text,uuid) to authenticated;
notify pgrst,'reload schema';
commit;
