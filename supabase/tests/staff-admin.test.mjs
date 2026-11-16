import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHandler} from '../functions/staff-admin/handler.mjs';
const job={operation_id:'operation',user_id:'new-user',email:'new@example.invalid',completed:false};
const input={action:'create',operation_id:'operation',device_id:'device',device_secret:'proof',email:job.email,full_name:'Staff',role:'CASHIER',password:'temporary-1234'};
const json=(value,status=200)=>new Response(JSON.stringify(value),{status});
function fixture({denied=false,completed=false,owned=true,existing=false,finishFailure=false,failCreate=false,loseCreateResponse=false}={}) {
 const calls=[];let made=existing;
 const user={id:job.user_id,email:job.email,app_metadata:{pos_provision_operation:owned?job.operation_id:'foreign'}};
 const handler=createHandler({url:'https://test.invalid',publicKey:'public-fixture',serviceKey:'admin-fixture',fetcher:async(url,opts)=>{
  const path=new URL(url).pathname;calls.push({path,opts});
  if(path==='/auth/v1/user')return json({id:'actor',email:'actor@example.invalid'});
  if(path.endsWith('prepare_staff_account'))return denied?json({},403):json({...job,completed});
  if(path==='/auth/v1/admin/users/new-user'&&opts.method==='GET')return made?json(user):json({},404);
  if(path==='/auth/v1/admin/users'&&opts.method==='POST'){if(failCreate)return json({},422);made=true;if(loseCreateResponse){loseCreateResponse=false;throw Error('simulated lost response');}return json(user);}
  if(path.endsWith('finish_staff_account'))return finishFailure?json({},503):json({status:'CREATED',operation_id:job.operation_id,user_id:job.user_id,outlet_id:'outlet',role:'CASHIER'});
  throw Error('Unexpected call');
 }});
 const send=(body=input)=>handler(new Request('https://edge.invalid',{method:'POST',headers:{Authorization:'Bearer caller-fixture'},body:JSON.stringify(body)}));
 return {calls,send};
}
test('verifies JWT and role/device before Auth admin create; forwards only allowlisted fields',async()=>{
 const f=fixture();const r=await f.send({...input,serviceKey:'evil',roleOverride:'OWNER'});assert.equal(r.status,200);
 assert.deepEqual(f.calls.map(c=>c.path),['/auth/v1/user','/rest/v1/rpc/prepare_staff_account','/auth/v1/admin/users/new-user','/auth/v1/admin/users','/rest/v1/rpc/finish_staff_account']);
 const admin=f.calls.find(c=>c.path==='/auth/v1/admin/users');const payload=JSON.parse(admin.opts.body);
 assert.equal(payload.id,job.user_id);assert.equal(payload.email_confirm,true);assert.equal(payload.role,undefined);assert.equal(payload.serviceKey,undefined);
 const reserved=f.calls.find(c=>c.path.endsWith('prepare_staff_account'));assert.equal(JSON.parse(reserved.opts.body).password,undefined);
 assert.equal(reserved.opts.headers.Authorization,'Bearer caller-fixture');assert.equal(admin.opts.headers.Authorization,'Bearer admin-fixture');
 const denied=fixture({denied:true});assert.equal((await denied.send()).status,403);assert.equal(denied.calls.length,2);
});
test('retries recover original identity without updating its password; completed command skips Auth entirely',async()=>{
 const f=fixture({existing:true});assert.equal((await f.send()).status,200);assert.equal(f.calls.filter(c=>c.opts.method==='POST'&&c.path==='/auth/v1/admin/users').length,0);
 assert.ok(!f.calls.some(c=>c.opts.method==='PUT'));
 const completed=fixture({completed:true});assert.equal((await completed.send()).status,200);assert.ok(!completed.calls.some(c=>c.path.includes('/admin/')));
});
test('lost Auth create response retries the reserved identity once without password reset',async()=>{
 const f=fixture({loseCreateResponse:true});assert.equal((await f.send()).status,503);
 assert.equal((await f.send()).status,200);
 assert.equal(f.calls.filter(c=>c.path==='/auth/v1/admin/users').length,1);
 assert.ok(!f.calls.some(c=>c.opts.method==='PUT'||c.opts.method==='DELETE'));
});
test('identity conflicts and Auth failures never finalize; lost finalization remains retryable',async()=>{
 for(const options of [{existing:true,owned:false},{failCreate:true}]) {
  const f=fixture(options);assert.equal((await f.send()).status,options.failCreate?422:409);assert.ok(!f.calls.some(c=>c.path.endsWith('finish_staff_account')));
 }
 const f=fixture({finishFailure:true});assert.equal((await f.send()).status,503);assert.ok(!f.calls.some(c=>c.opts.method==='DELETE'));
});
test('method, token, body and temporary-password validation are bounded',async()=>{
 let calls=0;const h=createHandler({url:'https://test.invalid',publicKey:'public',serviceKey:'secret',fetcher:async()=>{calls++;return json({id:'actor'});}});
 assert.equal((await h(new Request('https://edge.invalid'))).status,405);
 assert.equal((await h(new Request('https://edge.invalid',{method:'POST',body:'{}'}))).status,401);assert.equal(calls,0);
 for(const body of ['bad','"value"','x'.repeat(8200),JSON.stringify({...input,password:'short'}),JSON.stringify({...input,password:'😀'.repeat(30)})]) {
  const r=await h(new Request('https://edge.invalid',{method:'POST',headers:{Authorization:'Bearer fixture'},body}));assert.ok([413,422].includes(r.status));
 }
});
test('password replacement reauthenticates current password, updates Auth then clears server gate',async()=>{
 const calls=[];const h=createHandler({url:'https://test.invalid',publicKey:'public',serviceKey:'secret',fetcher:async(url,opts)=>{
  const path=new URL(url).pathname;calls.push(path);
  if(path==='/auth/v1/user')return json({id:'actor',email:'actor@example.invalid'});
  if(path.endsWith('staff_password_change_required'))return json(true);
  if(path==='/auth/v1/token')return json({user:{id:'actor'},access_token:'extra-session'});
  if(path.startsWith('/auth/v1/admin/users/'))return json({id:'actor'});
  return json(null);
 }});
 const r=await h(new Request('https://edge.invalid',{method:'POST',headers:{Authorization:'Bearer fixture'},body:JSON.stringify({action:'change_password',current_password:'temporary-1234',password:'personal-5678'})}));
 assert.equal(r.status,200);assert.ok(calls.indexOf('/auth/v1/admin/users/actor')<calls.indexOf('/rest/v1/rpc/complete_staff_password_change'));
});
test('incorrect current password and failed Auth update never clear password gate',async()=>{
 for(const failAt of ['verify','update']) {
  let cleared=false;const h=createHandler({url:'https://test.invalid',publicKey:'public',serviceKey:'secret',fetcher:async(url)=>{
   if(url.endsWith('/auth/v1/user'))return json({id:'actor',email:'actor@example.invalid'});
   if(url.endsWith('staff_password_change_required'))return json(true);
   if(url.includes('/auth/v1/token'))return failAt==='verify'?json({},400):json({user:{id:'actor'}});
   if(url.includes('/auth/v1/admin/users'))return json({},422);
   cleared=true;return json(null);
  }});
  const r=await h(new Request('https://edge.invalid',{method:'POST',headers:{Authorization:'Bearer fixture'},body:JSON.stringify({action:'change_password',current_password:'temporary-1234',password:'personal-5678'})}));
  assert.equal(r.status,422);assert.equal(cleared,false);
 }
});

test('quota and unconfigured codes remain distinct; neither calls Auth admin creation',async()=>{
 for(const code of ['STAFF_LIMIT_REACHED','STAFF_LIMITS_UNCONFIGURED']) {
  let adminCalled=false;
  const handler=createHandler({url:'https://test.invalid',publicKey:'public',serviceKey:'secret',fetcher:async(url)=>{
   if(url.endsWith('/auth/v1/user'))return json({id:'actor'});
   if(url.endsWith('prepare_staff_account'))return json({code:'PT409',message:code},409);
   adminCalled=true;throw Error('Auth admin must not be called');
  }});
  const result=await handler(new Request('https://edge.invalid',{method:'POST',headers:{Authorization:'Bearer fixture'},body:JSON.stringify(input)}));
  assert.equal(result.status,409);assert.equal((await result.json()).code,code);assert.equal(adminCalled,false);
 }
});
