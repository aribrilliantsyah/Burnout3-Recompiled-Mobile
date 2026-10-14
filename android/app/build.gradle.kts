// The Android shell around the recompiled game. The native code
// (libmain.so, libSDL2.so) is built by scripts/android/b3x.sh and copied
// into src/main/jniLibs/arm64-v8a/ (git-ignored) before Gradle runs.
plugins {
    id("com.android.application")
}

import java.util.Properties

fun props(name: String): Properties {
    val p = Properties()
    val f = rootProject.file(name)
    if (f.exists()) f.inputStream().use { p.load(it) }
    return p
}

// android/version.properties: VERSION_NAME=major.minor.patch
val appVersion: String = props("version.properties").getProperty("VERSION_NAME", "0.0.1")
val versionParts = appVersion.split(".").map { it.toInt() }
val versionCodeFromName = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

// android/keystore.properties (git-ignored), written by
// scripts/android/keystore.sh: storeFile, storePassword, keyAlias,
// keyPassword. Without it release builds fall back to the debug key.
val keystore = props("keystore.properties")

android {
    namespace = "com.ryuna.b3x"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ryuna.b3x"
        minSdk = 30
        targetSdk = 36
        versionCode = versionCodeFromName
        versionName = appVersion
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystore.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Keep the .so files uncompressed and page-aligned in the APK.
            useLegacyPackaging = false
        }
    }
}
