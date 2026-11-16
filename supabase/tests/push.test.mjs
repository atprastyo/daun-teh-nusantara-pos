import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
const id=n=>`10000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const epochs=new Map();
let db; let sequence=100; let productVersion=1;let optionVersion=1;
const user=id(1),brand=id(2),outlet=id(3),device=id(4),category=id(5),product=id(6),group=id(7),option=id(8);
async function admin(sql){await db.exec('reset role');return db.exec(sql);}
async function submit(p,secret='proof',actor=user){
 await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[actor]);await db.exec('set role authenticated');
 return (await db.query('select public.submit_sale($1::text,$2) as result',[JSON.stringify(p),secret])).rows[0].result;
}
function payload(){return {schema_version:1,demo:false,operation_id:id(sequence++),sale_id:id(sequence++),brand_id:brand,outlet_id:outlet,device_id:device,cashier_user_id:user,
 created_at_utc_ms:Date.now(),subtotal_idr:14000,discount_idr:0,total_idr:14000,
 items:[{id:id(sequence++),product_id:product,product_name:'Tea',product_version:productVersion,category_version:1,unit_base_price_idr:6000,quantity:2,line_total_idr:14000,
 groups:[{id:group,version:1}],modifiers:[{option_id:option,group_id:group,group_name:'Size',option_name:'Large',price_delta_idr:1000,option_version:optionVersion}]}],
 payment:{method:'CASH',amount_idr:14000,tendered_idr:20000,change_idr:6000,verification:'CASH_RECEIVED'}};}
async function count(table){return (await admin(`select count(*)::int n from public.${table}${table==='sync_changes'?" where entity_type='SALE'":""}`))[0].rows[0].n;}
before(async()=>{
 db=new PGlite();await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;create schema auth;
 create table auth.users(id uuid primary key,email text,email_confirmed_at timestamptz);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth to authenticated;grant execute on function auth.uid() to authenticated;`);
 await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
 await db.exec(`insert into auth.users values('${user}','test@example.invalid',now());insert into public.user_profiles(id) values('${user}');
 insert into public.brands(id,name) values('${brand}','Tea');insert into public.outlets(id,brand_id,name) values('${outlet}','${brand}','Outlet');
 insert into public.outlet_members values('${user}','${outlet}','CASHIER',true);
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash,enrolled_at,approved_at,approved_role) values('${device}','${outlet}','${user}',encode(sha256(convert_to('proof','UTF8')),'hex'),now()-interval '1 day',now(),'CASHIER');
 insert into public.categories(id,brand_id,name) values('${category}','${brand}','Tea');
 insert into public.products(id,brand_id,name,category_id,base_price_idr) values('${product}','${brand}','Tea','${category}',6000);
 insert into public.modifier_groups(id,brand_id,name,min_select,max_select) values('${group}','${brand}','Size',1,1);
 insert into public.modifier_options(id,brand_id,name,group_id,price_delta_idr) values('${option}','${brand}','Large','${group}',1000);
 insert into public.product_modifier_groups(product_id,group_id,brand_id) values('${product}','${group}','${brand}');`);
});
after(async()=>db.close());
test('ack loss and repeated retries create exactly one graph and change',async()=>{
 const p=payload();const a=await submit(p);assert.equal(a.status,'ACCEPTED');
 for(let i=0;i<5;i++)assert.deepEqual(await submit(p),a);
 for(const table of ['sales','sale_items','sale_item_modifiers','payments','sync_operations','sync_changes']) assert.equal(await count(table),1,table);
});
test('canonical key ordering dedupes, changed payload conflicts and is audited',async()=>{
 const p=payload(),a=await submit(p);assert.equal(a.status,'ACCEPTED');
 assert.equal((await submit(Object.fromEntries(Object.entries(p).reverse()))).request_hash,a.request_hash);
 p.payment.tendered_idr=30000;p.payment.change_idr=16000;
 assert.equal((await submit(p)).code,'OPERATION_CONFLICT');assert.equal(await count('sales'),2);
 assert.equal(await count('audit_logs'),1);
});
test('same sale under new operation does not duplicate',async()=>{const p=payload();await submit(p);p.operation_id=id(sequence++);assert.equal((await submit(p)).code,'SALE_CONFLICT');});
test('ack retry succeeds after catalog changes; new stale sale is held for review unchanged',async()=>{
 const p=payload();await submit(p);await admin(`update public.products set base_price_idr=7000,version=2 where id='${product}'`);
 assert.equal((await submit(p)).status,'ACCEPTED');const n=await count('sales');assert.equal((await submit(payload())).code,'CATALOG_CHANGED');assert.equal(await count('sales'),n);
 await admin(`update public.products set base_price_idr=6000 where id='${product}'`);productVersion=3;
});
test('scope, wrong device proof, revoked device and disabled membership are denied even for duplicates',async()=>{
 const p=payload();await submit(p);await assert.rejects(submit(p,'wrong'),/Device denied/);
 await assert.rejects(submit({...p,cashier_user_id:id(99)}),/Scope denied/);
 await assert.rejects(submit({...p,outlet_id:id(99)}),/Scope denied/);
 await admin(`update public.devices set revoked_at=now() where id='${device}'`);await assert.rejects(submit(p),/Device denied/);
 await admin(`update public.devices set revoked_at=null where id='${device}';update public.outlet_members set active=false`);
 await assert.rejects(submit(p),/Forbidden/);await admin('update public.outlet_members set active=true');
});
test('changed role requires approval again',async()=>{
 await admin("update public.outlet_members set role='MANAGER'");await assert.rejects(submit(payload()),/approval/);await admin("update public.outlet_members set role='CASHIER'");
});
test('malformed totals, payment, demo, missing fields and duplicate lines roll back entire graph',async()=>{
 const n=await count('sales');
 for(const change of [p=>p.total_idr=1,p=>p.payment.change_idr=0,p=>p.demo=true,p=>delete p.items[0].quantity,p=>p.items.push(p.items[0]),p=>p.items[0].quantity=2.5,p=>p.created_at_utc_ms=0]){
 const p=payload();change(p);assert.equal((await submit(p)).status,'REVIEW');assert.equal(await count('sales'),n);assert.equal(await count('payments'),n);assert.equal(await count('sync_changes'),n);
 }
});
test('invalid modifier counts, missing groups and inactive option are rejected',async()=>{
 for(const change of [p=>p.items[0].modifiers=[],p=>p.items[0].groups=[],p=>p.items[0].modifiers.push(p.items[0].modifiers[0])]){const p=payload();change(p);assert.equal((await submit(p)).status,'REVIEW');}
 await admin(`update public.modifier_options set active=false where id='${option}'`);assert.equal((await submit(payload())).code,'CATALOG_CHANGED');await admin('update public.modifier_options set active=true');optionVersion=3;
});
test('manual QRIS requires explicit cashier verification and cannot carry cash fields',async()=>{
 const p=payload();p.payment={method:'QRIS_MANUAL',amount_idr:14000,tendered_idr:null,change_idr:null,verification:'CASHIER_CONFIRMED_MANUALLY'};
 assert.equal((await submit(p)).status,'ACCEPTED');const bad=payload();bad.payment={...p.payment,verification:'PAID'};assert.equal((await submit(bad)).status,'REVIEW');
});


