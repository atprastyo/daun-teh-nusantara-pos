# M7 bounded security review

Scope: local Android release configuration, response parsing, device enrollment,
and online mobile catalog writes. This is source/test evidence, not a penetration
test of deployed Supabase or a claim that every production control is verified.

## Fixed findings

1. Build-time legacy JWT validation accepted any decoded payload containing `anon`.
   It now parses the root `role` and requires exactly `anon`; publishable keys remain
   supported and secret keys are rejected. Runtime configuration validation remains.
2. Byte limits alone did not prevent deeply nested JSON from exhausting recursive
   Android parsing. Network responses, encrypted auth cache, pulled receipts and
   recovery inspection now check nesting (32) and UTF-8 size before parsing.
3. Enrollment of a new device UUID used a row lock even when no row existed. A
   transaction advisory lock serializes that identity before insert/ownership checks.
4. A mobile catalog editor could not safely rely on local DAO writes. The new RPC
   checks live device/role, brand parents, epoch, versions and modifier requirements;
   direct table writes remain denied. Stable operation hashes, atomic audit/feed and
   pull-based visibility protect uncertain retry and other device convergence.
5. Release optimization was disabled. R8/resource shrinking are enabled and worker
   names retained; unsigned outputs require real-device/signing acceptance.

## Residual verification

See M7_RELEASE_CHECKLIST.md for real Auth, hosted concurrency, device accessibility,
performance, page-size compatibility, signing and authorized recovery gates. Test
fixtures use fake identities/proofs only. No live account, schema, email, signing
key, data reset or production restore was created/performed.
