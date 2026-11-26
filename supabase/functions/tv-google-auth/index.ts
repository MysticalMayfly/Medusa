type Config = { id: string; secret: string };

// Google requires a client secret for limited-input device polling. It stays in the
// function's environment; a TV only handles its own short-lived device code.
export async function handler(req: Request, fetcher: typeof fetch, config: Config): Promise<Response> {
  const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
  const fail = (status: number, error: string) => new Response(JSON.stringify({ error }), { status, headers });
  if (req.method !== 'POST') return fail(405, 'method_not_allowed');
  if (!config.id || !config.secret) return fail(503, 'not_configured');
  let input: {action?: string; device_code?: string};
  try { input = await req.json(); } catch { return fail(400, 'invalid_request'); }
  const params = new URLSearchParams({ client_id: config.id });
  let endpoint: string;
  if (input.action === 'start') {
    params.set('scope', 'openid email profile');
    endpoint = 'https://oauth2.googleapis.com/device/code';
  } else if (input.action === 'poll' && typeof input.device_code === 'string' && input.device_code.length > 0 && input.device_code.length <= 1024) {
    params.set('client_secret', config.secret);
    params.set('device_code', input.device_code);
    params.set('grant_type', 'urn:ietf:params:oauth:grant-type:device_code');
    endpoint = 'https://oauth2.googleapis.com/token';
  } else return fail(400, 'invalid_request');
  try {
    const response = await fetcher(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString() });
    return new Response(await response.text(), { status: response.status, headers });
  } catch { return fail(502, 'provider_unavailable'); }
}
if (import.meta.main) Deno.serve((req) => handler(req, fetch, { id: Deno.env.get('GOOGLE_TV_CLIENT_ID') ?? '', secret: Deno.env.get('GOOGLE_TV_CLIENT_SECRET') ?? '' }));
