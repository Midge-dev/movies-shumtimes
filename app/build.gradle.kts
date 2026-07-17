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

        // Injected from the RELAY_URL repo secret in CI (see
        // .github/workflows/build-apk.yml) so a sideloaded APK comes with
        // the relay pre-filled — nobody has to type a wss:// URL on a TV
        // remote. Falls back to empty (Settings screen's own default kicks
        // in) for local dev builds where the property isn't set.
        buildConfigField("String", "DEFAULT_RELAY_URL", "\"${project.findProperty("relayUrl") ?: ""}\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)
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
