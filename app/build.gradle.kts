plugins {
    alias(libs.plugins.android.application)
}

// CalVer YYYY.MM.MICRO. versionCode is derived from it: (YY * 10000) + (MM * 100) + MICRO.
val calVer = "2026.11.0"
val calVerParts = calVer.split(".").map { it.toInt() }
require(calVerParts.size == 3 && calVerParts[2] in 0..99) { "versionName must be YYYY.MM.MICRO (MICRO 0-99): $calVer" }
val calVerCode = (calVerParts[0] % 100) * 10000 + calVerParts[1] * 100 + calVerParts[2]

// Upload-key credentials come from Gradle properties (~/.gradle/gradle.properties) or env vars (CI secrets).
// If any is missing, release builds stay unsigned so debug/CI builds keep working.
fun uploadKeyValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

val uploadStoreFile = uploadKeyValue("UPLOAD_STORE_FILE")
val uploadStorePassword = uploadKeyValue("UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = uploadKeyValue("UPLOAD_KEY_ALIAS")
val uploadKeyPassword = uploadKeyValue("UPLOAD_KEY_PASSWORD")
val hasUploadKey = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { !it.isNullOrEmpty() }

android {
    namespace = "dev.yhw.tallgate"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.yhw.tallgate"
        minSdk = 26
        targetSdk = 35
        versionCode = calVerCode
        versionName = calVer
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasUploadKey) signingConfig = signingConfigs.getByName("release")
        }
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
