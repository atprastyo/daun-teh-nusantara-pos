import {test,before,after} from 'node:test';
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
before(async()=>{
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
 insert into public.outlet_staff_limits(outlet_id,manager_limit,cashier_limit) select id,100,100 from public.outlets;
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER');`);
 for(const [d,u,o,r] of [[40,1,20,'OWNER'],[41,2,20,'MANAGER'],[42,3,20,'CASHIER'],[43,1,21,'OWNER'],[45,5,22,'OWNER']])await db.query(`insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_role) values($1,$2,$3,encode(sha256(convert_to('proof','UTF8')),'hex'),now(),$4)`,[id(d),id(o),id(u),r]);
});
after(async()=>db.close());
test('owner can create manager/cashier, manager only cashier; cashier/owner-role/foreign device denied',async()=>{
 assert.ok((await prepare(100,'MANAGER')).user_id);
 assert.ok((await prepare(101,'CASHIER',2,41)).user_id);
 await assert.rejects(prepare(102,'MANAGER',2,41),/forbidden/);
 await assert.rejects(prepare(103,'CASHIER',3,42),/forbidden/);
 await assert.rejects(prepare(104,'OWNER'),/forbidden/);
 await assert.rejects(prepare(105,'CASHIER',2,43),/Device denied/);
 await assert.rejects(prepare(106,'CASHIER',1,45),/Device denied/);
});
test('operation payload immutable; duplicate email and existing identities never attach or reset',async()=>{
 const a=await prepare(110);assert.deepEqual(await prepare(110),a);
 await assert.rejects(rpc(1,'prepare_staff_account',{...request(110),p_role:'MANAGER'}),/conflict/);
 await assert.rejects(rpc(1,'prepare_staff_account',{...request(111),p_email:'NEW110@example.invalid'}),/Email unavailable/);
 await assert.rejects(rpc(1,'prepare_staff_account',{...request(112),p_email:'staff3@example.invalid'}),/Email unavailable/);
 await assert.rejects(rpc(2,'prepare_staff_account',{...request(110,'CASHIER',41)}),/conflict/);
});
test('finish checks Auth ownership, commits once; password gate blocks all role/RLS/lease until trusted replacement',async()=>{
 const job=await prepare(120,'MANAGER');const finish=()=>rpc(1,'finish_staff_account',{...proof(),p_operation_id:id(120)});
 await assert.rejects(finish(),/Auth identity not confirmed/);await authIdentity(job,'wrong');
 await assert.rejects(finish(),/Auth identity not confirmed/);
 await admin(`update auth.users set raw_app_meta_data='${JSON.stringify({pos_provision_operation:job.operation_id})}' where id='${job.user_id}'`);
 const result=await finish();assert.equal(result.status,'CREATED');assert.deepEqual(await finish(),result);
 assert.equal((await admin(`select count(*)::int n from public.audit_logs where target_id='${job.user_id}'`))[0].rows[0].n,1);
 await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[job.user_id]);await db.exec('set role authenticated');
 assert.equal((await db.query('select public.staff_password_change_required() v')).rows[0].v,true);
 assert.equal((await db.query('select count(*)::int n from public.get_access_context()')).rows[0].n,0);
 await assert.rejects(db.query('select public.enroll_device($1,$2)',[id(80),id(20)]),/Forbidden/);
 await assert.rejects(db.query('select public.complete_staff_password_change($1)',[job.user_id]),/permission denied/);
 await rpc(1,'complete_staff_password_change',{p_user_id:job.user_id},true);
 await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[job.user_id]);await db.exec('set role authenticated');
 assert.equal((await db.query('select public.staff_password_change_required() v')).rows[0].v,false);
 assert.equal((await db.query('select role from public.get_access_context()')).rows[0].role,'MANAGER');
 // Creation does not enroll or approve any device automatically.
 assert.equal((await db.query('select count(*)::int n from public.devices where enrolled_by=$1',[job.user_id])).rows[0].n,0);
});
test('revocation between Auth and finalization fails closed without profile or audit',async()=>{
 const job=await prepare(130,'CASHIER',2,41);await authIdentity(job);
 await admin(`update public.outlet_members set active=false where user_id='${id(2)}'`);
 await assert.rejects(rpc(2,'finish_staff_account',{...proof(41),p_operation_id:id(130)}),/Forbidden/);
 assert.equal((await admin(`select count(*)::int n from public.user_profiles where id='${job.user_id}'`))[0].rows[0].n,0);
 await admin(`update public.outlet_members set active=true where user_id='${id(2)}'`);
});
test('private ledger inaccessible, invalid names/emails denied, rate limited under actor lock',async()=>{
 await rpc(1,'staff_password_change_required');await assert.rejects(db.query('select * from private.staff_provision_operations'),/permission denied/);
 await assert.rejects(rpc(1,'prepare_staff_account',{...request(140),p_full_name:' '}),/Invalid staff/);
 await assert.rejects(rpc(1,'prepare_staff_account',{...request(140),p_email:'wrong'}),/Invalid staff/);
 for(let n=150;n<160;n++)await prepare(n,'CASHIER',5,45);
 await assert.rejects(prepare(160,'CASHIER',5,45),/rate limited/);
 assert.ok((await prepare(150,'CASHIER',5,45)).user_id); // Existing operation is still recoverable.
});
