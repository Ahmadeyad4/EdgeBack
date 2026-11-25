plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.edgeback"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.edgeback"
        minSdk = 24
        targetSdk = 34
        versionCode = (findProperty("verCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("verName") as String?) ?: "1.0.0"
        buildConfigField("String", "REPO", "\"Ahmadeyad4/EdgeBack\"") <-- غيّرها: اسمك/اسم المستودع
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("rel") {
            val p = System.getenv("KS_PATH")
            if (p != null) {
                storeFile = file(p)
                storePassword = System.getenv("KS_PASS")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASS")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = if (System.getenv("KS_PATH") != null)
                signingConfigs.getByName("rel") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
