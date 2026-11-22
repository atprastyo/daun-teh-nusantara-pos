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
test('untracked differs from zero; opening zero and later positive/negative differences are immutable and audited',async()=>{
 const proof=await context();let s=await view(1,proof);assert.equal(s.products[0].quantity,null);assert.equal(s.products[0].revision,0);
 await count(1,proof,0);await count(1,proof,20,1,71);await count(1,proof,7,2,72);
 s=await view(1,proof);assert.equal(s.products[0].quantity,7);assert.deepEqual(s.history.map(x=>x.delta),[-13,20,0]);assert.equal(s.history[2].revision,1);
 assert.equal((await admin("select count(*)::int n from public.audit_logs where action='STOCK_COUNT'"))[0].rows[0].n,3);
 await assert.rejects(admin('update public.outlet_stock_movements set reason=reason'),/append only/);
 await assert.rejects(admin('delete from public.outlet_stock_movements'),/append only/);
});
test('same-operation retry returns original ack after later counts; different payload conflicts',async()=>{
 const proof=await context();const ack=await count(1,proof);await count(1,proof,10,1,71);
 assert.deepEqual(await count(1,proof),ack);
 await assert.rejects(count(1,proof,22),/operation conflict/);
 assert.equal((await view(1,proof)).history.length,2);
});
test('stale revisions fail atomically, including two devices observing the same opening revision',async()=>{
 const owner=await context();const manager=await context(2,42);
 await count(1,owner);await assert.rejects(count(2,manager,10,0,71),/Stock changed/);
 const s=await view(2,manager);assert.equal(s.products[0].quantity,20);assert.equal(s.history.length,1);
 await count(2,manager,25,1,72);assert.equal((await view(1,owner)).products[0].quantity,25);
});
test('cashier, wrong proof, revoked device and inactive membership cannot read or change counts',async()=>{
 const owner=await context();const manager=await context(2,42);const cashier=await context(3,43);
 await assert.rejects(view(3,cashier),/Forbidden/);await assert.rejects(count(3,cashier),/Forbidden/);
 await assert.rejects(view(1,{...owner,p_device_secret:'wrong'}),/Device denied/);
 await admin(`update public.outlet_members set active=false where user_id='${id(2)}' and outlet_id='${id(20)}'`);
 await assert.rejects(count(2,manager),/Forbidden/);
 await admin(`update public.devices set revoked_at=now() where id='${id(40)}'`);await assert.rejects(view(1,owner),/Device denied/);
});
test('outlets are isolated; scope follows enrolled device; direct REST and anon access denied',async()=>{
 const a=await context();const b=await context(1,41,21);await count(1,a);
 assert.equal((await view(1,b)).products[0].quantity,null);assert.equal((await view(1,b)).history.length,0);
 await count(1,b,4,0,71);assert.equal((await view(1,a)).products[0].quantity,20);
 await assert.rejects(as(1,'select * from public.outlet_stock_balances'),/permission denied/);
 await assert.rejects(as(1,'insert into public.outlet_stock_balances values(null,null,1,1,now())'),/permission denied/);
 await admin('set role anon');await assert.rejects(db.query(`select public.get_outlet_stock('${id(40)}','wrong')`),/permission denied/);
});
test('validation rejects negative, fractional/big, unchanged, archived or foreign product counts and old epoch',async()=>{
 const proof=await context();
 for(const qty of [-1,1000000001,null,'1.5'])await assert.rejects(count(1,proof,qty));
 await count(1,proof);await assert.rejects(count(1,proof,20,1,71),/unchanged/);
 await assert.rejects(count(1,proof,21,1,71,{p_reason:'x'}),/Invalid/);
 await assert.rejects(count(1,proof,21,1,71,{p_epoch:id(99)}),/generation/);
 await admin(`update public.products set active=false where id='${id(31)}'`);await assert.rejects(count(1,proof,21,1,71),/unavailable/);
 await admin(`insert into public.brands(id,name) values('${id(11)}','Other');insert into public.categories(id,brand_id,name) values('${id(32)}','${id(11)}','Other');insert into public.products(id,brand_id,category_id,name,base_price_idr) values('${id(33)}','${id(11)}','${id(32)}','Other',1);`);
 await assert.rejects(count(1,proof,21,1,71,{p_product_id:id(33)}),/unavailable/);
 assert.equal((await view(1,proof)).history.length,1);
});
test('search is literal, products/history are bounded and history supports older pages',async()=>{
 const proof=await context();assert.equal((await view(1,proof,{p_query:'%'})).products.length,0);
 for(let i=0;i<52;i++)await count(1,proof,i,i,70+i);
 const first=await view(1,proof);assert.equal(first.history.length,50);
 const second=await view(1,proof,{p_before:first.history.at(-1).seq,p_product_id:id(31)});assert.equal(second.history.length,2);
 assert.ok(second.history[0].seq<first.history.at(-1).seq);
 for(let n=0;n<101;n++)await admin(`insert into public.products(id,brand_id,category_id,name,base_price_idr) values('${id(1000+n)}','${id(10)}','${id(30)}','Tea ${String(n).padStart(3,'0')}',1)`);
 const products=await view(1,proof);const next=await view(1,proof,{p_offset:100});
 assert.equal(products.products.length,100);assert.equal(products.has_more,true);assert.equal(next.products.length,2);assert.equal(next.has_more,false);
 assert.equal(new Set([...products.products,...next.products].map(p=>p.product_id)).size,102);
});
