plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)   // Room annotation processing
}

android {
    namespace = "com.example.sipatkilatis"
    compileSdk {
        version = release(37)   // compile against the newest SDK; runtime behaviour follows targetSdk
    }

    defaultConfig {
        applicationId = "com.example.sipatkilatis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime + MediaPipe ship big native libraries per CPU type. Keep only 64-bit ARM (real phones)
        // and x86_64 (emulator); this cuts ~110 MB. Add "armeabi-v7a" if a demo phone is 32-bit only.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the code; keep rules for ONNX Runtime / MediaPipe are in src/main/keepRules
            optimization {
                enable = true
            }
            // Hackathon build: signed with the debug key so it installs by sideloading (not for the Play Store).
            // For a store release, create a real keystore and keep it (and its password) out of git.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        generateLocaleConfig = true   // builds the English / Filipino list for per-app language settings
        noCompress += "onnx"          // models are stored as-is in the APK, so copying them out on first run is fast
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Sipat Kilatis
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.onnxruntime.android)      // ML classifier (phase 4)
    implementation(libs.mediapipe.tasks.genai)    // local LLM explainer (phase 6)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
