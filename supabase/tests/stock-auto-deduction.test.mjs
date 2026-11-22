import {test,beforeEach,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
let db;const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
async function admin(sql){await db.exec('reset role');return db.exec(sql);}
async function as(user,sql){await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[id(user)]);await db.exec('set role authenticated');return db.exec(sql);}
async function rpc(user,name,args){await as(user,'select 1');const keys=Object.keys(args);return (await db.query(`select public.${name}(${keys.map((k,i)=>k+'=> $'+(i+1)).join(',')}) result`,Object.values(args))).rows[0]?.result ?? [];}
const enroll=(user=3,device=43,outlet=20)=>rpc(user,'enroll_device',{p_device_id:id(device),p_outlet_id:id(outlet)});
const lease=(secret,user=3,device=43)=>rpc(user,'issue_offline_lease',{p_device_id:id(device),p_device_secret:secret});
const policy=async({user=3,outlet=20,role='CASHIER',expires='null',enabled=true}={})=>admin(`insert into private.device_approval_allowlist(user_id,outlet_id,role,enabled,reason,expires_at)
 values('${id(user)}','${id(outlet)}','${role}',${enabled},'Trusted test operator',${expires});`);
beforeEach(async()=>{
 db=new PGlite();await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;create schema auth;
 create table auth.users(id uuid primary key,email text,email_confirmed_at timestamptz,raw_app_meta_data jsonb default '{}');
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth to authenticated;grant execute on function auth.uid() to authenticated;`);
 await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));

 await db.exec(await readFile(new URL('../staff-provisioning.sql',import.meta.url),'utf8'));
 await db.exec(await readFile(new URL('../outlet-stock.sql',import.meta.url),'utf8'));
 await db.exec(await readFile(new URL('../stock-auto-deduction.sql',import.meta.url),'utf8'));
 for(let n=1;n<=4;n++)await db.query('insert into auth.users(id,email,email_confirmed_at) values($1,$2,now())',[id(n),`user${n}@example.invalid`]);
 await db.exec(`insert into public.user_profiles(id) select id from auth.users;
 insert into public.brands(id,name) values('${id(10)}','Tea');
 insert into public.outlets(id,brand_id,name) values('${id(20)}','${id(10)}','A'),('${id(21)}','${id(10)}','B');
 insert into public.brand_members(user_id,brand_id) values('${id(1)}','${id(10)}');
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER'),('${id(3)}','${id(21)}','CASHIER');`);
 await db.exec(`insert into public.categories(id,brand_id,name) values('${id(30)}','${id(10)}','Tea');
 insert into public.products(id,brand_id,category_id,name,base_price_idr) values('${id(31)}','${id(10)}','${id(30)}','Tea',5000);`);
});
afterEach(async()=>{await db.close();});

async function context(user=1,device=40,outlet=20){const d=await enroll(user,device,outlet);await lease(d.device_secret,user,device);return {p_device_id:id(device),p_device_secret:d.device_secret};}
async function view(user,proof,args={}){return rpc(user,'get_outlet_stock',{...proof,...args});}
async function count(user,proof,quantity=20,revision=0,op=70,extra={}){
 const epoch=(await admin('select epoch from private.sync_generation'))[0].rows[0].epoch;
 return rpc(user,'set_outlet_stock',{...proof,p_operation_id:id(op),p_epoch:epoch,p_product_id:id(31),p_quantity:quantity,p_expected_revision:revision,p_reason:'Physical count',...extra});
}
let next=300;
function sale(q=3){return {schema_version:1,demo:false,operation_id:id(next++),sale_id:id(next++),brand_id:id(10),outlet_id:id(20),device_id:id(43),cashier_user_id:id(3),created_at_utc_ms:Date.now(),subtotal_idr:5000*q,total_idr:5000*q,discount_idr:0,
 items:[{id:id(next++),product_id:id(31),product_name:'Tea',product_version:1,category_version:1,unit_base_price_idr:5000,quantity:q,line_total_idr:5000*q,groups:[],modifiers:[]}],payment:{method:'CASH',amount_idr:5000*q,tendered_idr:5000*q,change_idr:0,verification:'CASH_RECEIVED'}};}
