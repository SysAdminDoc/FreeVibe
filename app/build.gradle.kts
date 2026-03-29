import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.freevibe"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("signing.keystore.path", "../freevibe.jks"))
            storePassword = localProps.getProperty("signing.keystore.password", "")
            keyAlias = localProps.getProperty("signing.key.alias", "")
            keyPassword = localProps.getProperty("signing.key.password", "")
        }
    }

    defaultConfig {
        applicationId = "com.freevibe"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "4.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API keys — defaults baked in, user can override via settings
        buildConfigField("String", "PEXELS_API_KEY", "\"${localProps.getProperty("pexels.api.key", "3AN2RtNJNs6cT4M04xUzN1EuojlmC9283l6l3yPKaYQ7ez0rcFLwvpHP")}\"")
        buildConfigField("String", "PIXABAY_API_KEY", "\"${localProps.getProperty("pixabay.api.key", "24952670-25430be562a78b27d4746e060")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Image Loading
    implementation(libs.coil.compose)

    // Media Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // WorkManager
    implementation(libs.work.runtime)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // DataStore
    implementation(libs.datastore)

    // Palette (Material You color extraction)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.serialization.json)

    // Glance Widgets
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // NewPipe Extractor (YouTube search without API key)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.8")

    // yt-dlp for Android (YouTube stream URL extraction)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
