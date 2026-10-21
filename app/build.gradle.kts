plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

android {
    namespace = "sa.org.talmaarifa.tvsignage"
    compileSdk = 34

    defaultConfig {
        applicationId = "sa.org.talmaarifa.tvsignage"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Overridable per build without recompiling source: `-PsignageBaseUrl=https://portal.example.sa`
        // (gap: the API contract documents no device-side discovery mechanism for the portal's base
        // URL — see android-tv-client/README.md "Gaps vs. the documented contract" §1).
        buildConfigField(
            "String",
            "DEFAULT_BASE_URL",
            "\"${project.findProperty("signageBaseUrl") ?: "https://portal.t-almaarifa.org.sa"}\"",
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

val media3Version = "1.4.1"
val roomVersion = "2.6.1"
val workVersion = "2.9.1"
val composeTvVersion = "1.0.0"
val tvFoundationVersion = "1.0.0"

dependencies {
    implementation(project(":core"))

    // Compose for TV (docs: https://developer.android.com/training/tv/playback/compose)
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.tv:tv-material:$composeTvVersion")
    implementation("androidx.tv:tv-foundation:$tvFoundationVersion")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3 / ExoPlayer — playback of looping image+video playlists
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Room — local publication/media cache state + playback event log
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager — periodic manifest/media sync
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    // Retrofit/OkHttp — device API client
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // EncryptedSharedPreferences — device credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // QR rendering for the pairing screen
    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
