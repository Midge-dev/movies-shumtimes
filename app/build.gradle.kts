import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing identity lives in local.properties (gitignored) plus
// app/release.keystore (also gitignored, unlike the checked-in debug key —
// see the debug signingConfig's own comment) — neither exists on a fresh
// checkout or CI runner. Falls back to null/no signingConfig on release in
// that case, same as before this was set up: assembleRelease still
// succeeds, it just produces an unsigned APK.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.moviesshumtimes.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moviesshumtimes.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    // Checked into the repo (debug-only key, not the release signing
    // identity — fine to commit) so every debug build, whether built
    // locally or by CI on a fresh runner, signs identically. Without this,
    // AGP falls back to each machine's own implicit ~/.android/debug.keystore
    // — stable within one machine, but CI has no persisted keystore at all,
    // so it minted a brand new random one on every run. Installing a
    // differently-signed APK over an existing one forces an uninstall
    // first, wiping the Plex login/relay URL/reconnect token — this is what
    // was happening whenever a CI build and a local build (or two different
    // CI runs) met on the same device.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// The Gradle module is named "app" (see settings.gradle.kts), so the APK
// would otherwise default to app-debug.apk/app-release.apk — override it to
// something recognizable for whoever's downloading it to sideload.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("movies-shumtimes.apk")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.multiplatform.settings)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
}
