import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
let db;
const id = n => `00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const owner=id(1), manager=id(2), cashier=id(3), colleague=id(4), otherCashier=id(5), otherOwner=id(6), invited=id(7);
const brand=id(10), otherBrand=id(11), outlet=id(20), otherOutlet=id(21), foreignOutlet=id(22);
async function admin(sql) { await db.exec('reset role'); return db.exec(sql); }
async function as(user, sql) {
 await db.exec('reset role'); await db.query("select set_config('request.jwt.claim.sub',$1,false)",[user]);
 await db.exec('set role authenticated'); return db.exec(sql);
}
async function scalar(user,sql) { return (await as(user,sql))[0].rows[0]; }
before(async()=>{
 db=new PGlite();
 // Test-only Auth substrate; production schema uses real Supabase auth/users/JWT context.
 await db.exec(`create role anon nologin; create role authenticated nologin; create role service_role nologin bypassrls;
 create schema auth; create table auth.users(id uuid primary key,email text unique,email_confirmed_at timestamptz);
 create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
 grant usage on schema auth to authenticated; grant execute on function auth.uid() to authenticated;`);
 await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
 for(const [n,email] of [[1,'owner'],[2,'manager'],[3,'cashier'],[4,'colleague'],[5,'other'],[6,'foreign'],[7,'invited']]) {
  await db.query('insert into auth.users values($1,$2,now())',[id(n),email+'@example.invalid']);
  if(n!==7) await db.query('insert into public.user_profiles(id) values($1)',[id(n)]);
 }
 await db.exec(`insert into public.brands(id,name) values('${brand}','Daun Teh'),('${otherBrand}','Other');
 insert into public.outlets(id,brand_id,name) values('${outlet}','${brand}','A'),('${otherOutlet}','${brand}','B'),('${foreignOutlet}','${otherBrand}','C');
 insert into public.brand_members(user_id,brand_id) values('${owner}','${brand}'),('${otherOwner}','${otherBrand}');
 insert into public.outlet_members values('${manager}','${outlet}','MANAGER',true),('${cashier}','${outlet}','CASHIER',true),('${colleague}','${outlet}','CASHIER',true),('${otherCashier}','${otherOutlet}','CASHIER',true);
 insert into public.categories(id,brand_id,name) values('${id(30)}','${brand}','Tea'),('${id(31)}','${otherBrand}','Foreign');
 insert into public.products(id,brand_id,name,category_id,base_price_idr) values('${id(32)}','${brand}','Original','${id(30)}',6000),('${id(33)}','${otherBrand}','Foreign','${id(31)}',7000);`);
 for(const [index,user,out] of [[40,cashier,outlet],[41,colleague,outlet],[42,otherCashier,otherOutlet],[43,otherOwner,foreignOutlet]]) {
  await db.exec(`insert into public.devices(id,outlet_id,enrolled_by,secret_hash) values('${id(index)}','${out}','${user}',repeat('a',64));
   insert into public.sales(id,brand_id,outlet_id,device_id,cashier_user_id,subtotal_idr,total_idr,status,created_at)
   values('${id(index+10)}','${out===foreignOutlet?otherBrand:brand}','${out}','${id(index)}','${user}',6000,6000,'COMPLETED',now());
   insert into public.sale_items(id,sale_id,product_id,product_name_snapshot,unit_base_price_idr,quantity,line_total_idr)
   values('${id(index+20)}','${id(index+10)}','${id(32)}','Snapshot',6000,1,6000);
   insert into public.payments(id,sale_id,method,amount_idr,tendered_idr,change_idr,verification_status)
   values('${id(index+30)}','${id(index+10)}','CASH',6000,6000,0,'CASH_RECEIVED');
   insert into public.sale_item_modifiers(id,sale_item_id,option_id,group_name_snapshot,option_name_snapshot,price_delta_idr)
   values('${id(index+40)}','${id(index+20)}','${id(100)}','Size','Regular',0);
   insert into public.sync_changes(brand_id,outlet_id,entity_type,entity_id,action,cashier_user_id,payload)
   values('${out===foreignOutlet?otherBrand:brand}','${out}','SALE','${id(index+10)}','UPSERT','${user}','{}');`);
 }
 await db.exec(`insert into public.sync_changes(brand_id,entity_type,entity_id,action,payload) values('${brand}','CATALOG','${id(32)}','UPSERT','{}')`);
});
after(async()=>{await db.close();});

test('every public table has RLS and anonymous has no table access',async()=>{
 const result=await admin("select relname from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and relkind='r' and not relrowsecurity");
 assert.equal(result[0].rows.length,0);
 await admin('set role anon'); await assert.rejects(db.query('select * from public.sales'),/permission denied/);
 await assert.rejects(db.query('select * from public.get_access_context()'),/permission denied/);
});
test('cashier sees only own sale and nested items',async()=>{
 assert.deepEqual((await as(cashier,'select id from public.sales'))[0].rows,[{id:id(50)}]);
 assert.deepEqual((await as(cashier,'select sale_id from public.sale_items'))[0].rows,[{sale_id:id(50)}]);
});
test('manager cannot read another outlet; owner cannot read another brand',async()=>{
 assert.equal((await scalar(manager,'select count(*)::int as n from public.sales')).n,2);
 assert.equal((await scalar(owner,'select count(*)::int as n from public.sales')).n,3);
 assert.equal((await scalar(owner,`select count(*)::int as n from public.outlets where id='${foreignOutlet}'`)).n,0);
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.categories')).n,1);
});
test('cashier cannot edit price, insert sales, change memberships or disable profiles',async()=>{
 for(const sql of ["update public.products set base_price_idr=1",'insert into public.sales(id) values(gen_random_uuid())',"update public.outlet_members set role='MANAGER'",'update public.user_profiles set active=true']) {
  await assert.rejects(as(cashier,sql),/permission denied/);
 }
});
test('secret device material cannot be selected',async()=>{
 assert.equal((await scalar(cashier,`select count(id)::int as n from public.devices`)).n,1);
});
test('device hashes are denied even to owner',async()=>{
 await assert.rejects(as(owner,'select secret_hash from public.devices'),/permission denied/);
});
test('manager cannot invite manager or users into other outlets',async()=>{
 await assert.rejects(as(manager,`select public.create_staff_invitation('x@example.invalid','${outlet}','MANAGER')`),/Forbidden/);
 await assert.rejects(as(manager,`select public.create_staff_invitation('x@example.invalid','${otherOutlet}','CASHIER')`),/Forbidden/);
 await assert.rejects(as(cashier,`select public.create_staff_invitation('x@example.invalid','${outlet}','CASHIER')`),/Forbidden/);
});
test('verified invite acceptance grants only requested outlet and is idempotent',async()=>{
 await as(manager,`select public.create_staff_invitation('invited@example.invalid','${outlet}','CASHIER')`);
 assert.equal((await scalar(invited,'select public.accept_staff_invitations() as n')).n,1);
 assert.equal((await scalar(invited,'select public.accept_staff_invitations() as n')).n,0);
 const rows=(await as(invited,'select * from public.get_access_context()'))[0].rows;
 assert.equal(rows.length,1); assert.equal(rows[0].outlet_id,outlet); assert.equal(rows[0].role,'CASHIER');
});
test('revoked inviter cannot grant stale invitation',async()=>{
 await admin(`insert into auth.users values('${id(8)}','late@example.invalid',now());`);
 await as(manager,`select public.create_staff_invitation('late@example.invalid','${outlet}','CASHIER')`);
 await admin(`update public.outlet_members set active=false where user_id='${manager}';`);
 assert.equal((await scalar(id(8),'select public.accept_staff_invitations() as n')).n,0);
 await admin(`update public.outlet_members set active=true where user_id='${manager}';`);
});
test('online profile/membership/brand deactivation is checked on every request',async()=>{
 await admin(`update public.user_profiles set active=false where id='${cashier}';`);
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.sales')).n,0);
 assert.equal((await as(cashier,'select * from public.get_access_context()'))[0].rows.length,0);
 await admin(`update public.user_profiles set active=true where id='${cashier}'; update public.outlet_members set active=false where user_id='${cashier}';`);
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.sales')).n,0);
 await admin(`update public.outlet_members set active=true where user_id='${cashier}'; update public.brands set active=false where id='${brand}';`);
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.products')).n,0);
 await admin(`update public.brands set active=true where id='${brand}';`);
});
test('enrollment and one-calendar-month lease require current device secret and membership',async()=>{
 const device=id(90);
 const enrolled=(await scalar(cashier,`select public.enroll_device('${device}','${outlet}') as result`)).result;
 await assert.rejects(as(colleague,`select public.enroll_device('${device}','${outlet}')`),/denied/);
 await assert.rejects(as(cashier,`select public.issue_offline_lease('${device}','wrong')`),/denied/);
 await assert.rejects(as(cashier,`select public.issue_offline_lease('${device}','${enrolled.device_secret}')`),/approval required/);
 await assert.rejects(as(cashier,`select public.approve_device('${device}')`),/Forbidden/);
 await as(manager,`select public.approve_device('${device}')`);
 const lease=(await scalar(cashier,`select public.issue_offline_lease('${device}','${enrolled.device_secret}') as result`)).result;
 assert.equal(lease.device_id,device); assert.equal(lease.role,'CASHIER');
 const issued=new Date(lease.issued_at_ms),expiry=new Date(lease.expires_at_ms);
 const maximum=31*24*60*60*1000;
 assert.ok(expiry-issued>0 && expiry-issued<=maximum);
 await admin(`update public.devices set revoked_at=now() where id='${device}'`);
 await assert.rejects(as(cashier,`select public.issue_offline_lease('${device}','${enrolled.device_secret}')`),/denied/);
});
test('disabled offline policy issues no offline interval',async()=>{
 await admin(`update public.brands set offline_access_enabled=false where id='${brand}'`);
 const enrolled=(await scalar(cashier,`select public.enroll_device('${id(91)}','${outlet}') as result`)).result;
 await as(owner,`select public.approve_device('${id(91)}')`);
 const lease=(await scalar(cashier,`select public.issue_offline_lease('${id(91)}','${enrolled.device_secret}') as result`)).result;
 assert.equal(lease.expires_at_ms,lease.issued_at_ms);
 await admin(`update public.brands set offline_access_enabled=true where id='${brand}'`);
});
test('completed server sales remain immutable even for privileged SQL writers',async()=>{
 await assert.rejects(admin('update public.sales set total_idr=1'),/append-only/);
 await assert.rejects(admin('delete from public.sale_items'),/append-only/);
});

test('revoked cashier cannot bypass approval with a new device UUID',async()=>{
 const enrolled=(await scalar(cashier,`select public.enroll_device('${id(92)}','${outlet}') as result`)).result;
 await assert.rejects(as(cashier,`select public.issue_offline_lease('${id(92)}','${enrolled.device_secret}')`),/approval required/);
 await assert.rejects(as(manager,`select public.approve_device('${id(43)}')`),/Forbidden/);
 const requests=(await as(manager,`select * from public.list_device_requests('${outlet}')`))[0].rows;
 assert.ok(requests.some(r=>r.device_id===id(92)));
 await assert.rejects(as(cashier,`select * from public.list_device_requests('${outlet}')`),/Forbidden/);
});

test('managers cannot downgrade managers and no client may invite an owner',async()=>{
 await assert.rejects(as(manager,`select public.create_staff_invitation('manager@example.invalid','${outlet}','CASHIER')`),/Forbidden target/);
 await assert.rejects(as(owner,`select public.create_staff_invitation('new@example.invalid','${outlet}','OWNER')`),/Forbidden/);
});
test('inactive profiles cannot reactivate through accepting invitations',async()=>{
 await as(owner,`select public.create_staff_invitation('cashier@example.invalid','${outlet}','CASHIER')`);
 await admin(`update public.user_profiles set active=false where id='${cashier}'`);
 await assert.rejects(as(cashier,'select public.accept_staff_invitations()'),/Inactive/);
 await admin(`update public.user_profiles set active=true where id='${cashier}'`);
});
test('only confirmed email can accept and expired invitations grant nothing',async()=>{
 await admin(`insert into auth.users values('${id(9)}','unconfirmed@example.invalid',null);
 insert into public.staff_invitations(email,outlet_id,role,invited_by,expires_at) values('unconfirmed@example.invalid','${outlet}','CASHIER','${owner}',now()-interval '1 day')`);
 await assert.rejects(as(id(9),'select public.accept_staff_invitations()'),/Verified user/);
 await admin(`update auth.users set email_confirmed_at=now() where id='${id(9)}'`);
 assert.equal((await scalar(id(9),'select public.accept_staff_invitations() as n')).n,0);
});
test('invitation rate limit is enforced transactionally by SQL',async()=>{
 let limited=false;
 for(let n=0;n<8;n++) {
  try { await as(manager,`select public.create_staff_invitation('limit${n}@example.invalid','${outlet}','CASHIER')`); }
  catch(error) { assert.equal(error.code,'PT429');limited=true;break; }
 }
 assert.ok(limited);
});

test('nested payment and modifier RLS follows the authorized parent sale',async()=>{
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.payments')).n,1);
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.sale_item_modifiers')).n,1);
 assert.equal((await scalar(manager,'select count(*)::int as n from public.payments')).n,2);
 assert.equal((await scalar(owner,'select count(*)::int as n from public.sale_item_modifiers')).n,3);
});
test('change feed cannot leak a peer or another outlet sale',async()=>{
 assert.equal((await scalar(cashier,'select count(*)::int as n from public.sync_changes')).n,2);
 assert.equal((await scalar(manager,'select count(*)::int as n from public.sync_changes')).n,3);
 assert.equal((await scalar(owner,'select count(*)::int as n from public.sync_changes')).n,4);
 await assert.rejects(as(cashier,'create table public.injected_scope(id int)'),/permission denied/);
});

test('cashier device approval cannot silently become manager approval after promotion',async()=>{
 await admin(`update public.outlet_members set role='MANAGER' where user_id='${cashier}' and outlet_id='${outlet}'`);
 try {
  const enrolled=(await scalar(cashier,`select public.enroll_device('${id(91)}','${outlet}') as result`)).result;
  await assert.rejects(as(cashier,`select public.issue_offline_lease('${id(91)}','${enrolled.device_secret}')`),/approval required/);
  await assert.rejects(as(manager,`select public.approve_device('${id(91)}')`),/Forbidden/);
  const requests=(await as(owner,`select * from public.list_device_requests('${outlet}')`))[0].rows;
  assert.ok(requests.some(r=>r.device_id===id(91)&&r.role==='MANAGER'));
  await as(owner,`select public.approve_device('${id(91)}')`);
  const lease=(await scalar(cashier,`select public.issue_offline_lease('${id(91)}','${enrolled.device_secret}') as result`)).result;
  assert.equal(lease.role,'MANAGER');
 } finally { await admin(`update public.outlet_members set role='CASHIER' where user_id='${cashier}' and outlet_id='${outlet}'`); }
});
