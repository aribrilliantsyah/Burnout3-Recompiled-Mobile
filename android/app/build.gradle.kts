// The Android shell around the recompiled game. The native code
// (libmain.so, libSDL2.so) is built by scripts/android/b3x.sh and copied
// into src/main/jniLibs/arm64-v8a/ (git-ignored) before Gradle runs.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.ryuna.b3x"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ryuna.b3x"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed until a release key exists.
            signingConfig = signingConfigs.getByName("debug")
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
