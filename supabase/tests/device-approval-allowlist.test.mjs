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
 // Exercise the retained manual-approval policy in these regression scenarios.
 await db.exec("update private.device_approval_policy set auto_approve_registered=false,reason='Manual approval regression tests' where singleton");
 await db.exec(await readFile(new URL('../staff-provisioning.sql',import.meta.url),'utf8'));
 for(let n=1;n<=4;n++)await db.query('insert into auth.users(id,email,email_confirmed_at) values($1,$2,now())',[id(n),`user${n}@example.invalid`]);
 await db.exec(`insert into public.user_profiles(id) select id from auth.users;
 insert into public.brands(id,name) values('${id(10)}','Tea');
 insert into public.outlets(id,brand_id,name) values('${id(20)}','${id(10)}','A'),('${id(21)}','${id(10)}','B');
 insert into public.brand_members(user_id,brand_id) values('${id(1)}','${id(10)}');
 insert into public.outlet_members(user_id,outlet_id,role) values('${id(2)}','${id(20)}','MANAGER'),('${id(3)}','${id(20)}','CASHIER'),('${id(3)}','${id(21)}','CASHIER');`);
});
afterEach(async()=>{await db.close();});

test('default still pending; matching allowlist approves new Manager/Cashier devices and audits without repeat approvals',async()=>{
 const pending=await enroll();await assert.rejects(lease(pending.device_secret),/approval/);
 for(const [user,device,role] of [[2,42,'MANAGER'],[3,44,'CASHIER']]){
  await policy({user,role});const d=await enroll(user,device);
  assert.equal((await lease(d.device_secret,user,device)).role,role);
  await lease(d.device_secret,user,device);
  const approved=(await admin(`select approved_role,approved_by from public.devices where id='${id(device)}'`))[0].rows[0];
  assert.equal(approved.approved_role,role);assert.equal(approved.approved_by,id(user));
  const events=(await admin(`select metadata from public.audit_logs where action='DEVICE_APPROVE' and target_id='${id(device)}'`))[0].rows;
  assert.equal(events.length,1);assert.equal(events[0].metadata.source,'ADMIN_ALLOWLIST');
 }
});
test('existing pending device approves on valid lease retry, preserves proof and disappears from pending list',async()=>{
 const d=await enroll();const before=(await admin(`select secret_hash,version from public.devices where id='${id(43)}'`))[0].rows[0];
 await policy();await assert.rejects(lease('wrong'),/denied/);
 assert.equal((await lease(d.device_secret)).role,'CASHIER');
 const after=(await admin(`select secret_hash,version from public.devices where id='${id(43)}'`))[0].rows[0];
 assert.equal(after.secret_hash,before.secret_hash);assert.equal(after.version,before.version+1);
 assert.deepEqual(await rpc(1,'list_device_requests',{p_outlet_id:id(20)}),[]);
});
test('outlet, role, enabled and expiry scope cannot be bypassed',async()=>{
 await policy({outlet:21});let d=await enroll();await assert.rejects(lease(d.device_secret),/approval/);
 await admin('delete from private.device_approval_allowlist');
 await policy({expires:"now()-interval '1 minute'"});d=await enroll(3,44);await assert.rejects(lease(d.device_secret,3,44),/approval/);
 await admin('delete from private.device_approval_allowlist');await policy({enabled:false});d=await enroll(3,45);await assert.rejects(lease(d.device_secret,3,45),/approval/);
 await admin("update private.device_approval_allowlist set enabled=true,expires_at=null");
 d=await enroll(3,46);await lease(d.device_secret,3,46);
 await admin(`update public.outlet_members set role='MANAGER' where user_id='${id(3)}' and outlet_id='${id(20)}'`);
 await assert.rejects(lease(d.device_secret,3,46),/approval/);
});
test('revocation, inactive membership, foreign identity and temporary-password gate still deny',async()=>{
 await policy();const d=await enroll();await assert.rejects(lease(d.device_secret,2),/denied/);
 await admin(`update public.outlet_members set active=false where user_id='${id(3)}' and outlet_id='${id(20)}'`);
 await assert.rejects(lease(d.device_secret),/Forbidden/);
 await admin(`update public.outlet_members set active=true where user_id='${id(3)}' and outlet_id='${id(20)}';update public.devices set revoked_at=now() where id='${id(43)}'`);
 await assert.rejects(lease(d.device_secret),/denied/);await assert.rejects(enroll(),/denied/);
 await admin(`insert into public.devices(id,outlet_id,enrolled_by,secret_hash,approved_at,approved_role) values('${id(40)}','${id(20)}','${id(1)}',repeat('a',64),now(),'OWNER');
 insert into private.staff_provision_operations(id,actor_id,device_id,brand_id,outlet_id,target_id,email,full_name,role,completed_at)
 values('${id(60)}','${id(1)}','${id(40)}','${id(10)}','${id(20)}','${id(3)}','user3@example.invalid','Cashier','CASHIER',now());`);
 await assert.rejects(enroll(3,47),/Forbidden/);
});
test('client roles including Owner cannot manage/read the administrative allowlist',async()=>{
 for(const user of [1,2,3]){
  await assert.rejects(as(user,'select * from private.device_approval_allowlist'),/permission denied/);
  await assert.rejects(as(user,'select * from private.device_approval_allowlist_audit'),/permission denied/);
  await assert.rejects(as(user,`insert into private.device_approval_allowlist(user_id,outlet_id,role,reason) values('${id(3)}','${id(20)}','CASHIER','Escalation')`),/permission denied/);
  await assert.rejects(as(user,`select private.device_approval_allowlisted('${id(3)}','${id(20)}','CASHIER')`),/permission denied/);
 }
 await admin('set role anon');await assert.rejects(db.query('select * from private.device_approval_allowlist'),/permission denied/);
});
test('removal stops future automatic approvals; explicit device revocation still stops prior approvals',async()=>{
 await policy();const d=await enroll();await lease(d.device_secret);
 await admin('delete from private.device_approval_allowlist');await lease(d.device_secret);
 const next=await enroll(3,44);await assert.rejects(lease(next.device_secret,3,44),/approval/);
 await admin(`update public.devices set revoked_at=now() where id='${id(43)}'`);await assert.rejects(lease(d.device_secret),/denied/);
 const audit=(await admin("select action from private.device_approval_allowlist_audit where action like 'DEVICE_ALLOWLIST_%'"))[0].rows;
 assert.ok(audit.some(x=>x.action==='DEVICE_ALLOWLIST_INSERT'));assert.ok(audit.some(x=>x.action==='DEVICE_ALLOWLIST_DELETE'));
});
test('standalone deployment matches fresh schema and preserves existing policy records when reapplied',async()=>{
 await policy();const before=(await db.query("select prosrc from pg_proc where oid='public.issue_offline_lease(uuid,text)'::regprocedure")).rows[0].prosrc;
 const setup=await readFile(new URL('../device-approval-allowlist.sql',import.meta.url),'utf8');
 await admin(setup);await admin(setup);
 assert.equal((await db.query("select prosrc from pg_proc where oid='public.issue_offline_lease(uuid,text)'::regprocedure")).rows[0].prosrc,before);
 assert.equal((await db.query('select count(*)::int n from private.device_approval_allowlist')).rows[0].n,1);
 const d=await enroll();assert.equal((await lease(d.device_secret)).role,'CASHIER');
});

test('trusted SQL administration without Auth identity has accurate private audit attribution',async()=>{
 await admin("select set_config('request.jwt.claim.sub','',false)");await policy();
 const row=(await admin('select database_actor,auth_actor,action from private.device_approval_allowlist_audit'))[0].rows[0];
 assert.ok(row.database_actor);assert.equal(row.auth_actor,null);assert.equal(row.action,'DEVICE_ALLOWLIST_INSERT');
});
test('standalone deployment installs absent policy objects and retains a legacy pending enrollment',async()=>{
 await admin(`drop trigger device_approval_allowlist_audit on private.device_approval_allowlist;
 drop function private.audit_device_approval_allowlist();
 drop function private.device_approval_allowlisted(uuid,uuid,text);
 drop table private.device_approval_allowlist;drop table private.device_approval_allowlist_audit;
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash) values('${id(43)}','${id(20)}','${id(3)}',encode(sha256(convert_to('legacy-proof','UTF8')),'hex'));`);
 await admin(await readFile(new URL('../device-approval-allowlist.sql',import.meta.url),'utf8'));
 await policy();assert.equal((await lease('legacy-proof')).role,'CASHIER');
 assert.equal((await admin('select count(*)::int n from public.devices'))[0].rows[0].n,1);
});
