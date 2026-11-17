import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;
async function database(){
 const db=new PGlite();
 await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
 create schema auth;create table auth.users(id uuid primary key,email text,email_confirmed_at timestamptz);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth to authenticated;grant execute on function auth.uid() to authenticated;`);
 await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
 return db;
}

test('production catalog bootstrap preserves references, safely quotes names and does not copy operational identity',async()=>{
 const dev=await database(),prod=await database();
 try {
  await dev.query('insert into public.brands(id,name) values($1,$2)',[id(1),'Brand']);
  await dev.query('insert into public.outlets(id,brand_id,name) values($1,$2,$3)',[id(2),id(1),'Outlet']);
  const name="Tea O'Reilly \\ $body$ $catalog$ '; drop table public.products; --";
  await dev.query('insert into public.categories(id,brand_id,name) values($1,$2,$3)',[id(3),id(1),'Tea']);
  await dev.query('insert into public.products(id,brand_id,category_id,name,base_price_idr,version) values($1,$2,$3,$4,6000,7)',[id(4),id(1),id(3),name]);
  await dev.exec(`insert into public.modifier_groups(id,brand_id,name,min_select,max_select) values('${id(5)}','${id(1)}','Size',1,1);
  insert into public.modifier_options(id,brand_id,group_id,name,price_delta_idr) values('${id(6)}','${id(1)}','${id(5)}','Large',1000);
  insert into public.product_modifier_groups(product_id,group_id,brand_id) values('${id(4)}','${id(5)}','${id(1)}');
  insert into auth.users values('${id(99)}','dev-owner@example.invalid',now());
  insert into public.user_profiles(id) values('${id(99)}');
  insert into public.brand_members(user_id,brand_id) values('${id(99)}','${id(1)}');
  insert into public.devices(id,outlet_id,enrolled_by,secret_hash) values('${id(98)}','${id(2)}','${id(99)}',repeat('f',64));
  insert into public.sales(id,brand_id,outlet_id,device_id,cashier_user_id,subtotal_idr,total_idr,status,created_at)
  values('${id(97)}','${id(1)}','${id(2)}','${id(98)}','${id(99)}',6000,6000,'COMPLETED',now());`);
  const exporter=(await readFile(new URL('../export-prod-catalog.sql.example',import.meta.url),'utf8')).replaceAll('<brand-uuid>',id(1));
  const generated=(await dev.query(exporter)).rows[0].prod_catalog_import_sql;
  assert.ok(!generated.includes('dev-owner@example.invalid'));
  assert.ok(!generated.includes('f'.repeat(64)));
  await prod.exec(generated);
  assert.equal((await prod.query('select name from public.products')).rows[0].name,name);
  assert.equal((await prod.query('select version from public.products')).rows[0].version,7);
  assert.equal((await prod.query('select count(*)::int n from public.product_modifier_groups')).rows[0].n,1);
  for(const table of ['sales','devices','user_profiles','brand_members'])assert.equal((await prod.query(`select count(*)::int n from public.${table}`)).rows[0].n,0);
  assert.equal((await prod.query('select count(*)::int n from public.sync_changes')).rows[0].n,5);
  const epoch=async db=>(await db.query('select epoch from private.sync_generation')).rows[0].epoch;
  assert.notEqual(await epoch(dev),await epoch(prod));
  await assert.rejects(prod.exec(generated),/requires empty production/);
  await prod.exec('rollback');
  assert.equal((await prod.query('select count(*)::int n from public.products')).rows[0].n,1);
 }finally{await dev.close();await prod.close();}
});

test('unknown source brand fails without leaving partial production records',async()=>{
 const dev=await database(),prod=await database();
 try {
  const exporter=(await readFile(new URL('../export-prod-catalog.sql.example',import.meta.url),'utf8')).replaceAll('<brand-uuid>',id(55));
  const generated=(await dev.query(exporter)).rows[0].prod_catalog_import_sql;
  await assert.rejects(prod.exec(generated),/exactly one/);await prod.exec('rollback');
  assert.equal((await prod.query('select count(*)::int n from public.brands')).rows[0].n,0);
 }finally{await dev.close();await prod.close();}
});

test('quota export copies only selected-brand capacity and never memberships',async()=>{
 const dev=await database(),prod=await database();
 try {
  for(const db of [dev,prod]){
   await db.exec(await readFile(new URL('../staff-provisioning.sql',import.meta.url),'utf8'));
   await db.exec(await readFile(new URL('../staff-limits.sql',import.meta.url),'utf8'));
  }
  await dev.exec(`insert into public.brands(id,name) values('${id(1)}','Selected'),('${id(10)}','Other');
  insert into public.outlets(id,brand_id,name) values('${id(2)}','${id(1)}','A'),('${id(11)}','${id(10)}','B');
  insert into public.outlet_staff_limits(outlet_id,manager_limit,cashier_limit) values('${id(2)}',2,5),('${id(11)}',99,99);`);
  const catalogQuery=(await readFile(new URL('../export-prod-catalog.sql.example',import.meta.url),'utf8')).replaceAll('<brand-uuid>',id(1));
  await prod.exec((await dev.query(catalogQuery)).rows[0].prod_catalog_import_sql);
  const limitsQuery=(await readFile(new URL('../export-prod-staff-limits.sql.example',import.meta.url),'utf8')).replaceAll('<brand-uuid>',id(1));
  const rows=(await dev.query(limitsQuery)).rows;assert.equal(rows.length,1);
  await prod.exec(rows[0].prod_limit_sql);
  const limits=(await prod.query('select outlet_id,manager_limit,cashier_limit from public.outlet_staff_limits')).rows;
  assert.deepEqual(limits,[{outlet_id:id(2),manager_limit:2,cashier_limit:5}]);
  assert.equal((await prod.query('select count(*)::int n from public.outlet_members')).rows[0].n,0);
 }finally{await dev.close();await prod.close();}
});
