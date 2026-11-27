export type Pair = {id:string;poll_hash:string;challenge:string;expires_at:string;auth_code:string|null;consumed:boolean};
export type Store = {
  create(row:Pair):Promise<boolean>;
  get(id:string):Promise<Pair|null>;
  finish(id:string,code:string):Promise<boolean>;
  consume(id:string,hash:string):Promise<string|null>;
};
type Config = {supabaseUrl:string;functionUrl:string};
const idPattern=/^[A-Za-z0-9_-]{32}$/;
const secretPattern=/^[A-Za-z0-9_-]{43}$/;
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'};
const json=(value:unknown,status=200)=>new Response(JSON.stringify(value),{status,headers:{...headers,'Content-Type':'application/json'}});
// Supabase's shared function domain serves HTML as plain text, so return readable text directly.
const html=(body:string,status=200)=>new Response(`MEDUSA\n\n${body}`,{status,headers:{...headers,'Content-Type':'text/plain; charset=utf-8'}});
const hash=async(s:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))).map(x=>x.toString(16).padStart(2,'0')).join('');
export async function handler(req:Request,store:Store,config:Config):Promise<Response>{
 const u=new URL(req.url), part=u.pathname.split('/').filter(Boolean).pop() ?? '';
 if(req.method==='POST' && (part==='start'||part==='poll')){
  let input:Record<string,unknown>;try{input=await req.json()}catch{return json({error:'invalid_request'},400)}
  const {id,secret}=input;
  if(typeof id!=='string'||!idPattern.test(id)||typeof secret!=='string'||!secretPattern.test(secret))return json({error:'invalid_request'},400);
  const secretHash=await hash(secret);
  if(part==='start'){
   if(typeof input.challenge!=='string'||!secretPattern.test(input.challenge))return json({error:'invalid_request'},400);
   const created=await store.create({id,poll_hash:secretHash,challenge:input.challenge,expires_at:new Date(Date.now()+5*60*1000).toISOString(),auth_code:null,consumed:false});
   return created?json({url:`${config.functionUrl}/${id}`,expires_in:300},201):json({error:'conflict'},409);
  }
  const row=await store.get(id);
  if(!row||row.poll_hash!==secretHash||row.consumed||Date.parse(row.expires_at)<=Date.now())return json({error:'not_found'},404);
  if(!row.auth_code)return json({status:'pending'},202);
  const code=await store.consume(id,secretHash);
  return code?json({code}):json({error:'not_found'},404);
 }
 if(req.method!=='GET'||!idPattern.test(part))return json({error:'not_found'},404);
 const row=await store.get(part);
 if(!row||row.consumed||Date.parse(row.expires_at)<=Date.now())return html('This sign-in link has expired. Please try again on your TV.',410);
 if(u.searchParams.has('error')||u.searchParams.has('error_code'))return html('Sign-in was not completed. Please try again on your TV.',400);
 const code=u.searchParams.get('code');
 if(code){
  if(code.length>2048||!await store.finish(part,code))return html('This sign-in link has expired or has already been used.',410);
  return html('Sign-in complete. You can return to your TV.');
 }
 if(row.auth_code)return html('This sign-in link has already been used.',410);
 const authorize=new URL(`${config.supabaseUrl}/auth/v1/authorize`);
 authorize.searchParams.set('provider','google');
 authorize.searchParams.set('redirect_to',`${config.functionUrl}/${part}`);
 authorize.searchParams.set('code_challenge',row.challenge);
 authorize.searchParams.set('code_challenge_method','s256');
 return new Response(null,{status:302,headers:{...headers,Location:authorize.toString()}});
}
