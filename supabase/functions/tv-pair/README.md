# TV pairing

The TV creates independent random session and polling secrets plus a PKCE verifier. The phone opens a short session URL, which redirects to Supabase Google OAuth. Supabase returns a one-time authorization code to the callback; only the holder of the polling secret can retrieve it, and only the TV's PKCE verifier can exchange it. Pending sessions expire in five minutes; consumed codes cannot be read twice. The pairing table is inaccessible to anonymous and authenticated clients and is accessed only by this narrowly scoped Edge Function.

Provisioning requires the SQL in `schema.sql`, the exact callback URL `https://<project>.supabase.co/functions/v1/tv-pair/**` in Supabase Auth redirect allowlist, and deploying this function as a public endpoint with application-level session authorization.
