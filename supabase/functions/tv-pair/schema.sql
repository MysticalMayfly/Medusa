create table if not exists public.tv_login_pairs (
  id text primary key,
  poll_hash text not null,
  challenge text not null,
  expires_at timestamptz not null,
  auth_code text,
  consumed boolean not null default false
);
alter table public.tv_login_pairs enable row level security;
revoke all on public.tv_login_pairs from anon, authenticated, public;
grant select, insert, update, delete on public.tv_login_pairs to service_role;
