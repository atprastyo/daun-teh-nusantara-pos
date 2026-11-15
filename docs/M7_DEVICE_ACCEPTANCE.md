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
- The user signed in directly on the device. The updated debug APK restored that
  authenticated session after an in-place update; no password was requested in chat,
  read from storage or supplied by the agent. App data was not cleared/uninstalled.

## Acceptance matrix

| Check | Result in this pass | Remaining evidence |
| --- | --- | --- |
| Normal debug Hilt startup / sign-in screen | Observed on API36; session restored after update | Signed/minified and other API devices remain |
| Login and role-filtered destinations | User signed in; Owner navigation observed | Other roles/denials need controlled actors |
| Catalog/POS/history/report/staff/settings navigation | Five management destinations opened read-only | POS/history are not destinations for this Owner session |
| Export explanation and sign-out confirmation Cancel | Passed in normal app | Actual sign-out/retained-data re-login remains controlled acceptance |
| System document picker Cancel | Recovery JSON picker opened and cancelled; export re-enabled, Settings retained | CSV picker, completed files and provider/write failure remain separate |
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

## Initial artifact evidence — before the refresh fix

The initial readiness artifacts below were inspected before the normal-app refresh
fix. Updated current fingerprints are recorded in the follow-up section below:

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

## Signed-in follow-up and normal-app finding

The working Owner session exposed Ringkasan/Katalog/Laporan/Staf/Setelan and the
Owner audit tab. Those five destinations were opened read-only; no fabricated sale,
record edit, staff/device change or actual logout was submitted. Real sign-out and
recovery-explanation Cancel passed. The actual Android DocumentsUI picker opened for
authorized recovery capture, then Back cancelled it: no destination was chosen, no
export-completion message appeared, export re-enabled and Settings remained open.
No private archive or screenshot was saved by the test.

Observed periodic navigation reset: AuthRepository.loadChoices emitted OUTLETS during
same-context refresh. Fixed it to retain READY/busy only after newly fetched membership
confirms the same brand/outlet/role/account/enrolled user. Role changes and denials
still remove the old context. Three regressions passed within 27 AuthRepository tests.
On the updated phone, explicit online validation retained Settings; it also remained
open for more than 70 seconds across the nominal 60-second refresh interval.

Follow-up report-filter/staff checks were interrupted when the phone moved away
from POS. A read-only package check later confirmed another foreground app; the POS
process remained alive and the crash buffer had no app crash record. These attempts
are not counted as passed filter/subsection checks or an app crash. UI input stopped;
the temporary helper now requires the POS package before any tap. Resume those cases
only when the phone is available. No credential/business text or screenshots logged;
only fixed recognized labels/coordinates, package checks and boolean assertions.

Full fix verification: `./gradlew :app:assembleDebugAndroidTest build --offline
--max-workers=2 --console=plain` passed in 5m22s: debug295 passed, release286 passed
plus9 expected demo-only skips, lint0 errors/45 warnings. Debug/test/release APKs built;
the new debug APK was installed with `adb install -r -t`, preserving the session/data.
Log: app/build/m7-refresh-build.log. This adds normal-app evidence; earlier component
fixtures are still not evidence of real provider/auth acceptance.

Current artifact fingerprints after the fix:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug APK | 14230585 | `d0e64379137c49917df34459f16c8d3a8b12f6c273601990679ecd6d3089778f` |
| Unsigned release APK | 2185915 | `704bb4d0962fde28b08725d99c50b033ff28170c24357900e37c8dfe2ef7059e` |
| Release mapping.txt | 42006998 | `6aec08b309f127c2a768355e03f6470b678c255269949a3ef725bc34e7d58881` |

The artifact verifier/zipalign were rerun for the changed release APK: eight ELF load
alignment checks and ZIP16 KB passed. New debug signature verifies (v2, one signer).
The release remains unsigned; alignment is still not 16 KB-device runtime acceptance.

## Next gate

Use a controlled acceptance environment for completed private exports/provider failures,
actual sign-out/re-login, other roles, offline/OS credential/process-death, test sales,
concurrent actors and restore scenarios. Keep working-project business records intact.
Signed build, hosted concurrency/restore, API26/tablet/16 KB runtime, TalkBack and
operator backup/signing gates remain open. No production approval is implied by these
bounded normal-app results.

## Subsequent scheduling change

The user subsequently selected a one-hour periodic background sync sweep. Source and
local debug build include that change; no phone install/check was performed while
device checks were paused. The normal-app results and fingerprints above identify
the earlier refresh-fix build, not an acceptance run of the hourly build. See PROGRESS
for its local checks; refresh artifact hashes before the next release acceptance.
