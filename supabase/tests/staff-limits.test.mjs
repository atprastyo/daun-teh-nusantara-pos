import {test,beforeEach,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
const id=n=>`70000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
let db;
async function admin(sql){await db.exec('reset role');return db.exec(sql);}
async function rpc(user,name,args={},service=false){
 await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[id(user)]);await db.exec('set role '+(service?'service_role':'authenticated'));
 const keys=Object.keys(args);return (await db.query(`select public.${name}(${keys.map((k,i)=>`${k} => $${i+1}`).join(',')}) result`,Object.values(args))).rows[0].result;
}
const proof=(device=40)=>({p_device_id:id(device),p_device_secret:'proof'});
const request=(n,role='CASHIER',device=40)=>({...proof(device),p_operation_id:id(n),p_email:`new${n}@example.invalid`,p_full_name:'New staff',p_role:role});
const prepare=(n,role='CASHIER',user=1,device=40)=>rpc(user,'prepare_staff_account',request(n,role,device));
async function authIdentity(job,marker=job.operation_id){await admin(`insert into auth.users(id,email,email_confirmed_at,raw_app_meta_data) values('${job.user_id}','${job.email}',now(),'${JSON.stringify({pos_provision_operation:marker})}')`);}
beforeEach(async()=>{
 db=new PGlite();await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;create schema auth;
 create table auth.users(id uuid primary key,email text,email_confirmed_at timestamptz,raw_app_meta_data jsonb default '{}');
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth to authenticated;grant execute on function auth.uid() to authenticated;`);
 await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
 await db.exec(await readFile(new URL('../staff-provisioning.sql',import.meta.url),'utf8'));
 await db.exec(await readFile(new URL('../staff-limits.sql',import.meta.url),'utf8'));
 for(let n=1;n<=5;n++)await db.query('insert into auth.users(id,email,email_confirmed_at) values($1,$2,now())',[id(n),`staff${n}@example.invalid`]);
 await db.exec(`insert into public.user_profiles(id,full_name) select id,'Staff' from auth.users;
 insert into public.brands(id,name) values('${id(10)}','Tea'),('${id(11)}','Foreign');
 insert into public.outlets(id,brand_id,name) values('${id(20)}','${id(10)}','A'),('${id(21)}','${id(10)}','B'),('${id(22)}','${id(11)}','Foreign');
 insert into public.brand_members(user_id,brand_id) values('${id(1)}','${id(10)}'),('${id(5)}','${id(11)}');
 insert into public.outlet_staff_limits(outlet_id,manager_limit,cashier_limit) select id,2,3 from public.outlets;
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER');`);
 for(const [d,u,o,r] of [[40,1,20,'OWNER'],[41,2,20,'MANAGER'],[42,3,20,'CASHIER'],[43,1,21,'OWNER'],[45,5,22,'OWNER']])await db.query(`insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_role) values($1,$2,$3,encode(sha256(convert_to('proof','UTF8')),'hex'),now(),$4)`,[id(d),id(o),id(u),r]);
});
afterEach(async()=>db.close());
const capacity=(user=1,device=40)=>rpc(user,'list_staff',proof(device)).then(r=>r.capacity);
test('multiple cashiers share outlet cap; pending/disabled count and same operation reserves once',async()=>{
 const a=await prepare(200,'CASHIER');assert.equal((await capacity()).cashier_used,2);
 assert.deepEqual(await prepare(200),a);assert.equal((await capacity()).cashier_used,2);
 const b=await prepare(201,'CASHIER',2,41);assert.equal((await capacity()).cashier_used,3);
 await assert.rejects(prepare(202),/STAFF_LIMIT_REACHED/);
 await authIdentity(a);await rpc(1,'finish_staff_account',{...proof(),p_operation_id:id(200)});
 await authIdentity(b);await rpc(2,'finish_staff_account',{...proof(41),p_operation_id:id(201)});
 assert.equal((await capacity()).cashier_used,3); // reservations converted without double count
 await admin(`update public.outlet_members set active=false where user_id='${a.user_id}'`);
 await assert.rejects(prepare(202),/STAFF_LIMIT_REACHED/);
 assert.equal((await capacity()).cashier_used,3);
});
test('manager and cashier caps independent; other outlets and brands have separate capacity',async()=>{
 await prepare(210,'MANAGER');await assert.rejects(prepare(211,'MANAGER'),/STAFF_LIMIT_REACHED/);
 assert.ok((await prepare(212,'CASHIER')).user_id);
 assert.ok((await prepare(213,'MANAGER',1,43)).user_id);
 assert.equal((await capacity(1,43)).cashier_used,0);
 assert.equal((await capacity(5,45)).manager_used,0);
 await assert.rejects(prepare(214,'MANAGER',2,41),/forbidden/);
});
test('missing config and zero caps block new creation without removing users; reserved finish survives lower limit',async()=>{
 const reserved=await prepare(220);await admin(`update public.outlet_staff_limits set manager_limit=0,cashier_limit=0 where outlet_id='${id(20)}'`);
 await assert.rejects(prepare(221),/STAFF_LIMIT_REACHED/);
 await authIdentity(reserved);await rpc(1,'finish_staff_account',{...proof(),p_operation_id:id(220)});
 assert.equal((await capacity()).cashier_used,2);assert.equal((await capacity()).cashier_limit,0);
 await admin(`delete from public.outlet_staff_limits where outlet_id='${id(20)}'`);
 assert.equal((await capacity()).cashier_limit,null);
 await assert.rejects(prepare(222),/STAFF_LIMITS_UNCONFIGURED/);
 assert.equal((await admin('select count(*)::int n from public.outlet_members'))[0].rows[0].n,3);
});
test('admin membership additions/role changes use same guard; multirow overflow rolls back',async()=>{
 await admin(`update public.user_profiles set full_name='Manual staff' where id='${id(4)}'`);
 await admin(`insert into public.outlet_members(user_id,outlet_id,role) values('${id(4)}','${id(20)}','CASHIER')`);
 assert.equal((await capacity()).cashier_used,2);
 await admin(`update public.outlet_staff_limits set manager_limit=1 where outlet_id='${id(20)}'`);
 await assert.rejects(admin(`update public.outlet_members set role='MANAGER' where user_id='${id(4)}'`),/STAFF_LIMIT_REACHED/);
 await admin(`update public.outlet_staff_limits set cashier_limit=2 where outlet_id='${id(21)}'`);
 for(const n of [6,7,8])await admin(`insert into auth.users(id,email,email_confirmed_at) values('${id(n)}','manual${n}@example.invalid',now());insert into public.user_profiles(id) values('${id(n)}')`);
 await assert.rejects(admin(`insert into public.outlet_members(user_id,outlet_id,role) values('${id(6)}','${id(21)}','CASHIER'),('${id(7)}','${id(21)}','CASHIER'),('${id(8)}','${id(21)}','CASHIER')`),/STAFF_LIMIT_REACHED/);
 assert.equal((await capacity(1,43)).cashier_used,0);
});
test('owners/managers cannot edit quotas; cashier/foreign outlet reads denied and counts omit private ledger',async()=>{
 await capacity();await assert.rejects(db.query('update public.outlet_staff_limits set cashier_limit=100'),/permission denied/);
 await assert.rejects(db.query('insert into public.outlet_staff_limits values($1,10,10,1)',[id(21)]),/permission denied/);
 assert.equal((await db.query('select count(*)::int n from public.outlet_staff_limits')).rows[0].n,2);
 await capacity(2,41);assert.equal((await db.query('select count(*)::int n from public.outlet_staff_limits')).rows[0].n,1);
 await rpc(3,'staff_password_change_required');assert.equal((await db.query('select count(*)::int n from public.outlet_staff_limits')).rows[0].n,0);
 await assert.rejects(db.query('select private.staff_slot_count($1,$2)',[id(20),'CASHIER']),/permission denied/);
});
