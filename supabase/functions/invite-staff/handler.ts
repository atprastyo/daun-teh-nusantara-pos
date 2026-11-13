// No SDK dependency or service credential in the Android app. Auth verifies the caller;
// the caller's JWT authorizes the SQL invitation intent before admin email delivery.
export type InviteConfig = { url: string; publicKey: string; serviceKey: string };
export function createInviteHandler(config: InviteConfig, transport: typeof fetch = fetch) {
  return async (request: Request): Promise<Response> => {
    const reply = (status: number, code: string) => new Response(JSON.stringify({ code }), {
      status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
    });
    if (request.method !== 'POST') return reply(405, 'method_not_allowed');
    const authorization = request.headers.get('Authorization') ?? '';
    if (!/^Bearer [^\s]+$/.test(authorization)) return reply(401, 'authentication_required');
    try {
      const reader = request.body?.getReader();
      if (!reader) return reply(400, 'invalid_json');
      const parts: Uint8Array[] = []; let length = 0;
      while (true) {
        const chunk = await reader.read(); if (chunk.done) break;
        length += chunk.value.length;
        if (length > 4096) { await reader.cancel(); return reply(413, 'request_too_large'); }
        parts.push(chunk.value);
      }
      const bytes = new Uint8Array(length); let offset = 0;
      for (const part of parts) { bytes.set(part, offset); offset += part.length; }
      const raw = new TextDecoder().decode(bytes);
      const body = JSON.parse(raw);
      if (!body || typeof body !== 'object' || typeof body.email !== 'string' || body.email.length > 254 || !body.email.includes('@') ||
          typeof body.outlet_id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(body.outlet_id) ||
          !['MANAGER', 'CASHIER'].includes(body.role)) return reply(400, 'invalid_invitation');
      const headers = { apikey: config.publicKey, Authorization: authorization, 'Content-Type': 'application/json' };
      const user = await transport(config.url + '/auth/v1/user', { headers, signal: AbortSignal.timeout(15000) });
      if (!user.ok) return reply(401, 'authentication_required');
      const identity = await user.json();
      if (!identity.id) return reply(401, 'authentication_required');
      // Role/outlet values are validated by the SECURITY DEFINER RPC against live memberships.
      const intent = await transport(config.url + '/rest/v1/rpc/create_staff_invitation', {
        method: 'POST', headers, signal: AbortSignal.timeout(15000),
        body: JSON.stringify({ p_email: body.email, p_outlet_id: body.outlet_id, p_role: body.role }),
      });
      if (!intent.ok) {
        if (intent.status === 429) return reply(429, 'invitation_rate_limited');
        if (intent.status === 409) return reply(409, 'invitation_conflict');
        return reply(intent.status === 401 ? 401 : intent.status === 403 ? 403 : 503, 'invitation_not_authorized');
      }
      const authorized = await intent.json();
      if (!authorized.id || !authorized.email) return reply(502, 'invitation_not_confirmed');
      const delivered = await transport(config.url + '/auth/v1/invite', {
        method: 'POST', signal: AbortSignal.timeout(15000),
        headers: { apikey: config.serviceKey, Authorization: 'Bearer ' + config.serviceKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: authorized.email }),
      });
      // Leave the scoped intent pending on uncertain delivery; retries reuse it.
      // It grants nothing until a verified recipient accepts it and inviter permissions are rechecked.
      return delivered.ok ? reply(200, 'invitation_sent') : reply(502, 'invitation_delivery_not_confirmed');
    } catch (error) {
      if (error instanceof SyntaxError) return reply(400, 'invalid_json');
      return reply(503, 'invitation_not_confirmed'); // Do not log tokens, email or provider errors.
    }
  };
}
