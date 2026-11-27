import { handler, type Store } from './pair.ts';
const assert = (ok: unknown) => { if (!ok) throw new Error('assertion failed'); };
const id = 'a'.repeat(32), secret = 'b'.repeat(43), challenge = 'c'.repeat(43);
function memory(): Store {
  const data = new Map<string, any>();
  return {
    async create(row) { if (data.has(row.id)) return false; data.set(row.id, row); return true },
    async get(id) { return data.get(id) ?? null },
    async finish(id, code) { const r = data.get(id); if (!r || r.consumed || r.auth_code || Date.parse(r.expires_at) <= Date.now()) return false; r.auth_code = code; return true },
    async consume(id, hash) { const r = data.get(id); if (!r || r.consumed || r.poll_hash !== hash || !r.auth_code || Date.parse(r.expires_at) <= Date.now()) return null; r.consumed = true; return r.auth_code },
  };
}
const base = 'https://example.test/functions/v1/tv-pair';
const config = {supabaseUrl:'https://example.test', functionUrl:base};
const post = (path:string, value:unknown) => new Request(base+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});
Deno.test('PKCE pairing returns a code only to the TV and only once', async () => {
 const store=memory();
 let r=await handler(post('/start',{id,secret,challenge}),store,config); assert(r.status===201);
 r=await handler(new Request(base+'/'+id),store,config); assert(r.status===302); assert(r.headers.get('location')?.includes('code_challenge='));
 r=await handler(new Request(base+'/'+id+'?code=authcode'),store,config); assert(r.status===200);
 r=await handler(post('/poll',{id,secret:'x'.repeat(43)}),store,config); assert(r.status===404);
 r=await handler(post('/poll',{id,secret}),store,config); assert((await r.json()).code==='authcode');
 r=await handler(post('/poll',{id,secret}),store,config); assert(r.status===404);
});
Deno.test('invalid and expired requests are rejected', async () => {
 const store=memory();
 assert((await handler(post('/start',{id:'x',secret,challenge}),store,config)).status===400);
 assert((await handler(new Request(base+'/'+id+'?code=foo'),store,config)).status===410);
});
