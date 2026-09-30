plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zapshare.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zapshare.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        // yt-dlp / FFmpeg ship native binaries per CPU. Keep the ABIs you need.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            // Kept off so the embedded Python/yt-dlp runtime is not stripped.
            // Turn on only after testing with proguard-rules.pro.
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // Required by youtubedl-android (python + ffmpeg are executed from nativeLibraryDir)
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.biometric:biometric:1.1.0")
    // In-app language switching (per-app locales, works back to Android 8)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Premium: Google Play Billing (free library; Google takes its standard store fee on sales)
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    // Ads for free users (Google AdMob, free SDK – replace the test IDs with yours)
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Images + video thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // Music + video player (free, Google's open-source ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // QR code generate + scan (open source, on-device)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Tiny embedded web server for "Connect to PC"
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Social video downloader + media converter (yt-dlp + FFmpeg, open source, no API keys)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
