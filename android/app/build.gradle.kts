plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.sqltraining.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.sqltraining.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.5.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    val releaseKeyFile = System.getenv("ANDROID_SIGNING_STORE_FILE")
    val releasePassword = System.getenv("ANDROID_SIGNING_PASSWORD")
    if (!releaseKeyFile.isNullOrBlank() && !releasePassword.isNullOrBlank()) {
        signingConfigs {
            create("releaseUpload") {
                storeFile = file(releaseKeyFile)
                storePassword = releasePassword
                keyAlias = "sqltrainer"
                keyPassword = releasePassword
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("releaseUpload")
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
