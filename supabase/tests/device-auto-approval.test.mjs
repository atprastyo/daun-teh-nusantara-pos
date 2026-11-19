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
 for(let n=1;n<=4;n++)await db.query('insert into auth.users(id,email,email_confirmed_at) values($1,$2,now())',[id(n),`user${n}@example.invalid`]);
 await db.exec(`insert into public.user_profiles(id) select id from auth.users;
 insert into public.brands(id,name) values('${id(10)}','Tea');
 insert into public.outlets(id,brand_id,name) values('${id(20)}','${id(10)}','A'),('${id(21)}','${id(10)}','B');
 insert into public.brand_members(user_id,brand_id) values('${id(1)}','${id(10)}');
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER'),('${id(3)}','${id(21)}','CASHIER');`);
});
afterEach(async()=>{await db.close();});

test('registered Manager and Cashier automatically approve without an allowlist',async()=>{
 for(const [user,device,role] of [[2,42,'MANAGER'],[3,43,'CASHIER']]){
  const d=await enroll(user,device);assert.equal((await lease(d.device_secret,user,device)).role,role);
  await lease(d.device_secret,user,device);
  const events=(await admin(`select metadata from public.audit_logs where action='DEVICE_APPROVE' and target_id='${id(device)}'`))[0].rows;
  assert.equal(events.length,1);assert.equal(events[0].metadata.source,'REGISTERED_STAFF_POLICY');
 }
 assert.equal((await admin('select count(*)::int n from private.device_approval_allowlist'))[0].rows[0].n,0);
});
test('pending devices auto approve only after valid proof; live role changes update stored approval',async()=>{
 await admin("update private.device_approval_policy set auto_approve_registered=false");const d=await enroll();
 await assert.rejects(lease(d.device_secret),/approval/);
 await admin('update private.device_approval_policy set auto_approve_registered=true');
 await assert.rejects(lease('wrong'),/denied/);await assert.rejects(lease(d.device_secret,2),/denied/);
 assert.equal((await lease(d.device_secret)).role,'CASHIER');
 await admin(`update public.outlet_members set role='MANAGER' where user_id='${id(3)}' and outlet_id='${id(20)}'`);
 assert.equal((await lease(d.device_secret)).role,'MANAGER');
});
test('Owner deactivation revokes existing devices and prevents new enrollment',async()=>{
 const owner=await enroll(1,40);const d=await enroll();await lease(d.device_secret);
 const v=(await admin(`select version from public.outlet_members where user_id='${id(3)}' and outlet_id='${id(20)}'`))[0].rows[0].version;
 await rpc(1,'set_staff_access',{p_device_id:id(40),p_device_secret:owner.device_secret,p_user_id:id(3),p_active:false,p_expected_version:v,p_reason:'Owner disabled staff'});
 await assert.rejects(lease(d.device_secret),/denied|Forbidden/);await assert.rejects(enroll(3,44),/Forbidden/);
 assert.ok((await admin(`select revoked_at from public.devices where id='${id(43)}'`))[0].rows[0].revoked_at);
});
test('membership, account, outlet and password gates remain enforced',async()=>{
 await assert.rejects(enroll(4,44),/Forbidden/);
 await assert.rejects(enroll(2,44,21),/Forbidden/);
 await admin(`update public.user_profiles set active=false where id='${id(3)}'`);
 await assert.rejects(enroll(),/Forbidden/);
 await admin(`update public.user_profiles set active=true where id='${id(3)}';update auth.users set raw_app_meta_data='{"must_change_password":true}' where id='${id(3)}'`);
 // Provisioned temporary-password gate is tested using the canonical provisioning record below.
 const owner=await enroll(1,40);
 await admin(`insert into private.staff_provision_operations(id,actor_id,device_id,brand_id,outlet_id,target_id,email,full_name,role,completed_at)
 values('${id(60)}','${id(1)}','${id(40)}','${id(10)}','${id(20)}','${id(3)}','user3@example.invalid','Cashier','CASHIER',now());`);
 await assert.rejects(enroll(),/Forbidden/);
});
test('revoked UUID stays blocked; active staff may enroll a new device',async()=>{
 const d=await enroll();await admin(`update public.devices set revoked_at=now() where id='${id(43)}'`);
 await assert.rejects(lease(d.device_secret),/denied/);await assert.rejects(enroll(),/denied/);
 const next=await enroll(3,44);assert.equal((await lease(next.device_secret,3,44)).role,'CASHIER');
});
test('administrative policy is private and audited; installers preserve explicit manual mode',async()=>{
 for(const user of [1,2,3]){
  await assert.rejects(as(user,'select * from private.device_approval_policy'),/permission denied/);
  await assert.rejects(as(user,'update private.device_approval_policy set auto_approve_registered=true'),/permission denied/);
  await assert.rejects(as(user,'select * from private.device_approval_policy_audit'),/permission denied/);
 }
 await admin("select set_config('request.jwt.claim.sub','',false);update private.device_approval_policy set auto_approve_registered=false,reason='Manual policy rollback'");
 for(const file of ['device-auto-approval.sql','device-approval-allowlist.sql'])await admin(await readFile(new URL('../'+file,import.meta.url),'utf8'));
 const d=await enroll();await assert.rejects(lease(d.device_secret),/approval/);
 const row=(await admin("select * from private.device_approval_policy_audit where action='DEVICE_POLICY_UPDATE' order by changed_at desc limit 1"))[0].rows[0];
 assert.equal(row.auth_actor,null);assert.ok(row.database_actor);assert.equal(row.metadata.after.auto_approve_registered,false);
});
test('upgrade from previous allowlist setup enables default policy and retains pending devices',async()=>{
 await admin('update private.device_approval_policy set auto_approve_registered=false');const d=await enroll();
 await admin(`drop trigger device_approval_policy_audit on private.device_approval_policy;
 drop function private.audit_device_approval_policy();drop table private.device_approval_policy;drop table private.device_approval_policy_audit;`);
 await admin(await readFile(new URL('../device-auto-approval.sql',import.meta.url),'utf8'));
 assert.equal((await lease(d.device_secret)).role,'CASHIER');
});
