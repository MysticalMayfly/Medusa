import { handler } from './index.ts';
Deno.test('start sends only the configured client and identity scopes', async () => {
  let body = '';
  const fetcher = ((_url: string | URL | Request, init?: RequestInit) => { body = String(init?.body); return Promise.resolve(new Response('{"device_code":"secret-device-code","user_code":"ABCD-EFGH","verification_url":"https://www.google.com/device","interval":5,"expires_in":1800}', { status: 200 })); }) as typeof fetch;
  const r = await handler(new Request('https://example.test', {method:'POST', body:JSON.stringify({action:'start'})}), fetcher, {id:'client-id',secret:'private'});
  const data=await r.json();
  if(r.status!==200 || data.user_code!=='ABCD-EFGH' || !body.includes('client_id=client-id') || body.includes('private')) throw new Error('Invalid start');
});
Deno.test('poll keeps the client secret server side', async () => {
  let body = '';
  const fetcher = ((_url: string | URL | Request, init?: RequestInit) => { body=String(init?.body); return Promise.resolve(new Response('{"error":"authorization_pending"}',{status:428})); }) as typeof fetch;
  const r=await handler(new Request('https://example.test',{method:'POST',body:JSON.stringify({action:'poll',device_code:'test-code'})}),fetcher,{id:'client-id',secret:'private'});
  if(r.status!==428 || !(await r.text()).includes('authorization_pending') || !body.includes('client_secret=private')) throw new Error('Invalid poll');
});
