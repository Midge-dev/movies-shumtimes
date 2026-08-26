plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.moviesshumtimes.tv.shared"
        compileSdk = 36
        minSdk = 26
    }

    tvosArm64()
    tvosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
        }
        // OkHttp engine on Android — same underlying HTTP stack as before
        // this migration, just driven through Ktor's common API instead of
        // OkHttp's directly.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        // NSURLSession-backed engine, the only one that runs on Apple
        // platforms.
        tvosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
