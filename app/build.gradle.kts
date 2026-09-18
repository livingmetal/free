plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.livingmetal.salarytimer"
    compileSdk = 35
    defaultConfig {
        // The previous debug builds had incompatible signing certificates.
        // A new package allows installation without deleting the user's old settings.
        applicationId = "com.livingmetal.salarytimer.personal"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
        }
        debug { applicationIdSuffix = ".debug" }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