async function intent(p){const s=(await admin(`select count_generation from public.outlet_stock_balances where outlet_id='${id(20)}' and product_id='${id(31)}'`))[0].rows[0];return {epoch:(await admin('select epoch from private.sync_generation'))[0].rows[0].epoch,products:[{product_id:id(31),quantity:p.items.reduce((n,l)=>n+l.quantity,0),generation:s?.count_generation??null}]};}
const post=(p,proof,i)=>rpc(3,'submit_sale_stock',{p_payload:JSON.stringify(p),p_device_secret:proof.p_device_secret,p_stock:i?JSON.stringify(i):null});
async function prepare(q=2){const owner=await context();await count(1,owner,q);const cashier=await context(3,43);await admin(`update public.devices set enrolled_at=now()-interval '1 day' where id='${id(43)}'`);return{owner,cashier};}
test('accepted sale deducts quantity once, allows negative stock, and publishes own-op balance acknowledgement',async()=>{
 const {owner,cashier}=await prepare();const p=sale();const i=await intent(p);const a=await post(p,cashier,i);
 assert.equal(a.status,'ACCEPTED');assert.equal(a.stock_status,'APPLIED');assert.deepEqual(await post(p,cashier,i),a);
 const v=await view(1,owner);assert.equal(v.products[0].quantity,-1);assert.equal(v.history.length,2);assert.equal(v.history[0].delta,-3);
 const feed=await rpc(3,'pull_changes_stock',{...cashier,p_after_seq:0,p_role:'CASHIER'});
 assert.equal(feed.stock_enabled,true);assert.ok(feed.changes.some(c=>c.entity_type==='STOCK'&&c.payload.operation_id===p.operation_id&&c.payload.quantity===-1));
});
test('price rejection still consumes stock; invalid paid receipt never consumes',async()=>{
 const {owner,cashier}=await prepare(10);const p=sale(2);const i=await intent(p);await admin(`update public.products set base_price_idr=6000 where id='${id(31)}'`);
 const a=await post(p,cashier,i);assert.equal(a.code,'CATALOG_CHANGED');assert.equal(a.stock_status,'APPLIED');assert.deepEqual(await post(p,cashier,i),a);
 assert.equal((await view(1,owner)).products[0].quantity,8);
 const bad=sale();bad.payment.change_idr=100;await assert.rejects(post(bad,cashier,await intent(bad)),/Invalid paid receipt/);
 assert.equal((await view(1,owner)).products[0].quantity,8);
 assert.equal((await admin('select count(*)::int n from public.sales'))[0].rows[0].n,0);
});
test('new physical count prevents late offline double deduction; Owner can skip or deduct review once',async()=>{
 const {owner,cashier}=await prepare(10);const p=sale(2);const i=await intent(p);
 await count(1,owner,8,1,71);const a=await post(p,cashier,i);assert.equal(a.stock_status,'REVIEW');
 let v=await view(1,owner);assert.equal(v.products[0].quantity,8);assert.equal(v.reviews[0].issue,'COUNT_CHANGED');
 const args={...owner,p_operation_id:id(90),p_epoch:i.epoch,p_sale_operation_id:p.operation_id,p_product_id:id(31),p_deduct:false,p_expected_revision:2,p_reason:'Included in physical count'};
 const ack=await rpc(1,'resolve_stock_sale',args);assert.deepEqual(await rpc(1,'resolve_stock_sale',args),ack);
 assert.equal((await view(1,owner)).products[0].quantity,8);assert.equal((await view(1,owner)).reviews.length,0);
});
test('legacy/untracked receipts enter explicit stock review, while existing accepted receipts are not backfilled',async()=>{
 const {owner,cashier}=await prepare();const p=sale();const a=await post(p,cashier,null);assert.equal(a.stock_status,'REVIEW');assert.equal((await view(1,owner)).reviews[0].issue,'LEGACY_INTENT');
 const i=await intent(p);await assert.rejects(post(p,cashier,i),/operation conflict/);
 const resolve={...owner,p_operation_id:id(91),p_epoch:i.epoch,p_sale_operation_id:p.operation_id,p_product_id:id(31),p_deduct:true,p_expected_revision:1,p_reason:'Not included, deduct goods'};
 await assert.rejects(rpc(3,'resolve_stock_sale',{...resolve,...cashier}),/Forbidden/);
 await rpc(1,'resolve_stock_sale',resolve);assert.equal((await view(1,owner)).products[0].quantity,-1);
});
test('count-generation changes even when a recount confirms the same quantity; scope/proof/revocation still enforced',async()=>{
 const {owner,cashier}=await prepare();const p=sale(1);const i=await intent(p);await count(1,owner,2,1,71);
 assert.equal((await post(p,cashier,i)).stock_status,'REVIEW');
 await assert.rejects(rpc(3,'pull_changes_stock',{...cashier,p_device_secret:'bad',p_after_seq:0,p_role:'CASHIER'}),/denied/);
 await admin(`update public.devices set revoked_at=now() where id='${id(43)}'`);await assert.rejects(post(p,cashier,i),/denied/);
 await assert.rejects(as(1,'select * from private.stock_sale_lines'),/permission denied/);
});
test('upgrade from old feed cursor receives a fresh balance seed without leaking another outlet',async()=>{
 const {owner,cashier}=await prepare(12);
 const legacy=await rpc(3,'pull_changes',{...cashier,p_after_seq:0,p_role:'CASHIER'});
 const modern=await rpc(3,'pull_changes_stock',{...cashier,p_after_seq:legacy.next_seq,p_epoch:legacy.feed_epoch,p_role:'CASHIER'});
 assert.ok(modern.changes.some(c=>c.entity_type==='STOCK'&&c.payload.quantity===12));
 assert.ok(modern.changes.every(c=>!c.payload.outlet_id||c.payload.outlet_id===id(20)));
});
test('multi-line same-product sale aggregates quantities, resolution deduct is idempotent and stale resolution conflicts',async()=>{
 const {owner,cashier}=await prepare(20);const p=sale(2);p.items.push({...p.items[0],id:id(next++)});p.subtotal_idr=p.total_idr=p.payment.amount_idr=p.payment.tendered_idr=20000;
 const i=await intent(p);await count(1,owner,18,1,71);await post(p,cashier,i);
 const args={...owner,p_operation_id:id(92),p_epoch:i.epoch,p_sale_operation_id:p.operation_id,p_product_id:id(31),p_deduct:true,p_expected_revision:1,p_reason:'Deduct remaining sale'};
 await assert.rejects(rpc(1,'resolve_stock_sale',args),/Stock changed/);args.p_expected_revision=2;
 const a=await rpc(1,'resolve_stock_sale',args);assert.deepEqual(await rpc(1,'resolve_stock_sale',args),a);
 assert.equal((await view(1,owner)).products[0].quantity,14);assert.equal((await view(1,owner)).reviews.length,0);
});
test('mismatched intent, cross-outlet payload and old epoch roll back financial admission and stock',async()=>{
 const {owner,cashier}=await prepare(10);const p=sale();const i=await intent(p);
 await assert.rejects(post(p,cashier,{...i,products:[{...i.products[0],quantity:9}]}),/match receipt/);
 await assert.rejects(post({...p,outlet_id:id(21)},cashier,i),/Scope denied/);
 await assert.rejects(post(p,cashier,{...i,epoch:id(99)}),/generation/);
 assert.equal((await admin('select count(*)::int n from public.sales'))[0].rows[0].n,0);assert.equal((await view(1,owner)).products[0].quantity,10);
});
test('pre-cutover accepted receipt retry does not create stock history retroactively',async()=>{
 const {owner,cashier}=await prepare(10);const p=sale(1);const i=await intent(p);
 await admin(`select set_config('request.jwt.claim.sub','${id(3)}',false)`);
 const historical=(await db.query('select private.submit_sale_financial($1,$2) result',[JSON.stringify(p),cashier.p_device_secret])).rows[0].result;
 assert.equal(historical.status,'ACCEPTED');
 const ack=await post(p,cashier,i);assert.equal(ack.stock_status,'PRE_CUTOVER');assert.equal((await view(1,owner)).products[0].quantity,10);
 await assert.rejects(as(3,"select private.submit_sale_financial('{}','bad')"),/permission denied/);
});
test('otherwise valid paid receipt outside financial admission window enters stock review',async()=>{
 const {owner,cashier}=await prepare(10);const p=sale();p.created_at_utc_ms=Date.now()-62*86400000;
 const ack=await post(p,cashier,await intent(p));assert.equal(ack.code,'INVALID_SALE');assert.equal(ack.stock_status,'REVIEW');
 const snapshot=await view(1,owner);assert.equal(snapshot.products[0].quantity,10);assert.equal(snapshot.reviews[0].issue,'FINANCIAL_REVIEW');
});
