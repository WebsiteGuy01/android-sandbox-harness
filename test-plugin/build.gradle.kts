plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.testplugin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.testplugin"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}
