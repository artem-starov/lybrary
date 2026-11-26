plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nmt.navchannia"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nmt.navchannia"
        minSdk = 21
        targetSdk = 34
        versionCode = (findProperty("vCode") as String?)?.toInt() ?: 2
        versionName = (findProperty("vName") as String?) ?: "1.1"
    }
    // Постійний ключ підпису: нові версії встановлюються поверх старих без втрати прогресу
    signingConfigs {
        getByName("debug") {
            storeFile = file("nmt.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
