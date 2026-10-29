import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rhinoshot.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rhinoshot.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.3.3"

        // MediaPipe ships native code for every ABI; keep the two that real
        // phones use plus x86_64 for the emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Signing is optional: without keystore.properties the release build falls
    // back to the debug key so the project always builds.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // The MediaPipe model must not be compressed so it can be memory-mapped.
    androidResources { noCompress += "task" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// ---------------------------------------------------------------------------
// Downloads the MediaPipe face-landmarker model on the first build.
// The build must not fail when the machine is offline: the app then reports a
// clear error at runtime instead of crashing.
// ---------------------------------------------------------------------------
val modelUrl = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
val modelFile = file("src/main/assets/face_landmarker.task")

val downloadModel by tasks.registering {
    description = "Downloads the MediaPipe Face Landmarker model into app assets."
    outputs.file(modelFile)
    doLast {
        if (!modelFile.exists() || modelFile.length() < 1_000_000L) {
            modelFile.parentFile.mkdirs()
            val tmp = File(modelFile.parentFile, "face_landmarker.task.part")
            try {
                URI(modelUrl).toURL().openStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                tmp.renameTo(modelFile)
                println("RhinoShot: model downloaded (${modelFile.length()} bytes)")
            } catch (e: Exception) {
                tmp.delete()
                println("RhinoShot: WARNING - could not download the MediaPipe model (${e.message}).")
                println("RhinoShot: place face_landmarker.task manually in app/src/main/assets/ before running on a device.")
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadModel) }
