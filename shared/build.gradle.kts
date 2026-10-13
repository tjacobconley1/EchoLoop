import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "com.conley.game.shared"
        compileSdk = 37
        minSdk = 24
    }

    // JVM target: used for fast unit tests and the offline level generator (./gradlew :shared:jvmRun).
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun { mainClass.set("com.conley.game.tools.GenerateLevelsKt") }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


