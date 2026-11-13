import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
const id=n=>`60000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
let db;
async function admin(sql){await db.exec('reset role');return db.exec(sql);}
async function auth(user){await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[id(user)]);await db.exec('set role authenticated');}
async function rpc(user,name,args){await auth(user);const keys=Object.keys(args);return (await db.query(`select public.${name}(${keys.map((k,i)=>`${k} => $${i+1}`).join(',')}) result`,Object.values(args))).rows[0].result;}
const proof=d=>({p_device_id:id(d),p_device_secret:'proof'});
async function report(user=1,device=40,outlet=null,from='2026-11-13',to=from){return rpc(user,'get_sales_report',{...proof(device),p_from_date:from,p_to_date:to,p_outlet_id:outlet});}
async function roster(user=1,device=40){return rpc(user,'list_staff',proof(device));}
async function member(target,active,version,user=1,device=40,reason='Reviewed by owner'){return rpc(user,'set_staff_access',{...proof(device),p_user_id:id(target),p_active:active,p_expected_version:version,p_reason:reason});}
async function device(target,revoked,version,user=1,source=40){return rpc(user,'set_device_access',{...proof(source),p_target_id:id(target),p_revoked:revoked,p_expected_version:version,p_reason:'Reviewed device access'});}
async function version(table,key,value){return (await admin(`select version from public.${table} where ${key}='${id(value)}'`))[0].rows[0].version;}
before(async()=>{
 db=new PGlite();await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;create schema auth;
 create table auth.users(id uuid primary key,email text,email_confirmed_at timestamptz);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth to authenticated;grant execute on function auth.uid() to authenticated;`);
 await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
 for(let n=1;n<=6;n++)await db.query('insert into auth.users values($1,$2,now());',[id(n),`staff${n}@example.invalid`]);
 await db.exec(`insert into public.user_profiles(id,full_name) select id,'Staff' from auth.users;
 insert into public.brands(id,name) values('${id(10)}','Tea'),('${id(11)}','Foreign');
 insert into public.outlets(id,brand_id,name) values('${id(20)}','${id(10)}','A'),('${id(21)}','${id(10)}','B'),('${id(22)}','${id(11)}','Foreign');
 insert into public.brand_members(user_id,brand_id) values('${id(1)}','${id(10)}'),('${id(5)}','${id(11)}');
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER'),('${id(4)}','${id(20)}','CASHIER'),('${id(6)}','${id(21)}','CASHIER');
 insert into public.categories(id,brand_id,name) values('${id(30)}','${id(10)}','Tea'),('${id(32)}','${id(11)}','Foreign');
 insert into public.products(id,brand_id,name,category_id,base_price_idr) values('${id(31)}','${id(10)}','Current name','${id(30)}',6000),('${id(33)}','${id(11)}','Foreign','${id(32)}',6000);`);
 for(const [d,u,o,r] of [[40,1,20,'OWNER'],[41,2,20,'MANAGER'],[42,3,20,'CASHIER'],[43,4,20,'CASHIER'],[44,6,21,'CASHIER'],[45,5,22,'OWNER']])await db.query(`insert into public.devices(id,outlet_id,enrolled_by,secret_hash,enrolled_at,approved_at,approved_role) values($1,$2,$3,encode(sha256(convert_to('proof','UTF8')),'hex'),now()-interval '1 day',now(),$4)`,[id(d),id(o),id(u),r]);
 for(const [n,o,d,u,amount,method,time] of [[100,20,42,3,6000,'CASH','2026-11-12T16:59:59Z'],[101,20,42,3,7000,'CASH','2026-11-12T17:00:00Z'],[102,20,43,4,9000,'QRIS_MANUAL','2026-11-13T16:59:59.999Z'],[103,21,44,6,11000,'CASH','2026-11-13T08:00:00Z'],[104,22,45,5,13000,'CASH','2026-11-13T08:00:00Z'],[105,20,42,3,15000,'CASH','2026-11-13T17:00:00Z']]){
  await db.query(`insert into public.sales(id,brand_id,outlet_id,device_id,cashier_user_id,subtotal_idr,total_idr,status,created_at) values($1,$2,$3,$4,$5,$6,$6,'COMPLETED',$7)`,[id(n),id(o===22?11:10),id(o),id(d),id(u),amount,time]);
  await db.query(`insert into public.sale_items values($1,$2,$3,'Original snapshot',$4,1,$4)`,[id(n+1000),id(n),id(o===22?33:31),amount]);
  await db.query(`insert into public.payments values($1,$2,$3,$4,$5,$6,$7)`,[id(n+2000),id(n),method,amount,method==='CASH'?amount+2000:null,method==='CASH'?2000:null,method==='CASH'?'CASH_RECEIVED':'CASHIER_CONFIRMED_MANUALLY']);
 }
});
after(async()=>db.close());
let serial=500;
async function save(change,user=1,device=40,epoch=null){
 if(!epoch){await db.exec('reset role');epoch=(await db.query('select epoch from private.sync_generation')).rows[0].epoch;}
 return rpc(user,'save_catalog_record',{...proof(device),p_epoch:epoch,p_change:change});
}
function change(kind,fields={}){return {operation_id:id(serial++),id:id(serial++),kind,expected_version:0,name:'New '+kind,active:true,sort_order:0,group_ids:[],...fields};}
async function scalar(sql){await db.exec('reset role');return Object.values((await db.query(sql)).rows[0])[0];}
test('owner and manager create all catalog kinds with atomic feed and audit; duplicate retry has no extra writes',async()=>{
 const category=change('Category');const a=await save(category);assert.equal(a.version,1);assert.ok(a.through_seq>0);
 const count=await scalar('select count(*) from public.sync_changes');assert.deepEqual(await save(category),a);assert.equal(await scalar('select count(*) from public.sync_changes'),count);
 assert.equal(await scalar(`select count(*) from public.audit_logs where action='CATALOG_SAVE' and target_id='${category.id}'`),1);
 await assert.rejects(save({...category,name:'Different'}),/Operation conflict/);
 const group=change('Group',{min_select:1,max_select:2});await save(group,2,41);
 const option=change('Option',{price_idr:1000,parent_id:group.id});await save(option);
 const product=change('Product',{price_idr:6000,parent_id:category.id,group_ids:[group.id]});const p=await save(product);
 assert.equal(await scalar(`select count(*) from public.product_modifier_groups where product_id='${product.id}' and active`),1);
 await assert.rejects(save({...option,operation_id:id(serial++),expected_version:1,active:false}),/needs this option/);
 await assert.rejects(save({...group,operation_id:id(serial++),expected_version:1,min_select:2}),/lacks required options/);
 await assert.rejects(save({...product,operation_id:id(serial++),expected_version:0,price_idr:1}),/reload/);
 assert.equal(await scalar(`select base_price_idr from public.products where id='${product.id}'`),6000);
 const updated=await save({...product,operation_id:id(serial++),expected_version:p.version,group_ids:[]});assert.equal(updated.version,2);
 assert.equal(await scalar(`select count(*) from public.product_modifier_groups where product_id='${product.id}' and active`),0);
 await save({...option,operation_id:id(serial++),expected_version:1,active:false});
 assert.equal(await scalar(`select product_name_snapshot from public.sale_items where sale_id='${id(100)}'`),'Original snapshot');
});
test('cashier, wrong device, foreign parents, stale generation and direct writes are denied',async()=>{
 await assert.rejects(save(change('Category'),3,42),/Catalog forbidden/);
 await assert.rejects(save(change('Category'),1,41),/Device denied/);
 await assert.rejects(save(change('Product',{price_idr:1,parent_id:id(32)})),/Unavailable category/);
 await assert.rejects(save(change('Category'),1,40,id(9999)),/Feed generation changed/);
 await assert.rejects(save(change('Category',{id:id(32)})),/identity already exists/);
 await auth(1);await assert.rejects(db.query("update public.categories set name='Forbidden'"),/permission denied/);
 await assert.rejects(db.query('select * from private.catalog_operations'),/permission denied/);
});
test('invalid assignment rolls back product, operation, audit and feed together',async()=>{
 const c=change('Product',{price_idr:6000,parent_id:id(30),group_ids:[id(9998)]});
 const count=await scalar('select count(*) from public.sync_changes');await assert.rejects(save(c),/Unavailable assigned group/);
 assert.equal(await scalar(`select count(*) from public.products where id='${c.id}'`),0);
 assert.equal(await scalar(`select count(*) from private.catalog_operations where id='${c.operation_id}'`),0);
 assert.equal(await scalar('select count(*) from public.sync_changes'),count);
});
