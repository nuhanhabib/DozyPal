plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing values come only from environment variables (GitHub Actions secrets).
// Nothing secret is ever stored in this repository.
val keystorePath: String? = System.getenv("DOZYPAL_KEYSTORE_PATH")

android {
    namespace = "io.github.nuhanhabib.dozypal"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.nuhanhabib.dozypal"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = (System.getenv("VERSION_NAME") ?: "0.1.0").removePrefix("v")
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("DOZYPAL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DOZYPAL_KEY_ALIAS")
                keyPassword = System.getenv("DOZYPAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePath != null) {
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
}

// No third-party libraries on purpose: fewer packages, fewer things to patch.
