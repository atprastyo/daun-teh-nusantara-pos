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
test('Jakarta date boundaries and brand owner reports include both outlets without foreign sales',async()=>{
 const r=await report();assert.equal(r.summary.sale_count,'3');assert.equal(r.summary.total_idr,'27000');assert.equal(r.summary.cash_idr,'18000');assert.equal(r.summary.qris_idr,'9000');
 assert.equal(r.outlets.length,2);assert.equal(r.days[0].day,'2026-11-13');assert.equal(r.products[0].name,'Original snapshot');
 assert.equal((await report(1,40,id(21))).summary.total_idr,'11000');
});
test('manager report is selected-outlet only; cashier, wrong proof and foreign scope denied',async()=>{
 const r=await report(2,41);assert.equal(r.outlet_id,id(20));assert.equal(r.summary.total_idr,'16000');
 await assert.rejects(report(2,41,id(21)),/Outlet denied/);await assert.rejects(report(1,40,id(22)),/Outlet denied/);
 await assert.rejects(report(3,42),/Reports forbidden/);
 await assert.rejects(rpc(1,'get_sales_report',{...proof(40),p_device_secret:'wrong',p_from_date:'2026-11-13',p_to_date:'2026-11-13'}),/Device denied/);
});
test('reports empty/range states are explicit and do not use current catalog prices',async()=>{
 assert.equal((await report(1,40,null,'2020-01-01')).summary.sale_count,'0');
 await assert.rejects(report(1,40,null,'2026-11-14','2026-11-13'),/Invalid report range/);
 await assert.rejects(report(1,40,null,'2020-01-01','2026-11-13'),/Invalid report range/);
 await admin(`update public.products set name='Changed',base_price_idr=1 where id='${id(31)}'`);
 assert.equal((await report()).summary.total_idr,'27000');assert.equal((await report()).products[0].name,'Original snapshot');
 await admin(`update public.products set name='Current name',base_price_idr=6000 where id='${id(31)}'`);
});
test('staff roster is scoped, excludes secrets and does not create accounts',async()=>{
 const r=await roster();assert.equal(r.members.length,3);assert.equal(r.devices.length,4);assert.ok(!JSON.stringify(r).includes('secret_hash'));
 assert.equal((await roster(2,41)).members.length,3);assert.deepEqual((await roster(2,41)).audit,[]);await assert.rejects(roster(3,42),/Staff forbidden/);
 await auth(2);await assert.rejects(db.query('select * from private.sync_generation'),/permission denied/);
});
test('manager cannot modify manager/self/owner/other outlet and optimistic conflicts do not overwrite',async()=>{
 await assert.rejects(member(2,false,1,2,41),/forbidden/);await assert.rejects(member(1,false,1),/forbidden/);
 await assert.rejects(member(6,false,1),/forbidden/);await assert.rejects(member(3,false,999),/reload/);
 await assert.rejects(member(3,false,1,1,40,'x'),/Reason required/);
 assert.equal((await roster()).members.find(m=>m.user_id===id(3)).active,true);
});
test('disabling outlet access revokes devices, audits once, and reenable does not restore approval',async()=>{
 const v=await version('outlet_members','user_id',3);await member(3,false,v,2,41);
 await assert.rejects(rpc(3,'pull_changes',{...proof(42),p_after_seq:0,p_role:'CASHIER'}),/Device denied/);
 const revoked=(await roster()).devices.find(d=>d.id===id(42));assert.equal(revoked.revoked,true);assert.ok((await roster()).audit.some(a=>a.action==='STAFF_DISABLE'&&a.target_id===id(3))); 
 assert.equal((await admin("select count(*)::int n from public.audit_logs where action='STAFF_DISABLE'"))[0].rows[0].n,1);
 await assert.rejects(member(3,true,v),/reload/);await member(3,true,await version('outlet_members','user_id',3));
 await assert.rejects(rpc(3,'issue_offline_lease',proof(42)),/Device denied/);
 await assert.rejects(device(42,false,revoked.version,2,41),/forbidden/);
 await device(42,false,revoked.version);await assert.rejects(rpc(3,'issue_offline_lease',proof(42)),/approval required/);
 await rpc(1,'approve_device',{p_device_id:id(42)});assert.equal((await rpc(3,'issue_offline_lease',proof(42))).role,'CASHIER');
});
test('device revocation/version checks preserve receipts and forbid self/cross-outlet targets',async()=>{
 await assert.rejects(device(40,true,1),/forbidden/);await assert.rejects(device(44,true,1),/forbidden/);
 await assert.rejects(device(41,true,1,2,41),/forbidden/);
 const v=await version('devices','id',43);await device(43,true,v,2,41);
 await assert.rejects(device(43,false,v),/reload/);
 assert.equal((await admin('select count(*)::int n from public.sales'))[0].rows[0].n,6);
 await device(43,false,await version('devices','id',43));await rpc(1,'approve_device',{p_device_id:id(43)});
});
test('inactive global profiles cannot be reactivated through outlet access controls',async()=>{
 await admin(`update public.user_profiles set active=false where id='${id(4)}'`);
 await assert.rejects(member(4,true,await version('outlet_members','user_id',4)),/Inactive profile/);
 await admin(`update public.user_profiles set active=true where id='${id(4)}'`);
});
test('isolated database dump/restore preserves sale graphs, dedupe and RLS; epoch rotation blocks old cursors',async(t)=>{
 const payload={schema_version:1,demo:false,operation_id:id(300),sale_id:id(301),brand_id:id(10),outlet_id:id(20),device_id:id(40),cashier_user_id:id(1),created_at_utc_ms:Date.now(),subtotal_idr:6000,discount_idr:0,total_idr:6000,
 items:[{id:id(302),product_id:id(31),product_name:'Current name',product_version:3,category_version:1,unit_base_price_idr:6000,quantity:1,line_total_idr:6000,groups:[],modifiers:[]}],
 payment:{method:'CASH',amount_idr:6000,tendered_idr:10000,change_idr:4000,verification:'CASH_RECEIVED'}};
 const ack=await rpc(1,'submit_sale',{p_payload:JSON.stringify(payload),p_device_secret:'proof'});assert.equal(ack.status,'ACCEPTED');
 const page=await rpc(1,'pull_changes',{...proof(40),p_after_seq:0,p_role:'OWNER'});
 const summaryBefore=(await report()).summary;
 const countsSql='select (select count(*)::int from public.sales) sales,(select count(*)::int from public.sale_items) items,(select count(*)::int from public.payments) payments,(select count(*)::int from public.sync_operations) operations,(select count(*)::int from public.audit_logs) audits';
 const countsBefore=(await admin(countsSql))[0].rows[0];
 await admin('reset role');const backup=await db.dumpDataDir('gzip');assert.ok(backup.size>0);
 const original=db;const restored=new PGlite({loadDataDir:backup});await restored.waitReady;db=restored;
 try {
  assert.deepEqual((await admin(countsSql))[0].rows[0],countsBefore);
  assert.deepEqual((await report()).summary,summaryBefore);
  const same=await rpc(1,'submit_sale',{p_payload:JSON.stringify(payload),p_device_secret:'proof'});assert.equal(same.status,'ACCEPTED');assert.equal(same.request_hash,ack.request_hash);
  assert.equal((await admin(`select count(*)::int n from public.sales where id='${id(301)}'`))[0].rows[0].n,1);
  await auth(3);assert.equal((await db.query(`select count(*)::int n from public.sales where outlet_id='${id(21)}'`)).rows[0].n,0);
  const rotation=(await readFile(new URL('../rotate-feed-generation.sql.example',import.meta.url),'utf8'))
   .replaceAll('<restored-feed-epoch>',page.feed_epoch).replaceAll('<owner-auth-user-uuid>',id(1))
   .replaceAll('<brand-uuid>',id(10)).replaceAll('<restore-ticket-reference>','Isolated restore rehearsal');
  await admin(rotation);
  await assert.rejects(admin(rotation),/generation changed/);await db.exec('rollback');
  await assert.rejects(rpc(1,'pull_changes',{...proof(40),p_after_seq:page.next_seq,p_role:'OWNER',p_epoch:page.feed_epoch}),/generation changed/);
  const restarted=await rpc(1,'pull_changes',{...proof(40),p_after_seq:0,p_role:'OWNER'});assert.notEqual(restarted.feed_epoch,page.feed_epoch);assert.ok(restarted.changes.length>0);
  assert.equal((await admin("select count(*)::int n from public.audit_logs where action='FEED_RESTORE_BOUNDARY'"))[0].rows[0].n,1);
  t.diagnostic(`Isolated restore: backup_bytes=${backup.size}, unique_replayed_sale=1, restored_graph_matches=true, role_scope_checked=true, old_epoch_blocked=true`);
 }finally{db=original;await restored.close();}
});
