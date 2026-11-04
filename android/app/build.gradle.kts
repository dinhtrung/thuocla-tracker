plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

/*
 * Release signing comes from machine-local Gradle properties — never from this repo:
 *
 *   thuoclaStoreFile=/home/dinhtrung/keystores/thuocla-release.jks
 *   thuoclaStorePassword=…
 *   thuoclaKeyAlias=thuocla
 *   thuoclaKeyPassword=…
 *
 * They live in ~/.gradle/gradle.properties (outside git) so neither the keystore nor a password can
 * be committed. When any of them is missing the release build still configures — it just produces an
 * unsigned APK — so a fresh clone and CI keep building without secrets (task 9.1).
 */
val thuoclaStoreFile: String? = providers.gradleProperty("thuoclaStoreFile").orNull
val thuoclaStorePassword: String? = providers.gradleProperty("thuoclaStorePassword").orNull
val thuoclaKeyAlias: String? = providers.gradleProperty("thuoclaKeyAlias").orNull
val thuoclaKeyPassword: String? = providers.gradleProperty("thuoclaKeyPassword").orNull
val hasReleaseSigning = !thuoclaStoreFile.isNullOrBlank() &&
    !thuoclaStorePassword.isNullOrBlank() &&
    !thuoclaKeyAlias.isNullOrBlank() &&
    !thuoclaKeyPassword.isNullOrBlank()

android {
    namespace = "vn.dinhtrung.thuocla"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.dinhtrung.thuocla"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(thuoclaStoreFile!!)
                storePassword = thuoclaStorePassword
                keyAlias = thuoclaKeyAlias
                keyPassword = thuoclaKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Absent properties => no signingConfig, so the APK is unsigned instead of the build failing.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
