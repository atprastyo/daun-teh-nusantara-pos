-- STOCK-02: apply ONCE after schema.sql and outlet-stock.sql, with all writers paused.
-- Preserves receipts/counts. Existing accepted operations are not backfilled.
begin;
alter table public.sync_changes drop constraint sync_changes_entity_type_check;
alter table public.sync_changes add check(entity_type in ('CATALOG','SALE','STOCK','STOCK_DELIVERY'));
alter table public.outlet_stock_balances drop constraint outlet_stock_balances_quantity_check;
alter table public.outlet_stock_balances add check(quantity between -1000000000000 and 1000000000);
alter table public.outlet_stock_balances add column count_generation uuid not null default gen_random_uuid();
alter table public.outlet_stock_balances add column last_sale_operation uuid;
alter table public.outlet_stock_movements drop constraint outlet_stock_movements_quantity_check;
alter table public.outlet_stock_movements drop constraint outlet_stock_movements_previous_quantity_check;
alter table public.outlet_stock_movements add check(quantity between -1000000000000 and 1000000000);
alter table public.outlet_stock_movements add check(previous_quantity between -1000000000000 and 1000000000);
create table private.stock_sale_operations (
 id uuid primary key, device_id uuid not null, user_id uuid not null, outlet_id uuid not null,
 request_hash text not null, intent jsonb, result jsonb not null
);
create table private.stock_sale_lines (
 operation_id uuid not null references private.stock_sale_operations(id) deferrable initially deferred,
 product_id uuid not null references public.products(id), outlet_id uuid not null references public.outlets(id),
 device_id uuid not null, user_id uuid not null, product_name text not null,
 quantity bigint not null check(quantity between 1 and 99900),
 state text not null check(state in ('APPLIED','REVIEW','SKIPPED')),
 issue text, primary key(operation_id,product_id)
);
revoke all on private.stock_sale_operations,private.stock_sale_lines from public,anon,authenticated,service_role;

create function private.stock_event(p_outlet uuid,p_product uuid,p_operation uuid,p_status text,p_user uuid default null)
returns void language plpgsql security definer set search_path='' as $$
declare b uuid; v jsonb;
begin
 select brand_id into b from public.outlets where id=p_outlet;
 perform pg_advisory_xact_lock(58301924001);
 if p_status='BALANCE' then
 select jsonb_build_object('schema_version',1,'outlet_id',s.outlet_id,'product_id',s.product_id,'quantity',s.quantity,
 'revision',s.revision,'generation',s.count_generation,'operation_id',s.last_sale_operation) into v
 from public.outlet_stock_balances s where outlet_id=p_outlet and product_id=p_product;
 insert into public.sync_changes(brand_id,outlet_id,entity_type,entity_id,action,payload)
 values(b,p_outlet,'STOCK',p_product,'UPSERT',v);
 else
 insert into public.sync_changes(brand_id,outlet_id,entity_type,entity_id,action,cashier_user_id,payload)
 values(b,p_outlet,'STOCK_DELIVERY',p_operation,'UPSERT',p_user,jsonb_build_object('schema_version',1,'outlet_id',p_outlet,
 'operation_id',p_operation,'product_id',p_product,'status',p_status));
 end if;
end;$$;
create function private.stock_balance_event() returns trigger language plpgsql security definer set search_path='' as $$
begin perform private.stock_event(NEW.outlet_id,NEW.product_id,null,'BALANCE');return NEW;end;$$;
create trigger stock_balance_event after insert or update on public.outlet_stock_balances for each row execute function private.stock_balance_event();
revoke all on function private.stock_event(uuid,uuid,uuid,text,uuid),private.stock_balance_event() from public,anon,authenticated,service_role;

-- Rename rather than trusting a client-settable session flag to bypass the stock wrapper.
alter function public.submit_sale(text,text) set schema private;
alter function private.submit_sale(text,text) rename to submit_sale_financial;
revoke all on function private.submit_sale_financial(text,text) from public,anon,authenticated,service_role;

