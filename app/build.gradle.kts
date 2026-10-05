plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.monoapps.monolights"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.monoapps.monolights"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so the release APK is directly sideloadable
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    // Compress native libs: AGP 8.2 does not 16 KB-align uncompressed .so
    // files, which makes newer Android builds show a compatibility dialog
    // on debuggable apps. Compressed libs are exempt from the check.
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.3" }
}

dependencies {
    implementation("com.mudita:MMD:1.0.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
