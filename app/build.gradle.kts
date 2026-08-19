plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// Several tv-material3 components used throughout the UI (FilterChip, Switch,
// ListItem, NavigationDrawer, CardContainer, ...) are marked
// @ExperimentalTvMaterial3Api — opting in once here beats annotating every
// call site across a dozen files. AGP 9's built-in Kotlin support (no
// org.jetbrains.kotlin.android plugin) means the classic android.kotlinOptions
// DSL isn't available — configuring the KotlinCompile tasks directly instead.
// ExperimentalMaterial3ExpressiveApi covers the one thing pulled from the
// base (non-TV) Material3 library — LoadingIndicator/ContainedLoadingIndicator,
// which tv-material3 has no equivalent of and which only exists pre-stable in
// material3 1.5.0-alpha (see the version comment in libs.versions.toml).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
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
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)
    // Only for ContainedLoadingIndicator — tv-material3 has no loading
    // indicator of any kind. Pinned independently of the compose.bom
    // platform above since the indicator is 1.5.0-alpha-only; the BOM's
    // June 2026 snapshot only covers material3 up to the 1.4.0 stable line.
    implementation(libs.compose.material3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
}