create function private.apply_stock_sale(p_operation uuid,p_product uuid,p_actor uuid,p_device uuid,p_outlet uuid,p_qty bigint,p_name text,p_reason text)
returns void language plpgsql security definer set search_path='' as $$
declare s public.outlet_stock_balances;actor text;
begin
 select * into s from public.outlet_stock_balances where outlet_id=p_outlet and product_id=p_product for update;
 if not found then raise sqlstate 'PT422' using message='Record an opening count first';end if;
 select full_name into actor from public.user_profiles where id=p_actor;
 insert into public.outlet_stock_movements(operation_id,outlet_id,product_id,product_name,previous_quantity,quantity,delta,revision,actor_id,actor_name,device_id,reason)
 values(md5('sale-stock:'||p_operation::text||':'||p_product::text)::uuid,p_outlet,p_product,p_name,s.quantity,s.quantity-p_qty,-p_qty,s.revision+1,p_actor,coalesce(actor,''),p_device,p_reason);
 update public.outlet_stock_balances set quantity=quantity-p_qty,revision=revision+1,updated_at=now(),last_sale_operation=p_operation
 where outlet_id=p_outlet and product_id=p_product;
end;$$;
revoke all on function private.apply_stock_sale(uuid,uuid,uuid,uuid,uuid,bigint,text,text) from public,anon,authenticated,service_role;

create function private.stock_receipt_valid(j jsonb) returns boolean language plpgsql set search_path='' as $$
declare l jsonb;m jsonb;qty bigint;unit bigint;total bigint:=0;p jsonb;
begin
 if jsonb_typeof(j->'items')<>'array' or jsonb_array_length(j->'items') not between 1 and 100 then return false;end if;
 if (select count(distinct value->>'id') from jsonb_array_elements(j->'items'))<>jsonb_array_length(j->'items') then return false;end if;
 for l in select value from jsonb_array_elements(j->'items') loop
 perform (l->>'id')::uuid;
 qty:=(l->>'quantity')::bigint;unit:=(l->>'unit_base_price_idr')::bigint;
 if qty is null or qty not between 1 and 999 or l->'quantity' is distinct from to_jsonb(qty) or unit is null or unit<0 or jsonb_typeof(l->'modifiers') is distinct from 'array' then return false;end if;
 for m in select value from jsonb_array_elements(l->'modifiers') loop
 if (m->>'price_delta_idr')::bigint is null or (m->>'price_delta_idr')::bigint<0 then return false;end if;
 unit:=unit+(m->>'price_delta_idr')::bigint;
 end loop;
 if l->'line_total_idr' is distinct from to_jsonb(unit*qty) then return false;end if;
 total:=total+unit*qty;
 end loop;
 if j->'subtotal_idr' is distinct from to_jsonb(total) or j->'total_idr' is distinct from to_jsonb(total) or j->'discount_idr' is distinct from '0'::jsonb then return false;end if;
 p:=j->'payment';if p->'amount_idr' is distinct from to_jsonb(total) then return false;end if;
 if p->>'method'='CASH' then return (p->>'tendered_idr')::bigint>=total and p->'change_idr'=to_jsonb((p->>'tendered_idr')::bigint-total) and p->>'verification'='CASH_RECEIVED';
 elsif p->>'method'='QRIS_MANUAL' then return p->>'verification'='CASHIER_CONFIRMED_MANUALLY' and p->'tendered_idr'='null'::jsonb and p->'change_idr'='null'::jsonb;end if;
 return false;
 exception when data_exception then return false;
end;$$;
revoke all on function private.stock_receipt_valid(jsonb) from public,anon,authenticated,service_role;

create function public.submit_sale_stock(p_payload text,p_device_secret text,p_stock text default null)
returns jsonb language plpgsql security definer set search_path='' set timezone='UTC' as $$
declare j jsonb:=p_payload::jsonb;intent jsonb:=p_stock::jsonb;d public.devices;o private.stock_sale_operations;
 ack jsonb;h text;op uuid;r record;bal public.outlet_stock_balances;line jsonb;problem text;preexisting boolean;epoch uuid;
