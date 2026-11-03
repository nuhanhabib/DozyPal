plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase config (google-services.json) is git-ignored and comes from a GitHub secret in CI.
// Without it the app still builds and works offline; only Scroll Battles are unavailable.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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

// Kept to the minimum: Firebase (anonymous sign-in + database for Scroll Battles).
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
