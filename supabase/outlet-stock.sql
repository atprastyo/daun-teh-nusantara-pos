-- Optional manual finished-product counts. Apply once after schema.sql; preserves sales/catalog.
begin;
create table public.outlet_stock_balances (
 outlet_id uuid not null references public.outlets(id),
 product_id uuid not null references public.products(id),
 quantity bigint not null check(quantity between 0 and 1000000000),
 revision bigint not null check(revision>0),
 updated_at timestamptz not null default now(),
 primary key(outlet_id,product_id)
);
create table public.outlet_stock_movements (
 seq bigint generated always as identity primary key,
 operation_id uuid not null unique,
 outlet_id uuid not null references public.outlets(id),
 product_id uuid not null references public.products(id),
 product_name text not null,
 previous_quantity bigint not null check(previous_quantity between 0 and 1000000000),
 quantity bigint not null check(quantity between 0 and 1000000000),
 delta bigint not null,
 revision bigint not null check(revision>0),
 actor_id uuid not null references auth.users(id),
 actor_name text not null,
 device_id uuid not null references public.devices(id),
 reason text not null check(length(btrim(reason)) between 3 and 240),
 created_at timestamptz not null default now(),
 check(delta=quantity-previous_quantity),
 unique(outlet_id,product_id,revision)
);
create index outlet_stock_history on public.outlet_stock_movements(outlet_id,seq desc);
create index outlet_stock_product_history on public.outlet_stock_movements(outlet_id,product_id,seq desc);
create table private.stock_operations (
 id uuid primary key,
 actor_id uuid not null,
 device_id uuid not null,
 request jsonb not null,
 result jsonb not null
);
alter table public.outlet_stock_balances enable row level security;
alter table public.outlet_stock_movements enable row level security;
revoke all on public.outlet_stock_balances,public.outlet_stock_movements from public,anon,authenticated,service_role;
revoke all on private.stock_operations from public,anon,authenticated,service_role;
-- Reads use an approved device RPC, not an unproven direct REST table request.
create function private.stock_immutable() returns trigger language plpgsql set search_path='' as $$
begin raise exception 'Stock history is append only' using errcode='42501'; end;$$;
create trigger stock_immutable before update or delete on public.outlet_stock_movements for each row execute function private.stock_immutable();
revoke all on function private.stock_immutable() from public,anon,authenticated,service_role;

create function public.get_outlet_stock(p_device_id uuid,p_device_secret text,p_query text default '',p_offset integer default 0,p_product_id uuid default null,p_before bigint default null)
returns jsonb language plpgsql security definer set search_path='' set timezone='UTC' as $$
declare d public.devices;b uuid;items jsonb;history jsonb;more boolean;epoch uuid;
begin
 d:=private.approved_device(p_device_id,p_device_secret);
 if private.outlet_role(d.outlet_id) not in ('OWNER','MANAGER') then raise exception 'Forbidden' using errcode='42501';end if;
 if p_query is null or length(p_query)>120 or p_offset is null or p_offset<0 or p_offset>1000000 or (p_before is not null and p_before<=0) then raise sqlstate 'PT422' using message='Invalid stock query';end if;
 perform pg_advisory_xact_lock_shared(hashtextextended(d.outlet_id::text,73));
 select brand_id into b from public.outlets where id=d.outlet_id;
 if p_product_id is not null and not exists(select 1 from public.products where id=p_product_id and brand_id=b) then raise exception 'Forbidden' using errcode='42501';end if;
 select g.epoch into epoch from private.sync_generation g where singleton;
 select coalesce(jsonb_agg(x.row order by x.name,x.id),'[]'::jsonb) into items from (
 select p.id,p.name,jsonb_build_object('product_id',p.id,'name',p.name,'active',p.active,
 'quantity',s.quantity,'revision',coalesce(s.revision,0),'updated_at',s.updated_at) row
 from public.products p left join public.outlet_stock_balances s on s.product_id=p.id and s.outlet_id=d.outlet_id
 where p.brand_id=b and (p.active or s.product_id is not null) and strpos(lower(p.name),lower(btrim(p_query)))>0
 order by p.name,p.id limit 100 offset p_offset) x;
 select exists(select 1 from public.products p left join public.outlet_stock_balances s on s.product_id=p.id and s.outlet_id=d.outlet_id
 where p.brand_id=b and (p.active or s.product_id is not null) and strpos(lower(p.name),lower(btrim(p_query)))>0
 order by p.name,p.id offset p_offset+100 limit 1) into more;
 select coalesce(jsonb_agg(to_jsonb(x) order by x.seq desc),'[]'::jsonb) into history from (
 select seq,operation_id,product_id,product_name,previous_quantity,quantity,delta,revision,actor_id,actor_name,reason,created_at
 from public.outlet_stock_movements where outlet_id=d.outlet_id and (p_product_id is null or product_id=p_product_id)
 and (p_before is null or seq<p_before) order by seq desc limit 50) x;
 return jsonb_build_object('outlet_id',d.outlet_id,'epoch',epoch,'captured_at',clock_timestamp(),'products',items,'has_more',more,'history',history);
