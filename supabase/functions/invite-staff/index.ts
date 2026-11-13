import { createInviteHandler } from './handler.ts';
const url = Deno.env.get('SUPABASE_URL');
const publicKey = Deno.env.get('SUPABASE_ANON_KEY');
const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
if (!url || !publicKey || !serviceKey) throw new Error('Server configuration required');
Deno.serve(createInviteHandler({ url, publicKey, serviceKey }));
