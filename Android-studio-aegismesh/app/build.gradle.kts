plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aegismesh"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aegismesh"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Real BASE_URL now flows through BuildConfig instead of the
            // hardcoded 10.0.2.2 fallback inside ApiClient.getBaseUrl().
            // 10.0.2.2 only resolves on the Android EMULATOR, not physical
            // devices -- every request from a real phone was failing at the
            // connection level regardless of what ApiClient logged.
            //
            // Update this IP whenever your dev machine's LAN address changes
            // (check with `ipconfig` on Windows / `ifconfig` or `ip addr` on
            // Mac/Linux). Phone and dev machine must be on the same Wi-Fi
            // network, and the backend must be started with --host 0.0.0.0
            // so it actually listens on the LAN interface, not just localhost.
            buildConfigField("String", "BASE_URL", "\"http://192.168.0.104:8000/\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://your-production-domain.example.com/\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Merged into a single buildFeatures block -- a second buildFeatures {}
    // block previously appeared later in this file (with viewBinding +
    // dataBinding only). In Kotlin DSL that second block does NOT merge with
    // the first; it can silently drop compose = true depending on how AGP
    // resolves duplicate blocks. buildConfig = true is required (not on by
    // default in current AGP) for BuildConfig.BASE_URL above to actually
    // generate -- this is very likely why every ApiClient log this session
    // showed "BuildConfig.BASE_URL not found. Using development URL."
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.work:work-runtime:2.9.0")
}