begin
 if octet_length(p_payload)>262144 or octet_length(p_stock)>32768 then raise exception 'Oversized stock submission' using errcode='22023';end if;
 -- Same device lock order as financial admission, before the outlet lock.
 perform 1 from public.devices where id=(j->>'device_id')::uuid and enrolled_by=auth.uid() for update;
 d:=private.approved_device((j->>'device_id')::uuid,p_device_secret);
 if (j->>'outlet_id')::uuid is distinct from d.outlet_id or (j->>'cashier_user_id')::uuid is distinct from auth.uid() or
 (j->>'brand_id')::uuid is distinct from (select brand_id from public.outlets where id=d.outlet_id) then raise exception 'Scope denied' using errcode='42501';end if;
 op:=(j->>'operation_id')::uuid;if op is null then raise exception 'Missing operation' using errcode='22023';end if;
 h:=encode(sha256(convert_to(j::text,'UTF8')),'hex');
 perform pg_advisory_xact_lock(hashtextextended(op::text,1));
 select * into o from private.stock_sale_operations where id=op;
 if found then
 if o.device_id<>d.id or o.user_id<>auth.uid() or o.request_hash<>h or o.intent is distinct from intent then raise sqlstate 'PT409' using message='Stock operation conflict';end if;
 return o.result||jsonb_build_object('payload_hash',encode(sha256(convert_to(p_payload,'UTF8')),'hex'));
 end if;
 select exists(select 1 from public.sync_operations where id=op) into preexisting;
 select g.epoch into epoch from private.sync_generation g where singleton;
 if intent is not null then
 if intent->>'epoch' is distinct from epoch::text then raise sqlstate 'PT410' using message='Stock generation changed';end if;
 if jsonb_typeof(intent->'products') is distinct from 'array' or jsonb_array_length(intent->'products') not between 1 and 100 then raise exception 'Invalid stock intent' using errcode='22023';end if;
 if (select count(distinct value->>'product_id') from jsonb_array_elements(intent->'products'))<>jsonb_array_length(intent->'products') or
 exists(select 1 from jsonb_array_elements(intent->'products') x where not exists(select 1 from jsonb_array_elements(j->'items') y where y->>'product_id'=x->>'product_id')) then raise exception 'Invalid stock products' using errcode='22023';end if;
 end if;
 perform pg_advisory_xact_lock(hashtextextended(d.outlet_id::text,73));
 ack:=private.submit_sale_financial(p_payload,p_device_secret);
 -- No retroactive deductions for operations already accepted before this deployment.
 if preexisting then return ack||jsonb_build_object('stock_operation_id',op,'stock_status','PRE_CUTOVER');end if;
 if ack->>'status'='ACCEPTED' or ack->>'code'='CATALOG_CHANGED' or (ack->>'code'='INVALID_SALE' and private.stock_receipt_valid(j)) then
 if private.stock_receipt_valid(j) is distinct from true then raise exception 'Invalid paid receipt' using errcode='22023';end if;
 for r in select (value->>'product_id')::uuid product_id,sum((value->>'quantity')::bigint)::bigint qty,min(value->>'product_name') product_name
 from jsonb_array_elements(j->'items') group by value->>'product_id' order by value->>'product_id' loop
 -- Catalog mismatch must not turn arbitrary malformed/replayed data into stock consumption.
 if r.qty not between 1 and 99900 or not exists(select 1 from public.products p join public.outlets x on x.brand_id=p.brand_id where p.id=r.product_id and x.id=d.outlet_id) then raise exception 'Invalid consumption' using errcode='22023';end if;
 if exists(select 1 from jsonb_array_elements(j->'items') x where x->>'product_id'=r.product_id::text and
 ((x->>'quantity')::bigint not between 1 and 999 or x->'quantity' is distinct from to_jsonb((x->>'quantity')::bigint))) then raise exception 'Invalid quantity' using errcode='22023';end if;
 select * into bal from public.outlet_stock_balances where outlet_id=d.outlet_id and product_id=r.product_id for update;
 select value into line from jsonb_array_elements(coalesce(intent->'products','[]'::jsonb)) where value->>'product_id'=r.product_id::text;
 problem:=case when ack->>'code'='INVALID_SALE' then 'FINANCIAL_REVIEW' end;
 if intent is null then problem:='LEGACY_INTENT';
 elsif line is null or line->'quantity' is distinct from to_jsonb(r.qty) then raise exception 'Consumption does not match receipt' using errcode='22023';
 elsif bal.product_id is null or line->>'generation' is null then problem:='OPENING_REQUIRED';
 elsif line->>'generation' is distinct from bal.count_generation::text then problem:='COUNT_CHANGED';
 elsif bal.quantity-r.qty < -1000000000000 then problem:='BALANCE_LIMIT';end if;
 insert into private.stock_sale_lines values(op,r.product_id,d.outlet_id,d.id,auth.uid(),r.product_name,r.qty,case when problem is null then 'APPLIED' else 'REVIEW' end,problem);
 if problem is null then
 perform private.apply_stock_sale(op,r.product_id,auth.uid(),d.id,d.outlet_id,r.qty,r.product_name,
 case when ack->>'code'='CATALOG_CHANGED' then 'Penjualan otomatis; harga perlu ditinjau' else 'Penjualan otomatis' end);
 else perform private.stock_event(d.outlet_id,r.product_id,op,'REVIEW',auth.uid());end if;
 end loop;
 ack:=ack||jsonb_build_object('stock_operation_id',op,'stock_status',case when exists(select 1 from private.stock_sale_lines where operation_id=op and state='REVIEW') then 'REVIEW' else 'APPLIED' end);
 else ack:=ack||jsonb_build_object('stock_operation_id',op,'stock_status','NOT_APPLIED');end if;
 insert into private.stock_sale_operations values(op,d.id,auth.uid(),d.outlet_id,h,intent,ack);
 return ack;