end;$$;

create function public.set_outlet_stock(p_device_id uuid,p_device_secret text,p_operation_id uuid,p_epoch uuid,p_product_id uuid,p_quantity bigint,p_expected_revision bigint,p_reason text)
returns jsonb language plpgsql security definer set search_path='' set timezone='UTC' as $$
declare d public.devices;b uuid;p public.products;s public.outlet_stock_balances;o private.stock_operations;
 req jsonb;ack jsonb;old_quantity bigint;old_revision bigint;movement bigint;actor text;
begin
 d:=private.approved_device(p_device_id,p_device_secret);
 if private.outlet_role(d.outlet_id) not in ('OWNER','MANAGER') then raise exception 'Forbidden' using errcode='42501';end if;
 if p_epoch is distinct from (select g.epoch from private.sync_generation g where singleton) then raise sqlstate 'PT410' using message='Stock generation changed';end if;
 if p_operation_id is null or p_product_id is null or p_quantity is null or p_quantity not between 0 and 1000000000 or p_expected_revision is null or p_expected_revision<0 or p_reason is null or length(btrim(p_reason)) not between 3 and 240 then raise sqlstate 'PT422' using message='Invalid stock count';end if;
 req:=jsonb_build_object('outlet_id',d.outlet_id,'epoch',p_epoch,'product_id',p_product_id,'quantity',p_quantity,'expected_revision',p_expected_revision,'reason',btrim(p_reason));
 perform pg_advisory_xact_lock(hashtextextended(p_operation_id::text,71));
 select * into o from private.stock_operations where id=p_operation_id;
 if found then
 if o.actor_id<>auth.uid() or o.device_id<>d.id or o.request<>req then raise sqlstate 'PT409' using message='Stock operation conflict';end if;
 return o.result;
 end if;
 perform pg_advisory_xact_lock(hashtextextended(d.outlet_id::text,73));
 select brand_id into b from public.outlets where id=d.outlet_id;
 select * into p from public.products where id=p_product_id and brand_id=b and active for share;
 if not found then raise sqlstate 'PT422' using message='Product unavailable';end if;
 perform pg_advisory_xact_lock(hashtextextended(d.outlet_id::text||p.id::text,72));
 select * into s from public.outlet_stock_balances where outlet_id=d.outlet_id and product_id=p.id for update;
 old_quantity:=coalesce(s.quantity,0);old_revision:=coalesce(s.revision,0);
 if old_revision<>p_expected_revision then raise sqlstate 'PT409' using message='Stock changed; refresh and recount';end if;
 if old_revision>0 and old_quantity=p_quantity then raise sqlstate 'PT422' using message='Count unchanged';end if;
 select full_name into actor from public.user_profiles where id=auth.uid();
 insert into public.outlet_stock_movements(operation_id,outlet_id,product_id,product_name,previous_quantity,quantity,delta,revision,actor_id,actor_name,device_id,reason)
 values(p_operation_id,d.outlet_id,p.id,p.name,old_quantity,p_quantity,p_quantity-old_quantity,old_revision+1,auth.uid(),coalesce(actor,''),d.id,btrim(p_reason)) returning seq into movement;
 insert into public.outlet_stock_balances(outlet_id,product_id,quantity,revision) values(d.outlet_id,p.id,p_quantity,old_revision+1)
 on conflict(outlet_id,product_id) do update set quantity=excluded.quantity,revision=excluded.revision,updated_at=now();
 ack:=jsonb_build_object('operation_id',p_operation_id,'outlet_id',d.outlet_id,'product_id',p.id,'quantity',p_quantity,'revision',old_revision+1,'movement_seq',movement,'epoch',p_epoch);
 insert into private.stock_operations values(p_operation_id,auth.uid(),d.id,req,ack);
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(auth.uid(),b,d.outlet_id,'STOCK_COUNT',p_operation_id,jsonb_build_object('product_id',p.id,'previous_quantity',old_quantity,'quantity',p_quantity,'reason',btrim(p_reason)));
 return ack;
end;$$;
revoke all on function public.get_outlet_stock(uuid,text,text,integer,uuid,bigint),public.set_outlet_stock(uuid,text,uuid,uuid,uuid,bigint,bigint,text) from public,anon,authenticated,service_role;
grant execute on function public.get_outlet_stock(uuid,text,text,integer,uuid,bigint),public.set_outlet_stock(uuid,text,uuid,uuid,uuid,bigint,bigint,text) to authenticated;
notify pgrst,'reload schema';
commit;
