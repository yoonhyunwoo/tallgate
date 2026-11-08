plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.yhw.tallgate"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.yhw.tallgate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
