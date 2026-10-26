import java.util.Properties

// Firebase is enabled only when an owner-provided, local Android configuration
// exists. CI and preview debug builds remain usable without Firebase keys.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localProperty(name: String): String = localProperties.getProperty(name, "")

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}
fun quoted(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val releaseKeystorePath = System.getenv("ANDROID_RELEASE_KEYSTORE_PATH").orEmpty()
val releaseStorePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningEnabled = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

val resolvedVersionCode = providers.gradleProperty("CPIPOS_RELEASE_VERSION_CODE")
    .orNull?.toIntOrNull() ?: 2000000
val resolvedVersionName = providers.gradleProperty("CPIPOS_RELEASE_VERSION_NAME")
    .orNull?.takeIf { it.isNotBlank() } ?: "2.0.0-dev"

android {
    namespace = "com.cpipos.pos.next"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cpipos.pos.next"
        minSdk = 26
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public CpIPOS Web API host only. No admin keys are stored in the APK.
        buildConfigField(
            "String",
            "WEB_POS_API_URL",
            quoted(localProperty("CPIPOS_WEB_POS_API_URL")
                .ifBlank { "https://cp-ipos-web.vercel.app" })
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            quoted(localProperty("CPIPOS_SUPABASE_URL"))
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            quoted(localProperty("CPIPOS_SUPABASE_PUBLISHABLE_KEY"))
        )
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("productionRelease") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-local"
        }
        release {
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("productionRelease")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.5.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("com.google.firebase:firebase-messaging:24.1.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
