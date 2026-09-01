plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.levo_sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }


    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/main/java"))
            res.setSrcDirs(listOf("src/main/res"))
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}
kotlin {
    jvmToolchain(25)
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
