plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.facemocap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.facemocap"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.2.6"
        ndk {
            abiFilters += listOf("arm64-v8a")
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
        viewBinding = true
    }

    // Model files (.task) should not be compressed by AAPT
    androidResources {
        noCompress += "task"
    }
}

// AGP 9.x built-in Kotlin: configure via the top-level `kotlin` extension instead of
// the old android.kotlinOptions {} block.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.14.0")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe Tasks Vision (Face Landmarker) - check for a newer version at
    // https://developers.google.com/mediapipe/solutions/vision/face_landmarker
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    implementation("androidx.activity:activity-ktx:1.13.0")
}
