plugins {
    id("com.android.application")
    kotlin("android")
}

val pluginFingerprint = project.findProperty("sandboxPluginFingerprint")?.toString() ?: ""

android {
    namespace = "com.example.host"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.host"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PLUGIN_SIGNATURE_SHA256", "\"$pluginFingerprint\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra", "-Werror")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":sandbox-core"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}

tasks.register<Copy>("stageTestPlugin") {
    dependsOn(":test-plugin:assembleDebug")
    from(project(":test-plugin").layout.buildDirectory.file("outputs/apk/debug/test-plugin-debug.apk"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "test_plugin.apk" }
}

tasks.named("preBuild") {
    dependsOn("stageTestPlugin")
}