end;$$;
create function public.submit_sale(p_payload text,p_device_secret text) returns jsonb language sql security definer set search_path='' as $$
 select public.submit_sale_stock(p_payload,p_device_secret,null);
$$;
revoke all on function public.submit_sale_stock(text,text,text),public.submit_sale(text,text) from public,anon,authenticated,service_role;
grant execute on function public.submit_sale_stock(text,text,text),public.submit_sale(text,text) to authenticated;

create table private.stock_feed_devices(device_id uuid primary key references public.devices(id));
revoke all on private.stock_feed_devices from public,anon,authenticated,service_role;
create function public.pull_changes_stock(p_device_id uuid,p_device_secret text,p_after_seq bigint,p_role text,p_limit integer default 100,p_epoch uuid default null)
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
 -- Existing clients may have advanced the old cursor past stock rows. Seed current
 -- balances once per enrolled device on its first stock-capable pull.
 if not exists(select 1 from private.stock_feed_devices where device_id=v_device.id) then
 perform pg_advisory_xact_lock_shared(hashtextextended(v_device.outlet_id::text,73));
 perform pg_advisory_xact_lock(58301924001);
 insert into private.stock_feed_devices values(v_device.id) on conflict do nothing;
 if found then
 perform private.stock_event(s.outlet_id,s.product_id,null,'BALANCE') from public.outlet_stock_balances s where s.outlet_id=v_device.outlet_id order by s.product_id;
 end if;end if;
 -- Shared lock gives a stable committed high-water boundary; all feed writers use the exclusive lock.
 perform pg_advisory_xact_lock_shared(58301924001);
 select epoch into v_epoch from private.sync_generation where singleton;
 if (p_after_seq>0 or p_epoch is not null) and p_epoch is distinct from v_epoch then raise sqlstate 'PT410' using message='Feed generation changed; reviewed recovery required'; end if;
 select coalesce(max(seq),0) into v_high from public.sync_changes;
 if p_after_seq>v_high then raise sqlstate 'PT409' using message='Cursor ahead; explicit reset required'; end if;
 for v_change in select * from public.sync_changes c where c.seq>p_after_seq and c.seq<=v_high and c.brand_id=v_brand and
 ((c.entity_type='STOCK' and c.outlet_id=v_device.outlet_id) or (c.entity_type='STOCK_DELIVERY' and c.outlet_id=v_device.outlet_id and (v_role in ('OWNER','MANAGER') or c.cashier_user_id=auth.uid())) or (c.entity_type='CATALOG' and c.outlet_id is null) or (c.entity_type='SALE' and c.outlet_id=v_device.outlet_id and
 (v_role in ('OWNER','MANAGER') or c.cashier_user_id=auth.uid()))) order by c.seq limit p_limit+1 loop
 v_row:=jsonb_build_object('seq',v_change.seq,'entity_type',v_change.entity_type,'entity_id',v_change.entity_id,'action',v_change.action,'payload',v_change.payload);
 if v_count>=p_limit or (v_count>0 and v_size+octet_length(v_row::text)>524288) then exit; end if;
 if octet_length(v_row::text)>524288 then raise exception 'Unsupported oversized event' using errcode='22023'; end if;
 v_rows:=v_rows||jsonb_build_array(v_row); v_next:=v_change.seq; v_count:=v_count+1; v_size:=v_size+octet_length(v_row::text);
 end loop;
 select exists(select 1 from public.sync_changes c where c.seq>v_next and c.seq<=v_high and c.brand_id=v_brand and
 ((c.entity_type='STOCK' and c.outlet_id=v_device.outlet_id) or (c.entity_type='STOCK_DELIVERY' and c.outlet_id=v_device.outlet_id and (v_role in ('OWNER','MANAGER') or c.cashier_user_id=auth.uid())) or (c.entity_type='CATALOG' and c.outlet_id is null) or (c.entity_type='SALE' and c.outlet_id=v_device.outlet_id and
 (v_role in ('OWNER','MANAGER') or c.cashier_user_id=auth.uid())))) into v_more;
 if not v_more then v_next:=v_high; end if;
 return jsonb_build_object('stock_enabled',true,'schema_version',1,'brand_id',v_brand,'outlet_id',v_device.outlet_id,'device_id',v_device.id,
 'user_id',auth.uid(),'role',v_role,'after_seq',p_after_seq,'next_seq',v_next,'has_more',v_more,'changes',v_rows,'feed_epoch',v_epoch);
