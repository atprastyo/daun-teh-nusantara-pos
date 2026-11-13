import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createInviteHandler } from '../functions/invite-staff/handler.ts';
const cfg={url:'https://example.invalid',publicKey:'test-public',serviceKey:'server-only-test-value'};
const body={email:'new@example.invalid',outlet_id:'00000000-0000-4000-8000-000000000020',role:'CASHIER'};
const req=(data=body,headers={Authorization:'Bearer test-user'})=>new Request(cfg.url,{method:'POST',headers,body:JSON.stringify(data)});
test('unauthenticated invite never calls admin API',async()=>{
 let calls=0; const handler=createInviteHandler(cfg,async()=>{calls++;throw Error('unexpected');});
 assert.equal((await handler(req(body,{}))).status,401);assert.equal(calls,0);
});
test('client owner role cannot bypass server outlet authorization',async()=>{
 let calls=[]; const handler=createInviteHandler(cfg,async(url,init)=>{
  calls.push({url,init});return url.endsWith('/user')?Response.json({id:'verified-user'}):new Response('',{status:403});
 });
 assert.equal((await handler(req({...body,role:'MANAGER',actor_role:'OWNER'}))).status,403);
 assert.equal(calls.length,2);assert.ok(calls.every(c=>!c.url.endsWith('/invite')));
 assert.ok(calls.every(c=>!JSON.stringify(c.init).includes(cfg.serviceKey)));
});
test('only authorized normalized SQL email reaches Auth admin',async()=>{
 const calls=[]; const handler=createInviteHandler(cfg,async(url,init)=>{
  calls.push({url,init});
  if(url.endsWith('/user'))return Response.json({id:'verified-user'});
  if(url.includes('/rpc/'))return Response.json({id:'intent-id',email:'normalized@example.invalid'});
  return Response.json({id:'invited-user'});
 });
 assert.equal((await handler(req())).status,200);
 assert.equal(calls.length,3);assert.equal(JSON.parse(calls[2].init.body).email,'normalized@example.invalid');
 assert.equal(calls[2].init.headers.Authorization,'Bearer '+cfg.serviceKey);
});
test('uncertain delivery is not claimed successful or exposed in error body',async()=>{
 const handler=createInviteHandler(cfg,async(url)=>{
  if(url.endsWith('/user'))return Response.json({id:'verified'});
  if(url.includes('/rpc/'))return Response.json({id:'intent',email:body.email});
  return new Response('secret internal provider response',{status:500});
 });
 const response=await handler(req());assert.equal(response.status,502);
 const text=await response.text();assert.ok(!text.includes('secret'));assert.ok(!text.includes(body.email));
});
test('owner role grants and malformed identities are rejected before network',async()=>{
 let calls=0;const handler=createInviteHandler(cfg,async()=>{calls++;throw Error();});
 assert.equal((await handler(req({...body,role:'OWNER'}))).status,400);
 assert.equal((await handler(req({...body,outlet_id:'invalid'}))).status,400);assert.equal(calls,0);
});

test('malformed and oversized JSON never reaches any upstream service',async()=>{
 let calls=0;const handler=createInviteHandler(cfg,async()=>{calls++;throw Error();});
 assert.equal((await handler(new Request(cfg.url,{method:'POST',headers:{Authorization:'Bearer test'},body:'null'}))).status,400);
 assert.equal((await handler(new Request(cfg.url,{method:'POST',headers:{Authorization:'Bearer test'},body:'x'.repeat(5000)}))).status,413);
 assert.equal(calls,0);
});
test('invitation conflicts and rate limits preserve their status',async()=>{
 for(const status of [409,429]) {
  const handler=createInviteHandler(cfg,async(url)=>url.endsWith('/user')?Response.json({id:'verified'}):new Response('',{status}));
  assert.equal((await handler(req())).status,status);
 }
});
