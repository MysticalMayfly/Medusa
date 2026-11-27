import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const {PGlite} = await import(process.env.MEDUSA_PGLITE_MODULE);
const db = new PGlite();
await db.exec(`
  create role anon; create role authenticated;
  create schema auth;
  create function auth.role() returns text language sql stable as $$ select nullif(current_setting('request.jwt.claim.role', true), '') $$;
  create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
  $$;
  create table public.account_profile_links(user_id uuid primary key, profile_id text unique not null);
  create table public.profiles(id text primary key, name text not null, avatar integer not null default 0,
    photo_url text, updated_at timestamptz not null default now());
  create table public.profile_titles(profile_id text not null references public.profiles(id), title_id bigint not null,
    watched boolean not null default false, watched_at timestamptz, interested boolean not null default false,
    prog_season integer, prog_episode integer, prog_at timestamptz, updated_at timestamptz not null default now(),
    primary key(profile_id,title_id));
  create table public.profile_events(id bigserial primary key, profile_id text not null, kind text not null,
    title_id bigint, value jsonb not null default '{}', device text, created_at timestamptz not null default now());
  alter table public.profile_events enable row level security;
  alter table public.profiles enable row level security;
  alter table public.profile_titles enable row level security;
  grant usage on schema public,auth to authenticated;
  grant select on public.account_profile_links to authenticated;
  insert into public.account_profile_links values ('00000000-0000-0000-0000-000000000001','p1');
  insert into public.profiles values ('p1','Test',0,null,'2026-01-01Z');
  insert into public.profiles values ('other','Other',0,null,'2026-01-01Z');
  insert into public.profile_titles(profile_id,title_id,updated_at) values ('p1',1,'2026-01-01Z');
`);
const migration = await readFile(new URL('../profile_cloud_sync.sql', import.meta.url), 'utf8');
await db.exec(migration);
await db.exec(`set role authenticated; set request.jwt.claim.sub='00000000-0000-0000-0000-000000000001';
  insert into public.profile_events(profile_id,kind,title_id,value,created_at) values
  ('p1','watched',1,'{"on":true,"at":"2026-01-02T00:00:00Z"}','2026-01-02Z'),
  ('p1','interested',1,'{"on":true}','2026-01-03Z'),
  ('p1','watched',1,'{"on":false}','2026-01-01T12:00:00Z');
  `);
const visible=(await db.query('select id from public.profiles order by id')).rows;
assert.deepEqual(visible,[{id:'p1'}],'Only the signed-in account can read its profile');
await db.exec('reset role');
await db.exec(`set role authenticated; set request.jwt.claim.sub='00000000-0000-0000-0000-000000000001';
  insert into public.profile_events(profile_id,kind,title_id,value,created_at)
  values ('p1','watched',2,'{"on":true}','2026-01-10Z'); reset role;`);
await db.exec(migration);
await db.exec(`set role authenticated; set request.jwt.claim.sub='00000000-0000-0000-0000-000000000001';
  insert into public.profile_events(profile_id,kind,title_id,value,created_at)
  values ('p1','interested',2,'{"on":true}','2026-01-05Z'); reset role;`);
assert.equal((await db.query('select interested from public.profile_titles where title_id=2')).rows[0].interested,true,
  'Reapplying migration must not overwrite the independent field clock');
let row=(await db.query('select watched, interested from public.profile_titles where profile_id=$1 and title_id=$2',['p1',1])).rows[0];
assert.deepEqual(row,{watched:true,interested:true},'Independent fields and out-of-order events must be preserved');
await db.exec(`set role authenticated; set request.jwt.claim.sub='00000000-0000-0000-0000-000000000002';`);
await assert.rejects(db.exec(`insert into public.profile_events(profile_id,kind,title_id,value) values ('p1','watched',1,'{"on":false}')`));
await db.exec('reset role');
row=(await db.query('select watched from public.profile_titles where profile_id=$1 and title_id=$2',['p1',1])).rows[0];
assert.equal(row.watched,true,'Another account must not edit this profile');
await db.exec(`set request.jwt.claim.sub=''; set request.jwt.claim.role='service_role';
insert into public.profile_events(profile_id,kind,title_id,value,created_at)
values ('p1','watched',3,'{"on":true}','2026-02-01Z');`);
assert.equal((await db.query('select watched from public.profile_titles where title_id=3')).rows[0]?.watched,true,
  'Trusted desktop events must be applied in the cloud');
console.log('Profile sync: field conflict and account-ownership checks passed');
await db.close();
