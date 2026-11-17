# Development and production builds

17 November 2026. Supabase environments are selected at build time, not through an
in-app switch. Build type and environment are separate: prodDebug uses production
configuration; devRelease still uses development configuration.

| Variant | Supabase properties | Application ID | Launcher label |
| --- | --- | --- | --- |
| devDebug / devRelease | DEV_SUPABASE_URL, DEV_SUPABASE_PUBLIC_KEY | com.dauntehnusantara.pos.dev | Daun Teh Nusantara Dev |
| prodDebug / prodRelease | PROD_SUPABASE_URL, PROD_SUPABASE_PUBLIC_KEY | com.dauntehnusantara.pos | Daun Teh Nusantara |

Namespace/Kotlin packages remain com.dauntehnusantara.pos. Separate application IDs
isolate Room, preferences, encrypted enrollment and WorkManager jobs. Flavor-specific
configuration values are generated in BuildConfig; runtime repositories remain unchanged.

## Configure locally

Edit ignored local.properties; never commit the real values. The tracked
local.properties.example contains blank entries only:

```properties
DEV_SUPABASE_URL=
DEV_SUPABASE_PUBLIC_KEY=
PROD_SUPABASE_URL=
PROD_SUPABASE_PUBLIC_KEY=
```

Use the matching HTTPS project URL and public publishable/anon key from each project.
Secret/service-role keys are rejected. Per property name, precedence is Gradle
property, then environment variable, then local.properties. Prefer environment
variables for CI instead of putting real values in command lines or logs.

Existing unprefixed SUPABASE_URL / SUPABASE_PUBLIC_KEY remain DEV-only fallback when
the corresponding DEV_* entry is absent. An explicitly blank DEV_* entry overrides
that fallback. PROD never reads unprefixed or DEV properties. Missing/malformed PROD
configuration fails the requested production build; it does not silently use dev.
Unconfigured dev is allowed for the existing isolated debug demo.

## Select/build

Android Studio: sync Gradle, open Build Variants, choose app devDebug for development,
prodDebug for debugging production configuration, or prodRelease for release output.
Public client values are embedded in APKs; RLS/server functions remain authoritative.

```sh
./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest :app:lintDevDebug
./gradlew :app:assembleDevDebugAndroidTest
./gradlew :app:installDevDebug
# Both explicit PROD_* settings required:
./gradlew :app:assembleProdDebug
./gradlew :app:bundleProdRelease
```

Dev APK: app/build/outputs/apk/dev/debug/app-dev-debug.apk.
Prod debug APK: app/build/outputs/apk/prod/debug/app-prod-debug.apk.
Prod release bundle: app/build/outputs/bundle/prodRelease/app-prod-release.aab.
Release signing remains separately configured; this change does not supply a signing
key, deploy backend schema/functions, create users or install an app.

Generic assembleDebug/build can now select multiple environments and therefore
require production configuration. Use explicit variant tasks for routine dev checks.
Historical milestone commands in PROGRESS.md describe the old unflavored project.

## Existing installation

The old unflavored app already used the production package ID, even when pointed at
dev. It is not automatically moved into the new .dev app. Preserve/export any retained
or unsynced work before replacing an old installation with prod; do not clear or
uninstall it as an environment-switch shortcut. Backend changes do not migrate sales,
users, memberships, device approvals or cached data between projects. The app's
existing project-scope mismatch checks remain in force. Use a clean separately
prepared production device/profile when retained old data has not been resolved.

## Verification

Run scripts/test_supabase_flavors.py with the Gradle JDK in JAVA_HOME for isolated
missing-config, public-key and URL validation checks. These use synthetic public
fixtures and never contact Supabase. BuildEnvironmentTest runs in each selected
variant to verify package/namespace boundaries. Production compile checks with
synthetic credentials are not a production-configured or signed release.

Official reference: [Android build variants](https://developer.android.com/build/build-variants).
