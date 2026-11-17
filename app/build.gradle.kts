import groovy.json.JsonSlurper
import java.util.Properties
import java.util.Base64
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.isFile) configFile.inputStream().use { load(it) }
}
// Environment variables are convenient for CI; local.properties stays ignored.
fun clientConfig(name: String): String? = providers.gradleProperty(name).orNull
    ?: providers.environmentVariable(name).orNull ?: localConfig.getProperty(name)
val devUrl = clientConfig("DEV_SUPABASE_URL") ?: clientConfig("SUPABASE_URL") ?: ""
val devKey = clientConfig("DEV_SUPABASE_PUBLIC_KEY") ?: clientConfig("SUPABASE_PUBLIC_KEY") ?: ""
val prodUrl = clientConfig("PROD_SUPABASE_URL") ?: ""
val prodKey = clientConfig("PROD_SUPABASE_PUBLIC_KEY") ?: ""
fun validateClientConfig(environment: String, url: String, key: String, required: Boolean) {
    if (!required && url.isBlank() && key.isBlank()) return
    require(url.isNotBlank() && key.isNotBlank()) {
        "Set ${environment}_SUPABASE_URL and ${environment}_SUPABASE_PUBLIC_KEY in ignored local.properties or CI configuration."
    }
    val uri = runCatching { URI(url) }.getOrNull()
    require(uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        (uri.path.isNullOrEmpty() || uri.path == "/") && uri.query == null && uri.fragment == null) {
        "${environment}_SUPABASE_URL must be an HTTPS project origin without credentials, query or fragment."
    }
    val role = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(key.split('.')[1]), Charsets.UTF_8)
        (JsonSlurper().parseText(payload) as? Map<*, *>)?.get("role")
    }.getOrNull()
    require((key.startsWith("sb_publishable_") || (key.split('.').size == 3 && role == "anon")) &&
        !key.startsWith("sb_secret_") && key.none { it.isWhitespace() }) {
        "${environment}_SUPABASE_PUBLIC_KEY must be public anon/publishable; server keys are forbidden."
    }
}
val validateDevConfig = tasks.register("validateDevSupabaseConfiguration") {
    doLast { validateClientConfig("DEV", devUrl, devKey, false) }
}
val validateProdConfig = tasks.register("validateProdSupabaseConfiguration") {
    doLast { validateClientConfig("PROD", prodUrl, prodKey, true) }
}
tasks.configureEach {
    if (name in setOf("preDevDebugBuild", "preDevReleaseBuild", "generateDevDebugBuildConfig", "generateDevReleaseBuildConfig")) dependsOn(validateDevConfig)
    if (name in setOf("preProdDebugBuild", "preProdReleaseBuild", "generateProdDebugBuildConfig", "generateProdReleaseBuildConfig")) dependsOn(validateProdConfig)
}
fun configLiteral(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
android {
    namespace = "com.dauntehnusantara.pos"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.dauntehnusantara.pos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.dauntehnusantara.pos.ComponentTestRunner"
    }
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            isDefault = true
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "SUPABASE_URL", configLiteral(devUrl))
            buildConfigField("String", "SUPABASE_PUBLIC_KEY", configLiteral(devKey))
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "SUPABASE_URL", configLiteral(prodUrl))
            buildConfigField("String", "SUPABASE_PUBLIC_KEY", configLiteral(prodKey))
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:runner:1.6.2")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.07.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
