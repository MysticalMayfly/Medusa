import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

// Release key (kept out of git). Same key signs debug + release so the TV can always update.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

// The central catalogue's Supabase project (git-ignored). The key is the publishable one: it can only read the view.
val supabaseProps = Properties().apply {
    val f = rootProject.file("supabase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.priyabrat.medusa"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.priyabrat.medusa"
        minSdk = 34
        targetSdk = 37
        // versionCode +1 on EVERY release (never reuse/lower). versionName only for a major update.
        versionCode = 21
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseProps.getProperty("url", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${supabaseProps.getProperty("publishableKey", "")}\"")
        // Google sign-in: the web client id is the audience of the ID token Supabase checks (public, not a secret).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${supabaseProps.getProperty("googleWebClientId", "")}\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("medusa") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("medusa")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            proguardFiles("proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("medusa")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Speed: screen state in ui/model is immutable, so Compose can skip cards and rows that did not change.
composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Compose for TV (UI). Screens live in ui/, data comes only through data/MedusaRepository.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.tv.material)
    // Posters and backdrops from TMDB's image server (one image library, as the stage 1 plan said).
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    // Carousel trailers: YouTube's own embedded player in a WebView (no stream extraction; the stage 1 plan).
    implementation(libs.youtube.player)
    // Sign in with Google (Credential Manager), exchanged for a Supabase session.
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    // Speed: installs the baseline profile (src/release/generated/baselineProfiles) so the TV runs
    // Medusa's scrolling and focus code precompiled from the first launch instead of interpreted.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
