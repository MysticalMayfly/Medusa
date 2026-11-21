// Baseline profile generator for :app. Not part of the APK.
// Regenerate after big UI changes, with the Television_1080p emulator running:
//   ./gradlew :app:generateReleaseBaselineProfile
// The result is written to app/src/release/generated/baselineProfiles (commit it).
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.priyabrat.medusa.baselineprofile"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
