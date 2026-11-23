plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.dragolabs.able"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "com.dragolabs.able"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 9
        versionName = "0.9.0-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Italian and English in v1 (ADR-004); per-app language support via locales_config.
        localeFilters += listOf("en", "it")
    }

    buildTypes {
        release {
            // R8 shrinking and optimisation (ADR-052): debug builds are several times larger and
            // run Compose without optimisations, which made screen transitions stutter.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Beta builds stay signed with the same key as 0.6-0.8 so the in-app updater can install
            // over them (ADR-052). A dedicated release key is a separate owner decision: switching
            // to it requires users to uninstall once.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // ACRA reads version information from BuildConfig.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)

    implementation(projects.feature.home)
    implementation(projects.feature.battery)
    implementation(projects.feature.apps)
    implementation(projects.feature.tools)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.biometric)
    // Floating navigation bar background blur (Apache-2.0).
    implementation(libs.haze)
    // Blur effect for Haze 2.x lives in its own artifact (same project, same version and license).
    implementation("dev.chrisbanes.haze:haze-blur:${libs.versions.haze.get()}")

    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(libs.acra.mail)
    implementation(libs.acra.dialog)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
