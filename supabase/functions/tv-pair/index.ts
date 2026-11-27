import { handler, type Pair, type Store } from './pair.ts';
const url=Deno.env.get('SUPABASE_URL') ?? '';
const key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
const root=`${url}/rest/v1/tv_login_pairs`;
const dbHeaders={'apikey':key,'Authorization':`Bearer ${key}`,'Content-Type':'application/json','Prefer':'return=representation'};
async function db(path:string,method='GET',body?:unknown):Promise<Response>{
 return await fetch(root+path,{method,headers:dbHeaders,body:body===undefined?undefined:JSON.stringify(body)});
}
const now=()=>encodeURIComponent(new Date().toISOString());
const store:Store={
 async create(row:Pair){
  // Expired sessions contain no usable authorization codes and can be deleted.
  if(Math.random()<0.05) await db(`?expires_at=lt.${now()}`,'DELETE');
  const r=await db('','POST',row); if(r.status===409)return false; if(!r.ok)throw new Error('pair create failed');return true;
 },
 async get(id:string){const r=await db(`?id=eq.${id}&select=*`);if(!r.ok)throw new Error('pair lookup failed');return (await r.json())[0] ?? null},
 async finish(id:string,code:string){const r=await db(`?id=eq.${id}&consumed=eq.false&auth_code=is.null&expires_at=gt.${now()}`,'PATCH',{auth_code:code});if(!r.ok)throw new Error('pair update failed');return (await r.json()).length===1},
 async consume(id:string,hash:string){const r=await db(`?id=eq.${id}&poll_hash=eq.${hash}&consumed=eq.false&auth_code=not.is.null&expires_at=gt.${now()}&select=auth_code`,'PATCH',{consumed:true});if(!r.ok)throw new Error('pair consume failed');return (await r.json())[0]?.auth_code ?? null},
};
Deno.serve(async req=>{
 try{return await handler(req,store,{supabaseUrl:url,functionUrl:`${url}/functions/v1/tv-pair`})}
 catch{return new Response('{"error":"service_unavailable"}',{status:503,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}})}
});
