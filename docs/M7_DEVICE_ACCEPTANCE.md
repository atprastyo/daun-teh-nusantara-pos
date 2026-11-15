# M7 normal-application acceptance record

15 November 2026. This record separates normal-app evidence, isolated fixtures,
artifact checks and operator gates. Local build success is not production approval.
The user confirmed this phone targets the **brand's working project**; no fabricated
sales, catalog/access edits, real sign-out, recovery/restore or hosted SQL are part of
this pass. Keep retained data intact. Sign-in credentials belong on the phone only.

## Current device and normal-app evidence

- Infinix X6873, Android API 36, page size 4096 bytes, wireless ADB.
- Existing debug app started with `adb shell am start -W -n
  com.dauntehnusantara.pos/.MainActivity`: Status ok, COLD, TotalTime 439 ms,
  WaitTime 445 ms. This is one launch observation, not a performance benchmark.
- Manifest uses normal PosApplication; Hilt startup/normal activity reached the
  sign-in UI. UI hierarchy contained app nodes and recognized Masuk/Email/Kata sandi
  labels. No credential values, personal/business UI text or screenshots were logged.
- Normal PosApplication schedules its existing WorkManager startup/periodic work.
  This pass does not create test transactions; it does not claim that ordinary
  startup background traffic was disabled.
- App remains installed and its data was not cleared/uninstalled. Authenticated
  checks are awaiting the user signing in directly on this device. No password is
  requested in chat, read from storage or supplied by the agent.

## Acceptance matrix

| Check | Result in this pass | Remaining evidence |
| --- | --- | --- |
| Normal debug Hilt startup / sign-in screen | Observed on API 36 | Authenticated navigation/session still pending |
| Login and role-filtered destinations | Awaiting device sign-in | Observe actual role routes; no access mutations |
| Catalog/POS/history/report/staff/settings navigation | Awaiting device sign-in | Read-only only on working project |
| Export explanation and sign-out confirmation Cancel | Awaiting device sign-in | No real logout or exported private file needed for cancellation checks |
| System document picker Cancel | Awaiting device sign-in | Real provider launch/cancel only; file contents/write failure separate |
| Offline lease / OS credential / reboot / expiry | Not run | Controlled dev account/device; never send device PIN in chat |
| Lost ACK / sale durability / two-device sync | Not run live | Disposable backend and original IDs; no fake sales on working brand |
| Staff/device changes / concurrent catalog edits | Not run live | Disposable approved actors/devices and explicit scenarios |
| Physical tablet, TalkBack, keyboard, large text | Not completed | Device/manual acceptance; fixture results remain separate |
| Signed minified API 26/current runtime | Not run | Release signing and suitable devices |
| Hosted recovery, Auth/Storage/PITR, RPO/RTO | Not run | Authorized operator plan and rehearsal |

Existing phone component fixtures from the previous iteration passed 9 settings/sync
UI tests on this Infinix using a plain Application. They do not count as normal
MainActivity/Hilt/provider/auth acceptance. Historical debug 292 / release 283 +9
expected skips and lint 0 errors/45 warnings are in PROGRESS.md; Gradle was not rerun
for this read-only readiness review and Python tooling addition.

## Current artifact evidence

The current artifacts were inspected, not rebuilt or deployed in this pass:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug APK | 14230585 | `214b44a4fee4bcc2c3a84e16e4d6fc4a30c10eb4d33112ba66f142ee671881af` |
| Unsigned release APK | 2185915 | `cabeca2931f7a7d0c291b77018894475ca5110aaa2d2bec54363be3777a170d5` |
| Release mapping.txt | 41999715 | `eb5ace227e85df675b8c44421e709962f261a7c406301a55686e6df6e60c28a5` |

APK metadata: `com.dauntehnusantara.pos`, versionCode 1, versionName 0.1.0, minSdk26,
targetSdk36. Release manifest has allowBackup=false, fullBackupContent=false,
usesCleartextTraffic=false. Debug APK signature verifies with APK v2 and one signer.
Unsigned release verification fails as expected (no signing manifest); no release
certificate/keystore/signing configuration was created or read.

Build-tools 36.0.0 `zipalign -c -P 16 4` passed on the release APK. All eight shipped
native libraries (graphics.path and datastore_shared_counter across arm64-v8a,
armeabi-v7a, x86 and x86_64) are uncompressed and have PT_LOAD alignments >=16384.
This inspection is not runtime validation on a 16 KB page device; this phone is 4 KB.
The new script validates header bounds, load-segment presence and alignment; it is
not a complete ELF verifier, signing check or APK compatibility certification.

Repeat from repository root:

```sh
python3 -m unittest discover -s scripts -p 'test_verify_android_artifacts.py'
python3 scripts/verify_android_artifacts.py \
  --mapping app/build/outputs/mapping/release/mapping.txt \
  --zipalign "$ANDROID_SDK_ROOT/build-tools/36.0.0/zipalign"
```

Use the actual installed SDK root/build-tools on another machine. This pass used
`/Users/agustro/Library/Android/sdk/build-tools/36.0.0/zipalign`; no download/version
change was needed. Output is public artifact metadata only. Saved verification:
`app/build/m7-artifact-check.json` (generated/ignored). Hashes must be refreshed if any
artifact changes. Archive the actual mapping with the eventual signed release.

## Isolated backend evidence

`npm test --prefix supabase` passed 52 tests, 0 failures/skips, against disposable
PGlite fixtures. Covers existing RLS/manual provisioning, sale/catalog idempotence,
management/restore and optional photo controls. Log: app/build/m7-backend-tests.log.
These are not hosted concurrency/GoTrue/Storage/provider tests and used no working
project credentials or network backend. No server schema or test data deployed.

## Next gate

Continue read-only authenticated screen/cancellation checks once sign-in is complete
on the phone. Record actual observed outcomes here. Keep live money/access/restore
scenarios and completed exports off the working project until there is an appropriate
controlled acceptance setup. Production checklist items remain unchecked unless their
specific required evidence exists; signing, hosted and physical-device gates are open.
