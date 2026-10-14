import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing details live in keystore.properties (git-ignored). Without it, release builds are simply unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.conley.game"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.conley.echoloop"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Native debug symbols so Play Console can symbolicate native crashes and ANRs.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 minification is off: with it on, the release build crashed at startup
            // (IllegalAccessError on repackaged kotlin.collections classes). The app is small, so size is not a concern.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}