end;
$$;
create or replace function public.set_outlet_stock(p_device_id uuid,p_device_secret text,p_operation_id uuid,p_epoch uuid,p_product_id uuid,p_quantity bigint,p_expected_revision bigint,p_reason text)
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
 -- A physical recount may confirm an unchanged quantity and still advance its generation.
 select full_name into actor from public.user_profiles where id=auth.uid();
 insert into public.outlet_stock_movements(operation_id,outlet_id,product_id,product_name,previous_quantity,quantity,delta,revision,actor_id,actor_name,device_id,reason)
 values(p_operation_id,d.outlet_id,p.id,p.name,old_quantity,p_quantity,p_quantity-old_quantity,old_revision+1,auth.uid(),coalesce(actor,''),d.id,btrim(p_reason)) returning seq into movement;
 insert into public.outlet_stock_balances(outlet_id,product_id,quantity,revision,count_generation,last_sale_operation) values(d.outlet_id,p.id,p_quantity,old_revision+1,gen_random_uuid(),null)
 on conflict(outlet_id,product_id) do update set quantity=excluded.quantity,revision=excluded.revision,updated_at=now(),count_generation=excluded.count_generation,last_sale_operation=null;
 ack:=jsonb_build_object('operation_id',p_operation_id,'outlet_id',d.outlet_id,'product_id',p.id,'quantity',p_quantity,'revision',old_revision+1,'movement_seq',movement,'epoch',p_epoch,'count_generation',(select count_generation from public.outlet_stock_balances where outlet_id=d.outlet_id and product_id=p.id));
 insert into private.stock_operations values(p_operation_id,auth.uid(),d.id,req,ack);
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata)
 values(auth.uid(),b,d.outlet_id,'STOCK_COUNT',p_operation_id,jsonb_build_object('product_id',p.id,'previous_quantity',old_quantity,'quantity',p_quantity,'reason',btrim(p_reason)));
 return ack;
end;$$;

revoke all on function public.pull_changes_stock(uuid,text,bigint,text,integer,uuid) from public,anon,authenticated,service_role;
grant execute on function public.pull_changes_stock(uuid,text,bigint,text,integer,uuid) to authenticated;
do $$declare r record;begin
 for r in select outlet_id,product_id from public.outlet_stock_balances order by outlet_id,product_id loop
 perform private.stock_event(r.outlet_id,r.product_id,null,'BALANCE');end loop;
end;$$;

