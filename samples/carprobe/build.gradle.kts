// carprobe: throwaway diagnostic app for the Geely FX11 head unit.
//
// It pokes the head-unit HUD / instrument-cluster APIs (DIM protocol, OneOS API hub,
// the stock map's widget Surface service) from a normal third-party app, so we can
// learn which of them a non-system app is allowed to use. Nothing here ships.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.diplay.carprobe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.diplay.carprobe"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
