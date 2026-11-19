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
 // Keep storage setup in the same ordered fixture as the base schema and seed.
 await admin(`create schema storage;create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
 create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text,name text,metadata jsonb,unique(bucket_id,name));
 alter table storage.objects enable row level security;grant usage on schema storage to authenticated;grant select,insert,update,delete on storage.objects to authenticated;`);
 await db.exec(await readFile(new URL('../product-photos.sql',import.meta.url),'utf8'));
});
after(async()=>db.close());
const path=(n=500,brand=10,product=31)=>`${id(brand)}/${id(product)}/${id(n)}.jpg`;
async function upload(p,user=1,metadata={mimetype:'image/jpeg',size:12345}){await auth(user);await db.query('insert into storage.objects(bucket_id,name,metadata) values($1,$2,$3)',['product-photos',p,metadata]);}
async function setPhoto(p,version=0,op=700,user=1,device=40,epoch=null){
 if(!epoch){await db.exec('reset role');epoch=(await db.query('select epoch from private.sync_generation')).rows[0].epoch;}
 return rpc(user,'set_product_photo',{...proof(device),p_epoch:epoch,p_product_id:id(31),p_expected_version:version,p_path:p,p_operation_id:id(op)});
}
async function scalar(sql){await db.exec('reset role');return Object.values((await db.query(sql)).rows[0])[0];}
test('private photo bucket is bounded, owner/manager upload only, foreign/malformed paths denied',async()=>{
 const bucket=(await admin("select * from storage.buckets where id='product-photos'"))[0].rows[0];assert.equal(bucket.public,false);assert.equal(bucket.file_size_limit,204800);
 await upload(path());await upload(path(501),2);
 await assert.rejects(upload(path(502),3),/row-level security/);
 await assert.rejects(upload(path(503,11,33)),/row-level security/);
 await assert.rejects(upload('../escape.jpg'),/row-level security/);
 await assert.rejects(upload(`${id(10)}/${id(9999)}/${id(504)}.jpg`),/row-level security/);
 await auth(3);assert.equal((await db.query('select * from storage.objects')).rows.length,0);
});
test('publish is idempotent, synced and audited without changing catalog price/version or paid snapshots',async()=>{
 const original=await scalar(`select version from public.products where id='${id(31)}'`);
 const a=await setPhoto(path());assert.equal(a.version,1);assert.ok(a.through_seq>0);
 assert.deepEqual(await setPhoto(path()),a);
 assert.equal(await scalar("select count(*) from public.audit_logs where action='PRODUCT_PHOTO_SET'"),1);
 assert.equal(await scalar(`select version from public.products where id='${id(31)}'`),original);
 assert.equal(await scalar(`select product_name_snapshot from public.sale_items where sale_id='${id(100)}'`),'Original snapshot');
 await auth(3);assert.equal((await db.query('select name from storage.objects')).rows[0].name,path());
 await auth(5);assert.equal((await db.query('select * from storage.objects')).rows.length,0);
 const event=await scalar("select payload from public.sync_changes where payload->>'table'='product_photos' order by seq desc limit 1");assert.equal(event.record.image_path,path());
});
test('photo conflicts, cashier publication, bad proof, wrong epoch and unpublished/mismatched objects rejected atomically',async()=>{
 await assert.rejects(setPhoto(path(501),0,701),/Photo changed/);
 await assert.rejects(setPhoto(path(501),1,700),/operation conflict/);
 await assert.rejects(setPhoto(path(501),1,702,3,42),/Photos forbidden/);
 await assert.rejects(setPhoto(path(501),1,703,1,41),/Device denied/);
 await assert.rejects(setPhoto(path(501),1,704,1,40,id(999)),/generation changed/);
 await assert.rejects(setPhoto(path(999),1,705),/Upload photo first/);
 await assert.rejects(setPhoto(path(505,11,33),1,706),/Invalid photo path/);
 await upload(path(506),1,{mimetype:'text/plain',size:1});await assert.rejects(setPhoto(path(506),1,707),/Upload photo first/);
 assert.equal(await scalar('select count(*) from private.photo_operations'),1);
});
test('replacement/removal use photo revisions and tombstones; files cannot be overwritten or deleted by clients',async()=>{
 const a=await setPhoto(path(501),1,708,2,41);assert.equal(a.version,2);
 await auth(1);assert.equal((await db.query('update storage.objects set metadata=metadata')).affectedRows,0);
 assert.equal((await db.query('delete from storage.objects')).affectedRows,0);
 const removed=await setPhoto(null,2,709);assert.equal(removed.version,3);
 const event=await scalar("select payload from public.sync_changes where payload->>'table'='product_photos' order by seq desc limit 1");assert.equal(event.record.active,false);assert.equal(event.record.image_path,null);
 await auth(3);assert.equal((await db.query('select * from storage.objects')).rows.length,0);
 await assert.rejects(db.query('select * from private.photo_operations'),/permission denied/);
});
