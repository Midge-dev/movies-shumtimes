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
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api")
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
