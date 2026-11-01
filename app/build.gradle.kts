import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The signing key's location and passwords, kept in signing/ (which git ignores) instead of in this file.
val signingProps = Properties().apply {
    rootProject.file("signing/keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.example.mysmarttv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mysmarttv"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

    }

    signingConfigs {
        create("mysmarttv") {
            storeFile = rootProject.file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        // Debug and release share one key, so either can install over the other on the TV without
        // uninstalling, which would wipe the saved dashboard and settings.
        debug {
            signingConfig = signingConfigs.getByName("mysmarttv")
        }
        release {
            signingConfig = signingConfigs.getByName("mysmarttv")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.jsoup)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}