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
 await db.exec(await readFile(new URL('../device-labels.sql',import.meta.url),'utf8'));
 for(let n=1;n<=5;n++)await db.query('insert into auth.users(id,email,email_confirmed_at) values($1,$2,now())',[id(n),`staff${n}@example.invalid`]);
 await db.exec(`insert into public.user_profiles(id,full_name) select id,'Staff' from auth.users;
 insert into public.brands(id,name) values('${id(10)}','Tea'),('${id(11)}','Foreign');
 insert into public.outlets(id,brand_id,name) values('${id(20)}','${id(10)}','A'),('${id(21)}','${id(10)}','B'),('${id(22)}','${id(11)}','Foreign');
 insert into public.brand_members(user_id,brand_id) values('${id(1)}','${id(10)}'),('${id(5)}','${id(11)}');
 insert into public.outlet_staff_limits(outlet_id,manager_limit,cashier_limit) select id,100,100 from public.outlets;
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER');`);
 for(const [d,u,o,r] of [[40,1,20,'OWNER'],[41,2,20,'MANAGER'],[42,3,20,'CASHIER'],[43,1,21,'OWNER'],[45,5,22,'OWNER']])await db.query(`insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_role) values($1,$2,$3,encode(sha256(convert_to('proof','UTF8')),'hex'),now(),$4)`,[id(d),id(o),id(u),r]);
});
afterEach(async()=>db.close());
const write=(user=3,device=42,m='Samsung',n='SM-M146B',secret='proof')=>rpc(user,'set_device_label',{p_device_id:id(device),p_device_secret:secret,p_manufacturer:m,p_model:n});
const labels=(user=1,outlet=20)=>rpc(user,'get_device_labels',{p_outlet_id:id(outlet)});
test('pending own device can report name without changing secret/version/approval',async()=>{
 await admin(`update public.devices set approved_at=null,approved_role=null where id='${id(42)}'`);
 const before=(await admin(`select version,secret_hash,approved_at from public.devices where id='${id(42)}'`))[0].rows[0];
 await write();assert.equal((await labels())[0].device_name,'Samsung SM-M146B');
 const after=(await admin(`select version,secret_hash,approved_at from public.devices where id='${id(42)}'`))[0].rows[0];assert.deepEqual(after,before);
 await assert.rejects(rpc(3,'issue_offline_lease',proof(42)),/approval/);
 await rpc(1,'approve_device',{p_device_id:id(42)});assert.equal((await rpc(3,'issue_offline_lease',proof(42))).role,'CASHIER');
});
test('only matching own proof writes; revoked/foreign devices and cashier/foreign outlet reads denied',async()=>{
 await assert.rejects(write(2,42),/Device denied/);await assert.rejects(write(3,42,'Samsung','SM-M146B','wrong'),/Device denied/);
 await assert.rejects(labels(3),/Forbidden/);await assert.rejects(labels(5,20),/Forbidden/);
 await admin(`update public.devices set revoked_at=now() where id='${id(42)}'`);await assert.rejects(write(),/Device denied/);
});
test('manager receives cashier labels only; blank/duplicate model prefix and bounded values handled',async()=>{
 await write(1,40,'Owner','Device');await write(2,41,'Manager','Device');await write();
 assert.equal((await labels()).length,3);assert.deepEqual((await labels(2)).map(x=>x.device_id),[id(42)]);
 await write(3,42,'Samsung','Samsung Model');assert.equal((await labels(2))[0].device_name,'Samsung Model');
 await write(3,42,'','');assert.equal((await labels(2))[0].device_name,null);
 await assert.rejects(write(3,42,'x'.repeat(81),'Model'),/Invalid device label/);
 await assert.rejects(write(3,42,'Samsung','Line\nBreak'),/Invalid device label/);
 await rpc(3,'staff_password_change_required');await assert.rejects(db.query('select * from public.device_labels'),/permission denied/);
});