async function pull(deviceId=device,role='CASHIER',after=0,actor=user,secret='proof',limit=2){
 await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[actor]);await db.exec('set role authenticated');
 const result=(await db.query('select public.pull_changes($1,$2,$3,$4,$5,$6) result',[deviceId,secret,after,role,limit,epochs.get(deviceId)??null])).rows[0].result;epochs.set(deviceId,result.feed_epoch);return result;
}
async function allChanges(deviceId=device,role='CASHIER',actor=user){
 let after=0;const rows=[];
 for(let i=0;i<100;i++){
 const page=await pull(deviceId,role,after,actor);assert.equal(page.after_seq,after);
 for(const c of page.changes){assert.ok(c.seq>after);rows.push(c);after=c.seq;}
 assert.ok(page.next_seq>=after);after=page.next_seq;
 if(!page.has_more)return {rows,after};
 }
 assert.fail('Pagination did not terminate');
}
test('catalog triggers publish versioned updates and assignment tombstones atomically',async()=>{
 await admin(`update public.product_modifier_groups set active=false where product_id='${product}'`);
 const {rows}=await allChanges();const links=rows.filter(c=>c.entity_type==='CATALOG'&&c.payload.table==='product_modifier_groups');
 assert.equal(links.at(-1).action,'DELETE');assert.equal(links.at(-1).payload.record.version,2);
 await admin(`update public.product_modifier_groups set active=true where product_id='${product}'`);
 await assert.rejects(admin(`delete from public.products where id='${product}'`),/tombstones/);
 const n=(await admin('select count(*)::int n from public.sync_changes'))[0].rows[0].n;
 await assert.rejects(admin(`begin;update public.products set name='Rolled back' where id='${product}';select 1/0;commit;`));
 await db.exec('rollback');assert.equal((await admin('select count(*)::int n from public.sync_changes'))[0].rows[0].n,n);
 assert.equal((await admin(`select name from public.products where id='${product}'`))[0].rows[0].name,'Tea');
});
test('same-user devices converge; cashier sees own history and manager sees the outlet',async()=>{
 await admin(`insert into auth.users values('${id(20)}','manager@example.invalid',now()),('${id(21)}','peer@example.invalid',now());
 insert into public.user_profiles(id) values('${id(20)}'),('${id(21)}');
 insert into public.outlet_members values('${id(20)}','${outlet}','MANAGER',true),('${id(21)}','${outlet}','CASHIER',true);
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash,enrolled_at,approved_at,approved_role) values
 ('${id(22)}','${outlet}','${id(20)}',encode(sha256(convert_to('proof','UTF8')),'hex'),now()-interval '1 day',now(),'MANAGER'),
 ('${id(23)}','${outlet}','${id(21)}',encode(sha256(convert_to('proof','UTF8')),'hex'),now()-interval '1 day',now(),'CASHIER'),
 ('${id(24)}','${outlet}','${user}',encode(sha256(convert_to('proof','UTF8')),'hex'),now()-interval '1 day',now(),'CASHIER');`);
 const own=payload();own.device_id=id(24);assert.equal((await submit(own)).status,'ACCEPTED');
 const peer=payload();peer.device_id=id(23);peer.cashier_user_id=id(21);assert.equal((await submit(peer,'proof',id(21))).status,'ACCEPTED');
 const a=await allChanges(),b=await allChanges(id(24));assert.deepEqual(a,b);
 assert.ok(a.rows.some(c=>c.entity_id===own.sale_id));assert.ok(!a.rows.some(c=>c.entity_id===peer.sale_id));
 const manager=await allChanges(id(22),'MANAGER',id(20));assert.ok(manager.rows.some(c=>c.entity_id===peer.sale_id));
 assert.ok(manager.rows.filter(c=>c.entity_type==='SALE').every(c=>c.payload.outlet_id===outlet));
});
test('hidden feed gaps advance safely without leaking foreign catalog or sales',async()=>{
 const before=await allChanges();
 await admin(`insert into public.brands(id,name) values('${id(30)}','Foreign');
 insert into public.categories(id,brand_id,name) values('${id(31)}','${id(30)}','Foreign tea');`);
 const page=await pull(device,'CASHIER',before.after);assert.deepEqual(page.changes,[]);assert.equal(page.has_more,false);assert.ok(page.next_seq>before.after);
 assert.ok(!(await allChanges()).rows.some(c=>c.entity_id===id(31)));
});
test('pull rejects changed role, future cursors, invalid proof and revoked membership',async()=>{
 await assert.rejects(pull(device,'MANAGER'),/Scope changed/);
 await assert.rejects(pull(device,'CASHIER',999999),/Cursor ahead/);
 await assert.rejects(pull(device,'CASHIER',0,user,'wrong'),/Device denied/);
 await admin(`update public.outlet_members set active=false where user_id='${user}'`);await assert.rejects(pull(),/Forbidden/);
 await admin(`update public.outlet_members set active=true where user_id='${user}'`);
 await admin(`update public.devices set revoked_at=now() where id='${device}'`);await assert.rejects(pull(),/Device denied/);
 await admin(`update public.devices set revoked_at=null where id='${device}'`);
});
test('re-enrollment rotates proof and requires fresh non-owner approval',async()=>{
 const p=payload();await submit(p);
 await admin('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[user]);await db.exec('set role authenticated');
 const secret=(await db.query('select public.enroll_device($1,$2) result',[device,outlet])).rows[0].result.device_secret;
 await assert.rejects(submit(p,'proof'),/Device denied/);
 await assert.rejects(submit(p,secret),/approval/);
});

test('large valid receipts paginate by bytes without truncating or dropping an operation',async()=>{
 const before=(await allChanges(id(22),'MANAGER',id(20))).after;
 const ids=[];
 for(let i=0;i<3;i++){
  const p=payload();p.device_id=id(22);p.cashier_user_id=id(20);p.note='x'.repeat(200000);ids.push(p.sale_id);
  assert.equal((await submit(p,'proof',id(20))).status,'ACCEPTED');
 }
 const first=await pull(id(22),'MANAGER',before,id(20),'proof',100);
 assert.equal(first.changes.length,2);assert.equal(first.has_more,true);
 assert.ok(Buffer.byteLength(JSON.stringify(first))<1048576);
 const second=await pull(id(22),'MANAGER',first.next_seq,id(20),'proof',100);
 assert.equal(second.changes.length,1);assert.equal(second.has_more,false);
 assert.deepEqual([...first.changes,...second.changes].map(c=>c.entity_id),ids);
});

test('owner cashier keeps actual identity, idempotency and approved owner-device scope',async()=>{
 const owner=id(50),ownerDevice=id(51);
 await admin(`insert into auth.users values('${owner}','owner@example.invalid',now());
 insert into public.user_profiles(id) values('${owner}');
 insert into public.brand_members(user_id,brand_id) values('${owner}','${brand}');
 insert into public.devices(id,outlet_id,enrolled_by,secret_hash) values('${ownerDevice}','${outlet}','${owner}',encode(sha256(convert_to('proof','UTF8')),'hex'));`);
 const p=payload();p.device_id=ownerDevice;p.cashier_user_id=owner;
 await assert.rejects(submit(p,'proof',owner),/approval/);
 await admin(`update public.devices set approved_at=now(),approved_role='OWNER' where id='${ownerDevice}'`);
 const ack=await submit(p,'proof',owner);assert.equal(ack.status,'ACCEPTED');assert.deepEqual(await submit(p,'proof',owner),ack);
 const row=(await admin(`select cashier_user_id,outlet_id from public.sales where id='${p.sale_id}'`))[0].rows[0];
 assert.equal(row.cashier_user_id,owner);assert.equal(row.outlet_id,outlet);
 assert.equal((await admin(`select count(*)::int n from public.sales where id='${p.sale_id}'`))[0].rows[0].n,1);
 const history=await allChanges(ownerDevice,'OWNER',owner);assert.ok(history.rows.some(c=>c.entity_id===p.sale_id));
 await assert.rejects(submit({...p,cashier_user_id:user},'proof',owner),/Scope denied/);
 await admin(`update public.devices set revoked_at=now() where id='${ownerDevice}'`);
 await assert.rejects(submit(p,'proof',owner),/Device denied/);
});