alter function public.get_outlet_stock(uuid,text,text,integer,uuid,bigint) set schema private;
alter function private.get_outlet_stock(uuid,text,text,integer,uuid,bigint) rename to get_outlet_stock_manual;
revoke all on function private.get_outlet_stock_manual(uuid,text,text,integer,uuid,bigint) from public,anon,authenticated,service_role;
create function public.get_outlet_stock(p_device_id uuid,p_device_secret text,p_query text default '',p_offset integer default 0,p_product_id uuid default null,p_before bigint default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices;j jsonb;reviews jsonb;
begin
 j:=private.get_outlet_stock_manual(p_device_id,p_device_secret,p_query,p_offset,p_product_id,p_before);
 d:=private.approved_device(p_device_id,p_device_secret);
 select coalesce(jsonb_agg(to_jsonb(r)),'[]'::jsonb) into reviews from (
 select l.operation_id,l.product_id,l.product_name,l.quantity,l.issue,s.quantity balance,coalesce(s.revision,0) revision
 from private.stock_sale_lines l left join public.outlet_stock_balances s on s.outlet_id=l.outlet_id and s.product_id=l.product_id
 where l.outlet_id=d.outlet_id and l.state='REVIEW' order by l.operation_id,l.product_id limit 50) r;
 return j||jsonb_build_object('automatic',true,'reviews',reviews);
end;$$;

create function public.resolve_stock_sale(p_device_id uuid,p_device_secret text,p_operation_id uuid,p_epoch uuid,p_sale_operation_id uuid,p_product_id uuid,p_deduct boolean,p_expected_revision bigint,p_reason text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare d public.devices;l private.stock_sale_lines;s public.outlet_stock_balances;o private.stock_operations;req jsonb;ack jsonb;b uuid;
begin
 d:=private.approved_device(p_device_id,p_device_secret);
 if private.outlet_role(d.outlet_id) not in ('OWNER','MANAGER') then raise exception 'Forbidden' using errcode='42501';end if;
 if p_epoch is distinct from (select epoch from private.sync_generation where singleton) then raise sqlstate 'PT410' using message='Generation changed';end if;
 if p_operation_id is null or p_deduct is null or p_expected_revision is null or p_expected_revision<0 or p_reason is null or length(btrim(p_reason)) not between 3 and 240 then raise sqlstate 'PT422' using message='Invalid resolution';end if;
 req:=jsonb_build_object('kind','STOCK_RESOLVE','sale_operation',p_sale_operation_id,'product',p_product_id,'deduct',p_deduct,'revision',p_expected_revision,'reason',btrim(p_reason),'epoch',p_epoch);
 perform pg_advisory_xact_lock(hashtextextended(p_operation_id::text,71));
 select * into o from private.stock_operations where id=p_operation_id;
 if found then
 if o.actor_id<>auth.uid() or o.device_id<>d.id or o.request<>req then raise sqlstate 'PT409' using message='Operation conflict';end if;
 return o.result;end if;
 perform pg_advisory_xact_lock(hashtextextended(d.outlet_id::text,73));
 select * into l from private.stock_sale_lines where operation_id=p_sale_operation_id and product_id=p_product_id and outlet_id=d.outlet_id for update;
 if not found or l.state<>'REVIEW' then raise sqlstate 'PT409' using message='Review changed';end if;
 select * into s from public.outlet_stock_balances where outlet_id=d.outlet_id and product_id=p_product_id for update;
 if coalesce(s.revision,0)<>p_expected_revision then raise sqlstate 'PT409' using message='Stock changed';end if;
 if p_deduct and s.quantity-l.quantity < -1000000000000 then raise sqlstate 'PT422' using message='Stock balance limit; recount first';end if;
 if p_deduct then
 perform private.apply_stock_sale(l.operation_id,l.product_id,auth.uid(),d.id,d.outlet_id,l.quantity,l.product_name,btrim(p_reason));
 else perform private.stock_event(d.outlet_id,l.product_id,l.operation_id,'SKIPPED',l.user_id);end if;
 update private.stock_sale_lines set state=case when p_deduct then 'APPLIED' else 'SKIPPED' end,issue=null where operation_id=l.operation_id and product_id=l.product_id;
 ack:=jsonb_build_object('operation_id',p_operation_id,'sale_operation_id',l.operation_id,'product_id',l.product_id,'deduct',p_deduct,'outlet_id',d.outlet_id);
 insert into private.stock_operations values(p_operation_id,auth.uid(),d.id,req,ack);
 select brand_id into b from public.outlets where id=d.outlet_id;
 insert into public.audit_logs(actor_user_id,brand_id,outlet_id,action,target_id,metadata) values(auth.uid(),b,d.outlet_id,'STOCK_RESOLVE',p_operation_id,req);
 return ack;
end;$$;
revoke all on function public.get_outlet_stock(uuid,text,text,integer,uuid,bigint),public.resolve_stock_sale(uuid,text,uuid,uuid,uuid,uuid,boolean,bigint,text) from public,anon,authenticated,service_role;
grant execute on function public.get_outlet_stock(uuid,text,text,integer,uuid,bigint),public.resolve_stock_sale(uuid,text,uuid,uuid,uuid,uuid,boolean,bigint,text) to authenticated;
notify pgrst,'reload schema';
commit;
